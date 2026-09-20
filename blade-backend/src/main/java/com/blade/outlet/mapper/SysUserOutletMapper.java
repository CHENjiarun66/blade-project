package com.blade.outlet.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blade.outlet.entity.SysUserOutlet;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserOutletMapper extends BaseMapper<SysUserOutlet> {

    @Select("SELECT outlet_id FROM sys_user_outlet WHERE user_id = #{userId} AND status = 1 AND deleted = 0 ORDER BY id")
    List<Long> selectOutletIdsByUserId(@Param("userId") Long userId);

    @Select("SELECT outlet_id FROM sys_user_outlet WHERE user_id = #{userId} AND status = 1 AND deleted = 0 AND is_default = 1 ORDER BY id LIMIT 1")
    Long selectDefaultOutletIdByUserId(@Param("userId") Long userId);

    @Delete("DELETE FROM sys_user_outlet WHERE user_id = #{userId}")
    void deleteByUserId(@Param("userId") Long userId);
}
