package com.grad.purchase.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 物料分类（表 material_category）
 */
@Data
@TableName("material_category")
public class MaterialCategory {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类名称 */
    private String catName;

    /** 分类编码 */
    private String catCode;

    /** 排序 */
    private Integer sort;

    /** 状态 */
    private Integer status;

}
