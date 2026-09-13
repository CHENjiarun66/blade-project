package com.blade.agent.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class AgentCustomerDTO {
    private AgentCustomerDTO() {
    }

    public static class PageRequest {
        @NotNull(message = "页码不能为空")
        @Min(value = 1, message = "页码不能小于1")
        private Integer current = 1;

        @NotNull(message = "每页数量不能为空")
        @Min(value = 1, message = "每页数量不能小于1")
        @Max(value = 100, message = "每页最多100条")
        private Integer size = 20;

        @Size(max = 100, message = "搜索关键字最多100位")
        private String keyword;

        public Integer getCurrent() {
            return current;
        }

        public void setCurrent(Integer current) {
            this.current = current;
        }

        public Integer getSize() {
            return size;
        }

        public void setSize(Integer size) {
            this.size = size;
        }

        public String getKeyword() {
            return keyword;
        }

        public void setKeyword(String keyword) {
            this.keyword = keyword;
        }
    }

    public record CustomerView(
            Long id,
            String name,
            String countryCode,
            String countryName,
            List<String> phones,
            String address,
            String remark,
            Integer orderCount,
            LocalDateTime createTime) {
    }

    public record CreateRequest(
            @NotBlank(message = "客户名称不能为空")
            @Size(max = 50, message = "客户名称最多50位") String name,
            @NotEmpty(message = "至少填写一个电话号码")
            @Size(max = 10, message = "电话号码最多10个")
            List<@NotBlank(message = "电话号码不能为空")
                    @Size(max = 20, message = "电话号码最多20位") String> phones,
            @Size(max = 255, message = "地址最多255位") String address,
            @Size(max = 500, message = "备注最多500位") String remark,
            @Size(max = 8, message = "国家区号最多8位") String countryCode) {
    }

    public record CreateResult(
            Long customerId,
            String name,
            String result,
            String duplicatePhone) {
    }
}
