package com.blade.customer.controller;

import com.blade.common.result.PageResult;
import com.blade.common.result.R;
import com.blade.customer.dto.CustomerCreateDTO;
import com.blade.customer.dto.CustomerPageDTO;
import com.blade.customer.dto.CustomerUpdateDTO;
import com.blade.customer.dto.CustomerVO;
import com.blade.customer.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
@Tag(name = "客户管理接口")
public class CustomerController {

    @Autowired
    private CustomerService customerService;

    @GetMapping
    @Operation(summary = "客户分页列表")
    public R<PageResult<CustomerVO>> pageList(CustomerPageDTO dto) {
        return R.ok(customerService.pageList(dto));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取客户详情")
    public R<CustomerVO> getById(@PathVariable Long id) {
        return R.ok(customerService.getById(id));
    }

    @GetMapping("/search")
    @Operation(summary = "根据电话号码搜索客户")
    public R<CustomerVO> searchByPhone(@RequestParam String phone) {
        return R.ok(customerService.getByPhone(phone));
    }

    @PostMapping
    @Operation(summary = "创建客户")
    public R<Long> create(@RequestBody @Valid CustomerCreateDTO dto) {
        return R.ok(customerService.createCustomer(dto));
    }

    @PutMapping
    @Operation(summary = "更新客户")
    public R<Void> update(@RequestBody @Valid CustomerUpdateDTO dto) {
        customerService.updateCustomer(dto);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除客户")
    public R<Void> delete(@PathVariable Long id) {
        customerService.deleteCustomer(id);
        return R.ok();
    }
}
