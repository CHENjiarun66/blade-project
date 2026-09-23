package com.blade.order.draft.service;

import com.blade.common.exception.BusinessException;
import com.blade.common.tenant.TenantContext;
import com.blade.customer.dto.CustomerCreateDTO;
import com.blade.customer.dto.CustomerVO;
import com.blade.customer.entity.Customer;
import com.blade.customer.mapper.CustomerMapper;
import com.blade.customer.service.CustomerService;
import com.blade.order.draft.entity.OrderDraft;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 将草稿客户快照解析为客户主档关联，保证草稿确认与快速录单采用同一口径。
 */
@Service
@RequiredArgsConstructor
public class OrderDraftCustomerResolver {
    private final CustomerMapper customerMapper;
    private final CustomerService customerService;

    public Long resolveOrCreate(OrderDraft draft) {
        Long existingId = resolveExistingCustomerId(draft.getCustomerId());
        if (existingId != null) return existingId;

        String name = trimToNull(draft.getCustomerName());
        String phone = trimToNull(draft.getCustomerPhone());
        String address = trimToNull(draft.getCustomerAddress());
        String countryCode = trimToNull(draft.getCustomerCountryCode());
        boolean empty = name == null && phone == null && address == null && countryCode == null;
        if (empty || "散客".equals(name)) return null;
        if (name == null) {
            throw BusinessException.of(400, "请填写客户名称；如不建立客户，请清空客户信息后按散客保存");
        }
        if (phone == null) {
            throw BusinessException.of(400, "新客户请填写客户电话；如不建立客户，请清空客户信息后按散客保存");
        }

        CustomerVO matched = customerService.getByPhone(phone);
        if (matched != null) return matched.getId();

        CustomerCreateDTO create = new CustomerCreateDTO();
        create.setName(name);
        create.setPhones(List.of(phone));
        create.setAddress(address);
        create.setCountryCode(countryCode);
        return customerService.createCustomer(create);
    }

    private Long resolveExistingCustomerId(Long customerId) {
        if (customerId == null) return null;
        Customer customer = customerMapper.selectById(customerId);
        if (customer == null
                || Integer.valueOf(1).equals(customer.getDeleted())
                || !Objects.equals(TenantContext.requireTenantId(), customer.getTenantId())) {
            return null;
        }
        return customer.getId();
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
