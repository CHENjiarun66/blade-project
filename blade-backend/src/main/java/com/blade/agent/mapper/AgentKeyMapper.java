package com.blade.agent.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blade.agent.entity.AgentKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AgentKeyMapper extends BaseMapper<AgentKey> {
    // Authentication happens before a tenant is known. key_prefix is globally unique,
    // so this one lookup intentionally bypasses the tenant interceptor; all subsequent
    // work runs with the tenant from the authenticated key.
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM agent_key WHERE key_prefix = #{prefix} AND status = 1 LIMIT 1")
    AgentKey selectActiveByPrefixForAuthentication(@Param("prefix") String prefix);
}
