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

/**
 * 到货验收服务：
 * 支持分批到货，登记合格数与不合格数；未足额交付的明细保持待到货，
 * 不合格数自动转为退货记录，全部到货后采购单进入待结单。
 */
@Service
public class ReceiptService {

    private final ReceiptRecordMapper receiptMapper;
    private final ReceiptItemMapper receiptItemMapper;
    private final PurchaseOrderMapper orderMapper;
    private final PurchaseOrderItemMapper orderItemMapper;
    private final ReturnRecordMapper returnMapper;
    private final SupplierMapper supplierMapper;
    private final LogService logService;

    public ReceiptService(ReceiptRecordMapper receiptMapper, ReceiptItemMapper receiptItemMapper,
                          PurchaseOrderMapper orderMapper, PurchaseOrderItemMapper orderItemMapper,
                          ReturnRecordMapper returnMapper, SupplierMapper supplierMapper, LogService logService) {
        this.receiptMapper = receiptMapper;
        this.receiptItemMapper = receiptItemMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.returnMapper = returnMapper;
        this.supplierMapper = supplierMapper;
        this.logService = logService;
    }

    /** 待验收明细：执行中/部分到货的采购单中尚未足额到货的明细 */
    public List<Map<String, Object>> pending() {
        List<PurchaseOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<PurchaseOrder>()
                .in(PurchaseOrder::getStatus, List.of("EXECUTING", "PARTIAL"))
                .orderByAsc(PurchaseOrder::getDeliveryDate).last("limit 40"));
        List<Map<String, Object>> out = new ArrayList<>();
        for (PurchaseOrder o : orders) {
            Supplier s = supplierMapper.selectById(o.getSupplierId());
            List<PurchaseOrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItem>()
                    .eq(PurchaseOrderItem::getOrderId, o.getId()));
            for (PurchaseOrderItem i : items) {
                BigDecimal received = i.getReceivedQty() == null ? BigDecimal.ZERO : i.getReceivedQty();
                if (received.compareTo(i.getQuantity()) < 0) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("orderId", o.getId());
                    m.put("orderNo", o.getOrderNo());
                    m.put("supplierName", s == null ? "-" : s.getSupplierName());
                    m.put("deliveryDate", o.getDeliveryDate() == null ? null : o.getDeliveryDate().toString());
                    m.put("status", o.getStatus());
                    m.put("orderItemId", i.getId());
                    m.put("materialName", i.getMaterialName());
                    m.put("spec", i.getSpec());
                    m.put("unit", i.getUnit());
                    m.put("quantity", i.getQuantity());
                    m.put("receivedQty", received);
                    m.put("pendingQty", i.getQuantity().subtract(received));
                    out.add(m);
                }
            }
        }
        return out;
    }

    public PageResult<Map<String, Object>> page(int page, int size, String conclusion, String keyword) {
        LambdaQueryWrapper<ReceiptRecord> w = new LambdaQueryWrapper<>();
        if (conclusion != null && !conclusion.isBlank()) {
            w.eq(ReceiptRecord::getConclusion, conclusion);
        }
        if (keyword != null && !keyword.isBlank()) {
            w.like(ReceiptRecord::getReceiptNo, keyword);
        }
        w.orderByDesc(ReceiptRecord::getId);
        Page<ReceiptRecord> p = receiptMapper.selectPage(new Page<>(page, size), w);
        Map<Long, PurchaseOrder> orders = new HashMap<>();
        orderMapper.selectList(null).forEach(o -> orders.put(o.getId(), o));
        Map<Long, String> supNames = new HashMap<>();
        supplierMapper.selectList(null).forEach(s -> supNames.put(s.getId(), s.getSupplierName()));
        List<Map<String, Object>> list = new ArrayList<>();
        for (ReceiptRecord r : p.getRecords()) {
            PurchaseOrder o = orders.get(r.getOrderId());
            List<ReceiptItem> items = receiptItemMapper.selectList(new LambdaQueryWrapper<ReceiptItem>()
                    .eq(ReceiptItem::getReceiptId, r.getId()));
            BigDecimal arrival = items.stream().map(i -> i.getArrivalQty() == null ? BigDecimal.ZERO : i.getArrivalQty())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal unq = items.stream().map(i -> i.getUnqualifiedQty() == null ? BigDecimal.ZERO : i.getUnqualifiedQty())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("receiptNo", r.getReceiptNo());
            m.put("orderNo", o == null ? "-" : o.getOrderNo());
            m.put("supplierName", o == null ? "-" : supNames.getOrDefault(o.getSupplierId(), "-"));
            m.put("batchNo", r.getBatchNo());
            m.put("receiptDate", r.getReceiptDate() == null ? null : r.getReceiptDate().toString());
            m.put("arrivalQty", arrival);
            m.put("unqualifiedQty", unq);
            m.put("conclusion", r.getConclusion());
            m.put("images", r.getImages());
            m.put("createTime", r.getCreateTime());
            list.add(m);
        }
        return new PageResult<>(p.getTotal(), p.getCurrent(), p.getSize(), list);
    }

    public Map<String, Object> detail(Long id) {
        ReceiptRecord r = receiptMapper.selectById(id);
        if (r == null) {
            throw new BizException("验收单不存在");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("receipt", r);
        out.put("order", orderMapper.selectById(r.getOrderId()));
        out.put("items", receiptItemMapper.selectList(new LambdaQueryWrapper<ReceiptItem>()
                .eq(ReceiptItem::getReceiptId, id)));
        out.put("returns", returnMapper.selectList(new LambdaQueryWrapper<ReturnRecord>()
                .eq(ReturnRecord::getOrderId, r.getOrderId())));
        return out;
    }

    /**
     * 提交验收：逐条校验到货数不超过待到货数，累加采购明细，必要时生成退货记录。
     */
    @Transactional
    public Long create(Map<String, Object> body) {
        Long orderId = body.get("orderId") == null ? null : Long.valueOf(String.valueOf(body.get("orderId")));
        PurchaseOrder o = orderId == null ? null : orderMapper.selectById(orderId);
        if (o == null) {
            throw new BizException("采购单不存在");
        }
        if (!List.of("EXECUTING", "PARTIAL").contains(o.getStatus())) {
            throw new BizException("当前采购单状态不允许验收");
        }
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (items == null || items.isEmpty()) {
            throw new BizException("请填写验收明细");
        }
        String images = body.get("images") == null ? "" : String.valueOf(body.get("images"));
        if (images.isBlank()) {
            throw new BizException("请上传至少一张验收照片");
        }
        ReceiptRecord record = new ReceiptRecord();
        record.setReceiptNo("TMP" + System.nanoTime());
        record.setOrderId(orderId);
        record.setBatchNo(body.get("batchNo") == null ? "第 1 批" : String.valueOf(body.get("batchNo")));
        record.setReceiptDate(body.get("receiptDate") == null ? LocalDate.now()
                : LocalDate.parse(String.valueOf(body.get("receiptDate"))));
        record.setConclusion(body.get("conclusion") == null ? "QUALIFIED" : String.valueOf(body.get("conclusion")));
        record.setRemark(body.get("remark") == null ? null : String.valueOf(body.get("remark")));
        record.setImages(images);
        record.setReceiverId(UserContext.userId());
        record.setCreateTime(LocalDateTime.now());
        receiptMapper.insert(record);
        record.setReceiptNo("RC" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"))
                + String.format("%04d", record.getId()));
        receiptMapper.updateById(record);

        boolean allArrived = true;
        for (Map<String, Object> it : items) {
            Long orderItemId = Long.valueOf(String.valueOf(it.get("orderItemId")));
            PurchaseOrderItem oi = orderItemMapper.selectById(orderItemId);
            if (oi == null || !Objects.equals(oi.getOrderId(), orderId)) {
                throw new BizException("采购明细与采购单不匹配");
            }
            BigDecimal arrival = dec(it.get("arrivalQty"));
            BigDecimal qualified = dec(it.get("qualifiedQty"));
            BigDecimal unqualified = dec(it.get("unqualifiedQty"));
            if (arrival.compareTo(BigDecimal.ZERO) < 0 || qualified.compareTo(BigDecimal.ZERO) < 0
                    || unqualified.compareTo(BigDecimal.ZERO) < 0) {
                throw new BizException("验收数量不能为负数");
            }
            if (arrival.compareTo(qualified.add(unqualified)) != 0) {
                throw new BizException("到货数必须等于合格数与不合格数之和");
            }
            BigDecimal received = oi.getReceivedQty() == null ? BigDecimal.ZERO : oi.getReceivedQty();
            BigDecimal remaining = oi.getQuantity().subtract(received);
            if (arrival.compareTo(remaining) > 0) {
                throw new BizException("到货数超过待到货数：" + oi.getMaterialName() + " 剩余 " + remaining);
            }
            if (unqualified.compareTo(BigDecimal.ZERO) > 0
                    && (it.get("reason") == null || String.valueOf(it.get("reason")).trim().length() < 5)) {
                throw new BizException("存在不合格品时必须填写原因（≥5 字）");
            }
            ReceiptItem ri = new ReceiptItem();
            ri.setReceiptId(record.getId());
            ri.setOrderItemId(orderItemId);
            ri.setArrivalQty(arrival);
            ri.setQualifiedQty(qualified);
            ri.setUnqualifiedQty(unqualified);
            ri.setReason(it.get("reason") == null ? null : String.valueOf(it.get("reason")));
            receiptItemMapper.insert(ri);

            oi.setReceivedQty(received.add(arrival));
            oi.setQualifiedQty((oi.getQualifiedQty() == null ? BigDecimal.ZERO : oi.getQualifiedQty()).add(qualified));
            oi.setUnqualifiedQty((oi.getUnqualifiedQty() == null ? BigDecimal.ZERO : oi.getUnqualifiedQty()).add(unqualified));
            orderItemMapper.updateById(oi);

            if (unqualified.compareTo(BigDecimal.ZERO) > 0) {
                ReturnRecord rr = new ReturnRecord();
                rr.setReturnNo("TMP" + System.nanoTime());
                rr.setOrderId(orderId);
                rr.setOrderItemId(orderItemId);
                rr.setSupplierId(o.getSupplierId());
                rr.setQuantity(unqualified);
                rr.setReason(String.valueOf(it.get("reason")));
                rr.setHandleType("EXCHANGE");
                rr.setStatus("WAIT_SUPPLIER");
                rr.setCreateTime(LocalDateTime.now());
                returnMapper.insert(rr);
                rr.setReturnNo("RT" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"))
                        + String.format("%04d", rr.getId()));
                returnMapper.updateById(rr);
            }
        }
        // 校验是否全部到货
        List<PurchaseOrderItem> all = orderItemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItem>()
                .eq(PurchaseOrderItem::getOrderId, orderId));
        for (PurchaseOrderItem i : all) {
            BigDecimal received = i.getReceivedQty() == null ? BigDecimal.ZERO : i.getReceivedQty();
            if (received.compareTo(i.getQuantity()) < 0) {
                allArrived = false;
                break;
            }
        }
        String from = o.getStatus();
        o.setStatus(allArrived ? "PENDING_CLOSE" : "PARTIAL");
        o.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(o);

        logService.record("RECEIPT", record.getId(), record.getReceiptNo(), null, record.getConclusion(),
                "提交到货验收", record.getRemark());
        logService.record("ORDER", orderId, o.getOrderNo(), from, o.getStatus(),
                allArrived ? "全部到货待结单" : "分批到货", record.getBatchNo() + " 验收完成");
        logService.oper("到货验收", "提交验收", record.getReceiptNo(), "采购单 " + o.getOrderNo());
        return record.getId();
    }

    public PageResult<Map<String, Object>> returnPage(int page, int size) {
        Page<ReturnRecord> p = returnMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ReturnRecord>().orderByDesc(ReturnRecord::getId));
        Map<Long, String> supNames = new HashMap<>();
        supplierMapper.selectList(null).forEach(s -> supNames.put(s.getId(), s.getSupplierName()));
        Map<Long, PurchaseOrder> orders = new HashMap<>();
        orderMapper.selectList(null).forEach(o -> orders.put(o.getId(), o));
        Map<Long, PurchaseOrderItem> items = new HashMap<>();
        orderItemMapper.selectList(null).forEach(i -> items.put(i.getId(), i));
        List<Map<String, Object>> list = new ArrayList<>();
        for (ReturnRecord r : p.getRecords()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("returnNo", r.getReturnNo());
            m.put("orderNo", orders.containsKey(r.getOrderId()) ? orders.get(r.getOrderId()).getOrderNo() : "-");
            m.put("supplierName", supNames.getOrDefault(r.getSupplierId(), "-"));
            m.put("materialName", items.containsKey(r.getOrderItemId()) ? items.get(r.getOrderItemId()).getMaterialName() : "-");
            m.put("quantity", r.getQuantity());
            m.put("reason", r.getReason());
            m.put("handleType", r.getHandleType());
            m.put("status", r.getStatus());
            m.put("createTime", r.getCreateTime());
            list.add(m);
        }
        return new PageResult<>(p.getTotal(), p.getCurrent(), p.getSize(), list);
    }

    private BigDecimal dec(Object o) {
        if (o == null || String.valueOf(o).isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(String.valueOf(o));
    }
}
