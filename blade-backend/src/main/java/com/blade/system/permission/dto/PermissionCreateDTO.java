package com.blade.system.permission.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "权限创建DTO")
public class PermissionCreateDTO {

    @NotBlank(message = "权限名称不能为空")
    private String name;

    @NotBlank(message = "权限编码不能为空")
    private String code;

    @NotNull(message = "权限类型不能为空")
    private Integer type;

    private String module;

    private Long parentId;

    private String path;

    private String method;

    private String icon;

    private Integer sort;

    private Integer status;

    private Integer maskType;

    private String maskValue;

    private String description;

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }
    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getMaskType() { return maskType; }
    public void setMaskType(Integer maskType) { this.maskType = maskType; }
    public String getMaskValue() { return maskValue; }
    public void setMaskValue(String maskValue) { this.maskValue = maskValue; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
