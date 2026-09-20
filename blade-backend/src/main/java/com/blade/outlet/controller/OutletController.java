package com.blade.outlet.controller;

import com.blade.common.result.PageResult;
import com.blade.common.result.R;
import com.blade.outlet.dto.OutletCreateDTO;
import com.blade.outlet.dto.OutletOptionsVO;
import com.blade.outlet.dto.OutletPageDTO;
import com.blade.outlet.dto.OutletUpdateDTO;
import com.blade.outlet.dto.OutletVO;
import com.blade.outlet.service.OutletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/outlets")
@Tag(name = "档口管理接口")
public class OutletController {

    private final OutletService outletService;

    public OutletController(OutletService outletService) {
        this.outletService = outletService;
    }

    @GetMapping
    @Operation(summary = "档口列表（分页）")
    @PreAuthorize("hasAuthority('menu:outlet')")
    public R<PageResult<OutletVO>> list(OutletPageDTO dto) {
        return R.ok(outletService.pageList(dto));
    }

    @GetMapping("/options")
    @Operation(summary = "档口选项（仅当前调用者可用且启用）")
    public R<OutletOptionsVO> options() {
        return R.ok(outletService.options());
    }

    @GetMapping("/{id}")
    @Operation(summary = "档口详情")
    @PreAuthorize("hasAuthority('menu:outlet')")
    public R<OutletVO> getById(@PathVariable Long id) {
        return R.ok(outletService.getById(id));
    }

    @PostMapping
    @Operation(summary = "新建档口")
    @PreAuthorize("hasAuthority('btn:outlet:create')")
    public R<Long> create(@RequestBody @Valid OutletCreateDTO dto) {
        return R.ok(outletService.create(dto));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新档口")
    @PreAuthorize("hasAuthority('btn:outlet:edit')")
    public R<Void> update(@PathVariable Long id, @RequestBody @Valid OutletUpdateDTO dto) {
        dto.setId(id);
        outletService.update(dto);
        return R.ok();
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "启用/禁用档口")
    @PreAuthorize("hasAuthority('btn:outlet:disable')")
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        outletService.updateStatus(id, status);
        return R.ok();
    }
}
