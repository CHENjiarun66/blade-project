package com.blade.system.user.service;

import com.blade.common.result.PageResult;
import com.blade.system.user.dto.UserCreateDTO;
import com.blade.system.user.dto.UserPageDTO;
import com.blade.system.user.dto.UserUpdateDTO;
import com.blade.system.user.dto.UserVO;
import com.blade.system.user.entity.Role;
import com.blade.system.user.entity.User;

import java.util.List;

public interface UserService {

    PageResult<UserVO> pageList(UserPageDTO dto);

    UserVO getById(Long id);

    User getByUsername(String username);

    List<Role> getRolesByUserId(Long userId);

    Long create(UserCreateDTO dto);

    void update(UserUpdateDTO dto);

    void delete(Long id);

    void resetPassword(Long id, String newPassword);
}
