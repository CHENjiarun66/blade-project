package com.blade.outlet.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "档口选项结构化契约")
public class OutletOptionsVO {

    @Schema(description = "档口范围：ALL/ASSIGNED/NONE")
    private String scopeType;

    @Schema(description = "人员范围：ALL_USERS/SELF")
    private String peopleScope;

    @Schema(description = "是否单档口锁定（前端只读展示）")
    private boolean locked;

    @Schema(description = "默认档口ID（可能为空）")
    private Long defaultOutletId;

    @Schema(description = "可用且启用的档口选项")
    private List<OutletOptionVO> items;

    public OutletOptionsVO() {}

    public OutletOptionsVO(String scopeType, String peopleScope, boolean locked,
                           Long defaultOutletId, List<OutletOptionVO> items) {
        this.scopeType = scopeType;
        this.peopleScope = peopleScope;
        this.locked = locked;
        this.defaultOutletId = defaultOutletId;
        this.items = items;
    }

    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }
    public String getPeopleScope() { return peopleScope; }
    public void setPeopleScope(String peopleScope) { this.peopleScope = peopleScope; }
    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }
    public Long getDefaultOutletId() { return defaultOutletId; }
    public void setDefaultOutletId(Long defaultOutletId) { this.defaultOutletId = defaultOutletId; }
    public List<OutletOptionVO> getItems() { return items; }
    public void setItems(List<OutletOptionVO> items) { this.items = items; }
}
