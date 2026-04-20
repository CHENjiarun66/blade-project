package com.blade.system.permission.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("sys_permission")
public class SysPermission {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 权限名称 */
    private String name;

    /** 权限编码，全局唯一 */
    private String code;

    /** 权限类型: 1菜单 2按钮 3字段 4API */
    private Integer type;

    /** 所属模块: order/inventory/product/finance/system */
    private String module;

    /** 父权限ID，0表示顶级 */
    private Long parentId;

    /** 路由路径（菜单）或接口路径（API） */
    private String path;

    /** HTTP方法: GET/POST/PUT/DELETE */
    private String method;

    /** 图标 */
    private String icon;

    /** 排序 */
    private Integer sort;

    /** 状态: 1启用 0禁用 */
    private Integer status;

    /** 脱敏类型: 0不脱敏 1置空 2脱星 3替换 */
    private Integer maskType;

    /** 脱敏替换值 */
    private String maskValue;

    /** 权限描述 */
    private String description;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("deleted")
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    // 权限类型常量
    public static final int TYPE_MENU = 1;    // 菜单权限
    public static final int TYPE_BUTTON = 2;  // 按钮权限
    public static final int TYPE_FIELD = 3;   // 字段权限
    public static final int TYPE_API = 4;     // API权限

    // 脱敏类型常量
    public static final int MASK_NONE = 0;    // 不脱敏
    public static final int MASK_NULL = 1;    // 置空
    public static final int MASK_STAR = 2;    // 脱星
    public static final int MASK_REPLACE = 3; // 替换
}
