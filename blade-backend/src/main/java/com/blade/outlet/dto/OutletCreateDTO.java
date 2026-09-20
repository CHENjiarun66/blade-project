package com.blade.outlet.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "创建档口DTO")
public class OutletCreateDTO {

    @NotBlank(message = "档口编码不能为空")
    @Size(max = 30, message = "档口编码最多30位")
    private String outletCode;

    @NotBlank(message = "档口名称不能为空")
    @Size(max = 100, message = "档口名称最多100位")
    private String outletName;

    @Size(max = 20, message = "档口类型最多20位")
    private String outletType;

    @Size(max = 50, message = "联系人最多50位")
    private String contactName;

    @Size(max = 30, message = "电话最多30位")
    private String phone;

    @Size(max = 255, message = "地址最多255位")
    private String address;

    private Integer sort;

    private Integer isTenantDefault;

    private String remark;

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
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
