package com.blade.file;

import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.blade.common.tenant.TenantContext;
import com.blade.common.tenant.TenantLineHandler;
import com.blade.file.policy.FileBusinessAccessPolicy;
import com.blade.outlet.policy.OutletAccessPolicy;
import com.blade.outlet.policy.OutletAccessScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileVisibilitySqlRegressionTest {
    @AfterEach
    void clearContext() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @CsvSource({"true,true,3", "true,false,4", "false,true,4", "false,false,4"})
    void visibilityBindsOnlyUsedParametersAndPassesTenantSqlParser(
            boolean peopleAll, boolean viewAll, int expectedParameterCount) {
        TenantContext.setTenantId(1L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "user", "n/a", viewAll
                        ? List.of(new SimpleGrantedAuthority("btn:file:viewAll"))
                        : List.of(new SimpleGrantedAuthority("menu:file"))));
        OutletAccessPolicy outlets = mock(OutletAccessPolicy.class);
        when(outlets.resolveCurrentScope()).thenReturn(new OutletAccessScope(
                1L, OutletAccessScope.ActorType.USER, 4L, OutletAccessScope.ALL,
                peopleAll, true, List.of(1L, 2L), List.of(1L, 2L), 1L));
        FileBusinessAccessPolicy policy = new FileBusinessAccessPolicy(null, null, null, null, outlets);
        FileBusinessAccessPolicy.VisibilityCondition condition = policy.buildVisibilityCondition();
        assertEquals(expectedParameterCount, condition.params().length);
        QueryWrapper<Object> wrapper = new QueryWrapper<>();
        wrapper.apply(condition.sql(), condition.params());
        String sql = wrapper.getSqlSegment().replaceAll("#\\{[^}]+}", "?");
        TenantLineInnerInterceptor interceptor = new TenantLineInnerInterceptor(new TenantLineHandler());
        assertDoesNotThrow(() -> interceptor.parserSingle(
                "SELECT * FROM file_storage WHERE tenant_id = ? AND " + sql + " AND status = ? ORDER BY create_time DESC", null));
    }
}
