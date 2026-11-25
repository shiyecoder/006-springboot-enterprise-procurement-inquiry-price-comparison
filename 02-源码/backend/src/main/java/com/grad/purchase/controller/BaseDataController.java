package com.grad.purchase.controller;

import com.grad.purchase.common.PageResult;
import com.grad.purchase.common.R;
import com.grad.purchase.entity.MaterialCategory;
import com.grad.purchase.service.BaseDataService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 基础数据接口：物料分类、物料档案、供应商档案
 */
@RestController
@RequestMapping("/api")
public class BaseDataController {

    private final BaseDataService baseDataService;

    public BaseDataController(BaseDataService baseDataService) {
        this.baseDataService = baseDataService;
    }

    @GetMapping("/category/list")
    public R<List<MaterialCategory>> categories() {
        return R.ok(baseDataService.categories());
    }

    @GetMapping("/material/page")
    public R<PageResult<Map<String, Object>>> materialPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Integer status) {
        return R.ok(baseDataService.materialPage(page, size, keyword, categoryId, status));
    }

    @PostMapping("/material")
    public R<Long> saveMaterial(@RequestBody Map<String, Object> body) {
        return R.ok(baseDataService.saveMaterial(body));
    }

    @PutMapping("/material/{id}")
    public R<Long> updateMaterial(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        body.put("id", id);
        return R.ok(baseDataService.saveMaterial(body));
    }

    @GetMapping("/supplier/page")
    public R<PageResult<Map<String, Object>>> supplierPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return R.ok(baseDataService.supplierPage(page, size, keyword, status));
    }

    @PostMapping("/supplier")
    public R<Long> saveSupplier(@RequestBody Map<String, Object> body) {
        return R.ok(baseDataService.saveSupplier(body));
    }

    @PutMapping("/supplier/{id}")
    public R<Long> updateSupplier(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        body.put("id", id);
        return R.ok(baseDataService.saveSupplier(body));
    }
}
