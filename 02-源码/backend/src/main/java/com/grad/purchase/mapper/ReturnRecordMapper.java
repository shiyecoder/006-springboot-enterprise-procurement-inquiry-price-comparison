package com.grad.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.grad.purchase.entity.ReturnRecord;
import org.apache.ibatis.annotations.Mapper;

/** 退货记录 Mapper */
@Mapper
public interface ReturnRecordMapper extends BaseMapper<ReturnRecord> {
}
