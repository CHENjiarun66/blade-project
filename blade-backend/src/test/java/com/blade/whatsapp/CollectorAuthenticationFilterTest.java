package com.blade.whatsapp;

import com.blade.common.tenant.TenantContext;
import com.blade.whatsapp.auth.CollectorAuthenticationFilter;
import com.blade.whatsapp.auth.CollectorAuthenticationService;
import com.blade.whatsapp.auth.CollectorPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CollectorAuthenticationFilterTest {
    @AfterEach void clear(){TenantContext.clear();SecurityContextHolder.clearContext();}

    @Test void rejectsMissingOrInvalidCollectorKey() throws Exception {
        CollectorAuthenticationService service=mock(CollectorAuthenticationService.class);
        when(service.authenticate(any(),any())).thenThrow(new BadCredentialsException("bad"));
        CollectorAuthenticationFilter filter=new CollectorAuthenticationFilter(service);
        MockHttpServletRequest request=new MockHttpServletRequest("POST","/api/internal/whatsapp/batches");
        MockHttpServletResponse response=new MockHttpServletResponse();
        filter.doFilter(request,response,new MockFilterChain());
        assertEquals(401,response.getStatus());
        assertNull(TenantContext.getTenantId());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test void bindsCollectorTenantOnlyForRequestLifetime() throws Exception {
        CollectorAuthenticationService service=mock(CollectorAuthenticationService.class);
        CollectorPrincipal principal=new CollectorPrincipal(7L,42L,9L,Set.of("batch:write"));
        when(service.authenticate(eq("prefix.secret"),any())).thenAnswer(invocation->{TenantContext.setTenantId(42L);return principal;});
        CollectorAuthenticationFilter filter=new CollectorAuthenticationFilter(service);
        MockHttpServletRequest request=new MockHttpServletRequest("POST","/api/internal/whatsapp/batches");
        request.addHeader(CollectorAuthenticationFilter.HEADER,"prefix.secret");
        MockHttpServletResponse response=new MockHttpServletResponse();
        final boolean[] sawContext={false};
        filter.doFilter(request,response,(req,res)->{
            sawContext[0]=Long.valueOf(42L).equals(TenantContext.getTenantId())
                    && SecurityContextHolder.getContext().getAuthentication().getPrincipal()==principal;
        });
        assertTrue(sawContext[0]);
        assertNull(TenantContext.getTenantId());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
