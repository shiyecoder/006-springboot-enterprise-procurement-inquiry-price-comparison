package com.grad.purchase.controller;

import com.grad.purchase.common.PageResult;
import com.grad.purchase.common.R;
import com.grad.purchase.service.RequestService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 采购需求接口。
 * 同时兼容需求文档给出的 /api/purchase-inquiry 路径与前端使用的 /api/request 路径。
 */
@RestController
@RequestMapping({"/api/request", "/api/purchase-inquiry"})
public class RequestController {

    private final RequestService requestService;

    public RequestController(RequestService requestService) {
        this.requestService = requestService;
    }

    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String tab) {
        return R.ok(requestService.page(page, size, keyword, orgId, categoryId, status, minAmount, maxAmount,
                startDate, endDate, tab));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(requestService.detail(id));
    }

    @PostMapping
    public R<Long> create(@RequestBody Map<String, Object> body) {
        boolean submit = Boolean.TRUE.equals(body.get("submit"));
        return R.ok(requestService.save(body, submit));
    }

    @PutMapping("/{id}")
    public R<Long> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        body.put("id", id);
        boolean submit = Boolean.TRUE.equals(body.get("submit"));
        return R.ok(requestService.save(body, submit));
    }

    @PostMapping("/{id}/transition")
    public R<Void> transition(@PathVariable Long id, @RequestBody Map<String, String> body) {
        requestService.transition(id, body.get("targetStatus"), body.get("reason"));
        return R.ok();
    }

    @PostMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        requestService.transition(id, "CANCELLED", body == null ? null : body.get("reason"));
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        requestService.delete(id);
        return R.ok();
    }
}
