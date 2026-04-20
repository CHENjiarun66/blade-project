package com.blade.customer.service;

import com.blade.common.result.PageResult;
import com.blade.customer.dto.CustomerCreateDTO;
import com.blade.customer.dto.CustomerPageDTO;
import com.blade.customer.dto.CustomerUpdateDTO;
import com.blade.customer.dto.CustomerVO;

public interface CustomerService {

    /**
     * 客户分页列表
     * @param dto 查询条件
     * @return 分页结果
     */
    PageResult<CustomerVO> pageList(CustomerPageDTO dto);

    /**
     * 根据ID获取客户详情
     * @param id 客户ID
     * @return 客户信息
     */
    CustomerVO getById(Long id);

    /**
     * 根据电话号码查询客户
     * @param phone 电话号码
     * @return 客户信息，如果没有找到返回null
     */
    CustomerVO getByPhone(String phone);

    /**
     * 创建客户（同时创建主电话）
     * @param dto 创建客户DTO
     * @return 新客户ID
     */
    Long createCustomer(CustomerCreateDTO dto);

    /**
     * 更新客户信息
     * @param dto 更新客户DTO
     */
    void updateCustomer(CustomerUpdateDTO dto);

    /**
     * 删除客户
     * @param id 客户ID
     */
    void deleteCustomer(Long id);
}
