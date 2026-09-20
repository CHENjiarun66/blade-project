package com.blade.outlet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blade.outlet.entity.AgentKeyOutlet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AgentKeyOutletMapper extends BaseMapper<AgentKeyOutlet> {

    @Select("SELECT outlet_id FROM agent_key_outlet WHERE agent_key_id = #{agentKeyId} AND status = 1 ORDER BY id")
    List<Long> selectOutletIdsByKeyId(@Param("agentKeyId") Long agentKeyId);

    @Select("SELECT outlet_id FROM agent_key_outlet WHERE agent_key_id = #{agentKeyId} AND status = 1 AND is_default = 1 ORDER BY id LIMIT 1")
    Long selectDefaultOutletIdByKeyId(@Param("agentKeyId") Long agentKeyId);
}
