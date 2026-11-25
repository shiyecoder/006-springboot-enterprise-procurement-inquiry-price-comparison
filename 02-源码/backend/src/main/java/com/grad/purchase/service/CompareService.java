package com.grad.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grad.purchase.common.BizException;
import com.grad.purchase.common.UserContext;
import com.grad.purchase.entity.*;
import com.grad.purchase.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 创新点：可解释的综合比价。
 *
 * 计算口径（全部由后端完成，同一输入与同一规则版本必然得到相同结果）：
 *   价格得分 = 本单最低含税总价 / 该供应商总价 × 100
 *   交付得分 = 本单最短交付天数 / 该供应商交付天数 × 100
 *   质量、准时率、售后 = 供应商历史数据（0-100）
 *   付款得分按付款条件映射：预付 60 / 货到付款 75 / 月结30 85 / 月结60 90 / 月结90 100
 *   综合得分 = Σ(单项得分 × 权重) / 100
 * 结果分级：≥推荐阈值 推荐；≥可选阈值 可选；其余 不推荐。
 *
 * 每次计算都会落一条 score_snapshot（输入快照 + 分项 + 规则版本），人工调整另起一条，
 * 原始计算结果不被覆盖，保证结果可追溯。
 */
@Service
public class CompareService {

    private static final Map<String, BigDecimal> PAYMENT_SCORE = Map.of(
            "预付 30%", new BigDecimal("60"),
            "货到付款", new BigDecimal("75"),
            "月结 30 天", new BigDecimal("85"),
            "月结 60 天", new BigDecimal("90"),
            "月结 90 天", new BigDecimal("100"));

    private final InquiryMapper inquiryMapper;
    private final InquiryItemMapper inquiryItemMapper;
    private final SupplierQuoteMapper quoteMapper;
    private final QuoteItemMapper quoteItemMapper;
    private final SupplierMapper supplierMapper;
    private final ComparisonResultMapper comparisonMapper;
    private final ScoreSnapshotMapper snapshotMapper;
    private final RuleConfigMapper ruleMapper;
    private final PurchaseRequestMapper requestMapper;
    private final LogService logService;
    private final ObjectMapper json = new ObjectMapper();

    public CompareService(InquiryMapper inquiryMapper, InquiryItemMapper inquiryItemMapper,
                          SupplierQuoteMapper quoteMapper, QuoteItemMapper quoteItemMapper,
                          SupplierMapper supplierMapper, ComparisonResultMapper comparisonMapper,
                          ScoreSnapshotMapper snapshotMapper, RuleConfigMapper ruleMapper,
                          PurchaseRequestMapper requestMapper, LogService logService) {
        this.inquiryMapper = inquiryMapper;
        this.inquiryItemMapper = inquiryItemMapper;
        this.quoteMapper = quoteMapper;
        this.quoteItemMapper = quoteItemMapper;
        this.supplierMapper = supplierMapper;
        this.comparisonMapper = comparisonMapper;
        this.snapshotMapper = snapshotMapper;
        this.ruleMapper = ruleMapper;
        this.requestMapper = requestMapper;
        this.logService = logService;
    }

    public RuleConfig activeRule() {
        RuleConfig rule = ruleMapper.selectOne(new LambdaQueryWrapper<RuleConfig>()
                .eq(RuleConfig::getStatus, 1).orderByDesc(RuleConfig::getId).last("limit 1"));
        if (rule == null) {
            throw new BizException("未配置比价规则，请先在规则配置页启用一个版本");
        }
        return rule;
    }

    /**
     * 执行综合比价。persist=true 时写比价结果与计算快照。
     */
    @Transactional
    public Map<String, Object> compute(Long inquiryId, boolean persist) {
        Inquiry inquiry = inquiryMapper.selectById(inquiryId);
        if (inquiry == null) {
            throw new BizException("询价单不存在");
        }
        RuleConfig rule = activeRule();
        List<SupplierQuote> quotes = quoteMapper.selectList(new LambdaQueryWrapper<SupplierQuote>()
                .eq(SupplierQuote::getInquiryId, inquiryId)
                .in(SupplierQuote::getStatus, List.of("SUBMITTED", "LOCKED")));
        if (quotes.isEmpty()) {
            throw new BizException("该询价单还没有有效报价，无法比价");
        }
        List<InquiryItem> items = inquiryItemMapper.selectList(new LambdaQueryWrapper<InquiryItem>()
                .eq(InquiryItem::getInquiryId, inquiryId));

        BigDecimal minTotal = quotes.stream().map(SupplierQuote::getTotalAmount).min(BigDecimal::compareTo).orElse(BigDecimal.ONE);
        int minDays = quotes.stream().mapToInt(q -> q.getDeliveryDays() == null ? 999 : q.getDeliveryDays()).min().orElse(1);

        List<Map<String, Object>> rows = new ArrayList<>();
        List<Map<String, Object>> inputSnapshot = new ArrayList<>();
        for (SupplierQuote q : quotes) {
            Supplier s = supplierMapper.selectById(q.getSupplierId());
            if (s == null) {
                continue;
            }
            BigDecimal priceScore = minTotal.divide(q.getTotalAmount(), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
            int days = q.getDeliveryDays() == null ? 999 : q.getDeliveryDays();
            BigDecimal deliveryScore = new BigDecimal(minDays)
                    .divide(new BigDecimal(days), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal qualityScore = nz(s.getQualityScore(), "70");
            BigDecimal ontimeScore = nz(s.getOnTimeRate(), "80");
            BigDecimal paymentScore = PAYMENT_SCORE.getOrDefault(q.getPaymentTerm(), new BigDecimal("80"));
            BigDecimal afterScore = nz(s.getAfterSalesScore(), "70");

            BigDecimal total = priceScore.multiply(rule.getPriceWeight())
                    .add(deliveryScore.multiply(rule.getDeliveryWeight()))
                    .add(qualityScore.multiply(rule.getQualityWeight()))
                    .add(ontimeScore.multiply(rule.getOntimeWeight()))
                    .add(paymentScore.multiply(rule.getPaymentWeight()))
                    .add(afterScore.multiply(rule.getAfterSalesWeight()))
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

            String grade = total.compareTo(rule.getRecommendThreshold()) >= 0 ? "推荐"
                    : (total.compareTo(rule.getOptionalThreshold()) >= 0 ? "可选" : "不推荐");

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("supplierId", s.getId());
            row.put("supplierName", s.getSupplierName());
            row.put("rating", s.getRating());
            row.put("totalAmount", q.getTotalAmount());
            row.put("deliveryDays", days);
            row.put("paymentTerm", q.getPaymentTerm());
            row.put("warrantyMonths", q.getWarrantyMonths());
            row.put("qualityScore", qualityScore);
            row.put("onTimeRate", ontimeScore);
            row.put("afterSalesScore", afterScore);
            row.put("priceScore", priceScore);
            row.put("deliveryScore", deliveryScore);
            row.put("paymentScore", paymentScore);
            row.put("totalScore", total);
            row.put("grade", grade);
            row.put("lowestPrice", q.getTotalAmount().compareTo(minTotal) == 0);
            row.put("fastest", days == minDays);
            rows.add(row);

            Map<String, Object> in = new LinkedHashMap<>();
            in.put("供应商", s.getSupplierName());
            in.put("报价总额", q.getTotalAmount());
            in.put("交付天数", days);
            in.put("付款条件", q.getPaymentTerm());
            in.put("质量评分", qualityScore);
            in.put("历史准时率", ontimeScore);
            in.put("售后评分", afterScore);
            inputSnapshot.add(in);
        }
        if (rows.isEmpty()) {
            throw new BizException("报价供应商资料缺失，无法计算");
        }
        rows.sort((a, b) -> ((BigDecimal) b.get("totalScore")).compareTo((BigDecimal) a.get("totalScore")));
        Map<String, Object> recommended = rows.get(0);
        for (Map<String, Object> r : rows) {
            r.put("recommend", r == recommended);
        }

        // 解释：主要影响因素、未满足条件、可改进项
        List<String> factors = new ArrayList<>();
        Map<String, BigDecimal> weights = new LinkedHashMap<>();
        weights.put("priceScore", rule.getPriceWeight());
        weights.put("deliveryScore", rule.getDeliveryWeight());
        weights.put("qualityScore", rule.getQualityWeight());
        weights.put("onTimeRate", rule.getOntimeWeight());
        weights.put("paymentScore", rule.getPaymentWeight());
        weights.put("afterSalesScore", rule.getAfterSalesWeight());
        String[] names = {"价格", "交付", "质量", "准时率", "付款条件", "售后"};
        int idx = 0;
        Map<String, BigDecimal> contrib = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> e : weights.entrySet()) {
            BigDecimal score = (BigDecimal) recommended.get(e.getKey());
            contrib.put(names[idx], score.multiply(e.getValue()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP));
            idx++;
        }
        contrib.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(3)
                .forEach(e -> factors.add(e.getKey() + "贡献 " + e.getValue() + " 分"));

        List<String> unmet = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : weights.entrySet()) {
            BigDecimal score = (BigDecimal) recommended.get(e.getKey());
            if (score.compareTo(new BigDecimal("60")) < 0) {
                unmet.add(e.getKey() + " 得分仅 " + score + "，低于 60 分基线");
            }
        }
        List<String> improvements = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : weights.entrySet()) {
            BigDecimal score = (BigDecimal) recommended.get(e.getKey());
            if (score.compareTo(new BigDecimal("80")) < 0) {
                improvements.add(e.getKey() + " 低于 80 分，可要求供应商优化后再定标");
            }
        }
        if (improvements.isEmpty()) {
            improvements.add("各项指标均在 80 分以上，可维持当前方案并关注履约");
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("inquiryId", inquiryId);
        out.put("inquiryNo", inquiry.getInquiryNo());
        out.put("rule", rule);
        out.put("items", items);
        out.put("rows", rows);
        out.put("recommendedSupplierId", recommended.get("supplierId"));
        out.put("recommendedSupplierName", recommended.get("supplierName"));
        out.put("recommendedScore", recommended.get("totalScore"));
        out.put("lowestSupplierId", rows.stream().filter(r -> Boolean.TRUE.equals(r.get("lowestPrice")))
                .findFirst().map(r -> r.get("supplierId")).orElse(null));
        out.put("factors", factors);
        out.put("unmet", unmet);
        out.put("improvements", improvements);
        out.put("explain", buildExplain(recommended, rows, minTotal));

        if (persist) {
            comparisonMapper.delete(new LambdaQueryWrapper<ComparisonResult>()
                    .eq(ComparisonResult::getInquiryId, inquiryId));
            for (Map<String, Object> r : rows) {
                ComparisonResult cr = new ComparisonResult();
                cr.setInquiryId(inquiryId);
                cr.setSupplierId((Long) r.get("supplierId"));
                cr.setPriceScore((BigDecimal) r.get("priceScore"));
                cr.setDeliveryScore((BigDecimal) r.get("deliveryScore"));
                cr.setQualityScore((BigDecimal) r.get("qualityScore"));
                cr.setOntimeScore((BigDecimal) r.get("onTimeRate"));
                cr.setPaymentScore((BigDecimal) r.get("paymentScore"));
                cr.setAfterSalesScore((BigDecimal) r.get("afterSalesScore"));
                cr.setTotalScore((BigDecimal) r.get("totalScore"));
                cr.setGrade((String) r.get("grade"));
                cr.setRecommend(Boolean.TRUE.equals(r.get("recommend")) ? 1 : 0);
                cr.setRuleVersion(rule.getVersion());
                cr.setCalcTime(LocalDateTime.now());
                comparisonMapper.insert(cr);
            }
            ScoreSnapshot snap = new ScoreSnapshot();
            snap.setInquiryId(inquiryId);
            snap.setRuleId(rule.getId());
            snap.setRuleVersion(rule.getVersion());
            snap.setInputJson(toJson(inputSnapshot));
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("权重", Map.of("报价金额", rule.getPriceWeight(), "交付天数", rule.getDeliveryWeight(),
                    "质量评分", rule.getQualityWeight(), "历史准时率", rule.getOntimeWeight(),
                    "付款条件", rule.getPaymentWeight(), "售后评分", rule.getAfterSalesWeight()));
            detail.put("得分", rows);
            detail.put("主要影响因素", factors);
            detail.put("未满足条件", unmet);
            detail.put("可改进项", improvements);
            snap.setDetailJson(toJson(detail));
            snap.setTotalScore((BigDecimal) recommended.get("totalScore"));
            snap.setGrade((String) recommended.get("grade"));
            snap.setRecommendSupplierId((Long) recommended.get("supplierId"));
            snap.setRecommendSupplierName((String) recommended.get("supplierName"));
            snap.setCalcTime(LocalDateTime.now());
            snapshotMapper.insert(snap);

            inquiry.setRuleId(rule.getId());
            inquiry.setRuleVersion(rule.getVersion());
            if ("QUOTING".equals(inquiry.getStatus())) {
                inquiry.setStatus("COMPARING");
            }
            inquiry.setUpdateTime(LocalDateTime.now());
            inquiryMapper.updateById(inquiry);
            logService.record("INQUIRY", inquiryId, inquiry.getInquiryNo(), "COMPARING", "COMPARING",
                    "执行综合比价", "规则 " + rule.getVersion() + "，推荐 " + recommended.get("supplierName")
                            + "（" + recommended.get("totalScore") + " 分）");
            out.put("snapshotId", snap.getId());
        }
        return out;
    }

    private String buildExplain(Map<String, Object> rec, List<Map<String, Object>> rows, BigDecimal minTotal) {
        BigDecimal price = (BigDecimal) rec.get("totalAmount");
        String gap = "";
        if (price.compareTo(minTotal) > 0) {
            BigDecimal diff = price.subtract(minTotal);
            BigDecimal pct = diff.divide(minTotal, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
                    .setScale(1, RoundingMode.HALF_UP);
            gap = "虽非最低价（高出 " + diff.setScale(2, RoundingMode.HALF_UP) + " 元，约 " + pct + "%），";
        }
        return "推荐 " + rec.get("supplierName") + "：" + gap + "综合得分 " + rec.get("totalScore")
                + "，在价格、交付、质量、准时率、付款条件与售后六个维度加权后排名第一。";
    }

    /**
     * 人工调整：只追加记录，原始计算结果保留。
     */
    @Transactional
    public void adjust(Long inquiryId, Long supplierId, String reason) {
        if (reason == null || reason.trim().length() < 5) {
            throw new BizException("人工调整必须填写原因，且不少于 5 个字");
        }
        Inquiry inquiry = inquiryMapper.selectById(inquiryId);
        if (inquiry == null) {
            throw new BizException("询价单不存在");
        }
        if (!"APPROVING".equals(inquiry.getStatus()) && !"COMPARING".equals(inquiry.getStatus())) {
            throw new BizException("当前状态不允许人工调整");
        }
        Supplier s = supplierMapper.selectById(supplierId);
        if (s == null) {
            throw new BizException("供应商不存在");
        }
        ScoreSnapshot latest = snapshotMapper.selectOne(new LambdaQueryWrapper<ScoreSnapshot>()
                .eq(ScoreSnapshot::getInquiryId, inquiryId).orderByDesc(ScoreSnapshot::getId).last("limit 1"));
        if (latest == null) {
            throw new BizException("请先执行一次综合比价");
        }
        ScoreSnapshot copy = new ScoreSnapshot();
        copy.setInquiryId(inquiryId);
        copy.setRuleId(latest.getRuleId());
        copy.setRuleVersion(latest.getRuleVersion());
        copy.setInputJson(latest.getInputJson());
        copy.setDetailJson(latest.getDetailJson());
        copy.setTotalScore(latest.getTotalScore());
        copy.setGrade(latest.getGrade());
        copy.setRecommendSupplierId(latest.getRecommendSupplierId());
        copy.setRecommendSupplierName(latest.getRecommendSupplierName());
        copy.setCalcTime(latest.getCalcTime());
        copy.setAdjustResult(s.getSupplierName());
        copy.setAdjustReason(reason.trim());
        copy.setAdjustBy(UserContext.name());
        copy.setAdjustTime(LocalDateTime.now());
        snapshotMapper.insert(copy);
        logService.record("INQUIRY", inquiryId, inquiry.getInquiryNo(), inquiry.getStatus(), inquiry.getStatus(),
                "人工调整定标建议", "由 " + latest.getRecommendSupplierName() + " 调整为 " + s.getSupplierName()
                        + "，原因：" + reason);
        logService.oper("比价管理", "人工调整结果", inquiry.getInquiryNo(), reason);
    }

    /** 规则列表与保存新版本 */
    public List<RuleConfig> rules() {
        return ruleMapper.selectList(new LambdaQueryWrapper<RuleConfig>().orderByDesc(RuleConfig::getId));
    }

    @Transactional
    public RuleConfig saveRule(Map<String, Object> body) {
        BigDecimal sum = BigDecimal.ZERO;
        String[] keys = {"priceWeight", "deliveryWeight", "qualityWeight", "ontimeWeight", "paymentWeight", "afterSalesWeight"};
        Map<String, BigDecimal> vals = new LinkedHashMap<>();
        for (String k : keys) {
            BigDecimal v = body.get(k) == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(body.get(k)));
            if (v.compareTo(BigDecimal.ZERO) < 0) {
                throw new BizException("权重不能为负数");
            }
            vals.put(k, v);
            sum = sum.add(v);
        }
        if (sum.compareTo(new BigDecimal("100")) != 0) {
            throw new BizException("权重合计必须等于 100%，当前为 " + sum + "%");
        }
        RuleConfig last = ruleMapper.selectOne(new LambdaQueryWrapper<RuleConfig>()
                .orderByDesc(RuleConfig::getId).last("limit 1"));
        int next = 1;
        if (last != null && last.getVersion().startsWith("v")) {
            try {
                next = Integer.parseInt(last.getVersion().substring(1)) + 1;
            } catch (NumberFormatException ignored) {
                next = 1;
            }
        }
        ruleMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<RuleConfig>()
                .eq(RuleConfig::getStatus, 1).set(RuleConfig::getStatus, 0));
        RuleConfig cfg = new RuleConfig();
        cfg.setVersion("v" + next);
        cfg.setPriceWeight(vals.get("priceWeight"));
        cfg.setDeliveryWeight(vals.get("deliveryWeight"));
        cfg.setQualityWeight(vals.get("qualityWeight"));
        cfg.setOntimeWeight(vals.get("ontimeWeight"));
        cfg.setPaymentWeight(vals.get("paymentWeight"));
        cfg.setAfterSalesWeight(vals.get("afterSalesWeight"));
        cfg.setRecommendThreshold(body.get("recommendThreshold") == null ? new BigDecimal("80")
                : new BigDecimal(String.valueOf(body.get("recommendThreshold"))));
        cfg.setOptionalThreshold(body.get("optionalThreshold") == null ? new BigDecimal("70")
                : new BigDecimal(String.valueOf(body.get("optionalThreshold"))));
        cfg.setStatus(1);
        cfg.setEffectTime(LocalDateTime.now());
        cfg.setCreateBy(UserContext.name());
        cfg.setCreateTime(LocalDateTime.now());
        ruleMapper.insert(cfg);
        logService.oper("比价管理", "保存规则", cfg.getVersion(), "权重合计 100%，已生效");
        return cfg;
    }

    /** 修正关联需求状态，交由询价服务调用 */
    void markRequestsQuoted(String requestIds) {
        if (requestIds == null || requestIds.isBlank()) {
            return;
        }
        for (String s : requestIds.split(",")) {
            if (s.isBlank()) {
                continue;
            }
            PurchaseRequest r = requestMapper.selectById(Long.valueOf(s.trim()));
            if (r != null && ("PURCHASING".equals(r.getStatus()) || "DEPT_PENDING".equals(r.getStatus()))) {
                r.setStatus("QUOTED");
                r.setCurrentHandler("采购专员");
                r.setUpdateTime(LocalDateTime.now());
                requestMapper.updateById(r);
            }
        }
    }

    private BigDecimal nz(BigDecimal v, String def) {
        return v == null ? new BigDecimal(def) : v;
    }

    private String toJson(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }

    public String todayNo(String prefix, Long id) {
        return prefix + LocalDateNowUtil.now() + String.format("%04d", id);
    }

    /** 便于测试的纯函数：按给定权重计算综合得分 */
    public static BigDecimal score(BigDecimal price, BigDecimal delivery, BigDecimal quality,
                                   BigDecimal ontime, BigDecimal payment, BigDecimal after,
                                   RuleConfig rule) {
        return price.multiply(rule.getPriceWeight())
                .add(delivery.multiply(rule.getDeliveryWeight()))
                .add(quality.multiply(rule.getQualityWeight()))
                .add(ontime.multiply(rule.getOntimeWeight()))
                .add(payment.multiply(rule.getPaymentWeight()))
                .add(after.multiply(rule.getAfterSalesWeight()))
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    /** 内部小工具，避免额外依赖 */
    static class LocalDateNowUtil {
        static String now() {
            return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        }
    }
}
