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
 * 比价结果（表 comparison_result）
 */
@Data
@TableName("comparison_result")
public class ComparisonResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 询价单 */
    private Long inquiryId;

    /** 供应商 */
    private Long supplierId;

    /** 价格得分 */
    private BigDecimal priceScore;

    /** 交付得分 */
    private BigDecimal deliveryScore;

    /** 质量得分 */
    private BigDecimal qualityScore;

    /** 准时率得分 */
    private BigDecimal ontimeScore;

    /** 付款得分 */
    private BigDecimal paymentScore;

    /** 售后得分 */
    private BigDecimal afterSalesScore;

    /** 综合得分 */
    private BigDecimal totalScore;

    /** 等级 */
    private String grade;

    /** 是否推荐 */
    private Integer recommend;

    /** 规则版本 */
    private String ruleVersion;

    /** 计算时间 */
    private LocalDateTime calcTime;

}
