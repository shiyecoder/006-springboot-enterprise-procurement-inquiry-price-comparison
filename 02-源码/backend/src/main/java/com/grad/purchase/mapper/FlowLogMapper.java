package com.grad.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.grad.purchase.entity.FlowLog;
import org.apache.ibatis.annotations.Mapper;

/** 流转记录 Mapper */
@Mapper
public interface FlowLogMapper extends BaseMapper<FlowLog> {
}
