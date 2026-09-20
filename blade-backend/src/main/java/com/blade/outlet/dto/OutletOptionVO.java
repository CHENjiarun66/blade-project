package com.blade.outlet.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "档口选项VO")
public class OutletOptionVO {

    private Long id;
    private String outletCode;
    private String outletName;
    private Integer status;

    public OutletOptionVO() {}

    public OutletOptionVO(Long id, String outletCode, String outletName, Integer status) {
        this.id = id;
        this.outletCode = outletCode;
        this.outletName = outletName;
        this.status = status;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOutletCode() { return outletCode; }
    public void setOutletCode(String outletCode) { this.outletCode = outletCode; }
    public String getOutletName() { return outletName; }
    public void setOutletName(String outletName) { this.outletName = outletName; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
