package com.blade.file.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blade.file.entity.FileStorage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FileStorageMapper extends BaseMapper<FileStorage> {

    /**
     * 按全局唯一 id + status 读取文件，绕过租户拦截器的 TenantContext 兜底。
     * 仅供匿名 PUBLIC 媒体加载路径先定位文件，随后由策略按文件自带 tenant 与业务绑定判定。
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT * FROM file_storage WHERE id = #{id} AND status = 1 LIMIT 1")
    FileStorage selectActiveByIdGlobal(@Param("id") Long id);

    /** 系统清理任务：显式列出存在文件的 tenant（不依赖/不默认 TenantContext）。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT DISTINCT tenant_id FROM file_storage WHERE tenant_id IS NOT NULL ORDER BY tenant_id")
    List<Long> selectDistinctTenantIds();
}
