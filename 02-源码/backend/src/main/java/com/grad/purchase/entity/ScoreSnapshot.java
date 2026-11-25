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
 * 比价计算快照（表 score_snapshot）
 */
@Data
@TableName("score_snapshot")
public class ScoreSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 询价单 */
    private Long inquiryId;

    /** 规则 */
    private Long ruleId;

    /** 规则版本 */
    private String ruleVersion;

    /** 输入快照 */
    private String inputJson;

    /** 明细与解释 */
    private String detailJson;

    /** 总分 */
    private BigDecimal totalScore;

    /** 等级 */
    private String grade;

    /** 推荐供应商 */
    private Long recommendSupplierId;

    /** 推荐供应商名 */
    private String recommendSupplierName;

    /** 计算时间 */
    private LocalDateTime calcTime;

    /** 人工调整结果 */
    private String adjustResult;

    /** 调整原因 */
    private String adjustReason;

    /** 调整人 */
    private String adjustBy;

    /** 调整时间 */
    private LocalDateTime adjustTime;

}
