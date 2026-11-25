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
 * 供应商报价（表 supplier_quote）
 */
@Data
@TableName("supplier_quote")
public class SupplierQuote {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 询价单 */
    private Long inquiryId;

    /** 供应商 */
    private Long supplierId;

    /** 含税总价 */
    private BigDecimal totalAmount;

    /** 税额 */
    private BigDecimal taxAmount;

    /** 交付天数 */
    private Integer deliveryDays;

    /** 付款条件 */
    private String paymentTerm;

    /** 质保月数 */
    private Integer warrantyMonths;

    /** 备注 */
    private String remark;

    /** 附件 */
    private String attachment;

    /** 状态 */
    private String status;

    /** 报价时间 */
    private LocalDateTime quoteTime;

    /** 创建时间 */
    private LocalDateTime createTime;

}
