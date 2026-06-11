package com.blade.customer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "客户VO")
public class CustomerVO {

    @Schema(description = "客户ID")
    private Long id;

    @Schema(description = "客户名称")
    private String name;

    @Schema(description = "客户地址")
    private String address;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "国家区号")
    private String countryCode;

    @Schema(description = "国家名称")
    private String countryName;

    @Schema(description = "电话列表")
    private List<String> phones;

    @Schema(description = "订单数量")
    private Integer orderCount;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public String getCountryName() { return countryName; }
    public void setCountryName(String countryName) { this.countryName = countryName; }
    /** 根据 countryCode 计算国旗 emoji */
    public String getCountryFlag() {
        if (countryCode == null || countryCode.length() < 2) return null;
        return countryCodeToFlag(countryCode);
    }
    private static String countryCodeToFlag(String code) {
        String clean = code.startsWith("+") ? code.substring(1) : code;
        if (clean.isEmpty()) return null;
        StringBuilder flag = new StringBuilder();
        for (char c : clean.toCharArray()) {
            flag.append(Character.toChars(0x1F1E6 - 1 + Character.digit(c, 10)));
        }
        return flag.toString();
    }
    public List<String> getPhones() { return phones; }
    public void setPhones(List<String> phones) { this.phones = phones; }
    public Integer getOrderCount() { return orderCount; }
    public void setOrderCount(Integer orderCount) { this.orderCount = orderCount; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
