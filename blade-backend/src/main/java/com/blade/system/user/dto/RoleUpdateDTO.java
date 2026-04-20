package com.blade.system.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "角色更新DTO")
public class RoleUpdateDTO {

    @NotNull(message = "角色ID不能为空")
    private Long id;

    private String roleName;

    private String roleCode;

    private String description;

    private Integer status;

    /** 权限ID列表 */
    private Long[] permissionIds;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }
    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Long[] getPermissionIds() { return permissionIds; }
    public void setPermissionIds(Long[] permissionIds) { this.permissionIds = permissionIds; }
}
