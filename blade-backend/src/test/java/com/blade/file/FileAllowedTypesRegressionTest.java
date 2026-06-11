package com.blade.file;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BE-1010 regression test: verifies runtime application.yml
 * blade.file.allowed-types matches the expected set.
 *
 * <p>Config drift (e.g. accidental removal or reordering) is caught here.</p>
 */
class FileAllowedTypesRegressionTest {

    private static final List<String> EXPECTED = List.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "video/mp4",
            "video/webm",
            "video/quicktime"
    );

    @Test
    void applicationYml_allowedTypes_matchesExpected() {
        Yaml yaml = new Yaml();
        Map<String, Object> root;
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("application.yml")) {
            assertNotNull(in, "application.yml must exist on classpath");
            root = yaml.load(in);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read application.yml", e);
        }

        assertNotNull(root, "application.yml must not be empty");

        @SuppressWarnings("unchecked")
        Map<String, Object> blade = (Map<String, Object>) root.get("blade");
        assertNotNull(blade, "blade key must exist in application.yml");

        @SuppressWarnings("unchecked")
        Map<String, Object> fileConf = (Map<String, Object>) blade.get("file");
        assertNotNull(fileConf, "blade.file key must exist in application.yml");

        @SuppressWarnings("unchecked")
        List<String> allowedTypes = (List<String>) fileConf.get("allowed-types");
        assertNotNull(allowedTypes, "blade.file.allowed-types must exist in application.yml");

        assertEquals(
                EXPECTED,
                allowedTypes,
                "blade.file.allowed-types must be exactly [image/jpeg, image/png, image/webp, video/mp4, video/webm, video/quicktime]"
        );

        Object maxSizeMb = fileConf.get("max-size-mb");
        assertEquals(
                "${BLADE_FILE_MAX_SIZE_MB:200}",
                String.valueOf(maxSizeMb),
                "blade.file.max-size-mb must default to 200MB and remain environment-overridable"
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> spring = (Map<String, Object>) root.get("spring");
        assertNotNull(spring, "spring key must exist in application.yml");

        @SuppressWarnings("unchecked")
        Map<String, Object> servlet = (Map<String, Object>) spring.get("servlet");
        assertNotNull(servlet, "spring.servlet key must exist in application.yml");

        @SuppressWarnings("unchecked")
        Map<String, Object> multipart = (Map<String, Object>) servlet.get("multipart");
        assertNotNull(multipart, "spring.servlet.multipart key must exist in application.yml");
        assertEquals("${BLADE_MULTIPART_MAX_FILE_SIZE:200MB}", String.valueOf(multipart.get("max-file-size")));
        assertEquals("${BLADE_MULTIPART_MAX_REQUEST_SIZE:220MB}", String.valueOf(multipart.get("max-request-size")));
    }
}
