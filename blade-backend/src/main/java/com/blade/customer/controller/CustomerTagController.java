package com.blade.customer.controller;

import com.blade.common.result.R;
import com.blade.customer.dto.CustomerTagCreateDTO;
import com.blade.customer.dto.CustomerTagUpdateDTO;
import com.blade.customer.dto.CustomerTagVO;
import com.blade.customer.service.CustomerTagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customer-tags")
@Tag(name = "客户标签管理接口")
public class CustomerTagController {

    @Autowired
    private CustomerTagService customerTagService;

    @GetMapping
    @Operation(summary = "标签列表")
    public R<List<CustomerTagVO>> list() {
        return R.ok(customerTagService.listTags());
    }

    @PostMapping
    @Operation(summary = "创建标签")
    public R<Long> create(@RequestBody @Valid CustomerTagCreateDTO dto) {
        return R.ok(customerTagService.createTag(dto));
    }

    @PutMapping
    @Operation(summary = "更新标签")
    public R<Void> update(@RequestBody @Valid CustomerTagUpdateDTO dto) {
        customerTagService.updateTag(dto);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除标签")
    public R<Void> delete(@PathVariable Long id) {
        customerTagService.deleteTag(id);
        return R.ok();
    }

    @GetMapping("/customer/{customerId}")
    @Operation(summary = "获取客户的所有标签")
    public R<List<CustomerTagVO>> getCustomerTags(@PathVariable Long customerId) {
        return R.ok(customerTagService.getCustomerTags(customerId));
    }

    @PostMapping("/customer/{customerId}")
    @Operation(summary = "为客户分配标签")
    public R<Void> assignTags(@PathVariable Long customerId, @RequestBody List<Long> tagIds) {
        customerTagService.assignTags(customerId, tagIds);
        return R.ok();
    }

    @DeleteMapping("/customer/{customerId}/tag/{tagId}")
    @Operation(summary = "移除客户的标签")
    public R<Void> removeTag(@PathVariable Long customerId, @PathVariable Long tagId) {
        customerTagService.removeTag(customerId, tagId);
        return R.ok();
    }
}
