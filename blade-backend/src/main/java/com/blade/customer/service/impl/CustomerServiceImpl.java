package com.blade.customer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blade.common.result.PageResult;
import com.blade.common.tenant.TenantContext;
import com.blade.customer.dto.CustomerCreateDTO;
import com.blade.customer.dto.CustomerPageDTO;
import com.blade.customer.dto.CustomerUpdateDTO;
import com.blade.customer.dto.CustomerVO;
import com.blade.customer.entity.Customer;
import com.blade.customer.entity.CustomerPhone;
import com.blade.customer.mapper.CustomerMapper;
import com.blade.customer.mapper.CustomerPhoneMapper;
import com.blade.customer.service.CustomerService;
import com.blade.order.entity.Order;
import com.blade.order.mapper.OrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CustomerServiceImpl implements CustomerService {

    private final CustomerMapper customerMapper;
    private final CustomerPhoneMapper customerPhoneMapper;
    private final OrderMapper orderMapper;

    @Autowired
    public CustomerServiceImpl(CustomerMapper customerMapper, CustomerPhoneMapper customerPhoneMapper, OrderMapper orderMapper) {
        this.customerMapper = customerMapper;
        this.customerPhoneMapper = customerPhoneMapper;
        this.orderMapper = orderMapper;
    }

    @Override
    public PageResult<CustomerVO> pageList(CustomerPageDTO dto) {
        // TenantLineInnerInterceptor 会自动添加 tenant_id 条件，不需要手动添加
        Page<Customer> page = new Page<>(dto.getCurrent(), dto.getSize());
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Customer::getDeleted, 0);

        if (dto.getKeyword() != null && !dto.getKeyword().isBlank()) {
            String keyword = dto.getKeyword();
            // 先找出匹配的电话对应的客户ID
            LambdaQueryWrapper<CustomerPhone> phoneWrapper = new LambdaQueryWrapper<>();
            phoneWrapper.like(CustomerPhone::getPhone, keyword)
                       .eq(CustomerPhone::getDeleted, 0);
            List<CustomerPhone> matchingPhones = customerPhoneMapper.selectList(phoneWrapper);
            List<Long> customerIds = matchingPhones.stream()
                    .map(CustomerPhone::getCustomerId)
                    .distinct()
                    .collect(Collectors.toList());

            // 搜索客户名称 OR 电话匹配的客户的ID
            if (customerIds.isEmpty()) {
                // 只有名称匹配
                wrapper.like(Customer::getName, keyword);
            } else {
                // 名称匹配 或 电话匹配
                wrapper.and(w -> w.like(Customer::getName, keyword)
                                 .or()
                                 .in(Customer::getId, customerIds));
            }
        }

        wrapper.orderByDesc(Customer::getId);
        IPage<Customer> result = customerMapper.selectPage(page, wrapper);

        List<CustomerVO> voList = result.getRecords().stream().map(customer -> {
            CustomerVO vo = convertToVO(customer);
            // 查询该客户的所有电话
            LambdaQueryWrapper<CustomerPhone> phoneWrapper = new LambdaQueryWrapper<>();
            phoneWrapper.eq(CustomerPhone::getCustomerId, customer.getId())
                       .eq(CustomerPhone::getDeleted, 0);
            List<CustomerPhone> phones = customerPhoneMapper.selectList(phoneWrapper);
            vo.setPhones(phones.stream().map(CustomerPhone::getPhone).collect(Collectors.toList()));
            // 查询该客户的订单数量
            LambdaQueryWrapper<Order> orderWrapper = new LambdaQueryWrapper<>();
            orderWrapper.eq(Order::getCustomerId, customer.getId());
            Long orderCount = orderMapper.selectCount(orderWrapper);
            vo.setOrderCount(orderCount.intValue());
            return vo;
        }).collect(Collectors.toList());

        PageResult<CustomerVO> pageResult = new PageResult<>();
        pageResult.setRecords(voList);
        pageResult.setTotal(result.getTotal());
        pageResult.setSize(result.getSize());
        pageResult.setCurrent(result.getCurrent());
        pageResult.setPages(result.getPages());
        return pageResult;
    }

    @Override
    public CustomerVO getById(Long id) {
        // TenantLineInnerInterceptor 会自动添加 tenant_id 条件，不需要手动添加
        Customer customer = customerMapper.selectOne(new LambdaQueryWrapper<Customer>()
                .eq(Customer::getId, id)
                .eq(Customer::getDeleted, 0));

        if (customer == null) {
            return null;
        }

        CustomerVO vo = convertToVO(customer);
        // 查询该客户的所有电话
        LambdaQueryWrapper<CustomerPhone> phoneWrapper = new LambdaQueryWrapper<>();
        phoneWrapper.eq(CustomerPhone::getCustomerId, customer.getId())
                   .eq(CustomerPhone::getDeleted, 0);
        List<CustomerPhone> phones = customerPhoneMapper.selectList(phoneWrapper);
        vo.setPhones(phones.stream().map(CustomerPhone::getPhone).collect(Collectors.toList()));
        // 查询该客户的订单数量
        LambdaQueryWrapper<Order> orderWrapper = new LambdaQueryWrapper<>();
        orderWrapper.eq(Order::getCustomerId, customer.getId());
        Long orderCount = orderMapper.selectCount(orderWrapper);
        vo.setOrderCount(orderCount.intValue());
        return vo;
    }

    @Override
    public CustomerVO getByPhone(String phone) {
        // 去掉 + 号，空格、横杠等，只保留纯数字进行搜索
        String normalizedPhone = phone.replaceAll("[\\s\\-+]", "");

        // 1. 通过电话查询客户电话表，找到客户ID
        // TenantLineInnerInterceptor 会自动添加 tenant_id 条件
        LambdaQueryWrapper<CustomerPhone> phoneWrapper = new LambdaQueryWrapper<>();
        phoneWrapper.eq(CustomerPhone::getPhone, normalizedPhone)
                    .eq(CustomerPhone::getDeleted, 0);
        CustomerPhone customerPhone = customerPhoneMapper.selectOne(phoneWrapper);

        if (customerPhone == null) {
            return null;
        }

        // 2. 通过客户ID查询客户信息
        // TenantLineInnerInterceptor 会自动添加 tenant_id 条件，不需要手动添加
        LambdaQueryWrapper<Customer> customerWrapper = new LambdaQueryWrapper<>();
        customerWrapper.eq(Customer::getId, customerPhone.getCustomerId())
                       .eq(Customer::getDeleted, 0);
        Customer customer = customerMapper.selectOne(customerWrapper);

        if (customer == null) {
            return null;
        }

        // 3. 查询该客户的所有电话
        LambdaQueryWrapper<CustomerPhone> allPhonesWrapper = new LambdaQueryWrapper<>();
        allPhonesWrapper.eq(CustomerPhone::getCustomerId, customer.getId())
                        .eq(CustomerPhone::getDeleted, 0);
        List<CustomerPhone> phones = customerPhoneMapper.selectList(allPhonesWrapper);

        // 4. 组装VO
        CustomerVO vo = convertToVO(customer);
        vo.setPhones(phones.stream().map(CustomerPhone::getPhone).collect(Collectors.toList()));
        // 5. 查询该客户的订单数量
        LambdaQueryWrapper<Order> orderWrapper = new LambdaQueryWrapper<>();
        orderWrapper.eq(Order::getCustomerId, customer.getId());
        Long orderCount = orderMapper.selectCount(orderWrapper);
        vo.setOrderCount(orderCount.intValue());

        return vo;
    }

    @Override
    @Transactional
    public Long createCustomer(CustomerCreateDTO dto) {
        Long tenantId = TenantContext.getTenantId() != null ? TenantContext.getTenantId() : 1L;

        // 1. 创建客户
        Customer customer = new Customer();
        customer.setName(dto.getName());
        customer.setAddress(dto.getAddress());
        customer.setRemark(dto.getRemark());
        customer.setTenantId(tenantId);
        customer.setDeleted(0);
        customerMapper.insert(customer);

        // 2. 创建电话列表
        if (dto.getPhones() != null && !dto.getPhones().isEmpty()) {
            for (int i = 0; i < dto.getPhones().size(); i++) {
                String normalizedPhone = dto.getPhones().get(i).replaceAll("[\\s\\-+]", "");
                CustomerPhone customerPhone = new CustomerPhone();
                customerPhone.setCustomerId(customer.getId());
                customerPhone.setPhone(normalizedPhone);
                customerPhone.setIsPrimary(i == 0 ? 1 : 0); // 第一个设为主电话
                customerPhone.setTenantId(tenantId);
                customerPhone.setDeleted(0);
                customerPhoneMapper.insert(customerPhone);
            }
        }

        return customer.getId();
    }

    @Override
    @Transactional
    public void updateCustomer(CustomerUpdateDTO dto) {
        // 使用 MyBatis-Plus 的逻辑删除，TenantLineInnerInterceptor 会自动处理租户过滤
        // 不要手动添加 tenantId 条件，否则会导致 updateById 时 tenantId 为 null 的问题
        Customer customer = customerMapper.selectOne(new LambdaQueryWrapper<Customer>()
                .eq(Customer::getId, dto.getId())
                .eq(Customer::getDeleted, 0));

        if (customer == null) {
            throw new RuntimeException("客户不存在");
        }

        customer.setName(dto.getName());
        customer.setAddress(dto.getAddress());
        customer.setRemark(dto.getRemark());
        customerMapper.updateById(customer);

        // 更新电话列表：先删除旧的，再插入新的
        // TenantLineInnerInterceptor 会自动添加 tenant_id 条件，不要手动添加
        // 注意：使用 deleteById 进行软删除，而不是 updateById（@TableLogic 对 updateById 不生效）
        LambdaQueryWrapper<CustomerPhone> phoneWrapper = new LambdaQueryWrapper<>();
        phoneWrapper.eq(CustomerPhone::getCustomerId, dto.getId());
        List<CustomerPhone> oldPhones = customerPhoneMapper.selectList(phoneWrapper);
        for (CustomerPhone phone : oldPhones) {
            customerPhoneMapper.deleteById(phone.getId());
        }

        if (dto.getPhones() != null && !dto.getPhones().isEmpty()) {
            for (int i = 0; i < dto.getPhones().size(); i++) {
                String normalizedPhone = dto.getPhones().get(i).replaceAll("[\\s\\-+]", "");
                CustomerPhone customerPhone = new CustomerPhone();
                customerPhone.setCustomerId(dto.getId());
                customerPhone.setPhone(normalizedPhone);
                customerPhone.setIsPrimary(i == 0 ? 1 : 0);
                customerPhone.setDeleted(0);
                customerPhoneMapper.insert(customerPhone);
            }
        }
    }

    @Override
    @Transactional
    public void deleteCustomer(Long id) {
        // 使用 MyBatis-Plus 的逻辑删除，TenantLineInnerInterceptor 会自动处理租户过滤
        // 不要手动添加 tenantId 条件，否则会导致 updateById 时 tenantId 为 null 的问题
        Customer customer = customerMapper.selectOne(new LambdaQueryWrapper<Customer>()
                .eq(Customer::getId, id)
                .eq(Customer::getDeleted, 0));

        if (customer == null) {
            throw new RuntimeException("客户不存在");
        }

        // MyBatis-Plus 逻辑删除会自动处理：UPDATE SET deleted=1 WHERE id=? AND deleted=0
        // 同时 TenantLineInnerInterceptor 会添加 tenant_id 条件
        customerMapper.deleteById(id);

        // 同时软删除客户电话
        // 注意：使用 deleteById 进行软删除，而不是 updateById（@TableLogic 对 updateById 不生效）
        LambdaQueryWrapper<CustomerPhone> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CustomerPhone::getCustomerId, id);
        List<CustomerPhone> phones = customerPhoneMapper.selectList(wrapper);
        for (CustomerPhone phone : phones) {
            customerPhoneMapper.deleteById(phone.getId());
        }
    }

    private CustomerVO convertToVO(Customer customer) {
        CustomerVO vo = new CustomerVO();
        vo.setId(customer.getId());
        vo.setName(customer.getName());
        vo.setAddress(customer.getAddress());
        vo.setRemark(customer.getRemark());
        vo.setCreateTime(customer.getCreateTime());
        return vo;
    }
}
