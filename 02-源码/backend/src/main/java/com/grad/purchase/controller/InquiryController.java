package com.grad.purchase.controller;

import com.grad.purchase.common.PageResult;
import com.grad.purchase.common.R;
import com.grad.purchase.service.InquiryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 询价比价单接口：发布、锁定报价、录入报价、定标、作废
 */
@RestController
@RequestMapping("/api/inquiry")
public class InquiryController {

    private final InquiryService inquiryService;

    public InquiryController(InquiryService inquiryService) {
        this.inquiryService = inquiryService;
    }

    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tab) {
        return R.ok(inquiryService.page(page, size, keyword, status, tab));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(inquiryService.detail(id));
    }

    @GetMapping("/{id}/quote-desk")
    public R<List<Map<String, Object>>> quoteDesk(@PathVariable Long id) {
        return R.ok(inquiryService.quoteDesk(id));
    }

    /** 需求合并 / 拆分为询价单：只决定纳入哪些需求明细 */
    @PostMapping("/from-requests")
    public R<Long> fromRequests(@RequestBody Map<String, Object> body) {
        List<Long> itemIds = ((List<?>) body.get("requestItemIds")).stream()
                .map(x -> Long.valueOf(String.valueOf(x))).toList();
        List<Long> supplierIds = ((List<?>) body.get("supplierIds")).stream()
                .map(x -> Long.valueOf(String.valueOf(x))).toList();
        return R.ok(inquiryService.fromRequests(itemIds, (String) body.get("title"), supplierIds,
                (String) body.get("deadline")));
    }

    @PostMapping("/{id}/publish")
    public R<Void> publish(@PathVariable Long id) {
        inquiryService.publish(id);
        return R.ok();
    }

    @PostMapping("/{id}/lock")
    public R<Void> lock(@PathVariable Long id) {
        inquiryService.lockQuotes(id);
        return R.ok();
    }

    @PostMapping("/{id}/quotes")
    public R<Void> saveQuote(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        inquiryService.saveQuote(id, body);
        return R.ok();
    }

    @PostMapping("/{id}/submit-approval")
    public R<Void> submitApproval(@PathVariable Long id) {
        inquiryService.submitApproval(id);
        return R.ok();
    }

    @PostMapping("/{id}/approve")
    public R<Void> approve(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        boolean pass = Boolean.TRUE.equals(body.get("pass")) || "true".equals(String.valueOf(body.get("pass")));
        inquiryService.approve(id, pass, body.get("reason") == null ? null : String.valueOf(body.get("reason")));
        return R.ok();
    }

    @PostMapping("/{id}/award")
    public R<List<Long>> award(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long supplierId = body.get("supplierId") == null ? null : Long.valueOf(String.valueOf(body.get("supplierId")));
        List<Map<String, Object>> allocations = (List<Map<String, Object>>) body.get("allocations");
        String reason = body.get("reason") == null ? null : String.valueOf(body.get("reason"));
        return R.ok(inquiryService.award(id, supplierId, allocations, reason));
    }

    @PostMapping("/{id}/void")
    public R<Void> voidInquiry(@PathVariable Long id, @RequestBody Map<String, String> body) {
        inquiryService.voidInquiry(id, body.get("reason"));
        return R.ok();
    }
}
