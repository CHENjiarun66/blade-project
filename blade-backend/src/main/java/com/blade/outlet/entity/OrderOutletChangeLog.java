package com.blade.outlet.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 订单档口变更审计（order_outlet_change_log）。
 * 只追加不更新；历史归档和已确认/已完成订单改档口必须写入。
 */
@TableName("order_outlet_change_log")
public class OrderOutletChangeLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long orderId;
    private Long oldOutletId;
    private String oldOutletName;
    private Long newOutletId;
    private String newOutletName;
    private String reason;
    private Long operatorId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getOldOutletId() { return oldOutletId; }
    public void setOldOutletId(Long oldOutletId) { this.oldOutletId = oldOutletId; }
    public String getOldOutletName() { return oldOutletName; }
    public void setOldOutletName(String oldOutletName) { this.oldOutletName = oldOutletName; }
    public Long getNewOutletId() { return newOutletId; }
    public void setNewOutletId(Long newOutletId) { this.newOutletId = newOutletId; }
    public String getNewOutletName() { return newOutletName; }
    public void setNewOutletName(String newOutletName) { this.newOutletName = newOutletName; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
