package com.blade.agent.service;

import com.blade.common.exception.BusinessException;
import com.blade.dashboard.dto.DashboardQueryDTO;
import com.blade.outlet.policy.OutletAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Agent 查询上下文的稳定档口编码解析（Series E3 整改）。
 *
 * <p>Agent 不接受内部档口 ID：Dashboard/Analytics 查询若携带 {@code sourceOutletIds} 返回 400；
 * 只接受 {@code sourceOutletCodes}（允许逗号分隔/重复），按当前 Key readable 范围解析为内部 ID
 * 后写入 query，供统一的 {@code OrderReadScope} 使用。历史读取允许已停用档口，
 * 越权/不存在/跨租户统一 403。</p>
 */
@Service
@RequiredArgsConstructor
public class AgentOutletCodeQuerySupport {

    private final OutletAccessPolicy outletAccessPolicy;

    public void applyDashboardOutletCodes(DashboardQueryDTO query, List<String> sourceOutletCodes) {
        if (query.getSourceOutletIds() != null && !query.getSourceOutletIds().isEmpty()) {
            throw BusinessException.of(400, "Agent 请使用 sourceOutletCodes");
        }
        if (sourceOutletCodes == null || sourceOutletCodes.isEmpty()) {
            return;
        }
        List<Long> ids = outletAccessPolicy.resolveReadableOutletIdsByCodes(sourceOutletCodes);
        if (ids.isEmpty()) {
            throw BusinessException.of(400, "sourceOutletCodes 不能为空");
        }
        query.setSourceOutletIds(ids);
    }
}
