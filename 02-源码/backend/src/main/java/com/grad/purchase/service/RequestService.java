package com.grad.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.grad.purchase.common.BizException;
import com.grad.purchase.common.PageResult;
import com.grad.purchase.common.UserContext;
import com.grad.purchase.entity.*;
import com.grad.purchase.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 采购需求服务：
 * 状态机 草稿→待部门审批→待采购→已询价→已完成，含退回、取消分支；
 * 所有状态变更均由后端校验，并写入流转记录。
 */
@Service
public class RequestService {

    private final PurchaseRequestMapper requestMapper;
    private final RequestItemMapper itemMapper;
    private final MaterialMapper materialMapper;
    private final SysOrgMapper orgMapper;
    private final SysUserMapper userMapper;
    private final InquiryMapper inquiryMapper;
    private final FlowLogMapper flowLogMapper;
    private final LogService logService;

    public RequestService(PurchaseRequestMapper requestMapper, RequestItemMapper itemMapper,
                          MaterialMapper materialMapper, SysOrgMapper orgMapper, SysUserMapper userMapper,
                          InquiryMapper inquiryMapper, FlowLogMapper flowLogMapper, LogService logService) {
        this.requestMapper = requestMapper;
        this.itemMapper = itemMapper;
        this.materialMapper = materialMapper;
        this.orgMapper = orgMapper;
        this.userMapper = userMapper;
        this.inquiryMapper = inquiryMapper;
        this.flowLogMapper = flowLogMapper;
        this.logService = logService;
    }

    public PageResult<Map<String, Object>> page(int page, int size, String keyword, Long orgId, Long categoryId,
                                                String status, BigDecimal minAmount, BigDecimal maxAmount,
                                                String startDate, String endDate, String tab) {
        LambdaQueryWrapper<PurchaseRequest> w = new LambdaQueryWrapper<>();
        // 数据范围：申请人只看本人，部门负责人看本部门，其余按授权范围
        String scope = UserContext.get() == null ? "SELF" : UserContext.get().dataScope();
        String role = UserContext.role();
        if ("SELF".equals(scope)) {
            w.eq(PurchaseRequest::getApplicantId, UserContext.userId());
        } else if ("DEPT".equals(scope) && !"APPROVER".equals(role)) {
            w.eq(PurchaseRequest::getOrgId, UserContext.orgId());
        }
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(PurchaseRequest::getRequestNo, keyword)
                    .or().like(PurchaseRequest::getTitle, keyword));
        }
        if (orgId != null) {
            w.eq(PurchaseRequest::getOrgId, orgId);
        }
        if (categoryId != null) {
            w.eq(PurchaseRequest::getCategoryId, categoryId);
        }
        if (status != null && !status.isBlank()) {
            w.eq(PurchaseRequest::getStatus, status);
        }
        if (minAmount != null) {
            w.ge(PurchaseRequest::getBudgetAmount, minAmount);
        }
        if (maxAmount != null) {
            w.le(PurchaseRequest::getBudgetAmount, maxAmount);
        }
        if (startDate != null && !startDate.isBlank()) {
            w.ge(PurchaseRequest::getExpectDate, LocalDate.parse(startDate));
        }
        if (endDate != null && !endDate.isBlank()) {
            w.le(PurchaseRequest::getExpectDate, LocalDate.parse(endDate));
        }
        if ("todo".equals(tab)) {
            w.in(PurchaseRequest::getStatus, List.of("DEPT_PENDING", "PURCHASING"));
        } else if ("done".equals(tab)) {
            w.eq(PurchaseRequest::getStatus, "COMPLETED");
        } else if ("rejected".equals(tab)) {
            w.eq(PurchaseRequest::getStatus, "REJECTED");
        }
        w.orderByDesc(PurchaseRequest::getUpdateTime);

        Page<PurchaseRequest> p = requestMapper.selectPage(new Page<>(page, size), w);
        Map<Long, String> orgNames = orgMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysOrg::getId, SysOrg::getOrgName));
        Map<Long, String> userNames = userMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getRealName));
        List<Map<String, Object>> list = p.getRecords().stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("requestNo", r.getRequestNo());
            m.put("title", r.getTitle());
            m.put("orgId", r.getOrgId());
            m.put("orgName", orgNames.getOrDefault(r.getOrgId(), "-"));
            m.put("applicantName", userNames.getOrDefault(r.getApplicantId(), "-"));
            m.put("budgetAmount", r.getBudgetAmount());
            m.put("expectDate", r.getExpectDate() == null ? null : r.getExpectDate().toString());
            m.put("urgency", r.getUrgency());
            m.put("status", r.getStatus());
            m.put("currentHandler", r.getCurrentHandler());
            m.put("rejectReason", r.getRejectReason());
            m.put("createTime", r.getCreateTime());
            m.put("updateTime", r.getUpdateTime());
            m.put("itemCount", itemMapper.selectCount(new LambdaQueryWrapper<RequestItem>()
                    .eq(RequestItem::getRequestId, r.getId())));
            return m;
        }).collect(Collectors.toList());
        return new PageResult<>(p.getTotal(), p.getCurrent(), p.getSize(), list);
    }

    public Map<String, Object> detail(Long id) {
        PurchaseRequest r = requestMapper.selectById(id);
        if (r == null) {
            throw new BizException("需求不存在或已删除");
        }
        List<RequestItem> items = itemMapper.selectList(new LambdaQueryWrapper<RequestItem>()
                .eq(RequestItem::getRequestId, id));
        List<FlowLog> logs = flowLogMapper.selectList(new LambdaQueryWrapper<FlowLog>()
                .eq(FlowLog::getBizType, "REQUEST").eq(FlowLog::getBizId, id).orderByAsc(FlowLog::getId));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("request", r);
        out.put("orgName", orgMapper.selectById(r.getOrgId()) == null ? "-" : orgMapper.selectById(r.getOrgId()).getOrgName());
        SysUser applicant = userMapper.selectById(r.getApplicantId());
        out.put("applicantName", applicant == null ? "-" : applicant.getRealName());
        out.put("items", items);
        out.put("flow", logs);
        // 关联询价单：request_ids 里包含本需求即视为关联
        List<Inquiry> related = inquiryMapper.selectList(new LambdaQueryWrapper<Inquiry>()
                        .like(Inquiry::getRequestIds, String.valueOf(id)).orderByDesc(Inquiry::getId))
                .stream().filter(i -> Arrays.asList(i.getRequestIds().split(",")).contains(String.valueOf(id)))
                .collect(Collectors.toList());
        out.put("inquiries", related);
        return out;
    }

    /** 新建或保存草稿；submit=true 时直接进入待部门审批 */
    @Transactional
    public Long save(Map<String, Object> body, boolean submit) {
        Long id = body.get("id") == null ? null : Long.valueOf(String.valueOf(body.get("id")));
        PurchaseRequest r = id == null ? new PurchaseRequest() : requestMapper.selectById(id);
        if (r == null) {
            throw new BizException("需求不存在");
        }
        if (id != null && !"DRAFT".equals(r.getStatus()) && !"REJECTED".equals(r.getStatus())) {
            throw new BizException("仅草稿或已退回的需求可以编辑");
        }
        String title = str(body.get("title"));
        if (title == null || title.isBlank()) {
            throw new BizException("需求标题不能为空");
        }
        if (title.length() > 120) {
            throw new BizException("需求标题不能超过 120 个字符");
        }
        r.setTitle(title.trim());
        r.setPurpose(str(body.get("purpose")));
        r.setUrgency(body.get("urgency") == null ? "NORMAL" : str(body.get("urgency")));
        Object orgId = body.get("orgId");
        r.setOrgId(orgId == null ? UserContext.orgId() : Long.valueOf(String.valueOf(orgId)));
        r.setCategoryId(body.get("categoryId") == null ? null : Long.valueOf(String.valueOf(body.get("categoryId"))));
        if (body.get("expectDate") != null && !String.valueOf(body.get("expectDate")).isBlank()) {
            r.setExpectDate(LocalDate.parse(String.valueOf(body.get("expectDate"))));
        }
        if (r.getApplicantId() == null) {
            r.setApplicantId(UserContext.userId());
        }
        if (id == null) {
            r.setStatus("DRAFT");
            r.setVersion(0);
            r.setCreateTime(LocalDateTime.now());
            r.setUpdateTime(LocalDateTime.now());
            r.setRequestNo("TMP" + System.currentTimeMillis());
            requestMapper.insert(r);
            r.setRequestNo(genNo("PR", r.getId()));
            requestMapper.updateById(r);
            logService.record("REQUEST", r.getId(), r.getRequestNo(), null, "DRAFT", "创建需求", null);
        }

        // 明细：全量覆盖，后端重新汇总预算金额，避免前端篡改总额
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (items == null || items.isEmpty()) {
            throw new BizException("请至少添加一条需求明细");
        }
        itemMapper.delete(new LambdaQueryWrapper<RequestItem>().eq(RequestItem::getRequestId, r.getId()));
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> it : items) {
            RequestItem ri = new RequestItem();
            ri.setRequestId(r.getId());
            Long materialId = it.get("materialId") == null ? null : Long.valueOf(String.valueOf(it.get("materialId")));
            ri.setMaterialId(materialId);
            Material m = materialId == null ? null : materialMapper.selectById(materialId);
            String name = m != null ? m.getMaterialName() : str(it.get("materialName"));
            if (name == null || name.isBlank()) {
                throw new BizException("物料名称不能为空");
            }
            BigDecimal qty = dec(it.get("quantity"));
            if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("采购数量必须大于 0");
            }
            BigDecimal price = dec(it.get("budgetPrice"));
            if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
                throw new BizException("预算单价不能为负数");
            }
            ri.setMaterialName(m != null ? m.getMaterialName() : name);
            ri.setSpec(m != null ? m.getSpec() : str(it.get("spec")));
            ri.setUnit(m != null ? m.getUnit() : (str(it.get("unit")) == null ? "件" : str(it.get("unit"))));
            ri.setQuantity(qty);
            ri.setBudgetPrice(price);
            ri.setRemark(str(it.get("remark")));
            itemMapper.insert(ri);
            total = total.add(qty.multiply(price));
        }
        r.setBudgetAmount(total);
        r.setUpdateTime(LocalDateTime.now());

        if (submit) {
            if ("DRAFT".equals(r.getStatus()) || "REJECTED".equals(r.getStatus())) {
                String from = r.getStatus();
                r.setStatus("DEPT_PENDING");
                r.setSubmitTime(LocalDateTime.now());
                r.setCurrentHandler("部门负责人");
                r.setRejectReason(null);
                requestMapper.updateById(r);
                logService.record("REQUEST", r.getId(), r.getRequestNo(), from, "DEPT_PENDING", "提交部门审批",
                        "预算 " + total + " 元，明细 " + items.size() + " 条");
                logService.oper("需求管理", "提交需求", r.getRequestNo(), "提交至部门审批");
            } else {
                requestMapper.updateById(r);
            }
        } else {
            requestMapper.updateById(r);
        }
        return r.getId();
    }

    /** 状态流转：审批通过 / 退回 / 取消 / 标记已询价 / 完成 */
    @Transactional
    public void transition(Long id, String target, String reason) {
        PurchaseRequest r = requestMapper.selectById(id);
        if (r == null) {
            throw new BizException("需求不存在");
        }
        String from = r.getStatus();
        if (needReason(target) && (reason == null || reason.trim().length() < 5)) {
            throw new BizException("请填写原因，且不少于 5 个字");
        }
        switch (target) {
            case "DEPT_PENDING" -> {
                if (!List.of("DRAFT", "REJECTED").contains(from)) {
                    throw new BizException("当前状态不允许提交");
                }
                r.setStatus("DEPT_PENDING");
                r.setCurrentHandler("部门负责人");
                r.setSubmitTime(LocalDateTime.now());
                r.setRejectReason(null);
            }
            case "PURCHASING" -> {
                if (!"DEPT_PENDING".equals(from)) {
                    throw new BizException("仅待部门审批的需求可以通过");
                }
                if (!List.of("DEPT_HEAD", "ADMIN").contains(UserContext.role())) {
                    throw new BizException(403, "只有部门负责人可以审批需求");
                }
                r.setStatus("PURCHASING");
                r.setCurrentHandler("采购专员");
            }
            case "REJECTED" -> {
                if (!"DEPT_PENDING".equals(from)) {
                    throw new BizException("仅待部门审批的需求可以退回");
                }
                r.setStatus("REJECTED");
                r.setRejectReason(reason.trim());
                r.setCurrentHandler(null);
                logService.notifyUser(r.getApplicantId(), "需求 " + r.getRequestNo() + " 被退回，原因：" + reason,
                        "REQUEST", r.getId(), r.getRequestNo());
            }
            case "QUOTED" -> {
                if (!"PURCHASING".equals(from)) {
                    throw new BizException("仅待采购的需求可以标记为已询价");
                }
                r.setStatus("QUOTED");
                r.setCurrentHandler("采购专员");
            }
            case "COMPLETED" -> {
                if (!"QUOTED".equals(from)) {
                    throw new BizException("仅已询价的需求可以完成");
                }
                r.setStatus("COMPLETED");
            }
            case "CANCELLED" -> {
                if ("COMPLETED".equals(from)) {
                    throw new BizException("已完成的需求不能取消");
                }
                r.setStatus("CANCELLED");
            }
            default -> throw new BizException("不支持的目标状态：" + target);
        }
        r.setUpdateTime(LocalDateTime.now());
        r.setVersion((r.getVersion() == null ? 0 : r.getVersion()) + 1);
        requestMapper.updateById(r);
        logService.record("REQUEST", r.getId(), r.getRequestNo(), from, target, actionName(target), reason);
        logService.oper("需求管理", actionName(target), r.getRequestNo(), from + " → " + target);
    }

    private boolean needReason(String target) {
        return List.of("REJECTED", "CANCELLED").contains(target);
    }

    private String actionName(String target) {
        return switch (target) {
            case "DEPT_PENDING" -> "提交部门审批";
            case "PURCHASING" -> "部门审批通过";
            case "REJECTED" -> "部门退回";
            case "QUOTED" -> "归并入询价单";
            case "COMPLETED" -> "采购完成";
            case "CANCELLED" -> "取消需求";
            default -> "状态变更";
        };
    }

    /** 删除：仅草稿可删，且必须未被询价单引用 */
    @Transactional
    public void delete(Long id) {
        PurchaseRequest r = requestMapper.selectById(id);
        if (r == null) {
            throw new BizException("需求不存在");
        }
        if (!"DRAFT".equals(r.getStatus())) {
            throw new BizException("仅草稿状态的需求可以删除");
        }
        List<Inquiry> refs = inquiryMapper.selectList(new LambdaQueryWrapper<Inquiry>()
                .like(Inquiry::getRequestIds, String.valueOf(id)));
        if (!refs.isEmpty()) {
            throw new BizException("该需求已被询价单引用，不能删除");
        }
        itemMapper.delete(new LambdaQueryWrapper<RequestItem>().eq(RequestItem::getRequestId, id));
        requestMapper.deleteById(id);
        logService.oper("需求管理", "删除需求", r.getRequestNo(), "删除草稿需求");
    }

    /** 需求编号：PR + 年月 + 4 位序号 */
    public String genNo(String prefix, Long id) {
        return prefix + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM")) + String.format("%04d", id);
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static BigDecimal dec(Object o) {
        if (o == null || String.valueOf(o).isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(o));
        } catch (NumberFormatException e) {
            throw new BizException("数字格式不正确：" + o);
        }
    }
}
