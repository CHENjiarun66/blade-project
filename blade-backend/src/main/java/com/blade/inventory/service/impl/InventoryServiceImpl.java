package com.blade.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blade.common.result.PageResult;
import com.blade.common.tenant.TenantContext;
import com.blade.file.service.FileService;
import com.blade.inventory.dto.*;
import com.blade.inventory.entity.Inventory;
import com.blade.inventory.entity.InventoryLog;
import com.blade.inventory.entity.InventoryGlobalReserve;
import com.blade.inventory.mapper.InventoryMapper;
import com.blade.inventory.mapper.InventoryLogMapper;
import com.blade.inventory.mapper.InventoryGlobalReserveMapper;
import com.blade.inventory.service.InventoryService;
import com.blade.order.entity.OrderDeliveryPlan;
import com.blade.order.mapper.OrderDeliveryPlanMapper;
import com.blade.product.entity.ProductSku;
import com.blade.product.mapper.ProductSkuMapper;
import com.blade.product.service.InventorySkuEligibilityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class InventoryServiceImpl implements InventoryService {

    @Autowired
    private InventoryMapper inventoryMapper;

    @Autowired
    private InventoryLogMapper inventoryLogMapper;

    @Autowired
    private InventoryGlobalReserveMapper globalReserveMapper;

    @Autowired
    private ProductSkuMapper productSkuMapper;

    @Autowired
    private InventorySkuEligibilityService inventorySkuEligibilityService;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private OrderDeliveryPlanMapper deliveryPlanMapper;

    @Autowired
    private FileService fileService;

    @Autowired
    private ObjectMapper objectMapper;

    // 锁 Key 前缀
    private static final String INVENTORY_LOCK_PREFIX = "inventory:lock:";

    // 锁等待时间（秒）
    private static final long LOCK_WAIT_TIME = 3;
    // 锁持有时间（秒）
    private static final long LOCK_LEASE_TIME = 10;

    // 变动类型常量
    private static final String CHANGE_TYPE_PURCHASE_IN = "PURCHASE_IN";
    private static final String CHANGE_TYPE_SALE_OUT = "SALE_OUT";
    private static final String CHANGE_TYPE_SALE_CANCEL = "SALE_CANCEL";
    private static final String CHANGE_TYPE_ADJUST = "ADJUST";
    private static final String CHANGE_TYPE_OTHER_OUT = "OTHER_OUT";

    @Override
    public PageResult<InventoryVO> pageList(InventoryPageDTO dto) {
        Long tenantId = TenantContext.getTenantId();

        // 计算分页
        long offset = (dto.getCurrent() - 1) * dto.getSize();
        long size = dto.getSize();

        // 使用自定义查询
        List<InventoryVO> inventoryList = inventoryMapper.selectInventoryList(
                tenantId,
                dto.getWarehouseId(),
                dto.getAlertStatus(),
                dto.getKeyword(),
                offset,
                size
        );
        long total = inventoryMapper.countInventoryList(
                tenantId,
                dto.getWarehouseId(),
                dto.getAlertStatus(),
                dto.getKeyword()
        );

        // 计算预警状态
        List<InventoryVO> voList = new ArrayList<>();
        for (InventoryVO vo : inventoryList) {
            Integer qty = vo.getQuantity();
            Integer reserved = vo.getReservedQty();
            Integer globalReserved = vo.getGlobalReservedQty();
            Integer threshold = vo.getAlertThreshold();
            int available = (qty != null ? qty : 0)
                    - (reserved != null ? reserved : 0)
                    - (globalReserved != null ? globalReserved : 0);
            vo.setAvailableQty(available);
            if (threshold != null && available < threshold) {
                vo.setAlertStatus("below");
            } else {
                vo.setAlertStatus("normal");
            }
            voList.add(vo);
        }

        PageResult<InventoryVO> pageResult = new PageResult<>();
        pageResult.setRecords(voList);
        pageResult.setTotal(total);
        pageResult.setSize(size);
        pageResult.setCurrent(dto.getCurrent());
        pageResult.setPages((total + size - 1) / size);
        return pageResult;
    }

    @Override
    public InventoryVO getById(Long id) {
        Inventory inv = inventoryMapper.selectById(id);
        if (inv == null) {
            throw new RuntimeException("库存记录不存在");
        }
        return convertToVO(inv);
    }

    @Override
    public List<InventoryVO> listByWarehouse(Long warehouseId) {
        LambdaQueryWrapper<Inventory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Inventory::getWarehouseId, warehouseId);
        wrapper.eq(Inventory::getTenantId, TenantContext.getTenantId());
        List<Inventory> list = inventoryMapper.selectList(wrapper);
        return list.stream().map(this::convertToVO).toList();
    }

    @Override
    @Transactional
    public void in(InventoryInDTO dto, Long operatorId) {
        Long tenantId = TenantContext.getTenantId();
        String images = toImagesJson(dto.getImages());

        for (InventoryInItemDTO item : dto.getItems()) {
            inventorySkuEligibilityService.requireEligible(item.getSkuId(), tenantId);
            String lockKey = INVENTORY_LOCK_PREFIX + item.getSkuId() + ":" + dto.getWarehouseId();
            RLock lock = redissonClient.getLock(lockKey);

            try {
                // 尝试获取锁，最多等待3秒，锁定10秒后自动释放
                if (!lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                    throw new RuntimeException("系统繁忙，请稍后重试");
                }

                // 查询或创建库存记录
                Inventory inv = inventoryMapper.selectBySkuAndWarehouse(item.getSkuId(), dto.getWarehouseId());
                int beforeQty = 0;

                if (inv == null) {
                    // 创建新库存记录
                    inv = new Inventory();
                    inv.setSkuId(item.getSkuId());
                    inv.setWarehouseId(dto.getWarehouseId());
                    inv.setQuantity(item.getQuantity());
                    inv.setReservedQty(0);
                    inv.setAlertThreshold(10);
                    inv.setTenantId(tenantId);
                    inventoryMapper.insert(inv);
                    beforeQty = 0;
                } else {
                    beforeQty = inv.getQuantity();
                    // 使用乐观锁更新库存
                    LambdaUpdateWrapper<Inventory> wrapper = new LambdaUpdateWrapper<>();
                    wrapper.eq(Inventory::getId, inv.getId())
                            .eq(Inventory::getVersion, inv.getVersion());  // 乐观锁条件
                    wrapper.setSql("quantity = quantity + " + item.getQuantity());
                    int rows = inventoryMapper.update(null, wrapper);
                    if (rows == 0) {
                        throw new RuntimeException("库存已被其他操作修改，请重试");
                    }
                    inv.setQuantity(beforeQty + item.getQuantity());
                }

                // 记录变动日志
                InventoryLog log = new InventoryLog();
                log.setSkuId(item.getSkuId());
                log.setWarehouseId(dto.getWarehouseId());
                log.setChangeType(CHANGE_TYPE_PURCHASE_IN);
                log.setChangeQty(item.getQuantity());
                log.setBeforeQty(beforeQty);
                log.setAfterQty(inv.getQuantity());
                log.setSupplierId(dto.getSupplierId());
                log.setSupplierName(dto.getSupplierName());
                log.setOperatorId(operatorId);
                log.setRemark(item.getRemark() != null ? item.getRemark() : dto.getRemark());
                log.setImages(images);
                log.setTenantId(tenantId);
                inventoryLogMapper.insert(log);
                fileService.bindFiles("inventory", log.getId(), parseFileIds(dto.getImages()));

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("操作被中断，请重试");
            } finally {
                // 只有当前线程持有锁时才释放
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }
    }

    @Override
    @Transactional
    public void out(InventoryOutDTO dto, Long operatorId) {
        Long tenantId = TenantContext.getTenantId();

        for (InventoryOutItemDTO item : dto.getItems()) {
            inventorySkuEligibilityService.requireEligible(item.getSkuId(), tenantId);
            String lockKey = INVENTORY_LOCK_PREFIX + item.getSkuId() + ":" + dto.getWarehouseId();
            RLock lock = redissonClient.getLock(lockKey);

            try {
                if (!lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                    throw new RuntimeException("系统繁忙，请稍后重试");
                }

                Inventory inv = inventoryMapper.selectBySkuAndWarehouse(item.getSkuId(), dto.getWarehouseId());
                if (inv == null) {
                    throw new RuntimeException("库存不足，无法出库");
                }
                // ORDER来源时，global_reserved_qty是本订单的预留，出库时只检查quantity - reservedQty
                // 非ORDER来源时，检查 quantity - reservedQty - globalReservedQty
                int available = "ORDER".equals(dto.getSource())
                    ? inv.getQuantity() - inv.getReservedQty()
                    : inv.getQuantity() - inv.getReservedQty() - inv.getGlobalReservedQty();
                if (available < item.getQuantity()) {
                    throw new RuntimeException("库存不足，无法出库");
                }

                int beforeQty = inv.getQuantity();
                int beforeReserved = inv.getReservedQty();
                int changeQty = -item.getQuantity();

                // 使用乐观锁更新库存
                LambdaUpdateWrapper<Inventory> wrapper = new LambdaUpdateWrapper<>();
                wrapper.eq(Inventory::getId, inv.getId())
                        .eq(Inventory::getVersion, inv.getVersion());

                // ORDER来源时，同时扣减quantity和reserved_qty和global_reserved_qty
                if ("ORDER".equals(dto.getSource())) {
                    wrapper.setSql("quantity = quantity + " + changeQty + ", reserved_qty = reserved_qty + " + changeQty + ", global_reserved_qty = global_reserved_qty + " + changeQty);
                } else {
                    wrapper.setSql("quantity = quantity + " + changeQty);
                }
                int rows = inventoryMapper.update(null, wrapper);
                if (rows == 0) {
                    throw new RuntimeException("库存已被其他操作修改，请重试");
                }

                // 记录变动日志
                String changeType = "ORDER".equals(dto.getSource()) ? CHANGE_TYPE_SALE_OUT : CHANGE_TYPE_OTHER_OUT;
                String reason = "ORDER".equals(dto.getSource()) ? null : item.getReason();

                InventoryLog log = new InventoryLog();
                log.setSkuId(item.getSkuId());
                log.setWarehouseId(dto.getWarehouseId());
                log.setChangeType(changeType);
                log.setChangeQty(changeQty);
                log.setBeforeQty(beforeQty);
                log.setAfterQty(beforeQty + changeQty);
                log.setOrderId(dto.getOrderId());
                log.setOperatorId(operatorId);
                log.setRemark(reason != null ? reason : dto.getRemark());
                log.setTenantId(tenantId);
                inventoryLogMapper.insert(log);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("操作被中断，请重试");
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }
    }

    @Override
    @Transactional
    public void adjust(InventoryAdjustDTO dto, Long operatorId) {
        Long tenantId = TenantContext.getTenantId();

        for (InventoryAdjustItemDTO item : dto.getItems()) {
            inventorySkuEligibilityService.requireEligible(item.getSkuId(), tenantId);
            String lockKey = INVENTORY_LOCK_PREFIX + item.getSkuId() + ":" + dto.getWarehouseId();
            RLock lock = redissonClient.getLock(lockKey);

            try {
                if (!lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                    throw new RuntimeException("系统繁忙，请稍后重试");
                }

                Inventory inv = inventoryMapper.selectBySkuAndWarehouse(item.getSkuId(), dto.getWarehouseId());
                if (inv == null) {
                    throw new RuntimeException("库存记录不存在");
                }

                int beforeQty = inv.getQuantity();
                int changeQty = item.getQuantity();

                // 调整后库存不能为负
                if (beforeQty + changeQty < 0) {
                    throw new RuntimeException("调整后库存不能为负数");
                }

                // 使用乐观锁更新库存
                LambdaUpdateWrapper<Inventory> wrapper = new LambdaUpdateWrapper<>();
                wrapper.eq(Inventory::getId, inv.getId())
                        .eq(Inventory::getVersion, inv.getVersion());
                wrapper.setSql("quantity = quantity + " + changeQty);
                int rows = inventoryMapper.update(null, wrapper);
                if (rows == 0) {
                    throw new RuntimeException("库存已被其他操作修改，请重试");
                }

                // 记录变动日志
                InventoryLog log = new InventoryLog();
                log.setSkuId(item.getSkuId());
                log.setWarehouseId(dto.getWarehouseId());
                log.setChangeType(CHANGE_TYPE_ADJUST);
                log.setChangeQty(changeQty);
                log.setBeforeQty(beforeQty);
                log.setAfterQty(beforeQty + changeQty);
                log.setOperatorId(operatorId);
                String reason = item.getReason() != null ? item.getReason() : (dto.getReason() != null ? dto.getReason() : "");
                String remark = reason + (dto.getRemark() != null && !dto.getRemark().isBlank() ? ": " + dto.getRemark() : "");
                log.setRemark(remark);
                log.setTenantId(tenantId);
                inventoryLogMapper.insert(log);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("操作被中断，请重试");
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }
    }

    @Override
    @Transactional
    public void reserve(InventoryReserveDTO dto, Long operatorId) {
        Long tenantId = TenantContext.getTenantId();

        for (InventoryReserveDTO.ReserveItemDTO item : dto.getItems()) {
            inventorySkuEligibilityService.requireEligible(item.getSkuId(), tenantId);
            String lockKey = INVENTORY_LOCK_PREFIX + item.getSkuId() + ":" + dto.getWarehouseId();
            RLock lock = redissonClient.getLock(lockKey);

            try {
                if (!lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                    throw new RuntimeException("系统繁忙，请稍后重试");
                }

                Inventory inv = inventoryMapper.selectBySkuAndWarehouse(item.getSkuId(), dto.getWarehouseId());
                if (inv == null) {
                    throw new RuntimeException("库存记录不存在");
                }

                int available = inv.getQuantity() - inv.getReservedQty();
                if (available < item.getQuantity()) {
                    throw new RuntimeException("可用库存不足，无法预留");
                }

                // 使用乐观锁更新预留数量
                LambdaUpdateWrapper<Inventory> wrapper = new LambdaUpdateWrapper<>();
                wrapper.eq(Inventory::getId, inv.getId())
                        .eq(Inventory::getVersion, inv.getVersion());
                wrapper.setSql("reserved_qty = reserved_qty + " + item.getQuantity());
                int rows = inventoryMapper.update(null, wrapper);
                if (rows == 0) {
                    throw new RuntimeException("库存已被其他操作修改，请重试");
                }

                // 记录变动日志（SALE_OUT 扣可用库存，释放时用 SALE_CANCEL）
                InventoryLog log = new InventoryLog();
                log.setSkuId(item.getSkuId());
                log.setWarehouseId(dto.getWarehouseId());
                log.setChangeType(CHANGE_TYPE_SALE_OUT);
                log.setChangeQty(-item.getQuantity());
                log.setBeforeQty(inv.getQuantity() - inv.getReservedQty());
                log.setAfterQty(inv.getQuantity() - inv.getReservedQty() - item.getQuantity());
                log.setOrderId(dto.getOrderId());
                log.setOperatorId(operatorId);
                log.setRemark("预留锁定");
                log.setTenantId(tenantId);
                inventoryLogMapper.insert(log);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("操作被中断，请重试");
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }
    }

    @Override
    @Transactional
    public void release(InventoryReserveDTO dto, Long operatorId) {
        Long tenantId = TenantContext.getTenantId();

        for (InventoryReserveDTO.ReserveItemDTO item : dto.getItems()) {
            String lockKey = INVENTORY_LOCK_PREFIX + item.getSkuId() + ":" + dto.getWarehouseId();
            RLock lock = redissonClient.getLock(lockKey);

            try {
                if (!lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                    throw new RuntimeException("系统繁忙，请稍后重试");
                }

                Inventory inv = inventoryMapper.selectBySkuAndWarehouse(item.getSkuId(), dto.getWarehouseId());
                if (inv == null) {
                    throw new RuntimeException("库存记录不存在");
                }

                // 校验预留数量是否足够释放
                if (inv.getReservedQty() < item.getQuantity()) {
                    throw new RuntimeException("预留数量不足，无法释放");
                }

                // 使用乐观锁释放预留数量
                LambdaUpdateWrapper<Inventory> wrapper = new LambdaUpdateWrapper<>();
                wrapper.eq(Inventory::getId, inv.getId())
                        .eq(Inventory::getVersion, inv.getVersion());
                wrapper.setSql("reserved_qty = reserved_qty - " + item.getQuantity());
                int rows = inventoryMapper.update(null, wrapper);
                if (rows == 0) {
                    throw new RuntimeException("库存已被其他操作修改，请重试");
                }

                // 记录变动日志
                InventoryLog log = new InventoryLog();
                log.setSkuId(item.getSkuId());
                log.setWarehouseId(dto.getWarehouseId());
                log.setChangeType(CHANGE_TYPE_SALE_CANCEL);
                log.setChangeQty(item.getQuantity());
                log.setBeforeQty(inv.getQuantity() - inv.getReservedQty());
                log.setAfterQty(inv.getQuantity() - inv.getReservedQty() + item.getQuantity());
                log.setOrderId(dto.getOrderId());
                log.setOperatorId(operatorId);
                log.setRemark("预留释放");
                log.setTenantId(tenantId);
                inventoryLogMapper.insert(log);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("操作被中断，请重试");
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }
    }

    @Override
    @Transactional
    public void globalReserve(InventoryReserveDTO dto, Long operatorId) {
        Long tenantId = TenantContext.getTenantId();

        for (InventoryReserveDTO.ReserveItemDTO item : dto.getItems()) {
            inventorySkuEligibilityService.requireEligible(item.getSkuId(), tenantId);
            String lockKey = "sku:lock:" + item.getSkuId();
            RLock lock = redissonClient.getLock(lockKey);

            try {
                if (!lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                    throw new RuntimeException("系统繁忙，请稍后重试");
                }

                // 1. 查询跨仓可用总量
                Integer available = getGlobalAvailableQty(item.getSkuId());
                if (available < item.getQuantity()) {
                    throw new RuntimeException(String.format("商品SKU[%d]跨仓总量不足，可用:%d, 需要:%d",
                            item.getSkuId(), available, item.getQuantity()));
                }

                // 2. 按仓库可用量比例分配预留（从有库存的仓库依次预留）
                allocateGlobalReserve(item.getSkuId(), item.getQuantity(), dto.getOrderId(), tenantId, operatorId);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("操作被中断，请稍后重试");
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }
    }

    /**
     * 按仓库分配全局预留
     * 从有库存的仓库依次预留，更新每个仓库的 global_reserved_qty
     */
    private void allocateGlobalReserve(Long skuId, Integer needQty, Long orderId, Long tenantId, Long operatorId) {
        // 查询该SKU所有仓库的库存，按仓库可用量降序
        LambdaQueryWrapper<Inventory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Inventory::getSkuId, skuId);
        wrapper.eq(Inventory::getTenantId, tenantId);
        wrapper.gt(Inventory::getQuantity, 0); // 只查有库存的
        List<Inventory> inventories = inventoryMapper.selectList(wrapper);

        int remaining = needQty;
        for (Inventory inv : inventories) {
            if (remaining <= 0) break;

            int available = inv.getQuantity() - inv.getReservedQty() - inv.getGlobalReservedQty();
            if (available <= 0) continue;

            int allocate = Math.min(available, remaining);

            // 乐观锁更新 global_reserved_qty
            LambdaUpdateWrapper<Inventory> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(Inventory::getId, inv.getId())
                    .eq(Inventory::getVersion, inv.getVersion());
            updateWrapper.setSql("global_reserved_qty = global_reserved_qty + " + allocate);
            int rows = inventoryMapper.update(null, updateWrapper);
            if (rows == 0) {
                throw new RuntimeException("库存已被其他操作修改，请重试");
            }

            remaining -= allocate;
        }

        if (remaining > 0) {
            throw new RuntimeException(String.format("商品SKU[%d]跨仓总量不足，无法完成预留", skuId));
        }

        // 3. 记录跨仓预留表
        InventoryGlobalReserve record = new InventoryGlobalReserve();
        record.setOrderId(orderId);
        record.setSkuId(skuId);
        record.setReserveQty(needQty);
        record.setReleasedQty(0);
        record.setTenantId(tenantId);
        globalReserveMapper.insert(record);

        // 4. 记录库存日志
        InventoryLog log = new InventoryLog();
        log.setSkuId(skuId);
        log.setWarehouseId(null); // 跨仓不留具体仓库
        log.setChangeType("GLOBAL_RESERVE");
        log.setChangeQty(-needQty);
        log.setBeforeQty(0); // 跨仓不留具体仓库
        log.setAfterQty(0);
        log.setOrderId(orderId);
        log.setOperatorId(operatorId);
        log.setRemark("跨仓总量预留");
        log.setTenantId(tenantId);
        inventoryLogMapper.insert(log);
    }

    @Override
    @Transactional
    public void globalRelease(InventoryReserveDTO dto, Long operatorId) {
        Long tenantId = TenantContext.getTenantId();

        for (InventoryReserveDTO.ReserveItemDTO item : dto.getItems()) {
            String lockKey = "sku:lock:" + item.getSkuId();
            RLock lock = redissonClient.getLock(lockKey);

            try {
                if (!lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                    throw new RuntimeException("系统繁忙，请稍后重试");
                }

                // 查询该SKU的跨仓预留记录
                LambdaQueryWrapper<InventoryGlobalReserve> wrapper = new LambdaQueryWrapper<>();
                wrapper.eq(InventoryGlobalReserve::getOrderId, dto.getOrderId())
                        .eq(InventoryGlobalReserve::getSkuId, item.getSkuId());
                InventoryGlobalReserve record = globalReserveMapper.selectOne(wrapper);

                if (record == null) {
                    throw new RuntimeException("未找到跨仓预留记录");
                }

                int toRelease = Math.min(item.getQuantity(), record.getReserveQty() - record.getReleasedQty());
                if (toRelease <= 0) {
                    throw new RuntimeException("无可释放的预留数量");
                }

                // 按仓库比例释放（从预留的仓库依次释放）
                releaseFromWarehouses(item.getSkuId(), toRelease, tenantId, operatorId, dto.getOrderId());

                // 更新预留记录
                record.setReleasedQty(record.getReleasedQty() + toRelease);
                globalReserveMapper.updateById(record);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("操作被中断，请稍后重试");
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }
    }

    /**
     * 按仓库比例释放全局预留
     */
    private void releaseFromWarehouses(Long skuId, Integer releaseQty, Long tenantId, Long operatorId, Long orderId) {
        LambdaQueryWrapper<Inventory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Inventory::getSkuId, skuId);
        wrapper.eq(Inventory::getTenantId, tenantId);
        wrapper.gt(Inventory::getGlobalReservedQty, 0);
        List<Inventory> inventories = inventoryMapper.selectList(wrapper);

        int remaining = releaseQty;
        for (Inventory inv : inventories) {
            if (remaining <= 0) break;

            int reserved = inv.getGlobalReservedQty();
            if (reserved <= 0) continue;

            int release = Math.min(reserved, remaining);

            LambdaUpdateWrapper<Inventory> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(Inventory::getId, inv.getId())
                    .eq(Inventory::getVersion, inv.getVersion());
            updateWrapper.setSql("global_reserved_qty = global_reserved_qty - " + release);
            int rows = inventoryMapper.update(null, updateWrapper);
            if (rows == 0) {
                throw new RuntimeException("库存已被其他操作修改，请重试");
            }

            remaining -= release;
        }

        // 记录日志
        InventoryLog log = new InventoryLog();
        log.setSkuId(skuId);
        log.setChangeType("GLOBAL_RELEASE");
        log.setChangeQty(releaseQty);
        log.setBeforeQty(0);
        log.setAfterQty(0);
        log.setOrderId(orderId);
        log.setOperatorId(operatorId);
        log.setRemark("跨仓总量释放");
        log.setTenantId(tenantId);
        inventoryLogMapper.insert(log);
    }

    @Override
    @Transactional
    public void globalReleasePartial(Long skuId, Integer quantity, Long orderId, Long operatorId) {
        Long tenantId = TenantContext.getTenantId();
        String lockKey = "sku:lock:" + skuId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (!lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                throw new RuntimeException("系统繁忙，请稍后重试");
            }

            // 直接从各仓库按比例释放 global_reserved_qty，不依赖 inventory_global_reserve 记录
            releaseFromWarehouses(skuId, quantity, tenantId, operatorId, orderId);

            // 同步更新 inventory_global_reserve 记录（若存在）
            LambdaQueryWrapper<InventoryGlobalReserve> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(InventoryGlobalReserve::getOrderId, orderId)
                    .eq(InventoryGlobalReserve::getSkuId, skuId);
            InventoryGlobalReserve record = globalReserveMapper.selectOne(wrapper);
            if (record != null) {
                int newReleased = Math.min(record.getReleasedQty() + quantity, record.getReserveQty());
                record.setReleasedQty(newReleased);
                globalReserveMapper.updateById(record);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("操作被中断，请稍后重试");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public Integer getGlobalAvailableQty(Long skuId) {
        Long tenantId = TenantContext.getTenantId();

        // 计算跨仓可用总量 = SUM(quantity - reserved_qty - global_reserved_qty)
        String sql = """
            SELECT COALESCE(SUM(quantity - IFNULL(reserved_qty, 0) - IFNULL(global_reserved_qty, 0)), 0)
            FROM inventory
            WHERE sku_id = ? AND tenant_id = ?
            """;

        // 使用 MyBatis-Plus 查询
        LambdaQueryWrapper<Inventory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Inventory::getSkuId, skuId);
        wrapper.eq(Inventory::getTenantId, tenantId);
        List<Inventory> list = inventoryMapper.selectList(wrapper);

        int totalAvailable = 0;
        for (Inventory inv : list) {
            int available = inv.getQuantity() - inv.getReservedQty() - inv.getGlobalReservedQty();
            totalAvailable += Math.max(0, available);
        }
        return totalAvailable;
    }

    @Override
    @Transactional
    public void outByPlan(Long planId, Integer quantity, Long operatorId) {
        Long tenantId = TenantContext.getTenantId();

        // Validate positive quantity
        if (quantity == null || quantity <= 0) {
            throw new RuntimeException("出库数量必须大于0");
        }

        // Query delivery plan with explicit tenant filter
        LambdaQueryWrapper<OrderDeliveryPlan> planQw = new LambdaQueryWrapper<>();
        planQw.eq(OrderDeliveryPlan::getId, planId);
        planQw.eq(OrderDeliveryPlan::getTenantId, tenantId);
        OrderDeliveryPlan plan = deliveryPlanMapper.selectOne(planQw);
        if (plan == null) {
            throw new RuntimeException("配货计划不存在");
        }

        if (plan.getWarehouseId() == null) {
            throw new RuntimeException("配货计划未指定仓库");
        }

        inventorySkuEligibilityService.requireEligible(plan.getSkuId(), tenantId);

        // Validate plan data integrity — null allocatedQty/outQty is invalid
        if (plan.getAllocatedQty() == null || plan.getOutQty() == null) {
            throw new RuntimeException("配货计划数据异常: 配货数量或已出库数量为空");
        }

        // Validate not exceeding allocatedQty - outQty
        int maxOut = plan.getAllocatedQty() - plan.getOutQty();
        if (quantity > maxOut) {
            throw new RuntimeException("出库数量超过配货计划待出库数量");
        }

        String lockKey = INVENTORY_LOCK_PREFIX + plan.getSkuId() + ":" + plan.getWarehouseId();
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (!lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                throw new RuntimeException("系统繁忙，请稍后重试");
            }

            // Query inventory with explicit tenant filter
            LambdaQueryWrapper<Inventory> invQw = new LambdaQueryWrapper<>();
            invQw.eq(Inventory::getSkuId, plan.getSkuId());
            invQw.eq(Inventory::getWarehouseId, plan.getWarehouseId());
            invQw.eq(Inventory::getTenantId, tenantId);
            Inventory inv = inventoryMapper.selectOne(invQw);
            if (inv == null) {
                throw new RuntimeException("库存记录不存在");
            }

            // Validate inventory data integrity — quantity must not be null
            if (inv.getQuantity() == null) {
                throw new RuntimeException("库存数据异常: 库存数量为空");
            }

            int beforeQty = inv.getQuantity();
            int reservedQty = inv.getReservedQty() != null ? inv.getReservedQty() : 0;

            // Available = quantity - reserved_qty (never requires global_reserved_qty)
            int available = inv.getQuantity() - reservedQty;
            if (available < quantity) {
                throw new RuntimeException(String.format(
                        "库存不足: SKU[%d] 仓库[%d] 可用:%d 需要:%d",
                        plan.getSkuId(), plan.getWarehouseId(), available, quantity));
            }

            // Atomic deduct: only quantity, conditioned by id, tenant_id and
            // available >= requested, version auto-incremented
            int rows = inventoryMapper.deductQuantity(inv.getId(), tenantId, quantity);
            if (rows == 0) {
                // Re-read with explicit tenant-scoped query (selectById does not filter by tenant)
                LambdaQueryWrapper<Inventory> recheckQw = new LambdaQueryWrapper<>();
                recheckQw.eq(Inventory::getId, inv.getId());
                recheckQw.eq(Inventory::getTenantId, tenantId);
                Inventory recheck = inventoryMapper.selectOne(recheckQw);
                int rq = recheck != null && recheck.getReservedQty() != null ? recheck.getReservedQty() : 0;
                int recheckQuantity = recheck != null && recheck.getQuantity() != null ? recheck.getQuantity() : 0;
                int ravail = recheckQuantity - rq;
                throw new RuntimeException(String.format(
                        "库存不足或已被修改: SKU[%d] 仓库[%d] 可用:%d 需要:%d",
                        plan.getSkuId(), plan.getWarehouseId(), ravail, quantity));
            }

            // Update delivery plan outQty and status
            OrderDeliveryPlan updatePlan = new OrderDeliveryPlan();
            updatePlan.setId(planId);
            int newOutQty = plan.getOutQty() + quantity;
            updatePlan.setOutQty(newOutQty);
            if (plan.getAllocatedQty() <= newOutQty) {
                updatePlan.setStatus(OrderDeliveryPlan.Status.OUT);
            }
            deliveryPlanMapper.updateById(updatePlan);

            // Record inventory log
            InventoryLog log = new InventoryLog();
            log.setSkuId(plan.getSkuId());
            log.setWarehouseId(plan.getWarehouseId());
            log.setChangeType("SALE_OUT");
            log.setChangeQty(-quantity);
            log.setBeforeQty(beforeQty);
            log.setAfterQty(beforeQty - quantity);
            log.setOrderId(plan.getOrderId());
            log.setOperatorId(operatorId);
            log.setRemark("配货计划出库(SOW-2)");
            log.setTenantId(tenantId);
            inventoryLogMapper.insert(log);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("操作被中断，请重试");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public List<InventoryVO> listAlerts(Long warehouseId) {
        Long tenantId = TenantContext.getTenantId();

        String sql = """
            SELECT i.*, ps.sku_code, p.name AS product_name, w.warehouse_name
            FROM inventory i
            INNER JOIN product_sku ps ON i.sku_id = ps.id
            INNER JOIN product p ON ps.product_id = p.id
            LEFT JOIN warehouse w ON i.warehouse_id = w.id
            WHERE i.tenant_id = ? AND (i.quantity - i.reserved_qty) < i.alert_threshold
            """;

        LambdaQueryWrapper<Inventory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Inventory::getTenantId, tenantId);
        wrapper.apply("(quantity - reserved_qty) < alert_threshold");

        if (warehouseId != null) {
            wrapper.eq(Inventory::getWarehouseId, warehouseId);
        }

        List<Inventory> list = inventoryMapper.selectList(wrapper);
        return list.stream().map(inv -> {
            InventoryVO vo = convertToVO(inv);
            vo.setAlertStatus("below");
            return vo;
        }).toList();
    }

    @Override
    public PageResult<InventoryLogVO> listLogs(InventoryLogPageDTO dto) {
        Long tenantId = TenantContext.getTenantId();

        // 计算分页
        long current = dto.getCurrent() != null ? dto.getCurrent() : 1L;
        long pageSize = dto.getSize() != null ? dto.getSize() : 20L;
        long offset = (current - 1) * pageSize;
        long size = pageSize;

        // 使用自定义JOIN查询
        List<InventoryLog> logList = inventoryLogMapper.selectInventoryLogList(
                tenantId,
                dto.getSkuId(),
                dto.getWarehouseId(),
                dto.getChangeType(),
                offset,
                size
        );

        long total = inventoryLogMapper.countInventoryLogList(
                tenantId,
                dto.getSkuId(),
                dto.getWarehouseId(),
                dto.getChangeType()
        );

        List<InventoryLogVO> voList = new ArrayList<>();
        for (InventoryLog log : logList) {
            InventoryLogVO vo = new InventoryLogVO();
            BeanUtils.copyProperties(log, vo);
            vo.setChangeTypeName(getChangeTypeName(log.getChangeType()));
            voList.add(vo);
        }

        PageResult<InventoryLogVO> pageResult = new PageResult<>();
        pageResult.setRecords(voList);
        pageResult.setTotal(total);
        pageResult.setSize(size);
        pageResult.setCurrent(dto.getCurrent() != null ? dto.getCurrent() : 1);
        pageResult.setPages((total + size - 1) / size);
        return pageResult;
    }

    private String getChangeTypeName(String changeType) {
        return switch (changeType) {
            case "PURCHASE_IN" -> "采购入库";
            case "SALE_OUT" -> "销售出库";
            case "SALE_CANCEL" -> "订单取消";
            case "ADJUST" -> "库存调整";
            case "OTHER_OUT" -> "其他出库";
            default -> changeType;
        };
    }

    private InventoryVO convertToVO(Inventory inv) {
        InventoryVO vo = new InventoryVO();
        BeanUtils.copyProperties(inv, vo);
        int globalReserved = inv.getGlobalReservedQty() != null ? inv.getGlobalReservedQty() : 0;
        vo.setAvailableQty(inv.getQuantity() - inv.getReservedQty() - globalReserved);
        return vo;
    }

    private List<Long> parseFileIds(List<String> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        return images.stream()
                .filter(value -> value != null && value.matches("\\d+"))
                .map(Long::valueOf)
                .collect(Collectors.toList());
    }

    private String toImagesJson(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(images);
        } catch (Exception e) {
            throw new RuntimeException("入库凭证图片保存失败");
        }
    }
}
