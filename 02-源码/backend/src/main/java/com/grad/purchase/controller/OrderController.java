package com.grad.purchase.controller;

import com.grad.purchase.common.PageResult;
import com.grad.purchase.common.R;
import com.grad.purchase.service.OrderService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 采购单接口：列表、详情、下单确认与结单
 */
@RestController
@RequestMapping("/api/order")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/page")
    public R<PageResult<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return R.ok(orderService.page(page, size, keyword, status, supplierId, startDate, endDate));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(orderService.detail(id));
    }

    @PostMapping("/{id}/execute")
    public R<Void> execute(@PathVariable Long id) {
        orderService.execute(id);
        return R.ok();
    }

    @PostMapping("/{id}/close")
    public R<Void> close(@PathVariable Long id) {
        orderService.close(id);
        return R.ok();
    }
}
