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
 * 验收明细（表 receipt_item）
 */
@Data
@TableName("receipt_item")
public class ReceiptItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 验收单 */
    private Long receiptId;

    /** 采购明细 */
    private Long orderItemId;

    /** 到货数 */
    private BigDecimal arrivalQty;

    /** 合格数 */
    private BigDecimal qualifiedQty;

    /** 不合格数 */
    private BigDecimal unqualifiedQty;

    /** 不合格原因 */
    private String reason;

}
