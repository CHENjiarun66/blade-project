package com.blade.order.service;

import com.blade.order.dto.AdjustmentLogDTO;
import com.blade.order.dto.DeliveryPlanDTO;
import com.blade.order.dto.DeliveryPlanVO;

import java.util.List;

/**
 * 配货计划服务接口
 */
public interface OrderDeliveryPlanService {

    /**
     * 创建配货计划（从订单明细生成）
     *
     * @param orderId 订单ID
     * @return 配货计划列表
     */
    List<DeliveryPlanVO> createDeliveryPlan(Long orderId);

    /**
     * 更新配货计划
     *
     * @param orderId 订单ID
     * @param dto 配货计划DTO
     * @return 更新后的配货计划列表
     */
    List<DeliveryPlanVO> updateDeliveryPlan(Long orderId, DeliveryPlanDTO dto);

    /**
     * 获取订单的配货计划
     *
     * @param orderId 订单ID
     * @return 配货计划列表
     */
    List<DeliveryPlanVO> getDeliveryPlanByOrderId(Long orderId);

    /**
     * 删除配货计划（取消配货）
     *
     * @param orderId 订单ID
     */
    void deleteDeliveryPlan(Long orderId);

    /**
     * 记录订单调整
     *
     * @param dto 调整记录DTO
     */
    void recordAdjustment(AdjustmentLogDTO dto);

    /**
     * 确认调整方案
     * 同时更新订单状态为 READY_TO_SHIP
     *
     * @param orderId 订单ID
     */
    void confirmAdjustment(Long orderId);

    /**
     * 取消调整
     *
     * @param orderId 订单ID
     */
    void cancelAdjustment(Long orderId);

    /**
     * 获取订单调整记录
     *
     * @param orderId 订单ID
     * @return 调整记录列表
     */
    List<AdjustmentLogDTO> getAdjustmentLogs(Long orderId);
}
