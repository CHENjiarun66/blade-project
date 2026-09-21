package com.blade.outlet.migration;

import com.blade.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OutletBackfillSafetyGateTest {

    private final OutletBackfillSafetyGate gate = new OutletBackfillSafetyGate();

    private OutletBackfillProperties base() {
        OutletBackfillProperties properties = new OutletBackfillProperties();
        properties.setApply(true);
        properties.setMappingFile("/tmp/mapping.csv");
        properties.setTenantId(1L);
        properties.setCopyEnvironmentAck(true);
        return properties;
    }

    @Test
    void rejectsWhenAnyGateMissing() {
        OutletBackfillProperties noApply = base();
        noApply.setApply(false);
        assertThrows(BusinessException.class, () -> gate.requireApplyAllowed(noApply, "jdbc:mysql://localhost/copy", "copy"));

        OutletBackfillProperties noFile = base();
        noFile.setMappingFile(null);
        assertThrows(BusinessException.class, () -> gate.requireApplyAllowed(noFile, "jdbc:mysql://localhost/copy", "copy"));

        OutletBackfillProperties noTenant = base();
        noTenant.setTenantId(null);
        assertThrows(BusinessException.class, () -> gate.requireApplyAllowed(noTenant, "jdbc:mysql://localhost/copy", "copy"));

        OutletBackfillProperties noAck = base();
        noAck.setCopyEnvironmentAck(false);
        assertThrows(BusinessException.class, () -> gate.requireApplyAllowed(noAck, "jdbc:mysql://localhost/copy", "copy"));
    }

    @Test
    void rejectsProductionLookingTargets() {
        assertThrows(BusinessException.class,
                () -> gate.requireApplyAllowed(base(), "jdbc:mysql://nas-host/prod_db", "prod_db"));
        assertThrows(BusinessException.class,
                () -> gate.requireApplyAllowed(base(), "jdbc:mysql://localhost/blade_project_prod", "blade_project_prod"));
        assertThrows(BusinessException.class,
                () -> gate.requireApplyAllowed(base(), "jdbc:mysql://localhost/copy", "nas_copy"));
    }

    @Test
    void allowsConfirmedCopyTarget() {
        assertDoesNotThrow(() -> gate.requireApplyAllowed(base(),
                "jdbc:mysql://localhost:3306/blade_project_copy", "blade_project_copy"));
    }
}
