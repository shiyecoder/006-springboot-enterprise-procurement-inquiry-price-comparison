package com.grad.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.grad.purchase.entity.PurchaseRequest;
import org.apache.ibatis.annotations.Mapper;

/** 采购需求 Mapper */
@Mapper
public interface PurchaseRequestMapper extends BaseMapper<PurchaseRequest> {
}
