package com.blade.customer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blade.common.result.PageResult;
import com.blade.common.tenant.TenantContext;
import com.blade.customer.dto.CustomerCreateDTO;
import com.blade.customer.dto.CustomerOrderPageDTO;
import com.blade.customer.dto.CustomerPageDTO;
import com.blade.customer.dto.CustomerOrderVO;
import com.blade.customer.dto.CustomerPreferenceQueryDTO;
import com.blade.customer.dto.CustomerPreferenceVO;
import com.blade.customer.dto.CustomerStatsVO;
import com.blade.customer.dto.CustomerUpdateDTO;
import com.blade.customer.dto.CustomerVO;
import com.blade.customer.entity.Customer;
import com.blade.customer.entity.CustomerPhone;
import com.blade.customer.entity.CustomerOperationLog;
import com.blade.customer.mapper.CustomerMapper;
import com.blade.customer.mapper.CustomerPhoneMapper;
import com.blade.customer.mapper.CustomerOperationLogMapper;
import com.blade.customer.service.CustomerService;
import com.blade.order.entity.Order;
import com.blade.order.entity.OrderItem;
import com.blade.order.mapper.OrderMapper;
import com.blade.order.mapper.OrderItemMapper;
import com.blade.system.user.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class CustomerServiceImpl implements CustomerService {

    private final CustomerMapper customerMapper;
    private final CustomerPhoneMapper customerPhoneMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final CustomerOperationLogMapper operationLogMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public CustomerServiceImpl(CustomerMapper customerMapper, CustomerPhoneMapper customerPhoneMapper, OrderMapper orderMapper, OrderItemMapper orderItemMapper, CustomerOperationLogMapper operationLogMapper, RedisTemplate<String, Object> redisTemplate) {
        this.customerMapper = customerMapper;
        this.customerPhoneMapper = customerPhoneMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.operationLogMapper = operationLogMapper;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public PageResult<CustomerVO> pageList(CustomerPageDTO dto) {
        // TenantLineInnerInterceptor 会自动添加 tenant_id 条件，不需要手动添加
        Page<Customer> page = new Page<>(dto.getCurrent(), dto.getSize());
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Customer::getDeleted, 0);

        // mine=true 时只看自己创建的客户
        if (Boolean.TRUE.equals(dto.getMine())) {
            Long currentUserId = getCurrentUserId();
            wrapper.eq(Customer::getCreateBy, currentUserId);
        }

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
        Long currentUserId = getCurrentUserId();

        // 0. 检查电话是否重复
        if (dto.getPhones() != null && !dto.getPhones().isEmpty()) {
            for (String phone : dto.getPhones()) {
                checkPhoneDuplicate(tenantId, phone, null);
            }
        }

        // 1. 创建客户
        Customer customer = new Customer();
        customer.setName(dto.getName());
        customer.setAddress(dto.getAddress());
        customer.setRemark(dto.getRemark());
        customer.setCountryCode(dto.getCountryCode());
        customer.setTenantId(tenantId);
        customer.setCreateBy(currentUserId);
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

        // 3. 记录操作日志
        logOperation(tenantId, customer.getId(), currentUserId, "CREATE",
            "{\"name\":\"" + dto.getName() + "\",\"address\":\"" + (dto.getAddress() != null ? dto.getAddress() : "") + "\"}");

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
        customer.setCountryCode(dto.getCountryCode());
        customerMapper.updateById(customer);

        // 更新电话列表：先检查重复，再删除旧的，最后插入新的
        // TenantLineInnerInterceptor 会自动添加 tenant_id 条件，不要手动添加
        Long tenantId = customer.getTenantId() != null ? customer.getTenantId() : 1L;

        // 0. 检查电话是否重复（排除当前客户的旧电话）
        if (dto.getPhones() != null && !dto.getPhones().isEmpty()) {
            for (String phone : dto.getPhones()) {
                checkPhoneDuplicate(tenantId, phone, dto.getId());
            }
        }

        // 1. 删除旧电话
        // 注意：使用 deleteById 进行软删除，而不是 updateById（@TableLogic 对 updateById 不生效）
        LambdaQueryWrapper<CustomerPhone> phoneWrapper = new LambdaQueryWrapper<>();
        phoneWrapper.eq(CustomerPhone::getCustomerId, dto.getId());
        List<CustomerPhone> oldPhones = customerPhoneMapper.selectList(phoneWrapper);
        for (CustomerPhone phone : oldPhones) {
            customerPhoneMapper.deleteById(phone.getId());
        }

        // 2. 插入新电话
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

        // 3. 记录操作日志
        Long currentUserId = getCurrentUserId();
        logOperation(tenantId, dto.getId(), currentUserId, "UPDATE",
            "{\"name\":\"" + dto.getName() + "\"}");
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

        // 检查是否有进行中的订单（status NOT IN 4,5）
        LambdaQueryWrapper<Order> orderWrapper = new LambdaQueryWrapper<>();
        orderWrapper.eq(Order::getCustomerId, id)
                   .notIn(Order::getStatus, java.util.Arrays.asList(4, 5)); // 排除已发货、已完成
        List<Order> activeOrders = orderMapper.selectList(orderWrapper);
        if (!activeOrders.isEmpty()) {
            String orderNos = activeOrders.stream()
                    .map(Order::getOrderNo)
                    .limit(3) // 最多显示3个
                    .collect(Collectors.joining(", "));
            if (activeOrders.size() > 3) {
                orderNos += " 等" + activeOrders.size() + "个订单";
            }
            throw new RuntimeException("该客户有进行中的订单，请先处理：" + orderNos);
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

        // 记录操作日志
        Long currentUserId = getCurrentUserId();
        Long tenantId = customer.getTenantId() != null ? customer.getTenantId() : 1L;
        logOperation(tenantId, id, currentUserId, "DELETE",
            "{\"name\":\"" + customer.getName() + "\"}");
    }

    private CustomerVO convertToVO(Customer customer) {
        CustomerVO vo = new CustomerVO();
        vo.setId(customer.getId());
        vo.setName(customer.getName());
        vo.setAddress(customer.getAddress());
        vo.setRemark(customer.getRemark());
        vo.setCountryCode(customer.getCountryCode());
        vo.setCountryName(customer.getCountryName());
        vo.setCreateTime(customer.getCreateTime());
        return vo;
    }

    @Override
    public CustomerStatsVO getStats(Long customerId) {
        Customer customer = customerMapper.selectOne(new LambdaQueryWrapper<Customer>()
                .eq(Customer::getId, customerId)
                .eq(Customer::getDeleted, 0));
        if (customer == null) {
            throw new RuntimeException("客户不存在");
        }

        LambdaQueryWrapper<Order> orderWrapper = new LambdaQueryWrapper<>();
        orderWrapper.eq(Order::getCustomerId, customerId);
        List<Order> orders = orderMapper.selectList(orderWrapper);

        int totalOrders = orders.size();
        int completedOrders = 0;
        java.math.BigDecimal totalSpending = java.math.BigDecimal.ZERO;
        java.time.LocalDateTime lastOrderTime = null;
        java.time.LocalDateTime firstOrderTime = null;

        for (Order order : orders) {
            if (order.getStatus() == 5) { // 已完成
                completedOrders++;
            }
            if (order.getPaidAmount() != null) {
                totalSpending = totalSpending.add(order.getPaidAmount());
            }
            java.time.LocalDateTime ct = order.getCreateTime();
            if (ct != null) {
                if (lastOrderTime == null || ct.isAfter(lastOrderTime)) lastOrderTime = ct;
                if (firstOrderTime == null || ct.isBefore(firstOrderTime)) firstOrderTime = ct;
            }
        }

        CustomerStatsVO stats = new CustomerStatsVO();
        stats.setCustomerId(customerId);
        stats.setCustomerName(customer.getName());
        stats.setTotalOrders(totalOrders);
        stats.setCompletedOrders(completedOrders);
        stats.setTotalSpending(totalSpending);
        stats.setLastOrderTime(lastOrderTime != null ? lastOrderTime.toString() : null);
        stats.setFirstOrderTime(firstOrderTime != null ? firstOrderTime.toString() : null);
        return stats;
    }

    @Override
    public PageResult<CustomerOrderVO> getCustomerOrders(Long customerId, CustomerOrderPageDTO dto) {
        // 设置分页参数默认值和最大值
        int current = dto.getCurrent() != null && dto.getCurrent() > 0 ? dto.getCurrent() : 1;
        int size = dto.getSize() != null && dto.getSize() > 0 ? Math.min(dto.getSize(), dto.getMaxSize() != null ? dto.getMaxSize() : 100) : 20;

        Page<Order> page = new Page<>(current, size);
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getCustomerId, customerId)
               .orderByDesc(Order::getCreateTime);

        // 查询订单总数
        Long totalCount = orderMapper.selectCount(wrapper);
        if (totalCount == 0) {
            PageResult<CustomerOrderVO> emptyResult = new PageResult<>();
            emptyResult.setRecords(java.util.Collections.emptyList());
            emptyResult.setTotal(0L);
            emptyResult.setCurrent(current);
            emptyResult.setSize(size);
            emptyResult.setPages(0);
            return emptyResult;
        }

        // 查询当前页的订单
        List<Order> orders = orderMapper.selectPage(page, wrapper).getRecords();

        // 一次性查询所有订单项，避免 N+1 查询
        java.util.List<Long> orderIds = orders.stream().map(Order::getId).collect(java.util.stream.Collectors.toList());
        LambdaQueryWrapper<OrderItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.in(OrderItem::getOrderId, orderIds);
        List<OrderItem> allItems = orderItemMapper.selectList(itemWrapper);

        // 按 orderId 分组
        java.util.Map<Long, List<OrderItem>> itemsByOrderId = allItems.stream()
                .collect(java.util.stream.Collectors.groupingBy(OrderItem::getOrderId));

        List<CustomerOrderVO> voList = orders.stream().map(order -> {
            CustomerOrderVO vo = new CustomerOrderVO();
            vo.setId(order.getId());
            vo.setOrderNo(order.getOrderNo());
            vo.setStatus(order.getStatus());
            vo.setStatusName(getStatusName(order.getStatus()));
            vo.setPaymentStatus(order.getPaymentStatus());
            vo.setCollectionStatus(order.getCollectionStatus());
            vo.setFulfillmentStatus(order.getFulfillmentStatus());
            vo.setLegacyUnmigrated(order.getCollectionStatus() == null);
            vo.setTotalAmount(order.getTotalAmount());
            vo.setPaidAmount(order.getPaidAmount());
            vo.setTotalAmountText(formatMoney(order.getTotalAmount()));
            vo.setPaidAmountText(formatMoney(order.getPaidAmount()));
            vo.setCreateTime(order.getCreateTime());

            // 从已分组的 Map 中获取订单项
            List<OrderItem> items = itemsByOrderId.getOrDefault(order.getId(), java.util.Collections.emptyList());

            int totalQty = items.stream().mapToInt(item -> item.getQuantity() != null ? item.getQuantity() : 0).sum();
            List<CustomerOrderVO.OrderItemVO> itemVOs = items.stream().map(item -> {
                CustomerOrderVO.OrderItemVO itemVO = new CustomerOrderVO.OrderItemVO();
                itemVO.setProductName(item.getProductName());
                itemVO.setSkuDesc(item.getColorName() + " / " + item.getSizeName());
                itemVO.setQuantity(item.getQuantity());
                itemVO.setPrice(item.getPrice());
                return itemVO;
            }).collect(java.util.stream.Collectors.toList());

            vo.setItems(itemVOs);
            vo.setTotalQuantity(totalQty);
            return vo;
        }).collect(java.util.stream.Collectors.toList());

        PageResult<CustomerOrderVO> result = new PageResult<>();
        result.setRecords(voList);
        result.setTotal(totalCount);
        result.setCurrent(current);
        result.setSize(size);
        result.setPages((totalCount + size - 1) / size);
        return result;
    }

    @Override
    public CustomerPreferenceVO getPreference(Long customerId, CustomerPreferenceQueryDTO dto) {
        // 构建缓存 key：customer:preference:{customerId}:{startDate}:{endDate}
        String startDate = (dto != null && dto.getStartDate() != null) ? dto.getStartDate() : "all";
        String endDate = (dto != null && dto.getEndDate() != null) ? dto.getEndDate() : "all";
        String cacheKey = "customer:preference:" + customerId + ":" + startDate + ":" + endDate;

        // 尝试从缓存获取
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached instanceof CustomerPreferenceVO) {
            return (CustomerPreferenceVO) cached;
        }

        // 查询该客户所有已完成或已发货的订单项
        LambdaQueryWrapper<Order> orderWrapper = new LambdaQueryWrapper<>();
        orderWrapper.eq(Order::getCustomerId, customerId)
                    .in(Order::getStatus, java.util.Arrays.asList(4, 5)); // 已发货、已完成

        // 时间范围过滤
        if (dto != null && dto.getStartDate() != null && !dto.getStartDate().isBlank()) {
            orderWrapper.ge(Order::getCreateTime, dto.getStartDate() + " 00:00:00");
        }
        if (dto != null && dto.getEndDate() != null && !dto.getEndDate().isBlank()) {
            orderWrapper.le(Order::getCreateTime, dto.getEndDate() + " 23:59:59");
        }

        List<Order> orders = orderMapper.selectList(orderWrapper);

        if (orders.isEmpty()) {
            CustomerPreferenceVO empty = new CustomerPreferenceVO();
            empty.setCustomerId(customerId);
            empty.setProductTypeCount(0);
            empty.setCategories(java.util.Collections.emptyList());
            empty.setColors(java.util.Collections.emptyList());
            empty.setSizes(java.util.Collections.emptyList());
            // 缓存空结果
            redisTemplate.opsForValue().set(cacheKey, empty, 1, TimeUnit.HOURS);
            return empty;
        }

        List<Long> orderIds = orders.stream().map(Order::getId).collect(java.util.stream.Collectors.toList());

        LambdaQueryWrapper<OrderItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.in(OrderItem::getOrderId, orderIds);
        List<OrderItem> items = orderItemMapper.selectList(itemWrapper);

        // 统计品类
        java.util.Map<String, Integer> categoryCount = new java.util.HashMap<>();
        java.util.Map<String, Integer> colorCount = new java.util.HashMap<>();
        java.util.Map<String, Integer> sizeCount = new java.util.HashMap<>();
        java.util.Set<String> productTypes = new java.util.HashSet<>();

        for (OrderItem item : items) {
            if (item.getProductName() != null) productTypes.add(item.getProductName());
            if (item.getColorName() != null) categoryCount.merge(item.getProductName(), 1, Integer::sum);
            if (item.getColorName() != null) colorCount.merge(item.getColorName(), 1, Integer::sum);
            if (item.getSizeName() != null) sizeCount.merge(item.getSizeName(), 1, Integer::sum);
        }

        int total = items.size();
        CustomerPreferenceVO vo = new CustomerPreferenceVO();
        vo.setCustomerId(customerId);
        vo.setProductTypeCount(productTypes.size());

        vo.setCategories(toCategoryPercent(categoryCount, total));
        vo.setColors(toColorPercent(colorCount, total));
        vo.setSizes(toSizePercent(sizeCount, total));

        // 缓存结果，TTL=1小时
        redisTemplate.opsForValue().set(cacheKey, vo, 1, TimeUnit.HOURS);
        return vo;
    }

    private List<CustomerPreferenceVO.CategoryPreference> toCategoryPercent(java.util.Map<String, Integer> map, int total) {
        if (map.isEmpty() || total == 0) return java.util.Collections.emptyList();
        return map.entrySet().stream()
            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
            .limit(10)
            .map(e -> {
                CustomerPreferenceVO.CategoryPreference cp = new CustomerPreferenceVO.CategoryPreference();
                cp.setCategoryName(e.getKey());
                cp.setCount(e.getValue());
                cp.setPercentage(Math.round(e.getValue() * 100.0 / total * 10) / 10.0);
                return cp;
            })
            .collect(java.util.stream.Collectors.toList());
    }

    private List<CustomerPreferenceVO.ColorPreference> toColorPercent(java.util.Map<String, Integer> map, int total) {
        if (map.isEmpty() || total == 0) return java.util.Collections.emptyList();
        return map.entrySet().stream()
            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
            .limit(10)
            .map(e -> {
                CustomerPreferenceVO.ColorPreference cp = new CustomerPreferenceVO.ColorPreference();
                cp.setColorName(e.getKey());
                cp.setCount(e.getValue());
                cp.setPercentage(Math.round(e.getValue() * 100.0 / total * 10) / 10.0);
                return cp;
            })
            .collect(java.util.stream.Collectors.toList());
    }

    private List<CustomerPreferenceVO.SizePreference> toSizePercent(java.util.Map<String, Integer> map, int total) {
        if (map.isEmpty() || total == 0) return java.util.Collections.emptyList();
        return map.entrySet().stream()
            .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
            .limit(10)
            .map(e -> {
                CustomerPreferenceVO.SizePreference sp = new CustomerPreferenceVO.SizePreference();
                sp.setSizeName(e.getKey());
                sp.setCount(e.getValue());
                sp.setPercentage(Math.round(e.getValue() * 100.0 / total * 10) / 10.0);
                return sp;
            })
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 检查电话是否重复
     * @param tenantId 租户ID
     * @param phone 原始电话
     * @param excludeCustomerId 更新时排除的客户ID（查重时应排除自己）
     */
    private void checkPhoneDuplicate(Long tenantId, String phone, Long excludeCustomerId) {
        String normalizedPhone = phone.replaceAll("[\\s\\-+]", "");
        LambdaQueryWrapper<CustomerPhone> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CustomerPhone::getTenantId, tenantId)
               .eq(CustomerPhone::getPhone, normalizedPhone)
               .eq(CustomerPhone::getDeleted, 0);
        if (excludeCustomerId != null) {
            // 更新时排除当前客户的电话
            wrapper.ne(CustomerPhone::getCustomerId, excludeCustomerId);
        }
        CustomerPhone existing = customerPhoneMapper.selectOne(wrapper);
        if (existing != null) {
            throw new RuntimeException("该电话已被其他客户使用，请勿重复创建");
        }
    }

    private String getStatusName(Integer status) {
        if (status == null) return "未知";
        switch (status) {
            case 0: return "已创建";
            case 1: return "已付款";
            case 2: return "配货中";
            case 3: return "待发货";
            case 4: return "已发货";
            case 5: return "已完成";
            case 6: return "已取消";
            default: return "未知";
        }
    }

    private String formatMoney(java.math.BigDecimal amount) {
        if (amount == null) return "¥0.00";
        return "¥" + amount.setScale(2, java.math.RoundingMode.HALF_UP).toString();
    }

    /**
     * 记录客户操作日志
     */
    private void logOperation(Long tenantId, Long customerId, Long operatorId, String operation, String detail) {
        CustomerOperationLog log = new CustomerOperationLog();
        log.setTenantId(tenantId);
        log.setCustomerId(customerId);
        log.setOperatorId(operatorId);
        log.setOperation(operation);
        log.setDetail(detail);
        operationLogMapper.insert(log);
    }

    /**
     * 获取当前用户ID
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user.getId();
        }
        return 1L; // 默认管理员
    }
}
