package com.blade.file.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blade.file.entity.FileBusinessBind;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FileBusinessBindMapper extends BaseMapper<FileBusinessBind> {

    /**
     * 按显式 tenant + file 查询有效绑定，绕过租户拦截器的 TenantContext 兜底，
     * 供文件访问策略在匿名 PUBLIC 预览等缺少 TenantContext 的场景安全判定。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM file_business_bind WHERE file_id = #{fileId} AND tenant_id = #{tenantId} "
            + "AND deleted = 0 ORDER BY sort, id")
    List<FileBusinessBind> selectActiveByFileAndTenant(@Param("fileId") Long fileId,
                                                       @Param("tenantId") Long tenantId);
}
