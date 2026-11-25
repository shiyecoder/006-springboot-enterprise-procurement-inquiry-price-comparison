package com.grad.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.grad.purchase.entity.ReceiptItem;
import org.apache.ibatis.annotations.Mapper;

/** 验收明细 Mapper */
@Mapper
public interface ReceiptItemMapper extends BaseMapper<ReceiptItem> {
}
