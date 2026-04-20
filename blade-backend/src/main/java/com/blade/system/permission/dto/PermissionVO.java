package com.blade.system.permission.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "权限VO")
public class PermissionVO {

    private Long id;
    private String name;
    private String code;
    private Integer type;
    private String typeName;
    private String module;
    private Long parentId;
    private String parentName;
    private String path;
    private String method;
    private String icon;
    private Integer sort;
    private Integer status;
    private Integer maskType;
    private String maskValue;
    private String description;
    private LocalDateTime createTime;

    /** 子权限列表（用于树形结构） */
    private List<PermissionVO> children;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }
    public String getTypeName() { return typeName; }
    public void setTypeName(String typeName) { this.typeName = typeName; }
    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public String getParentName() { return parentName; }
    public void setParentName(String parentName) { this.parentName = parentName; }
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
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public List<PermissionVO> getChildren() { return children; }
    public void setChildren(List<PermissionVO> children) { this.children = children; }
}
