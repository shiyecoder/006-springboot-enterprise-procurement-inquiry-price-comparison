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
 * 到货验收（表 receipt_record）
 */
@Data
@TableName("receipt_record")
public class ReceiptRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 验收单号 */
    private String receiptNo;

    /** 采购单 */
    private Long orderId;

    /** 批次 */
    private String batchNo;

    /** 到货日期 */
    private LocalDate receiptDate;

    /** 结论 */
    private String conclusion;

    /** 说明 */
    private String remark;

    /** 图片 */
    private String images;

    /** 验收人 */
    private Long receiverId;

    /** 创建时间 */
    private LocalDateTime createTime;

}
