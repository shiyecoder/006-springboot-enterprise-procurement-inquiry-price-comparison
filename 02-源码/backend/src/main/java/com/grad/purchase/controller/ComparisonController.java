package com.grad.purchase.controller;

import com.grad.purchase.common.R;
import com.grad.purchase.entity.RuleConfig;
import com.grad.purchase.service.CompareService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 综合比价与规则配置接口（创新点）
 * 支持重新计算、人工修正、规则版本管理。
 */
@RestController
@RequestMapping("/api")
public class ComparisonController {

    private final CompareService compareService;

    public ComparisonController(CompareService compareService) {
        this.compareService = compareService;
    }

    /** 查看比价矩阵（不落库，用于预览） */
    @GetMapping("/comparison/{inquiryId}")
    public R<Map<String, Object>> matrix(@PathVariable Long inquiryId) {
        return R.ok(compareService.compute(inquiryId, false));
    }

    /** 执行计算并保存快照 */
    @PostMapping("/comparison/{inquiryId}/calc")
    public R<Map<String, Object>> calc(@PathVariable Long inquiryId) {
        return R.ok(compareService.compute(inquiryId, true));
    }

    /** 人工修正：追加记录，不覆盖原始结果 */
    @PostMapping("/comparison/{inquiryId}/adjust")
    public R<Void> adjust(@PathVariable Long inquiryId, @RequestBody Map<String, Object> body) {
        compareService.adjust(inquiryId, Long.valueOf(String.valueOf(body.get("supplierId"))),
                body.get("reason") == null ? null : String.valueOf(body.get("reason")));
        return R.ok();
    }

    @GetMapping("/rule/list")
    public R<List<RuleConfig>> rules() {
        return R.ok(compareService.rules());
    }

    @GetMapping("/rule/active")
    public R<RuleConfig> active() {
        return R.ok(compareService.activeRule());
    }

    @PostMapping("/rule")
    public R<RuleConfig> save(@RequestBody Map<String, Object> body) {
        return R.ok(compareService.saveRule(body));
    }
}
