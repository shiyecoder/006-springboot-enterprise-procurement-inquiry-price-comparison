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
 * 退货记录（表 return_record）
 */
@Data
@TableName("return_record")
public class ReturnRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 退货单号 */
    private String returnNo;

    /** 采购单 */
    private Long orderId;

    /** 采购明细 */
    private Long orderItemId;

    /** 供应商 */
    private Long supplierId;

    /** 数量 */
    private BigDecimal quantity;

    /** 原因 */
    private String reason;

    /** 处理方式 */
    private String handleType;

    /** 状态 */
    private String status;

    /** 创建时间 */
    private LocalDateTime createTime;

}
