package com.grad.purchase.controller;

import com.grad.purchase.common.PageResult;
import com.grad.purchase.common.R;
import com.grad.purchase.entity.SysMessage;
import com.grad.purchase.service.DashboardService;
import com.grad.purchase.service.MessageService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 看板与消息接口
 */
@RestController
@RequestMapping("/api")
public class DashboardController {

    private final DashboardService dashboardService;
    private final MessageService messageService;

    public DashboardController(DashboardService dashboardService, MessageService messageService) {
        this.dashboardService = dashboardService;
        this.messageService = messageService;
    }

    @GetMapping("/dashboard/overview")
    public R<Map<String, Object>> overview() {
        return R.ok(dashboardService.overview());
    }

    @GetMapping("/message/page")
    public R<PageResult<SysMessage>> messages(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Boolean unreadOnly) {
        return R.ok(messageService.page(page, size, unreadOnly));
    }

    @GetMapping("/message/unread-count")
    public R<Long> unread() {
        return R.ok(messageService.unreadCount());
    }

    @GetMapping("/message/latest")
    public R<List<SysMessage>> latest(@RequestParam(defaultValue = "5") int limit) {
        return R.ok(messageService.latest(limit));
    }

    @PostMapping("/message/{id}/read")
    public R<Void> read(@PathVariable Long id) {
        messageService.read(id);
        return R.ok();
    }

    @PostMapping("/message/read-all")
    public R<Void> readAll() {
        messageService.readAll();
        return R.ok();
    }
}
