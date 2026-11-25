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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 采购单服务：由定标结果生成采购单，支持分批到货与结单。
 * 状态机 待下单→执行中→部分到货→待结单→已完成。
 */
@Service
public class OrderService {

    private final PurchaseOrderMapper orderMapper;
    private final PurchaseOrderItemMapper itemMapper;
    private final SupplierMapper supplierMapper;
    private final InquiryMapper inquiryMapper;
    private final ReceiptRecordMapper receiptMapper;
    private final LogService logService;

    public OrderService(PurchaseOrderMapper orderMapper, PurchaseOrderItemMapper itemMapper,
                        SupplierMapper supplierMapper, InquiryMapper inquiryMapper,
                        ReceiptRecordMapper receiptMapper, LogService logService) {
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.supplierMapper = supplierMapper;
        this.inquiryMapper = inquiryMapper;
        this.receiptMapper = receiptMapper;
        this.logService = logService;
    }

    public PageResult<Map<String, Object>> page(int page, int size, String keyword, String status,
                                                Long supplierId, String startDate, String endDate) {
        LambdaQueryWrapper<PurchaseOrder> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.like(PurchaseOrder::getOrderNo, keyword);
        }
        if (status != null && !status.isBlank()) {
            w.eq(PurchaseOrder::getStatus, status);
        }
        if (supplierId != null) {
            w.eq(PurchaseOrder::getSupplierId, supplierId);
        }
        if (startDate != null && !startDate.isBlank()) {
            w.ge(PurchaseOrder::getDeliveryDate, LocalDate.parse(startDate));
        }
        if (endDate != null && !endDate.isBlank()) {
            w.le(PurchaseOrder::getDeliveryDate, LocalDate.parse(endDate));
        }
        w.orderByDesc(PurchaseOrder::getUpdateTime);
        Page<PurchaseOrder> p = orderMapper.selectPage(new Page<>(page, size), w);
        Map<Long, String> supNames = new HashMap<>();
        supplierMapper.selectList(null).forEach(s -> supNames.put(s.getId(), s.getSupplierName()));
        Map<Long, String> inqNos = new HashMap<>();
        inquiryMapper.selectList(null).forEach(i -> inqNos.put(i.getId(), i.getInquiryNo()));

        List<Map<String, Object>> list = new ArrayList<>();
        for (PurchaseOrder o : p.getRecords()) {
            List<PurchaseOrderItem> items = itemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItem>()
                    .eq(PurchaseOrderItem::getOrderId, o.getId()));
            BigDecimal ordered = items.stream().map(PurchaseOrderItem::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal received = items.stream().map(i -> i.getReceivedQty() == null ? BigDecimal.ZERO : i.getReceivedQty())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            int progress = ordered.compareTo(BigDecimal.ZERO) == 0 ? 0
                    : received.multiply(new BigDecimal("100")).divide(ordered, 0, RoundingMode.HALF_UP).intValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.getId());
            m.put("orderNo", o.getOrderNo());
            m.put("inquiryNo", o.getInquiryId() == null ? null : inqNos.get(o.getInquiryId()));
            m.put("supplierName", supNames.getOrDefault(o.getSupplierId(), "-"));
            m.put("totalAmount", o.getTotalAmount());
            m.put("taxAmount", o.getTaxAmount());
            m.put("deliveryDate", o.getDeliveryDate() == null ? null : o.getDeliveryDate().toString());
            m.put("contact", o.getContact());
            m.put("status", o.getStatus());
            m.put("progress", progress);
            m.put("itemCount", items.size());
            m.put("createTime", o.getCreateTime());
            list.add(m);
        }
        return new PageResult<>(p.getTotal(), p.getCurrent(), p.getSize(), list);
    }

    public Map<String, Object> detail(Long id) {
        PurchaseOrder o = orderMapper.selectById(id);
        if (o == null) {
            throw new BizException("采购单不存在");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("order", o);
        Supplier s = supplierMapper.selectById(o.getSupplierId());
        out.put("supplier", s);
        out.put("items", itemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItem>()
                .eq(PurchaseOrderItem::getOrderId, id)));
        out.put("receipts", receiptMapper.selectList(new LambdaQueryWrapper<ReceiptRecord>()
                .eq(ReceiptRecord::getOrderId, id).orderByAsc(ReceiptRecord::getId)));
        return out;
    }

    /**
     * 按定标分配生成采购单：同一供应商的明细汇总为一张采购单，金额后端重新计算。
     */
    @Transactional
    public List<Long> generate(Inquiry inquiry, Map<Long, List<Map<String, Object>>> bySupplier,
                              List<InquiryItem> inquiryItems) {
        List<Long> ids = new ArrayList<>();
        for (Map.Entry<Long, List<Map<String, Object>>> e : bySupplier.entrySet()) {
            Long supplierId = e.getKey();
            Supplier sup = supplierMapper.selectById(supplierId);
            if (sup == null) {
                throw new BizException("供应商不存在：" + supplierId);
            }
            PurchaseOrder o = new PurchaseOrder();
            o.setOrderNo("TMP" + System.nanoTime());
            o.setInquiryId(inquiry.getId());
            o.setSupplierId(supplierId);
            o.setStatus("PENDING");
            o.setContact(sup.getContact());
            o.setContactPhone(sup.getPhone());
            o.setCreateBy(UserContext.userId());
            o.setVersion(0);
            o.setCreateTime(LocalDateTime.now());
            o.setUpdateTime(LocalDateTime.now());
            int maxDays = 0;
            BigDecimal total = BigDecimal.ZERO;
            for (Map<String, Object> it : e.getValue()) {
                maxDays = Math.max(maxDays, intOf(it.get("deliveryDays"), 7));
                BigDecimal qty = decimal(it.get("quantity"));
                BigDecimal price = decimal(it.get("unitPrice"));
                total = total.add(qty.multiply(price));
            }
            o.setDeliveryDate(LocalDate.now().plusDays(maxDays));
            o.setTotalAmount(total);
            o.setTaxAmount(total.divide(new BigDecimal("1.13"), 2, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("0.13")).setScale(2, RoundingMode.HALF_UP));
            orderMapper.insert(o);
            o.setOrderNo("PO" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"))
                    + String.format("%04d", o.getId()));
            orderMapper.updateById(o);

            for (Map<String, Object> it : e.getValue()) {
                PurchaseOrderItem oi = new PurchaseOrderItem();
                oi.setOrderId(o.getId());
                oi.setMaterialName(String.valueOf(it.get("materialName")));
                oi.setSpec(it.get("spec") == null ? null : String.valueOf(it.get("spec")));
                oi.setUnit(it.get("unit") == null ? "件" : String.valueOf(it.get("unit")));
                BigDecimal qty = decimal(it.get("quantity"));
                BigDecimal price = decimal(it.get("unitPrice"));
                oi.setQuantity(qty);
                oi.setUnitPrice(price);
                oi.setAmount(qty.multiply(price));
                oi.setReceivedQty(BigDecimal.ZERO);
                oi.setQualifiedQty(BigDecimal.ZERO);
                oi.setUnqualifiedQty(BigDecimal.ZERO);
                itemMapper.insert(oi);
            }
            ids.add(o.getId());
            logService.record("ORDER", o.getId(), o.getOrderNo(), null, "PENDING", "生成采购单",
                    "由询价单 " + inquiry.getInquiryNo() + " 定标结果生成，供应商 " + sup.getSupplierName());
        }
        return ids;
    }

    @Transactional
    public void execute(Long id) {
        PurchaseOrder o = orderMapper.selectById(id);
        if (o == null) {
            throw new BizException("采购单不存在");
        }
        if (!"PENDING".equals(o.getStatus())) {
            throw new BizException("仅待下单状态可以执行下单");
        }
        o.setStatus("EXECUTING");
        o.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(o);
        logService.record("ORDER", id, o.getOrderNo(), "PENDING", "EXECUTING", "下单确认", "已通知供应商按交期发货");
    }

    @Transactional
    public void close(Long id) {
        PurchaseOrder o = orderMapper.selectById(id);
        if (o == null) {
            throw new BizException("采购单不存在");
        }
        if (!"PENDING_CLOSE".equals(o.getStatus())) {
            throw new BizException("仅待结单状态可以确认结单");
        }
        o.setStatus("COMPLETED");
        o.setCloseTime(LocalDateTime.now());
        o.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(o);
        logService.record("ORDER", id, o.getOrderNo(), "PENDING_CLOSE", "COMPLETED", "确认结单",
                "全部明细验收完成，采购单关闭");
    }

    private static BigDecimal decimal(Object o) {
        if (o == null) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(String.valueOf(o));
    }

    private static int intOf(Object o, int def) {
        if (o == null) {
            return def;
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
