package com.grad.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.grad.purchase.entity.PurchaseOrderItem;
import org.apache.ibatis.annotations.Mapper;

/** 采购单明细 Mapper */
@Mapper
public interface PurchaseOrderItemMapper extends BaseMapper<PurchaseOrderItem> {
}
