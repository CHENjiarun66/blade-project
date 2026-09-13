package com.blade.agent.service;

import com.blade.agent.auth.AgentPrincipal;
import com.blade.agent.dto.AgentCustomerDTO;
import com.blade.common.exception.BusinessException;
import com.blade.common.result.PageResult;
import com.blade.customer.dto.CustomerCreateDTO;
import com.blade.customer.dto.CustomerPageDTO;
import com.blade.customer.dto.CustomerVO;
import com.blade.customer.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentCustomerService {
    private final CustomerService customerService;

    public PageResult<AgentCustomerDTO.CustomerView> page(AgentCustomerDTO.PageRequest request) {
        CustomerPageDTO query = new CustomerPageDTO();
        query.setCurrent(request.getCurrent());
        query.setSize(request.getSize());
        query.setKeyword(trimToNull(request.getKeyword()));
        query.setMine(false);

        PageResult<CustomerVO> source = customerService.pageList(query);
        return new PageResult<>(source.getRecords().stream().map(this::toView).toList(),
                source.getTotal(), source.getSize(), source.getCurrent());
    }

    public AgentCustomerDTO.CustomerView detail(Long id) {
        CustomerVO customer = customerService.getById(id);
        if (customer == null) {
            throw BusinessException.of(404, "客户不存在");
        }
        return toView(customer);
    }

    @Transactional
    public AgentCustomerDTO.CreateResult create(AgentCustomerDTO.CreateRequest request,
                                                 AgentPrincipal principal) {
        List<String> phones = normalizePhones(request.phones());
        for (String phone : phones) {
            CustomerVO existing = customerService.getByPhone(phone);
            if (existing != null) {
                boolean mayReadCustomer = principal.getAuthorities().stream()
                        .anyMatch(authority -> "agent:customers:read".equals(authority.getAuthority()));
                return new AgentCustomerDTO.CreateResult(
                        mayReadCustomer ? existing.getId() : null,
                        mayReadCustomer ? existing.getName() : null,
                        "DUPLICATE", phone);
            }
        }

        CustomerCreateDTO dto = new CustomerCreateDTO();
        dto.setName(request.name().trim());
        dto.setPhones(phones);
        dto.setAddress(trimToNull(request.address()));
        dto.setRemark(trimToNull(request.remark()));
        dto.setCountryCode(trimToNull(request.countryCode()));

        Long customerId = customerService.createCustomerFromAgent(dto, principal.getKeyId());
        return new AgentCustomerDTO.CreateResult(customerId, dto.getName(), "CREATED", null);
    }

    private List<String> normalizePhones(List<String> rawPhones) {
        LinkedHashSet<String> phones = new LinkedHashSet<>();
        for (String rawPhone : rawPhones) {
            String phone = rawPhone.replaceAll("[\\s\\-+]", "");
            if (phone.isBlank()) {
                throw BusinessException.of(400, "电话号码不能为空");
            }
            if (!phone.chars().allMatch(Character::isDigit)) {
                throw BusinessException.of(400, "电话号码只能包含数字、空格、横杠或加号");
            }
            phones.add(phone);
        }
        return List.copyOf(phones);
    }

    private AgentCustomerDTO.CustomerView toView(CustomerVO customer) {
        return new AgentCustomerDTO.CustomerView(
                customer.getId(), customer.getName(), customer.getCountryCode(), customer.getCountryName(),
                customer.getPhones() == null ? List.of() : List.copyOf(customer.getPhones()),
                customer.getAddress(), customer.getRemark(), customer.getOrderCount(), customer.getCreateTime());
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
