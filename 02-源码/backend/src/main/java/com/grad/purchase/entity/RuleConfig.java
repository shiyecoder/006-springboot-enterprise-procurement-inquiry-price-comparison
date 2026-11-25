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
 * 比价规则（表 rule_config）
 */
@Data
@TableName("rule_config")
public class RuleConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 版本 */
    private String version;

    /** 价格权重 */
    private BigDecimal priceWeight;

    /** 交付权重 */
    private BigDecimal deliveryWeight;

    /** 质量权重 */
    private BigDecimal qualityWeight;

    /** 准时率权重 */
    private BigDecimal ontimeWeight;

    /** 付款权重 */
    private BigDecimal paymentWeight;

    /** 售后权重 */
    private BigDecimal afterSalesWeight;

    /** 推荐阈值 */
    private BigDecimal recommendThreshold;

    /** 可选阈值 */
    private BigDecimal optionalThreshold;

    /** 状态 */
    private Integer status;

    /** 生效时间 */
    private LocalDateTime effectTime;

    /** 创建人 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

}
