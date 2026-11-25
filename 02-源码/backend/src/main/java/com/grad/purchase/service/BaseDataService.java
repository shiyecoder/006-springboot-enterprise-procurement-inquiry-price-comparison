package com.grad.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.grad.purchase.common.BizException;
import com.grad.purchase.common.PageResult;
import com.grad.purchase.entity.Material;
import com.grad.purchase.entity.MaterialCategory;
import com.grad.purchase.entity.Supplier;
import com.grad.purchase.mapper.MaterialCategoryMapper;
import com.grad.purchase.mapper.MaterialMapper;
import com.grad.purchase.mapper.SupplierMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基础数据服务：物料分类、物料档案、供应商档案。
 * 图片一律保存相对路径，禁止外链。
 */
@Service
public class BaseDataService {

    private final MaterialMapper materialMapper;
    private final MaterialCategoryMapper categoryMapper;
    private final SupplierMapper supplierMapper;

    public BaseDataService(MaterialMapper materialMapper, MaterialCategoryMapper categoryMapper,
                           SupplierMapper supplierMapper) {
        this.materialMapper = materialMapper;
        this.categoryMapper = categoryMapper;
        this.supplierMapper = supplierMapper;
    }

    public List<MaterialCategory> categories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<MaterialCategory>()
                .orderByAsc(MaterialCategory::getSort));
    }

    public PageResult<Map<String, Object>> materialPage(int page, int size, String keyword, Long categoryId, Integer status) {
        LambdaQueryWrapper<Material> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(Material::getMaterialName, keyword).or().like(Material::getMaterialCode, keyword));
        }
        if (categoryId != null) {
            w.eq(Material::getCategoryId, categoryId);
        }
        if (status != null) {
            w.eq(Material::getStatus, status);
        }
        w.orderByAsc(Material::getId);
        Page<Material> p = materialMapper.selectPage(new Page<>(page, size), w);
        Map<Long, String> cats = new LinkedHashMap<>();
        categories().forEach(c -> cats.put(c.getId(), c.getCatName()));
        List<Map<String, Object>> list = p.getRecords().stream().map(m -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", m.getId());
            row.put("materialCode", m.getMaterialCode());
            row.put("materialName", m.getMaterialName());
            row.put("spec", m.getSpec());
            row.put("unit", m.getUnit());
            row.put("categoryId", m.getCategoryId());
            row.put("categoryName", cats.getOrDefault(m.getCategoryId(), "-"));
            row.put("refPrice", m.getRefPrice());
            row.put("image", m.getImage());
            row.put("status", m.getStatus());
            row.put("createTime", m.getCreateTime());
            return row;
        }).toList();
        return new PageResult<>(p.getTotal(), p.getCurrent(), p.getSize(), list);
    }

    @Transactional
    public Long saveMaterial(Map<String, Object> body) {
        Long id = body.get("id") == null ? null : Long.valueOf(String.valueOf(body.get("id")));
        Material m = id == null ? new Material() : materialMapper.selectById(id);
        if (m == null) {
            throw new BizException("物料不存在");
        }
        String name = str(body.get("materialName"));
        if (name == null || name.isBlank()) {
            throw new BizException("物料名称不能为空");
        }
        m.setMaterialName(name.trim());
        m.setSpec(str(body.get("spec")));
        m.setUnit(body.get("unit") == null ? "件" : str(body.get("unit")));
        m.setCategoryId(body.get("categoryId") == null ? null : Long.valueOf(String.valueOf(body.get("categoryId"))));
        m.setRefPrice(body.get("refPrice") == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(body.get("refPrice"))));
        String image = str(body.get("image"));
        if (image != null && (image.startsWith("http://") || image.startsWith("https://"))) {
            throw new BizException("图片必须通过上传组件上传，不允许填写外部链接");
        }
        m.setImage(image);
        m.setStatus(body.get("status") == null ? 1 : Integer.valueOf(String.valueOf(body.get("status"))));
        m.setUpdateTime(LocalDateTime.now());
        if (id == null) {
            m.setMaterialCode("TMP" + System.currentTimeMillis());
            m.setCreateTime(LocalDateTime.now());
            materialMapper.insert(m);
            m.setMaterialCode("M-" + String.format("%04d", m.getId()));
            materialMapper.updateById(m);
        } else {
            materialMapper.updateById(m);
        }
        return m.getId();
    }

    public PageResult<Map<String, Object>> supplierPage(int page, int size, String keyword, String status) {
        LambdaQueryWrapper<Supplier> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.like(Supplier::getSupplierName, keyword);
        }
        if (status != null && !status.isBlank()) {
            w.eq(Supplier::getStatus, status);
        }
        w.orderByAsc(Supplier::getId);
        Page<Supplier> p = supplierMapper.selectPage(new Page<>(page, size), w);
        List<Map<String, Object>> list = p.getRecords().stream().map(s -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", s.getId());
            row.put("supplierName", s.getSupplierName());
            row.put("creditCode", s.getCreditCode());
            row.put("category", s.getCategory());
            row.put("contact", s.getContact());
            row.put("phone", s.getPhone());
            row.put("rating", s.getRating());
            row.put("qualityScore", s.getQualityScore());
            row.put("onTimeRate", s.getOnTimeRate());
            row.put("afterSalesScore", s.getAfterSalesScore());
            row.put("paymentTerm", s.getPaymentTerm());
            row.put("status", s.getStatus());
            return row;
        }).toList();
        return new PageResult<>(p.getTotal(), p.getCurrent(), p.getSize(), list);
    }

    @Transactional
    public Long saveSupplier(Map<String, Object> body) {
        Long id = body.get("id") == null ? null : Long.valueOf(String.valueOf(body.get("id")));
        Supplier s = id == null ? new Supplier() : supplierMapper.selectById(id);
        if (s == null) {
            throw new BizException("供应商不存在");
        }
        String name = str(body.get("supplierName"));
        if (name == null || name.isBlank()) {
            throw new BizException("供应商名称不能为空");
        }
        s.setSupplierName(name.trim());
        s.setCreditCode(str(body.get("creditCode")));
        s.setCategory(str(body.get("category")));
        s.setContact(str(body.get("contact")));
        s.setPhone(str(body.get("phone")));
        s.setAddress(str(body.get("address")));
        s.setRating(body.get("rating") == null ? "B" : str(body.get("rating")));
        s.setQualityScore(body.get("qualityScore") == null ? new BigDecimal("70") : new BigDecimal(String.valueOf(body.get("qualityScore"))));
        s.setOnTimeRate(body.get("onTimeRate") == null ? new BigDecimal("80") : new BigDecimal(String.valueOf(body.get("onTimeRate"))));
        s.setAfterSalesScore(body.get("afterSalesScore") == null ? new BigDecimal("70") : new BigDecimal(String.valueOf(body.get("afterSalesScore"))));
        s.setPaymentTerm(body.get("paymentTerm") == null ? "月结 30 天" : str(body.get("paymentTerm")));
        s.setStatus(body.get("status") == null ? "QUALIFIED" : str(body.get("status")));
        if (id == null) {
            s.setCreateTime(LocalDateTime.now());
            supplierMapper.insert(s);
        } else {
            supplierMapper.updateById(s);
        }
        return s.getId();
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
