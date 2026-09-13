package com.blade.agent;

import com.blade.agent.auth.AgentPrincipal;
import com.blade.agent.dto.AgentCustomerDTO;
import com.blade.agent.entity.AgentKey;
import com.blade.agent.service.AgentCustomerService;
import com.blade.customer.dto.CustomerCreateDTO;
import com.blade.customer.dto.CustomerVO;
import com.blade.customer.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentCustomerServiceTest {
    private final CustomerService customerService = mock(CustomerService.class);
    private final AgentCustomerService service = new AgentCustomerService(customerService);

    @Test
    void duplicatePhoneReturnsExistingCustomerWithoutMutation() {
        CustomerVO existing = new CustomerVO();
        existing.setId(18L);
        existing.setName("已有客户");
        when(customerService.getByPhone("8613800000000")).thenReturn(existing);

        AgentCustomerDTO.CreateResult result = service.create(
                request(List.of("+86 138-0000-0000")), principal(7L));

        assertEquals("DUPLICATE", result.result());
        assertEquals(18L, result.customerId());
        assertEquals("8613800000000", result.duplicatePhone());
        verify(customerService, never()).createCustomerFromAgent(any(), any());
    }

    @Test
    void createNormalizesAndDeduplicatesPhonesAndPreservesAgentAttribution() {
        when(customerService.getByPhone(any())).thenReturn(null);
        when(customerService.createCustomerFromAgent(any(), eq(7L))).thenReturn(31L);

        AgentCustomerDTO.CreateResult result = service.create(
                request(List.of("138 0000-0000", "13800000000", "+86-13900000000")), principal(7L));

        ArgumentCaptor<CustomerCreateDTO> captor = ArgumentCaptor.forClass(CustomerCreateDTO.class);
        verify(customerService).createCustomerFromAgent(captor.capture(), eq(7L));
        assertEquals(List.of("13800000000", "8613900000000"), captor.getValue().getPhones());
        assertEquals("测试客户", captor.getValue().getName());
        assertNull(captor.getValue().getAddress());
        assertEquals("CREATED", result.result());
        assertEquals(31L, result.customerId());
    }

    private AgentCustomerDTO.CreateRequest request(List<String> phones) {
        return new AgentCustomerDTO.CreateRequest(" 测试客户 ", phones, " ", " Agent 新增 ", "+86");
    }

    private AgentPrincipal principal(Long id) {
        AgentKey key = new AgentKey();
        key.setId(id);
        key.setTenantId(1L);
        key.setKeyPrefix("agk_test");
        key.setName("Test Agent");
        key.setScopes("customers:read,customers:create");
        return AgentPrincipal.from(key);
    }
}
