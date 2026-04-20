package com.blade.system.tenant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_tenant")
public class Tenant {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String tenantName;

    private String tenantCode;

    private Integer status;

    private LocalDateTime expireTime;

    private Long packageId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
