package com.grad.purchase.controller;

import com.grad.purchase.common.PageResult;
import com.grad.purchase.common.R;
import com.grad.purchase.service.ReceiptService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 到货验收与退货接口
 */
@RestController
@RequestMapping("/api")
public class ReceiptController {

    private final ReceiptService receiptService;

    public ReceiptController(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    /** 待到货明细（仓管/验收人工作台） */
    @GetMapping("/receipt/pending")
    public R<List<Map<String, Object>>> pending() {
        return R.ok(receiptService.pending());
    }

    @GetMapping("/receipt/page")
    public R<PageResult<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String conclusion,
            @RequestParam(required = false) String keyword) {
        return R.ok(receiptService.page(page, size, conclusion, keyword));
    }

    @GetMapping("/receipt/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(receiptService.detail(id));
    }

    @PostMapping("/receipt")
    public R<Long> create(@RequestBody Map<String, Object> body) {
        return R.ok(receiptService.create(body));
    }

    @GetMapping("/return/page")
    public R<PageResult<Map<String, Object>>> returnPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return R.ok(receiptService.returnPage(page, size));
    }
}
