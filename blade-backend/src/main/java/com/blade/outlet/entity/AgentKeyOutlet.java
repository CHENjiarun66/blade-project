package com.blade.outlet.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * Agent Key 档口关联（agent_key_outlet）。
 * Key 的业务 scope 决定“能做什么”，档口关联决定“可以在哪些档口做”。
 * 当前仅建数据模型，不改变 Key 签发/轮换服务。
 */
@TableName("agent_key_outlet")
public class AgentKeyOutlet {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long agentKeyId;
    private Long outletId;
    private Integer isDefault;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getAgentKeyId() { return agentKeyId; }
    public void setAgentKeyId(Long agentKeyId) { this.agentKeyId = agentKeyId; }
    public Long getOutletId() { return outletId; }
    public void setOutletId(Long outletId) { this.outletId = outletId; }
    public Integer getIsDefault() { return isDefault; }
    public void setIsDefault(Integer isDefault) { this.isDefault = isDefault; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
