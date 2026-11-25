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
 * 需求明细（表 request_item）
 */
@Data
@TableName("request_item")
public class RequestItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 需求 */
    private Long requestId;

    /** 物料 */
    private Long materialId;

    /** 物料名称 */
    private String materialName;

    /** 规格 */
    private String spec;

    /** 单位 */
    private String unit;

    /** 数量 */
    private BigDecimal quantity;

    /** 预算单价 */
    private BigDecimal budgetPrice;

    /** 备注 */
    private String remark;

}
