package com.blade.outlet.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 档口主数据（sales_outlet）。
 * 档口是订单归属和经营分析维度，不等同于库存仓库。
 */
@TableName("sales_outlet")
public class SalesOutlet {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String outletCode;
    private String outletName;
    private String outletType;
    private String contactName;
    private String phone;
    private String address;
    private Integer sort;
    private Integer isTenantDefault;
    private Integer status;
    private String remark;
    private Integer deleted;
    private Long createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    private Long updateBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getOutletCode() { return outletCode; }
    public void setOutletCode(String outletCode) { this.outletCode = outletCode; }
    public String getOutletName() { return outletName; }
    public void setOutletName(String outletName) { this.outletName = outletName; }
    public String getOutletType() { return outletType; }
    public void setOutletType(String outletType) { this.outletType = outletType; }
    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
    public Integer getIsTenantDefault() { return isTenantDefault; }
    public void setIsTenantDefault(Integer isTenantDefault) { this.isTenantDefault = isTenantDefault; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Integer getDeleted() { return deleted; }
    public void setDeleted(Integer deleted) { this.deleted = deleted; }
    public Long getCreateBy() { return createBy; }
    public void setCreateBy(Long createBy) { this.createBy = createBy; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public Long getUpdateBy() { return updateBy; }
    public void setUpdateBy(Long updateBy) { this.updateBy = updateBy; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
