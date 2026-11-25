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
 * 询价明细（表 inquiry_item）
 */
@Data
@TableName("inquiry_item")
public class InquiryItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 询价单 */
    private Long inquiryId;

    /** 来源需求明细 */
    private Long requestItemId;

    /** 物料名称 */
    private String materialName;

    /** 规格 */
    private String spec;

    /** 单位 */
    private String unit;

    /** 数量 */
    private BigDecimal quantity;

}
