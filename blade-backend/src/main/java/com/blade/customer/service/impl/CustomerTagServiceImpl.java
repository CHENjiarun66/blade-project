package com.blade.customer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.blade.common.tenant.TenantContext;
import com.blade.customer.dto.CustomerTagCreateDTO;
import com.blade.customer.dto.CustomerTagUpdateDTO;
import com.blade.customer.dto.CustomerTagVO;
import com.blade.customer.entity.CustomerTag;
import com.blade.customer.entity.CustomerTagRel;
import com.blade.customer.mapper.CustomerTagMapper;
import com.blade.customer.mapper.CustomerTagRelMapper;
import com.blade.customer.service.CustomerTagService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CustomerTagServiceImpl implements CustomerTagService {

    private final CustomerTagMapper customerTagMapper;
    private final CustomerTagRelMapper customerTagRelMapper;

    @Autowired
    public CustomerTagServiceImpl(CustomerTagMapper customerTagMapper, CustomerTagRelMapper customerTagRelMapper) {
        this.customerTagMapper = customerTagMapper;
        this.customerTagRelMapper = customerTagRelMapper;
    }

    @Override
    public List<CustomerTagVO> listTags() {
        Long tenantId = TenantContext.getTenantId();
        LambdaQueryWrapper<CustomerTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CustomerTag::getTenantId, tenantId)
               .eq(CustomerTag::getDeleted, 0)
               .orderByAsc(CustomerTag::getSort);
        List<CustomerTag> tags = customerTagMapper.selectList(wrapper);
        return convertToVO(tags);
    }

    @Override
    @Transactional
    public Long createTag(CustomerTagCreateDTO dto) {
        Long tenantId = TenantContext.getTenantId();

        // Check duplicate name
        LambdaQueryWrapper<CustomerTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CustomerTag::getTenantId, tenantId)
               .eq(CustomerTag::getName, dto.getName())
               .eq(CustomerTag::getDeleted, 0);
        if (customerTagMapper.selectCount(wrapper) > 0) {
            throw new RuntimeException("标签名称已存在");
        }

        CustomerTag tag = new CustomerTag();
        tag.setTenantId(tenantId);
        tag.setName(dto.getName());
        tag.setColor(dto.getColor());
        tag.setSort(dto.getSort() != null ? dto.getSort() : 0);
        tag.setDeleted(0);
        customerTagMapper.insert(tag);
        return tag.getId();
    }

    @Override
    @Transactional
    public void updateTag(CustomerTagUpdateDTO dto) {
        Long tenantId = TenantContext.getTenantId();

        // Check duplicate name (exclude self)
        LambdaQueryWrapper<CustomerTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CustomerTag::getTenantId, tenantId)
               .eq(CustomerTag::getName, dto.getName())
               .eq(CustomerTag::getDeleted, 0)
               .ne(CustomerTag::getId, dto.getId());
        if (customerTagMapper.selectCount(wrapper) > 0) {
            throw new RuntimeException("标签名称已存在");
        }

        CustomerTag tag = customerTagMapper.selectById(dto.getId());
        if (tag == null || !tag.getTenantId().equals(tenantId)) {
            throw new RuntimeException("标签不存在");
        }

        tag.setName(dto.getName());
        tag.setColor(dto.getColor());
        tag.setSort(dto.getSort() != null ? dto.getSort() : 0);
        customerTagMapper.updateById(tag);
    }

    @Override
    @Transactional
    public void deleteTag(Long id) {
        Long tenantId = TenantContext.getTenantId();
        CustomerTag tag = customerTagMapper.selectById(id);
        if (tag == null || !tag.getTenantId().equals(tenantId)) {
            throw new RuntimeException("标签不存在");
        }

        // Soft delete tag
        tag.setDeleted(1);
        customerTagMapper.updateById(tag);

        // Delete all customer-tag relations
        LambdaQueryWrapper<CustomerTagRel> relWrapper = new LambdaQueryWrapper<>();
        relWrapper.eq(CustomerTagRel::getTagId, id)
                 .eq(CustomerTagRel::getTenantId, tenantId);
        customerTagRelMapper.delete(relWrapper);
    }

    @Override
    public List<CustomerTagVO> getCustomerTags(Long customerId) {
        LambdaQueryWrapper<CustomerTagRel> relWrapper = new LambdaQueryWrapper<>();
        relWrapper.eq(CustomerTagRel::getCustomerId, customerId);
        List<CustomerTagRel> rels = customerTagRelMapper.selectList(relWrapper);

        if (rels.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> tagIds = rels.stream().map(CustomerTagRel::getTagId).collect(Collectors.toList());
        LambdaQueryWrapper<CustomerTag> tagWrapper = new LambdaQueryWrapper<>();
        tagWrapper.in(CustomerTag::getId, tagIds)
                  .eq(CustomerTag::getDeleted, 0);
        List<CustomerTag> tags = customerTagMapper.selectList(tagWrapper);
        return convertToVO(tags);
    }

    @Override
    @Transactional
    public void assignTags(Long customerId, List<Long> tagIds) {
        Long tenantId = TenantContext.getTenantId();
        // Delete existing relations
        LambdaQueryWrapper<CustomerTagRel> delWrapper = new LambdaQueryWrapper<>();
        delWrapper.eq(CustomerTagRel::getCustomerId, customerId)
                 .eq(CustomerTagRel::getTenantId, tenantId);
        customerTagRelMapper.delete(delWrapper);

        // Insert new relations
        for (Long tagId : tagIds) {
            CustomerTagRel rel = new CustomerTagRel();
            rel.setCustomerId(customerId);
            rel.setTagId(tagId);
            rel.setTenantId(tenantId);
            customerTagRelMapper.insert(rel);
        }
    }

    @Override
    @Transactional
    public void removeTag(Long customerId, Long tagId) {
        Long tenantId = TenantContext.getTenantId();
        LambdaQueryWrapper<CustomerTagRel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CustomerTagRel::getCustomerId, customerId)
               .eq(CustomerTagRel::getTagId, tagId)
               .eq(CustomerTagRel::getTenantId, tenantId);
        customerTagRelMapper.delete(wrapper);
    }

    private List<CustomerTagVO> convertToVO(List<CustomerTag> tags) {
        return tags.stream().map(tag -> {
            CustomerTagVO vo = new CustomerTagVO();
            BeanUtils.copyProperties(tag, vo);
            return vo;
        }).collect(Collectors.toList());
    }
}
