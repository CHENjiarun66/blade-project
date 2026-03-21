package com.blade.system.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blade.system.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
