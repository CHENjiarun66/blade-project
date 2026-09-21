package com.blade.outlet.migration;

import com.blade.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class OutletBackfillSafetyGateTest {

    private final OutletBackfillSafetyGate gate =
            new OutletBackfillSafetyGate(mock(JdbcTemplate.class), mock(Environment.class));

    @TempDir
    Path tempDir;

    private OutletBackfillProperties base() {
        OutletBackfillProperties properties = new OutletBackfillProperties();
        properties.setApply(true);
        properties.setMappingFile("/tmp/mapping.csv");
        properties.setTenantId(1L);
        properties.setExpectedDatabaseName("blade_rehearsal");
        properties.setReportDir(tempDir.toString());
        properties.setCopyEnvironmentAck(true);
        properties.setOperator("ops-dsh");
        return properties;
    }

    @Test
    void rejectsWhenAnyBaseGateMissing() {
        OutletBackfillProperties noApply = base();
        noApply.setApply(false);
        assertThrows(BusinessException.class,
                () -> gate.requireApplyAllowed(noApply, "jdbc:mysql://mysql:3306/blade_rehearsal", "blade_rehearsal"));

        OutletBackfillProperties noFile = base();
        noFile.setMappingFile(null);
        assertThrows(BusinessException.class,
                () -> gate.requireApplyAllowed(noFile, "jdbc:mysql://mysql:3306/blade_rehearsal", "blade_rehearsal"));

        OutletBackfillProperties noTenant = base();
        noTenant.setTenantId(null);
        assertThrows(BusinessException.class,
                () -> gate.requireApplyAllowed(noTenant, "jdbc:mysql://mysql:3306/blade_rehearsal", "blade_rehearsal"));

        OutletBackfillProperties noAck = base();
        noAck.setCopyEnvironmentAck(false);
        assertThrows(BusinessException.class,
                () -> gate.requireApplyAllowed(noAck, "jdbc:mysql://mysql:3306/blade_rehearsal", "blade_rehearsal"));

        OutletBackfillProperties noExpected = base();
        noExpected.setExpectedDatabaseName(null);
        assertThrows(BusinessException.class,
                () -> gate.requireApplyAllowed(noExpected, "jdbc:mysql://mysql:3306/blade_rehearsal", "blade_rehearsal"));

        OutletBackfillProperties noReport = base();
        noReport.setReportDir(null);
        assertThrows(BusinessException.class,
                () -> gate.requireApplyAllowed(noReport, "jdbc:mysql://mysql:3306/blade_rehearsal", "blade_rehearsal"));

        OutletBackfillProperties noOperator = base();
        noOperator.setOperator("  ");
        assertThrows(BusinessException.class,
                () -> gate.requireApplyAllowed(noOperator, "jdbc:mysql://mysql:3306/blade_rehearsal", "blade_rehearsal"),
                "apply 必须显式提供 operator");
    }

    @Test
    void rejectsPlainProductionBladeEvenWithAck() {
        // P0-1 反例：jdbc:mysql://mysql:3306/blade + databaseName=blade + ack=true 必须拒绝
        OutletBackfillProperties properties = base();
        properties.setExpectedDatabaseName("blade");
        assertThrows(BusinessException.class,
                () -> gate.requireApplyAllowed(properties, "jdbc:mysql://mysql:3306/blade", "blade"));
    }

    @Test
    void rejectsExpectedNameMismatch() {
        assertThrows(BusinessException.class, () -> gate.requireApplyAllowed(
                base(), "jdbc:mysql://mysql:3306/blade_rehearsal", "blade_copy"));
    }

    @Test
    void rejectsNonCopyDatabaseNaming() {
        OutletBackfillProperties properties = base();
        properties.setExpectedDatabaseName("blade_project");
        assertThrows(BusinessException.class, () -> gate.requireApplyAllowed(
                properties, "jdbc:mysql://mysql:3306/blade_project", "blade_project"));
    }

    @Test
    void rejectsProductionNasMarkersAsSecondLayer() {
        OutletBackfillProperties properties = base();
        properties.setExpectedDatabaseName("blade_rehearsal");
        assertThrows(BusinessException.class, () -> gate.requireApplyAllowed(
                properties, "jdbc:mysql://nas-host:3306/blade_rehearsal", "blade_rehearsal"));
        assertThrows(BusinessException.class, () -> gate.requireApplyAllowed(
                properties, "jdbc:mysql://localhost:3306/prod_blade_copy", "prod_blade_copy"));
    }

    @Test
    void allowsConfirmedCopyTarget() {
        assertDoesNotThrow(() -> gate.requireApplyAllowed(
                base(), "jdbc:mysql://localhost:3306/blade_rehearsal", "blade_rehearsal"));
    }
}
