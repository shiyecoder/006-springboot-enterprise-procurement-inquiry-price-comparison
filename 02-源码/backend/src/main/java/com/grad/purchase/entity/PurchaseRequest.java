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
 * 采购需求（表 purchase_request）
 */
@Data
@TableName("purchase_request")
public class PurchaseRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 需求编号 */
    private String requestNo;

    /** 标题 */
    private String title;

    /** 申请部门 */
    private Long orgId;

    /** 申请人 */
    private Long applicantId;

    /** 分类 */
    private Long categoryId;

    /** 预算金额 */
    private BigDecimal budgetAmount;

    /** 期望到货日 */
    private LocalDate expectDate;

    /** 紧急程度 */
    private String urgency;

    /** 用途 */
    private String purpose;

    /** 状态 */
    private String status;

    /** 当前处理人 */
    private String currentHandler;

    /** 退回原因 */
    private String rejectReason;

    /** 提交时间 */
    private LocalDateTime submitTime;

    /** 版本号 */
    private Integer version;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

}
