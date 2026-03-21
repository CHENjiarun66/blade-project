package com.blade.system.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

@Schema(description = "更新用户DTO")
public class UserUpdateDTO {

    @Schema(description = "用户ID")
    private Long id;

    @Size(max = 30, message = "昵称最多30位")
    @Schema(description = "昵称")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    @Size(max = 50, message = "邮箱最多50位")
    @Schema(description = "邮箱")
    private String email;

    @Size(max = 11, message = "手机号最多11位")
    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "状态: 1启用 0禁用")
    private Integer status;

    @Schema(description = "角色ID列表")
    private Long[] roleIds;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Long[] getRoleIds() { return roleIds; }
    public void setRoleIds(Long[] roleIds) { this.roleIds = roleIds; }
}
