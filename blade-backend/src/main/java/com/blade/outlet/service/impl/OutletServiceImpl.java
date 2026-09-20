package com.blade.outlet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blade.common.exception.BusinessException;
import com.blade.common.result.PageResult;
import com.blade.common.tenant.TenantContext;
import com.blade.order.draft.entity.OrderDraft;
import com.blade.order.draft.mapper.OrderDraftMapper;
import com.blade.order.entity.Order;
import com.blade.order.mapper.OrderMapper;
import com.blade.outlet.dto.OutletCreateDTO;
import com.blade.outlet.dto.OutletOptionsVO;
import com.blade.outlet.dto.OutletPageDTO;
import com.blade.outlet.dto.OutletUpdateDTO;
import com.blade.outlet.dto.OutletVO;
import com.blade.outlet.entity.SalesOutlet;
import com.blade.outlet.entity.SysUserOutlet;
import com.blade.outlet.policy.OutletAccessPolicy;
import com.blade.outlet.mapper.SalesOutletMapper;
import com.blade.outlet.mapper.SysUserOutletMapper;
import com.blade.outlet.service.OutletService;
import com.blade.system.user.entity.User;
import com.blade.system.user.mapper.UserMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OutletServiceImpl implements OutletService {

    private final SalesOutletMapper outletMapper;
    private final SysUserOutletMapper sysUserOutletMapper;
    private final OrderMapper orderMapper;
    private final OrderDraftMapper orderDraftMapper;
    private final UserMapper userMapper;
    private final OutletAccessPolicy outletAccessPolicy;

    public OutletServiceImpl(SalesOutletMapper outletMapper,
                             SysUserOutletMapper sysUserOutletMapper,
                             OrderMapper orderMapper,
                             OrderDraftMapper orderDraftMapper,
                             UserMapper userMapper,
                             OutletAccessPolicy outletAccessPolicy) {
        this.outletMapper = outletMapper;
        this.sysUserOutletMapper = sysUserOutletMapper;
        this.orderMapper = orderMapper;
        this.orderDraftMapper = orderDraftMapper;
        this.userMapper = userMapper;
        this.outletAccessPolicy = outletAccessPolicy;
    }

    @Override
    public PageResult<OutletVO> pageList(OutletPageDTO dto) {
        Page<SalesOutlet> page = new Page<>(dto.getCurrent(), dto.getSize());
        LambdaQueryWrapper<SalesOutlet> wrapper = new LambdaQueryWrapper<>();
        if (dto.getKeyword() != null && !dto.getKeyword().isBlank()) {
            String kw = dto.getKeyword().trim();
            wrapper.and(w -> w.like(SalesOutlet::getOutletCode, kw)
                    .or().like(SalesOutlet::getOutletName, kw));
        }
        if (dto.getStatus() != null) {
            wrapper.eq(SalesOutlet::getStatus, dto.getStatus());
        }
        wrapper.orderByAsc(SalesOutlet::getSort).orderByDesc(SalesOutlet::getId);

        IPage<SalesOutlet> result = outletMapper.selectPage(page, wrapper);
        List<OutletVO> voList = result.getRecords().stream().map(this::toVO).toList();
        return new PageResult<>(voList, result.getTotal(), result.getSize(), result.getCurrent());
    }

    @Override
    public OutletVO getById(Long id) {
        return toVO(requiredOutlet(id));
    }

    @Override
    @Transactional
    public Long create(OutletCreateDTO dto) {
        String code = normalizeCode(dto.getOutletCode());
        if (outletMapper.selectCount(
                new LambdaQueryWrapper<SalesOutlet>().eq(SalesOutlet::getOutletCode, code)) > 0) {
            throw BusinessException.of(400, "档口编码已存在");
        }

        SalesOutlet outlet = new SalesOutlet();
        applyFields(outlet, code, dto.getOutletName(), dto.getOutletType(), dto.getContactName(),
                dto.getPhone(), dto.getAddress(), dto.getSort(), dto.getRemark());
        outlet.setStatus(1);
        outlet.setIsTenantDefault(dto.getIsTenantDefault() != null && dto.getIsTenantDefault() == 1 ? 1 : 0);
        outlet.setTenantId(requiredTenantId());
        outlet.setCreateBy(currentUserIdOrNull());
        outlet.setDeleted(0);

        outletMapper.insert(outlet);

        if (Integer.valueOf(1).equals(outlet.getIsTenantDefault())) {
            makeExclusiveTenantDefault(outlet.getId());
        }
        return outlet.getId();
    }

    @Override
    @Transactional
    public void update(OutletUpdateDTO dto) {
        SalesOutlet outlet = requiredOutlet(dto.getId());
        String code = normalizeCode(dto.getOutletCode());
        if (!outlet.getOutletCode().equals(code)) {
            throw BusinessException.of(400, "档口编码创建后不可修改");
        }

        applyFields(outlet, code, dto.getOutletName(), dto.getOutletType(), dto.getContactName(),
                dto.getPhone(), dto.getAddress(), dto.getSort(), dto.getRemark());

        // isTenantDefault 为 null 时保留原值；显式设置才校验并更新。
        if (dto.getIsTenantDefault() != null) {
            int isDefault = dto.getIsTenantDefault() == 1 ? 1 : 0;
            if (isDefault == 1 && !Integer.valueOf(1).equals(outlet.getStatus())) {
                throw BusinessException.of(400, "默认档口必须启用");
            }
            outlet.setIsTenantDefault(isDefault);
        }
        outlet.setUpdateBy(currentUserIdOrNull());
        outletMapper.updateById(outlet);

        if (Integer.valueOf(1).equals(outlet.getIsTenantDefault())) {
            makeExclusiveTenantDefault(outlet.getId());
        }
    }

    @Override
    @Transactional
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw BusinessException.of(400, "状态只能为 1(启用) 或 0(禁用)");
        }
        SalesOutlet outlet = requiredOutlet(id);
        outlet.setStatus(status);
        if (Integer.valueOf(0).equals(status) && Integer.valueOf(1).equals(outlet.getIsTenantDefault())) {
            outlet.setIsTenantDefault(0);
        }
        outlet.setUpdateBy(currentUserIdOrNull());
        outletMapper.updateById(outlet);
    }

    @Override
    public OutletOptionsVO options() {
        return outletAccessPolicy.listAvailableOptions();
    }

    private void applyFields(SalesOutlet outlet, String code, String name, String type,
                             String contactName, String phone, String address, Integer sort, String remark) {
        outlet.setOutletCode(code);
        outlet.setOutletName(name == null ? "" : name.trim());
        outlet.setOutletType(type == null || type.isBlank() ? "STORE" : type.trim());
        outlet.setContactName(contactName);
        outlet.setPhone(phone);
        outlet.setAddress(address);
        outlet.setSort(sort == null ? 0 : sort);
        outlet.setRemark(remark);
    }

    /** 批量清除除 self 外的全部默认标记（租户由拦截器约束），处理异常多默认。 */
    private void makeExclusiveTenantDefault(Long selfId) {
        outletMapper.clearOtherTenantDefaults(selfId);
    }

    private SalesOutlet requiredOutlet(Long id) {
        if (id == null) {
            throw BusinessException.of(404, "档口不存在");
        }
        SalesOutlet outlet = outletMapper.selectById(id);
        if (outlet == null) {
            throw BusinessException.of(404, "档口不存在");
        }
        return outlet;
    }

    private OutletVO toVO(SalesOutlet o) {
        OutletVO vo = new OutletVO();
        vo.setId(o.getId());
        vo.setOutletCode(o.getOutletCode());
        vo.setOutletName(o.getOutletName());
        vo.setOutletType(o.getOutletType());
        vo.setContactName(o.getContactName());
        vo.setPhone(o.getPhone());
        vo.setAddress(o.getAddress());
        vo.setSort(o.getSort());
        vo.setIsTenantDefault(o.getIsTenantDefault());
        vo.setStatus(o.getStatus());
        vo.setRemark(o.getRemark());
        vo.setCreateTime(o.getCreateTime());
        vo.setUpdateTime(o.getUpdateTime());
        vo.setBoundUserCount(countBoundUsers(o.getId()));
        vo.setOrderCount(countOrders(o.getId()));
        vo.setDraftCount(countDrafts(o.getId()));
        return vo;
    }

    private long countBoundUsers(Long outletId) {
        Long c = sysUserOutletMapper.selectCount(
                new LambdaQueryWrapper<SysUserOutlet>()
                        .eq(SysUserOutlet::getOutletId, outletId)
                        .eq(SysUserOutlet::getStatus, 1));
        return c == null ? 0L : c;
    }

    private long countOrders(Long outletId) {
        Long c = orderMapper.selectCount(
                new LambdaQueryWrapper<Order>().eq(Order::getSourceOutletId, outletId));
        return c == null ? 0L : c;
    }

    private long countDrafts(Long outletId) {
        Long c = orderDraftMapper.selectCount(
                new LambdaQueryWrapper<OrderDraft>().eq(OrderDraft::getSourceOutletId, outletId));
        return c == null ? 0L : c;
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw BusinessException.of(400, "档口编码不能为空");
        }
        return code.trim();
    }

    private Long requiredTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw BusinessException.of(401, "缺少租户上下文");
        }
        return tenantId;
    }

    private Long currentUserId() {
        Long userId = currentUserIdOrNull();
        if (userId == null) {
            throw BusinessException.of(401, "当前用户不存在");
        }
        return userId;
    }

    private Long currentUserIdOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        User user = userMapper.selectByUsername(auth.getName());
        return user == null ? null : user.getId();
    }
}
