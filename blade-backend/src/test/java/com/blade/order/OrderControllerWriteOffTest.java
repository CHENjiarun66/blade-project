package com.blade.order;

import com.blade.common.tenant.TenantContext;
import com.blade.order.controller.OrderController;
import com.blade.order.dto.AddPaymentDTO;
import com.blade.order.service.OrderDeliveryPlanService;
import com.blade.order.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

/**
 * SOW-3: Controller-level unit test proving markAsSettled and writeOffReason
 * reach the DTO-based addPayment service method.
 */
@ExtendWith(MockitoExtension.class)
class OrderControllerWriteOffTest {

    @Mock private OrderServiceImpl orderService;
    @Mock private OrderDeliveryPlanService deliveryPlanService;

    @InjectMocks
    private OrderController controller;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void addPayment_shouldPassMarkAsSettledAndReasonToDtoBasedService() {
        AddPaymentDTO dto = new AddPaymentDTO();
        dto.setAdditionalAmount(new BigDecimal("0.00"));
        dto.setMarkAsSettled(true);
        dto.setWriteOffReason("客户少付5元尾款抹零");

        controller.addPayment(1L, dto);

        ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<AddPaymentDTO> dtoCaptor = ArgumentCaptor.forClass(AddPaymentDTO.class);
        verify(orderService).addPayment(idCaptor.capture(), dtoCaptor.capture());

        assertEquals(1L, idCaptor.getValue());
        AddPaymentDTO captured = dtoCaptor.getValue();
        assertTrue(Boolean.TRUE.equals(captured.getMarkAsSettled()));
        assertEquals("客户少付5元尾款抹零", captured.getWriteOffReason());
    }

    @Test
    void addPayment_shouldPassNormalPaymentWithoutSettlementFlags() {
        AddPaymentDTO dto = new AddPaymentDTO();
        dto.setAdditionalAmount(new BigDecimal("50.00"));

        controller.addPayment(2L, dto);

        ArgumentCaptor<AddPaymentDTO> dtoCaptor = ArgumentCaptor.forClass(AddPaymentDTO.class);
        verify(orderService).addPayment(org.mockito.ArgumentMatchers.eq(2L), dtoCaptor.capture());

        AddPaymentDTO captured = dtoCaptor.getValue();
        assertFalse(Boolean.TRUE.equals(captured.getMarkAsSettled()));
        assertEquals(0, captured.getAdditionalAmount().compareTo(new BigDecimal("50.00")));
    }
}
