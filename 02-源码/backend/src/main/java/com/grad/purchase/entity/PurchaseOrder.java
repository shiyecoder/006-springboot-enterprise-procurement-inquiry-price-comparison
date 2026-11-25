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
 * 采购单（表 purchase_order）
 */
@Data
@TableName("purchase_order")
public class PurchaseOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 采购单号 */
    private String orderNo;

    /** 询价单 */
    private Long inquiryId;

    /** 供应商 */
    private Long supplierId;

    /** 含税金额 */
    private BigDecimal totalAmount;

    /** 税额 */
    private BigDecimal taxAmount;

    /** 交货日期 */
    private LocalDate deliveryDate;

    /** 联系人 */
    private String contact;

    /** 联系电话 */
    private String contactPhone;

    /** 状态 */
    private String status;

    /** 结单时间 */
    private LocalDateTime closeTime;

    /** 创建人 */
    private Long createBy;

    /** 版本号 */
    private Integer version;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

}
