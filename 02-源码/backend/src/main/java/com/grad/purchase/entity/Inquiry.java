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
 * 询价比价单（表 inquiry）
 */
@Data
@TableName("inquiry")
public class Inquiry {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 询价单号 */
    private String inquiryNo;

    /** 标题 */
    private String title;

    /** 采购专员 */
    private Long buyerId;

    /** 关联需求 */
    private String requestIds;

    /** 预算金额 */
    private BigDecimal budgetAmount;

    /** 报价截止 */
    private LocalDateTime deadline;

    /** 税率 */
    private BigDecimal taxRate;

    /** 交货要求 */
    private String deliveryReq;

    /** 状态 */
    private String status;

    /** 规则 */
    private Long ruleId;

    /** 规则版本 */
    private String ruleVersion;

    /** 定标说明 */
    private String awardReason;

    /** 版本号 */
    private Integer version;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

}
