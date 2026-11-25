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
 * 报价明细（表 quote_item）
 */
@Data
@TableName("quote_item")
public class QuoteItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 报价 */
    private Long quoteId;

    /** 询价明细 */
    private Long inquiryItemId;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 数量 */
    private BigDecimal quantity;

    /** 小计 */
    private BigDecimal amount;

}
