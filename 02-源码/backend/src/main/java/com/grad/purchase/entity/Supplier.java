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
 * 供应商（表 supplier）
 */
@Data
@TableName("supplier")
public class Supplier {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 名称 */
    private String supplierName;

    /** 信用代码 */
    private String creditCode;

    /** 品类 */
    private String category;

    /** 联系人 */
    private String contact;

    /** 电话 */
    private String phone;

    /** 地址 */
    private String address;

    /** 评级 */
    private String rating;

    /** 质量评分 */
    private BigDecimal qualityScore;

    /** 准时率 */
    private BigDecimal onTimeRate;

    /** 售后评分 */
    private BigDecimal afterSalesScore;

    /** 付款条件 */
    private String paymentTerm;

    /** 准入状态 */
    private String status;

    /** 创建时间 */
    private LocalDateTime createTime;

    @TableLogic
    private Integer deleted;

}
