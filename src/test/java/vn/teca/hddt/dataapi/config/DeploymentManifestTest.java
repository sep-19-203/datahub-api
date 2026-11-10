package vn.teca.hddt.dataapi.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** Mẫu OpenShift hợp lệ YAML, đủ thành phần, không chứa secret thật; application.yml không chứa credential. */
class DeploymentManifestTest {

    @Test
    @SuppressWarnings("unchecked")
    void openshiftManifestIsValidAndCarriesNoRealSecrets() throws Exception {
        List<String> kinds = new ArrayList<>();
        try (InputStream in = new FileInputStream("deploy/openshift/hddt-data-api-service.yaml")) {
            for (Object d : new Yaml().loadAll(in)) {
                Map<String, Object> m = (Map<String, Object>) d;
                kinds.add((String) m.get("kind"));
                if ("Secret".equals(m.get("kind"))) {
                    ((Map<String, Object>) m.get("stringData")).values()
                            .forEach(v -> assertThat(v.toString()).startsWith("REPLACE"));
                }
            }
        }
        assertThat(kinds).containsExactly("ConfigMap", "Secret", "Deployment", "Deployment", "Service", "Route",
                "NetworkPolicy", "PodDisruptionBudget");
    }

    @Test
    void applicationYamlHasNoInlineCredentials() throws Exception {
        String yml = new String(Files.readAllBytes(Paths.get("src/main/resources/application.yml")), StandardCharsets.UTF_8);
        assertThat(yml).doesNotContainPattern("(?im)^\\s*[a-z0-9-]*(password|secret-key|secret-sha256|access-key):[ \\t]*[^$\\s{]");
    }
}
