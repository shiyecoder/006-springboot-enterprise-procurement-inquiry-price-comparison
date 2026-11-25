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
 * 邀约供应商（表 inquiry_supplier）
 */
@Data
@TableName("inquiry_supplier")
public class InquirySupplier {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 询价单 */
    private Long inquiryId;

    /** 供应商 */
    private Long supplierId;

    /** 邀约时间 */
    private LocalDateTime invitedTime;

    /** 报价状态 */
    private String quoteStatus;

}
