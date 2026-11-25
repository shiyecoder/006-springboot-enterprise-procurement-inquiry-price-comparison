package com.grad.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.grad.purchase.entity.*;
import com.grad.purchase.mapper.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 看板服务：首屏 6 个核心指标 + 6 个图表，全部后端聚合返回，前端不做二次全表统计。
 * 统计口径与明细列表一致，点击图表可带筛选条件回到列表。
 */
@Service
public class DashboardService {

    private final PurchaseOrderMapper orderMapper;
    private final InquiryMapper inquiryMapper;
    private final SupplierQuoteMapper quoteMapper;
    private final PurchaseRequestMapper requestMapper;
    private final SupplierMapper supplierMapper;
    private final MaterialMapper materialMapper;
    private final MaterialCategoryMapper categoryMapper;
    private final ScoreSnapshotMapper snapshotMapper;
    private final SysOrgMapper orgMapper;
    private final ReceiptRecordMapper receiptMapper;
    private final PurchaseOrderItemMapper orderItemMapper;

    public DashboardService(PurchaseOrderMapper orderMapper, InquiryMapper inquiryMapper,
                            SupplierQuoteMapper quoteMapper, PurchaseRequestMapper requestMapper,
                            SupplierMapper supplierMapper, MaterialMapper materialMapper,
                            MaterialCategoryMapper categoryMapper, ScoreSnapshotMapper snapshotMapper,
                            SysOrgMapper orgMapper, ReceiptRecordMapper receiptMapper,
                            PurchaseOrderItemMapper orderItemMapper) {
        this.orderMapper = orderMapper;
        this.inquiryMapper = inquiryMapper;
        this.quoteMapper = quoteMapper;
        this.requestMapper = requestMapper;
        this.supplierMapper = supplierMapper;
        this.materialMapper = materialMapper;
        this.categoryMapper = categoryMapper;
        this.snapshotMapper = snapshotMapper;
        this.orgMapper = orgMapper;
        this.receiptMapper = receiptMapper;
        this.orderItemMapper = orderItemMapper;
    }

    public Map<String, Object> overview() {
        List<PurchaseOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<PurchaseOrder>());
        List<Inquiry> inquiries = inquiryMapper.selectList(new LambdaQueryWrapper<Inquiry>());
        List<SupplierQuote> quotes = quoteMapper.selectList(new LambdaQueryWrapper<SupplierQuote>()
                .in(SupplierQuote::getStatus, List.of("SUBMITTED", "LOCKED")));
        List<PurchaseRequest> requests = requestMapper.selectList(new LambdaQueryWrapper<PurchaseRequest>());
        Map<Long, String> orgNames = new HashMap<>();
        orgMapper.selectList(null).forEach(o -> orgNames.put(o.getId(), o.getOrgName()));

        BigDecimal totalAmount = orders.stream().map(o -> o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // 节约金额：中标金额与预算的差额，按已定标询价单统计
        BigDecimal savedAmount = BigDecimal.ZERO;
        BigDecimal budgetSum = BigDecimal.ZERO;
        for (Inquiry i : inquiries) {
            if (!"AWARDED".equals(i.getStatus())) {
                continue;
            }
            List<PurchaseOrder> os = orders.stream().filter(o -> i.getId().equals(o.getInquiryId())).toList();
            BigDecimal actual = os.stream().map(o -> o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (i.getBudgetAmount() != null && i.getBudgetAmount().compareTo(BigDecimal.ZERO) > 0 && actual.compareTo(BigDecimal.ZERO) > 0) {
                budgetSum = budgetSum.add(i.getBudgetAmount());
                savedAmount = savedAmount.add(i.getBudgetAmount().subtract(actual).max(BigDecimal.ZERO));
            }
        }
        BigDecimal savingRate = budgetSum.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                : savedAmount.multiply(new BigDecimal("100")).divide(budgetSum, 1, RoundingMode.HALF_UP);

        double avgQuotes = inquiries.isEmpty() ? 0 : (double) quotes.size() / Math.max(1, inquiries.stream()
                .filter(i -> !"DRAFT".equals(i.getStatus()) && !"VOID".equals(i.getStatus())).count());

        List<ReceiptRecord> receipts = receiptMapper.selectList(new LambdaQueryWrapper<ReceiptRecord>());
        long qualified = receipts.stream().filter(r -> "QUALIFIED".equals(r.getConclusion())).count();
        BigDecimal onTimeRate = receipts.isEmpty() ? new BigDecimal("0")
                : new BigDecimal(qualified).multiply(new BigDecimal("100"))
                .divide(new BigDecimal(receipts.size()), 1, RoundingMode.HALF_UP);

        long todo = requests.stream().filter(r -> List.of("DEPT_PENDING", "PURCHASING").contains(r.getStatus())).count()
                + inquiries.stream().filter(i -> List.of("COMPARING", "APPROVING").contains(i.getStatus())).count()
                + orders.stream().filter(o -> List.of("PARTIAL", "PENDING_CLOSE").contains(o.getStatus())).count();

        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("totalAmount", totalAmount.setScale(2, RoundingMode.HALF_UP));
        kpi.put("savingRate", savingRate);
        kpi.put("savedAmount", savedAmount.setScale(2, RoundingMode.HALF_UP));
        kpi.put("avgQuoteCount", BigDecimal.valueOf(avgQuotes).setScale(1, RoundingMode.HALF_UP));
        kpi.put("onTimeRate", onTimeRate);
        kpi.put("todoCount", todo);
        kpi.put("inquiryCount", inquiries.size());
        kpi.put("orderCount", orders.size());
        kpi.put("supplierCount", supplierMapper.selectCount(null));

        // 图 1：近 6 个月采购金额与预算
        List<String> months = new ArrayList<>();
        List<BigDecimal> amountTrend = new ArrayList<>();
        List<BigDecimal> budgetTrend = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int k = 5; k >= 0; k--) {
            LocalDate m = today.minusMonths(k);
            String label = m.format(DateTimeFormatter.ofPattern("yyyy-MM"));
            months.add(label.substring(5) + "月");
            BigDecimal amt = orders.stream()
                    .filter(o -> o.getCreateTime() != null && o.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM")).equals(label))
                    .map(o -> o.getTotalAmount() == null ? BigDecimal.ZERO : o.getTotalAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal bud = requests.stream()
                    .filter(r -> r.getCreateTime() != null && r.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM")).equals(label))
                    .map(r -> r.getBudgetAmount() == null ? BigDecimal.ZERO : r.getBudgetAmount())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            amountTrend.add(amt);
            budgetTrend.add(bud);
        }

        // 图 2：询价单状态分布
        Map<String, Long> statusCount = new LinkedHashMap<>();
        for (String s : List.of("DRAFT", "QUOTING", "COMPARING", "APPROVING", "AWARDED", "VOID")) {
            statusCount.put(s, inquiries.stream().filter(i -> s.equals(i.getStatus())).count());
        }

        // 图 3：品类采购占比 TOP5
        Map<Long, BigDecimal> catAmount = new LinkedHashMap<>();
        Map<Long, String> catNames = new HashMap<>();
        categoryMapper.selectList(null).forEach(c -> catNames.put(c.getId(), c.getCatName()));
        for (PurchaseOrder o : orders) {
            List<PurchaseOrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItem>()
                    .eq(PurchaseOrderItem::getOrderId, o.getId()));
            for (PurchaseOrderItem it : items) {
                Material m = materialMapper.selectOne(new LambdaQueryWrapper<Material>()
                        .eq(Material::getMaterialName, it.getMaterialName()).last("limit 1"));
                Long cid = m == null ? 0L : m.getCategoryId();
                catAmount.merge(cid, it.getAmount() == null ? BigDecimal.ZERO : it.getAmount(), BigDecimal::add);
            }
        }
        List<Map<String, Object>> categoryShare = new ArrayList<>();
        catAmount.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(5)
                .forEach(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", catNames.getOrDefault(e.getKey(), "其他"));
                    m.put("amount", e.getValue().setScale(2, RoundingMode.HALF_UP));
                    categoryShare.add(m);
                });

        // 图 4：部门需求数量 TOP5
        Map<Long, Long> orgCount = new LinkedHashMap<>();
        requests.forEach(r -> orgCount.merge(r.getOrgId(), 1L, Long::sum));
        List<Map<String, Object>> deptShare = new ArrayList<>();
        orgCount.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue(), a.getValue())).limit(5)
                .forEach(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", orgNames.getOrDefault(e.getKey(), "-"));
                    m.put("value", e.getValue());
                    deptShare.add(m);
                });

        // 图 5：供应商中标率
        Map<Long, Long> winCount = new LinkedHashMap<>();
        orders.forEach(o -> winCount.merge(o.getSupplierId(), 1L, Long::sum));
        Map<Long, String> supNames = new HashMap<>();
        supplierMapper.selectList(null).forEach(s -> supNames.put(s.getId(), s.getSupplierName()));
        long orderTotal = Math.max(1, orders.size());
        List<Map<String, Object>> winRate = new ArrayList<>();
        winCount.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue(), a.getValue())).limit(5)
                .forEach(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", supNames.getOrDefault(e.getKey(), "-"));
                    m.put("rate", BigDecimal.valueOf(e.getValue() * 100.0 / orderTotal).setScale(1, RoundingMode.HALF_UP));
                    winRate.add(m);
                });

        // 图 6：创新模块结果分布与采纳率
        List<ScoreSnapshot> snaps = snapshotMapper.selectList(new LambdaQueryWrapper<ScoreSnapshot>());
        Map<String, Long> gradeCount = new LinkedHashMap<>();
        gradeCount.put("推荐", 0L);
        gradeCount.put("可选", 0L);
        gradeCount.put("不推荐", 0L);
        long adjusted = 0;
        for (ScoreSnapshot s : snaps) {
            gradeCount.merge(s.getGrade() == null ? "可选" : s.getGrade(), 1L, Long::sum);
            if (s.getAdjustResult() != null) {
                adjusted++;
            }
        }
        BigDecimal adjustRate = snaps.isEmpty() ? BigDecimal.ZERO
                : BigDecimal.valueOf(adjusted * 100.0 / snaps.size()).setScale(1, RoundingMode.HALF_UP);
        BigDecimal adoptRate = BigDecimal.valueOf(100).subtract(adjustRate);
        List<Map<String, Object>> gradeShare = new ArrayList<>();
        gradeCount.forEach((k, v) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", k);
            m.put("value", v);
            gradeShare.add(m);
        });

        Map<String, Object> charts = new LinkedHashMap<>();
        charts.put("monthLabels", months);
        charts.put("amountTrend", amountTrend);
        charts.put("budgetTrend", budgetTrend);
        charts.put("inquiryStatus", statusCount);
        charts.put("categoryShare", categoryShare);
        charts.put("deptShare", deptShare);
        charts.put("winRate", winRate);
        charts.put("gradeShare", gradeShare);
        charts.put("adoptRate", adoptRate);
        charts.put("adjustRate", adjustRate);
        charts.put("snapshotCount", snaps.size());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kpi", kpi);
        out.put("charts", charts);
        out.put("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return out;
    }
}
