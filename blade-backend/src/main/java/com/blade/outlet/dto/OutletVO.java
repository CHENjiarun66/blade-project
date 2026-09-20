package com.blade.outlet.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "档口VO")
public class OutletVO {

    private Long id;
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
    private long boundUserCount;
    private long orderCount;
    private long draftCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public long getBoundUserCount() { return boundUserCount; }
    public void setBoundUserCount(long boundUserCount) { this.boundUserCount = boundUserCount; }
    public long getOrderCount() { return orderCount; }
    public void setOrderCount(long orderCount) { this.orderCount = orderCount; }
    public long getDraftCount() { return draftCount; }
    public void setDraftCount(long draftCount) { this.draftCount = draftCount; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
