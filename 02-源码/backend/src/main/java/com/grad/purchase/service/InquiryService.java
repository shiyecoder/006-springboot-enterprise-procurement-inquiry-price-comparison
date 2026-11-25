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
 * 询价比价单服务：
 * 状态机 草稿→报价中→待比价→待审批→已定标，另有已作废分支；
 * 发布校验至少 2 家供应商；截止后锁定报价；非最低价中标必须填写原因。
 */
@Service
public class InquiryService {

    private final InquiryMapper inquiryMapper;
    private final InquiryItemMapper itemMapper;
    private final InquirySupplierMapper inquirySupplierMapper;
    private final SupplierQuoteMapper quoteMapper;
    private final QuoteItemMapper quoteItemMapper;
    private final SupplierMapper supplierMapper;
    private final RequestItemMapper requestItemMapper;
    private final PurchaseRequestMapper requestMapper;
    private final FlowLogMapper flowLogMapper;
    private final ScoreSnapshotMapper snapshotMapper;
    private final ComparisonResultMapper comparisonMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final OrderService orderService;
    private final CompareService compareService;
    private final LogService logService;

    public InquiryService(InquiryMapper inquiryMapper, InquiryItemMapper itemMapper,
                          InquirySupplierMapper inquirySupplierMapper, SupplierQuoteMapper quoteMapper,
                          QuoteItemMapper quoteItemMapper, SupplierMapper supplierMapper,
                          RequestItemMapper requestItemMapper, PurchaseRequestMapper requestMapper,
                          FlowLogMapper flowLogMapper, ScoreSnapshotMapper snapshotMapper,
                          ComparisonResultMapper comparisonMapper, PurchaseOrderMapper purchaseOrderMapper,
                          OrderService orderService,
                          CompareService compareService, LogService logService) {
        this.inquiryMapper = inquiryMapper;
        this.itemMapper = itemMapper;
        this.inquirySupplierMapper = inquirySupplierMapper;
        this.quoteMapper = quoteMapper;
        this.quoteItemMapper = quoteItemMapper;
        this.supplierMapper = supplierMapper;
        this.requestItemMapper = requestItemMapper;
        this.requestMapper = requestMapper;
        this.flowLogMapper = flowLogMapper;
        this.snapshotMapper = snapshotMapper;
        this.comparisonMapper = comparisonMapper;
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.orderService = orderService;
        this.compareService = compareService;
        this.logService = logService;
    }

    public PageResult<Map<String, Object>> page(int page, int size, String keyword, String status, String tab) {
        LambdaQueryWrapper<Inquiry> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(Inquiry::getInquiryNo, keyword).or().like(Inquiry::getTitle, keyword));
        }
        if (status != null && !status.isBlank()) {
            w.eq(Inquiry::getStatus, status);
        }
        if ("todo".equals(tab)) {
            w.in(Inquiry::getStatus, List.of("COMPARING", "APPROVING", "QUOTING"));
        } else if ("done".equals(tab)) {
            w.eq(Inquiry::getStatus, "AWARDED");
        } else if ("abnormal".equals(tab)) {
            w.in(Inquiry::getStatus, List.of("VOID"));
        }
        // 待本人处理优先：按状态排序，再按更新时间倒序
        w.last("order by field(status,'COMPARING','APPROVING','QUOTING','DRAFT','AWARDED','VOID'), update_time desc");
        Page<Inquiry> p = inquiryMapper.selectPage(new Page<>(page, size), w);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Inquiry i : p.getRecords()) {
            List<SupplierQuote> quotes = quoteMapper.selectList(new LambdaQueryWrapper<SupplierQuote>()
                    .eq(SupplierQuote::getInquiryId, i.getId())
                    .in(SupplierQuote::getStatus, List.of("SUBMITTED", "LOCKED")));
            BigDecimal min = quotes.stream().map(SupplierQuote::getTotalAmount).min(BigDecimal::compareTo).orElse(null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", i.getId());
            m.put("inquiryNo", i.getInquiryNo());
            m.put("title", i.getTitle());
            m.put("requestIds", i.getRequestIds());
            m.put("budgetAmount", i.getBudgetAmount());
            m.put("deadline", i.getDeadline());
            m.put("status", i.getStatus());
            m.put("ruleVersion", i.getRuleVersion());
            m.put("awardReason", i.getAwardReason());
            m.put("supplierCount", inquirySupplierMapper.selectCount(new LambdaQueryWrapper<InquirySupplier>()
                    .eq(InquirySupplier::getInquiryId, i.getId())));
            m.put("quoteCount", quotes.size());
            m.put("minQuote", min);
            m.put("updateTime", i.getUpdateTime());
            list.add(m);
        }
        return new PageResult<>(p.getTotal(), p.getCurrent(), p.getSize(), list);
    }

    public Map<String, Object> detail(Long id) {
        Inquiry i = inquiryMapper.selectById(id);
        if (i == null) {
            throw new BizException("询价单不存在");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("inquiry", i);
        List<InquiryItem> items = itemMapper.selectList(new LambdaQueryWrapper<InquiryItem>()
                .eq(InquiryItem::getInquiryId, id));
        out.put("items", items);
        List<InquirySupplier> invited = inquirySupplierMapper.selectList(new LambdaQueryWrapper<InquirySupplier>()
                .eq(InquirySupplier::getInquiryId, id));
        List<Map<String, Object>> supplierRows = new ArrayList<>();
        for (InquirySupplier is : invited) {
            Supplier s = supplierMapper.selectById(is.getSupplierId());
            SupplierQuote q = quoteMapper.selectOne(new LambdaQueryWrapper<SupplierQuote>()
                    .eq(SupplierQuote::getInquiryId, id).eq(SupplierQuote::getSupplierId, is.getSupplierId())
                    .last("limit 1"));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("supplierId", is.getSupplierId());
            row.put("supplierName", s == null ? "-" : s.getSupplierName());
            row.put("rating", s == null ? "-" : s.getRating());
            row.put("quoteStatus", is.getQuoteStatus());
            row.put("quote", q);
            if (q != null) {
                row.put("quoteItems", quoteItemMapper.selectList(new LambdaQueryWrapper<QuoteItem>()
                        .eq(QuoteItem::getQuoteId, q.getId())));
            }
            supplierRows.add(row);
        }
        out.put("suppliers", supplierRows);
        out.put("flow", flowLogMapper.selectList(new LambdaQueryWrapper<FlowLog>()
                .eq(FlowLog::getBizType, "INQUIRY").eq(FlowLog::getBizId, id).orderByAsc(FlowLog::getId)));
        out.put("comparison", comparisonMapper.selectList(new LambdaQueryWrapper<ComparisonResult>()
                .eq(ComparisonResult::getInquiryId, id).orderByDesc(ComparisonResult::getTotalScore)));
        out.put("snapshots", snapshotMapper.selectList(new LambdaQueryWrapper<ScoreSnapshot>()
                .eq(ScoreSnapshot::getInquiryId, id).orderByDesc(ScoreSnapshot::getId)));
        out.put("orders", purchaseOrderMapper.selectList(new LambdaQueryWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getInquiryId, id)));
        return out;
    }

    /** 由需求明细生成询价单（合并 / 拆分都走这里，只决定纳入哪些明细） */
    @Transactional
    public Long fromRequests(List<Long> requestItemIds, String title, List<Long> supplierIds, String deadlineStr) {
        if (requestItemIds == null || requestItemIds.isEmpty()) {
            throw new BizException("请选择要纳入询价的需求明细");
        }
        if (supplierIds == null || supplierIds.size() < 2) {
            throw new BizException("询价单至少需要选择 2 家供应商");
        }
        List<RequestItem> items = requestItemMapper.selectList(new LambdaQueryWrapper<RequestItem>()
                .in(RequestItem::getId, requestItemIds));
        if (items.isEmpty()) {
            throw new BizException("需求明细不存在");
        }
        Set<Long> requestIds = items.stream().map(RequestItem::getRequestId).collect(Collectors.toCollection(LinkedHashSet::new));
        for (Long rid : requestIds) {
            PurchaseRequest r = requestMapper.selectById(rid);
            if (r == null) {
                throw new BizException("需求不存在：" + rid);
            }
            if (!List.of("PURCHASING", "DEPT_PENDING", "QUOTED").contains(r.getStatus())) {
                throw new BizException("需求 " + r.getRequestNo() + " 当前状态不允许询价");
            }
        }
        Inquiry inq = new Inquiry();
        inq.setInquiryNo("TMP" + System.nanoTime());
        inq.setTitle(title == null || title.isBlank() ? "询价单" : title.trim());
        inq.setBuyerId(UserContext.userId());
        inq.setRequestIds(requestIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
        BigDecimal budget = BigDecimal.ZERO;
        for (RequestItem ri : items) {
            budget = budget.add(ri.getQuantity().multiply(ri.getBudgetPrice()));
        }
        inq.setBudgetAmount(budget);
        inq.setDeadline(deadlineStr == null || deadlineStr.isBlank()
                ? LocalDateTime.now().plusDays(7) : LocalDateTime.parse(deadlineStr.replace(" ", "T")));
        inq.setTaxRate(new BigDecimal("13.00"));
        inq.setStatus("DRAFT");
        inq.setVersion(0);
        inq.setCreateTime(LocalDateTime.now());
        inq.setUpdateTime(LocalDateTime.now());
        inquiryMapper.insert(inq);
        inq.setInquiryNo("INQ" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"))
                + String.format("%04d", inq.getId()));
        inquiryMapper.updateById(inq);

        for (RequestItem ri : items) {
            InquiryItem ii = new InquiryItem();
            ii.setInquiryId(inq.getId());
            ii.setRequestItemId(ri.getId());
            ii.setMaterialName(ri.getMaterialName());
            ii.setSpec(ri.getSpec());
            ii.setUnit(ri.getUnit());
            ii.setQuantity(ri.getQuantity());
            itemMapper.insert(ii);
        }
        for (Long sid : supplierIds) {
            InquirySupplier is = new InquirySupplier();
            is.setInquiryId(inq.getId());
            is.setSupplierId(sid);
            is.setInvitedTime(LocalDateTime.now());
            is.setQuoteStatus("PENDING");
            inquirySupplierMapper.insert(is);
        }
        for (Long rid : requestIds) {
            PurchaseRequest r = requestMapper.selectById(rid);
            String from = r.getStatus();
            if (!"QUOTED".equals(from)) {
                r.setStatus("QUOTED");
                r.setCurrentHandler("采购专员");
                r.setUpdateTime(LocalDateTime.now());
                requestMapper.updateById(r);
                logService.record("REQUEST", rid, r.getRequestNo(), from, "QUOTED", "归并入询价单",
                        "纳入询价单 " + inq.getInquiryNo() + "，明细 " + items.size() + " 条");
            }
        }
        logService.record("INQUIRY", inq.getId(), inq.getInquiryNo(), null, "DRAFT", "创建询价单",
                "关联需求 " + requestIds.size() + " 条，邀约供应商 " + supplierIds.size() + " 家");
        return inq.getId();
    }

    @Transactional
    public void publish(Long id) {
        Inquiry i = inquiryMapper.selectById(id);
        if (i == null) {
            throw new BizException("询价单不存在");
        }
        if (!"DRAFT".equals(i.getStatus())) {
            throw new BizException("仅草稿状态可以发布");
        }
        long count = inquirySupplierMapper.selectCount(new LambdaQueryWrapper<InquirySupplier>()
                .eq(InquirySupplier::getInquiryId, id));
        if (count < 2) {
            throw new BizException("至少选择 2 家供应商才能发布询价");
        }
        if (i.getDeadline() == null || i.getDeadline().isBefore(LocalDateTime.now())) {
            throw new BizException("报价截止时间必须晚于当前时间");
        }
        i.setStatus("QUOTING");
        i.setUpdateTime(LocalDateTime.now());
        inquiryMapper.updateById(i);
        logService.record("INQUIRY", id, i.getInquiryNo(), "DRAFT", "QUOTING", "发布询价",
                "已邀约 " + count + " 家供应商，截止 " + i.getDeadline());
        logService.notify("询价单 " + i.getInquiryNo() + " 已发布，请及时报价", "INQUIRY", id, i.getInquiryNo(), null);
    }

    /** 报价截止锁定：报价中的询价单进入待比价，报价状态置为 LOCKED */
    @Transactional
    public void lockQuotes(Long id) {
        Inquiry i = inquiryMapper.selectById(id);
        if (i == null) {
            throw new BizException("询价单不存在");
        }
        if (!"QUOTING".equals(i.getStatus())) {
            throw new BizException("仅报价中状态可以锁定报价");
        }
        List<SupplierQuote> quotes = quoteMapper.selectList(new LambdaQueryWrapper<SupplierQuote>()
                .eq(SupplierQuote::getInquiryId, id));
        long valid = quotes.stream().filter(q -> List.of("SUBMITTED", "LOCKED").contains(q.getStatus())).count();
        if (valid < 2) {
            throw new BizException("有效报价不足 2 家，需延期或作废后重新询价");
        }
        for (SupplierQuote q : quotes) {
            if ("SUBMITTED".equals(q.getStatus())) {
                q.setStatus("LOCKED");
                quoteMapper.updateById(q);
            }
        }
        i.setStatus("COMPARING");
        i.setUpdateTime(LocalDateTime.now());
        inquiryMapper.updateById(i);
        logService.record("INQUIRY", id, i.getInquiryNo(), "QUOTING", "COMPARING", "报价截止锁定",
                "有效报价 " + valid + " 份，进入待比价");
    }

    /** 录入或更新供应商报价：小计与总价全部由后端重算 */
    @Transactional
    public void saveQuote(Long inquiryId, Map<String, Object> body) {
        Inquiry i = inquiryMapper.selectById(inquiryId);
        if (i == null) {
            throw new BizException("询价单不存在");
        }
        if (!List.of("QUOTING", "DRAFT").contains(i.getStatus())) {
            throw new BizException("当前状态不允许录入报价");
        }
        Long supplierId = Long.valueOf(String.valueOf(body.get("supplierId")));
        SupplierQuote q = quoteMapper.selectOne(new LambdaQueryWrapper<SupplierQuote>()
                .eq(SupplierQuote::getInquiryId, inquiryId).eq(SupplierQuote::getSupplierId, supplierId).last("limit 1"));
        boolean create = q == null;
        if (create) {
            q = new SupplierQuote();
            q.setInquiryId(inquiryId);
            q.setSupplierId(supplierId);
            q.setCreateTime(LocalDateTime.now());
        } else if ("LOCKED".equals(q.getStatus())) {
            throw new BizException("报价已锁定，如需修改请填写原因并联系采购主管");
        }
        q.setDeliveryDays(body.get("deliveryDays") == null ? 15 : Integer.valueOf(String.valueOf(body.get("deliveryDays"))));
        q.setPaymentTerm(body.get("paymentTerm") == null ? "月结 30 天" : String.valueOf(body.get("paymentTerm")));
        q.setWarrantyMonths(body.get("warrantyMonths") == null ? 12 : Integer.valueOf(String.valueOf(body.get("warrantyMonths"))));
        q.setRemark(body.get("remark") == null ? null : String.valueOf(body.get("remark")));
        q.setAttachment(body.get("attachment") == null ? null : String.valueOf(body.get("attachment")));
        q.setStatus("SUBMITTED");
        q.setQuoteTime(LocalDateTime.now());
        if (create) {
            quoteMapper.insert(q);
        } else {
            quoteItemMapper.delete(new LambdaQueryWrapper<QuoteItem>().eq(QuoteItem::getQuoteId, q.getId()));
        }
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (items == null || items.isEmpty()) {
            throw new BizException("请填写报价明细");
        }
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> it : items) {
            Long inquiryItemId = Long.valueOf(String.valueOf(it.get("inquiryItemId")));
            InquiryItem ii = itemMapper.selectById(inquiryItemId);
            if (ii == null) {
                throw new BizException("询价明细不存在");
            }
            BigDecimal price = new BigDecimal(String.valueOf(it.get("unitPrice")));
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("报价单价必须大于 0");
            }
            QuoteItem qi = new QuoteItem();
            qi.setQuoteId(q.getId());
            qi.setInquiryItemId(inquiryItemId);
            qi.setUnitPrice(price);
            qi.setQuantity(ii.getQuantity());
            qi.setAmount(price.multiply(ii.getQuantity()));
            quoteItemMapper.insert(qi);
            total = total.add(qi.getAmount());
        }
        q.setTotalAmount(total);
        q.setTaxAmount(total.divide(new BigDecimal("1.13"), 2, java.math.RoundingMode.HALF_UP)
                .multiply(new BigDecimal("0.13")).setScale(2, java.math.RoundingMode.HALF_UP));
        quoteMapper.updateById(q);

        InquirySupplier is = inquirySupplierMapper.selectOne(new LambdaQueryWrapper<InquirySupplier>()
                .eq(InquirySupplier::getInquiryId, inquiryId).eq(InquirySupplier::getSupplierId, supplierId).last("limit 1"));
        if (is != null) {
            is.setQuoteStatus("SUBMITTED");
            inquirySupplierMapper.updateById(is);
        }
        logService.record("INQUIRY", inquiryId, i.getInquiryNo(), i.getStatus(), i.getStatus(), "录入供应商报价",
                "供应商 " + supplierId + " 含税总价 " + total);
    }

    @Transactional
    public void submitApproval(Long id) {
        Inquiry i = inquiryMapper.selectById(id);
        if (i == null) {
            throw new BizException("询价单不存在");
        }
        if (!"COMPARING".equals(i.getStatus())) {
            throw new BizException("仅待比价状态可以提交审批");
        }
        long compared = comparisonMapper.selectCount(new LambdaQueryWrapper<ComparisonResult>()
                .eq(ComparisonResult::getInquiryId, id));
        if (compared == 0) {
            throw new BizException("请先执行综合比价再提交审批");
        }
        i.setStatus("APPROVING");
        i.setUpdateTime(LocalDateTime.now());
        inquiryMapper.updateById(i);
        logService.record("INQUIRY", id, i.getInquiryNo(), "COMPARING", "APPROVING", "提交定标审批", null);
        logService.notify("定标结果待审批：" + i.getTitle() + "（" + i.getInquiryNo() + "）", "INQUIRY", id, i.getInquiryNo(), "APPROVER");
    }

    /** 审批：通过则按推荐方案定标并生成采购单；驳回必须填原因 */
    @Transactional
    public void approve(Long id, boolean pass, String reason) {
        Inquiry i = inquiryMapper.selectById(id);
        if (i == null) {
            throw new BizException("询价单不存在");
        }
        if (!"APPROVING".equals(i.getStatus())) {
            throw new BizException("仅待审批状态可以审批");
        }
        if (!List.of("APPROVER", "ADMIN").contains(UserContext.role())) {
            throw new BizException(403, "只有审批人可以审批定标结果");
        }
        if (!pass) {
            if (reason == null || reason.trim().length() < 5) {
                throw new BizException("驳回必须填写原因，且不少于 5 个字");
            }
            i.setStatus("COMPARING");
            i.setUpdateTime(LocalDateTime.now());
            inquiryMapper.updateById(i);
            logService.record("INQUIRY", id, i.getInquiryNo(), "APPROVING", "COMPARING", "审批驳回", reason.trim());
            return;
        }
        ScoreSnapshot snap = snapshotMapper.selectOne(new LambdaQueryWrapper<ScoreSnapshot>()
                .eq(ScoreSnapshot::getInquiryId, id).orderByDesc(ScoreSnapshot::getId).last("limit 1"));
        if (snap == null || snap.getRecommendSupplierId() == null) {
            throw new BizException("缺少比价结果，无法定标");
        }
        Long targetSupplier = snap.getAdjustResult() == null ? snap.getRecommendSupplierId()
                : supplierMapper.selectList(new LambdaQueryWrapper<Supplier>()
                        .eq(Supplier::getSupplierName, snap.getAdjustResult())).stream()
                        .findFirst().map(Supplier::getId).orElse(snap.getRecommendSupplierId());
        doAward(i, targetSupplier, null, reason == null ? "审批通过，按综合推荐结果定标" : reason);
    }

    /**
     * 定标：支持按明细分配给不同供应商，校验数量不超过需求数量。
     * allocations 为空时，全部明细授予 supplierId。
     */
    @Transactional
    public List<Long> award(Long id, Long supplierId, List<Map<String, Object>> allocations, String reason) {
        Inquiry i = inquiryMapper.selectById(id);
        if (i == null) {
            throw new BizException("询价单不存在");
        }
        if (!List.of("APPROVING", "COMPARING").contains(i.getStatus())) {
            throw new BizException("当前状态不允许定标");
        }
        return doAward(i, supplierId, allocations, reason);
    }

    private List<Long> doAward(Inquiry i, Long supplierId, List<Map<String, Object>> allocations, String reason) {
        List<InquiryItem> items = itemMapper.selectList(new LambdaQueryWrapper<InquiryItem>()
                .eq(InquiryItem::getInquiryId, i.getId()));
        Map<Long, InquiryItem> itemMap = items.stream().collect(Collectors.toMap(InquiryItem::getId, x -> x));
        Map<Long, List<Map<String, Object>>> bySupplier = new LinkedHashMap<>();
        Map<Long, SupplierQuote> quoteCache = new HashMap<>();
        Map<Long, BigDecimal> allocated = new HashMap<>();

        List<Map<String, Object>> plan = new ArrayList<>();
        if (allocations == null || allocations.isEmpty()) {
            if (supplierId == null) {
                throw new BizException("请选择中标供应商");
            }
            for (InquiryItem it : items) {
                Map<String, Object> one = new LinkedHashMap<>();
                one.put("supplierId", supplierId);
                one.put("inquiryItemId", it.getId());
                one.put("quantity", it.getQuantity());
                plan.add(one);
            }
        } else {
            plan.addAll(allocations);
        }
        for (Map<String, Object> p : plan) {
            Long sid = Long.valueOf(String.valueOf(p.get("supplierId")));
            Long iid = Long.valueOf(String.valueOf(p.get("inquiryItemId")));
            BigDecimal qty = new BigDecimal(String.valueOf(p.get("quantity")));
            InquiryItem it = itemMap.get(iid);
            if (it == null) {
                throw new BizException("明细不属于本询价单");
            }
            BigDecimal used = allocated.getOrDefault(iid, BigDecimal.ZERO).add(qty);
            if (used.compareTo(it.getQuantity()) > 0) {
                throw new BizException("明细「" + it.getMaterialName() + "」分配数量超过需求数量 " + it.getQuantity());
            }
            allocated.put(iid, used);
            SupplierQuote q = quoteCache.computeIfAbsent(sid, k -> quoteMapper.selectOne(
                    new LambdaQueryWrapper<SupplierQuote>().eq(SupplierQuote::getInquiryId, i.getId())
                            .eq(SupplierQuote::getSupplierId, k).last("limit 1")));
            if (q == null) {
                throw new BizException("供应商 " + sid + " 未报价，不能定标");
            }
            QuoteItem qi = quoteItemMapper.selectOne(new LambdaQueryWrapper<QuoteItem>()
                    .eq(QuoteItem::getQuoteId, q.getId()).eq(QuoteItem::getInquiryItemId, iid).last("limit 1"));
            if (qi == null) {
                throw new BizException("该供应商缺少明细「" + it.getMaterialName() + "」的报价");
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("materialName", it.getMaterialName());
            row.put("spec", it.getSpec());
            row.put("unit", it.getUnit());
            row.put("quantity", qty);
            row.put("unitPrice", qi.getUnitPrice());
            row.put("deliveryDays", q.getDeliveryDays());
            bySupplier.computeIfAbsent(sid, k -> new ArrayList<>()).add(row);
        }
        // 非推荐 / 非最低价中标必须有说明
        ScoreSnapshot snap = snapshotMapper.selectOne(new LambdaQueryWrapper<ScoreSnapshot>()
                .eq(ScoreSnapshot::getInquiryId, i.getId()).orderByDesc(ScoreSnapshot::getId).last("limit 1"));
        Long recommendedId = snap == null ? null : snap.getRecommendSupplierId();
        boolean single = bySupplier.size() == 1;
        boolean deviate = single && recommendedId != null && !recommendedId.equals(bySupplier.keySet().iterator().next());
        if (deviate && (reason == null || reason.trim().length() < 5)) {
            throw new BizException("偏离综合推荐结果必须填写原因，且不少于 5 个字");
        }
        List<Long> orderIds = orderService.generate(i, bySupplier, items);
        i.setStatus("AWARDED");
        i.setAwardReason(reason);
        i.setUpdateTime(LocalDateTime.now());
        inquiryMapper.updateById(i);
        compareService.markRequestsQuoted(i.getRequestIds());
        logService.record("INQUIRY", i.getId(), i.getInquiryNo(), "APPROVING", "AWARDED", "定标完成",
                "生成采购单 " + orderIds.size() + " 张" + (reason == null ? "" : "，说明：" + reason));
        logService.oper("比价管理", "定标", i.getInquiryNo(), "生成采购单 " + orderIds.size() + " 张");
        return orderIds;
    }

    @Transactional
    public void voidInquiry(Long id, String reason) {
        Inquiry i = inquiryMapper.selectById(id);
        if (i == null) {
            throw new BizException("询价单不存在");
        }
        if ("AWARDED".equals(i.getStatus())) {
            throw new BizException("已定标的询价单不能作废");
        }
        if (reason == null || reason.trim().length() < 5) {
            throw new BizException("作废必须填写原因，且不少于 5 个字");
        }
        i.setStatus("VOID");
        i.setUpdateTime(LocalDateTime.now());
        inquiryMapper.updateById(i);
        logService.record("INQUIRY", id, i.getInquiryNo(), null, "VOID", "作废询价单", reason.trim());
    }

    public List<Map<String, Object>> quoteDesk(Long inquiryId) {
        Inquiry i = inquiryMapper.selectById(inquiryId);
        if (i == null) {
            throw new BizException("询价单不存在");
        }
        List<InquiryItem> items = itemMapper.selectList(new LambdaQueryWrapper<InquiryItem>()
                .eq(InquiryItem::getInquiryId, inquiryId));
        List<Map<String, Object>> out = new ArrayList<>();
        for (InquiryItem it : items) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("inquiryItemId", it.getId());
            m.put("materialName", it.getMaterialName());
            m.put("spec", it.getSpec());
            m.put("unit", it.getUnit());
            m.put("quantity", it.getQuantity());
            m.put("quotes", quoteItemMapper.selectList(new LambdaQueryWrapper<QuoteItem>()
                    .eq(QuoteItem::getInquiryItemId, it.getId())));
            out.add(m);
        }
        return out;
    }
}
