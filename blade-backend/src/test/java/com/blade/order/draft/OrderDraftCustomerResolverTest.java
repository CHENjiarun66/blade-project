package com.blade.order.draft;

import com.blade.common.exception.BusinessException;
import com.blade.common.tenant.TenantContext;
import com.blade.customer.dto.CustomerCreateDTO;
import com.blade.customer.dto.CustomerVO;
import com.blade.customer.entity.Customer;
import com.blade.customer.mapper.CustomerMapper;
import com.blade.customer.service.CustomerService;
import com.blade.order.draft.entity.OrderDraft;
import com.blade.order.draft.service.OrderDraftCustomerResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderDraftCustomerResolverTest {
    @Mock private CustomerMapper customerMapper;
    @Mock private CustomerService customerService;
    @InjectMocks private OrderDraftCustomerResolver resolver;

    @BeforeEach
    void bindTenant() {
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void existingCustomerIsUsedWithoutCreatingAnotherMaster() {
        Customer customer = customer(9L, 1L, 0);
        when(customerMapper.selectById(9L)).thenReturn(customer);
        OrderDraft draft = new OrderDraft();
        draft.setCustomerId(9L);

        assertEquals(9L, resolver.resolveOrCreate(draft));
        verifyNoInteractions(customerService);
    }

    @Test
    void newCustomerIsCreatedWithCountryCodeAndAddress() {
        when(customerService.getByPhone("+232 76-857-336")).thenReturn(null);
        when(customerService.createCustomer(any())).thenReturn(18L);
        OrderDraft draft = draft(" K. Sankoh ", "+232 76-857-336", "+232", " Freetown ");

        assertEquals(18L, resolver.resolveOrCreate(draft));
        ArgumentCaptor<CustomerCreateDTO> captor = ArgumentCaptor.forClass(CustomerCreateDTO.class);
        verify(customerService).createCustomer(captor.capture());
        assertEquals("K. Sankoh", captor.getValue().getName());
        assertEquals(java.util.List.of("+232 76-857-336"), captor.getValue().getPhones());
        assertEquals("+232", captor.getValue().getCountryCode());
        assertEquals("Freetown", captor.getValue().getAddress());
    }

    @Test
    void matchingPhoneLinksExistingCustomerWithoutDuplicate() {
        CustomerVO match = new CustomerVO();
        match.setId(27L);
        when(customerService.getByPhone("23276857336")).thenReturn(match);

        assertEquals(27L, resolver.resolveOrCreate(draft("识别名称", "23276857336", null, null)));
        verify(customerService, never()).createCustomer(any());
    }

    @Test
    void blankCustomerIsWalkInAndDoesNotCreateMaster() {
        assertNull(resolver.resolveOrCreate(new OrderDraft()));
        verifyNoInteractions(customerMapper, customerService);
    }

    @Test
    void namedNewCustomerWithoutPhoneIsRejected() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> resolver.resolveOrCreate(draft("新客户", null, "+86", null)));
        assertEquals(400, error.getCode());
        assertTrue(error.getMessage().contains("新客户请填写客户电话"));
        verifyNoInteractions(customerService);
    }

    @Test
    void staleCrossTenantCustomerIdFallsBackToPhoneMatching() {
        when(customerMapper.selectById(9L)).thenReturn(customer(9L, 2L, 0));
        CustomerVO match = new CustomerVO();
        match.setId(30L);
        when(customerService.getByPhone("13800138000")).thenReturn(match);
        OrderDraft draft = draft("客户", "13800138000", "+86", null);
        draft.setCustomerId(9L);

        assertEquals(30L, resolver.resolveOrCreate(draft));
    }

    private OrderDraft draft(String name, String phone, String countryCode, String address) {
        OrderDraft draft = new OrderDraft();
        draft.setCustomerName(name);
        draft.setCustomerPhone(phone);
        draft.setCustomerCountryCode(countryCode);
        draft.setCustomerAddress(address);
        return draft;
    }

    private Customer customer(Long id, Long tenantId, Integer deleted) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setTenantId(tenantId);
        customer.setDeleted(deleted);
        return customer;
    }
}
