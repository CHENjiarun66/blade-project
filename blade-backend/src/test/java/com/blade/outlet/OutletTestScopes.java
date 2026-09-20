package com.blade.outlet;

import com.blade.outlet.policy.OutletAccessPolicy;
import com.blade.outlet.policy.OutletAccessScope;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 测试用范围夹具：默认给不关心范围的用例一个全档口/全部人员范围。 */
public final class OutletTestScopes {

    private OutletTestScopes() {}

    public static OutletAccessScope all() {
        return new OutletAccessScope(1L, OutletAccessScope.ActorType.USER, 1L,
                OutletAccessScope.ALL, true, true, List.of(), List.of(), null);
    }

    public static OutletAccessPolicy allScopedPolicy() {
        OutletAccessPolicy policy = mock(OutletAccessPolicy.class);
        when(policy.resolveCurrentScope()).thenReturn(all());
        return policy;
    }

    public static OutletAccessScope assignedSelf(Long userId) {
        return new OutletAccessScope(1L, OutletAccessScope.ActorType.USER, userId,
                OutletAccessScope.ASSIGNED, false, true, List.of(10L), List.of(10L), null);
    }

    public static OutletAccessPolicy assignedSelfPolicy(Long userId) {
        OutletAccessPolicy policy = mock(OutletAccessPolicy.class);
        when(policy.resolveCurrentScope()).thenReturn(assignedSelf(userId));
        return policy;
    }
}
