package com.blade.customer.service;

import com.blade.customer.dto.CustomerTagCreateDTO;
import com.blade.customer.dto.CustomerTagUpdateDTO;
import com.blade.customer.dto.CustomerTagVO;

import java.util.List;

public interface CustomerTagService {

    /**
     * 标签列表
     */
    List<CustomerTagVO> listTags();

    /**
     * 创建标签
     */
    Long createTag(CustomerTagCreateDTO dto);

    /**
     * 更新标签
     */
    void updateTag(CustomerTagUpdateDTO dto);

    /**
     * 删除标签
     */
    void deleteTag(Long id);

    /**
     * 获取客户的所有标签
     */
    List<CustomerTagVO> getCustomerTags(Long customerId);

    /**
     * 为客户分配标签
     */
    void assignTags(Long customerId, List<Long> tagIds);

    /**
     * 移除客户的标签
     */
    void removeTag(Long customerId, Long tagId);
}
