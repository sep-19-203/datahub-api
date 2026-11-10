package vn.teca.hddt.dataapi.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/**
 * TD-API-40: khối hddt.security của application.yml thật bind đúng — ESB mặc định tắt, bật bằng ESB_HMAC_KEY hoặc
 * tthc.hmac.key (tên của tthc-sharing); partner-clients TTHC → tthc-hthue, TMS → tms-hthue; không còn secret client.
 */
class EsbPropertiesBindingTest {

    static HddtProperties.Security.Esb bind(Map<String, Object> env) throws Exception {
        return bindSecurity(env).esb();
    }

    static HddtProperties.Security bindSecurity(Map<String, Object> env) throws Exception {
        StandardEnvironment e = new StandardEnvironment();
        e.getPropertySources().addFirst(new MapPropertySource("env-gia", env));
        for (PropertySource<?> ps : new YamlPropertySourceLoader().load("application.yml", new ClassPathResource("application.yml"))) {
            e.getPropertySources().addLast(ps);
        }
        return new Binder(ConfigurationPropertySources.get(e), new org.springframework.boot.context.properties.bind
                .PropertySourcesPlaceholdersResolver(e)).bind("hddt", HddtProperties.class).get().security();
    }

    @Test
    void defaultYaml_partnerClients_defaultClient_noSecrets_registryStarts() throws Exception {
        HddtProperties.Security s = bindSecurity(new HashMap<>());
        assertThat(s.partnerClients()).containsEntry("TTHC", "tthc-hthue").containsEntry("TMS", "tms-hthue").hasSize(2);
        assertThat(s.defaultClient()).isEqualTo("noi-bo");
        assertThat(s.clients()).extracting(HddtProperties.Security.Client::id)
                .containsExactly("tthc-hthue", "tms-hthue", "noi-bo");
        assertThat(s.clients().get(2).permissions()).containsExactlyInAnyOrder(
                vn.teca.hddt.dataapi.application.security.ApiPermission.values());
        // yml thật dựng được registry (partner-clients/default-client trỏ đúng client đã khai báo)
        HddtProperties p = vn.teca.hddt.dataapi.support.TestProps.defaults();
        new vn.teca.hddt.dataapi.adapter.web.security.ClientRegistry(new HddtProperties(p.api(), p.doris(), p.dse(),
                p.s3(), p.mstStatus(), p.hd1(), p.hd5(), p.hd7(), p.hd8(), p.tms(), p.export(), p.worker(), s,
                p.jobCodes()));
    }

    @Test
    void defaultYaml_esbPresentButDisabled() throws Exception {
        // người dùng chốt 09/11: khóa CHỈ qua biến môi trường ⇒ application.yml không được mang khóa mặc định
        HddtProperties.Security.Esb esb = bind(new HashMap<>());
        assertThat(esb).isNotNull();
        assertThat(esb.enabled()).isFalse();
        assertThat(esb.hmacKey()).isNullOrEmpty();
        assertThat(esb.partnerHeader()).isEqualTo("x-partner");
        assertThat(esb.hmacHeader()).isEqualTo("x-hmac");
        assertThat(esb.zone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(esb.clockSkew()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void tthcSharingPropertyName_tthcHmacKey_enablesEsb() throws Exception {
        // đặt khóa bằng đúng tên cấu hình của hddt-tthc-sharing
        Map<String, Object> env = new HashMap<>();
        env.put("tthc.hmac.key", "khoa-esb-gia-cho-test");
        HddtProperties.Security.Esb esb = bind(env);
        assertThat(esb.enabled()).isTrue();
        assertThat(esb.hmacKey()).isEqualTo("khoa-esb-gia-cho-test");
    }

    @Test
    void esbHmacKey_winsOverTthcHmacKey() throws Exception {
        Map<String, Object> env = new HashMap<>();
        env.put("ESB_HMAC_KEY", "khoa-uu-tien");
        env.put("tthc.hmac.key", "khoa-phu");
        assertThat(bind(env).hmacKey()).isEqualTo("khoa-uu-tien");
    }

    @Test
    void envVariables_enableEsb_andSplitPartnerList() throws Exception {
        Map<String, Object> env = new HashMap<>();
        env.put("ESB_HMAC_KEY", "khoa-esb-gia-cho-test");
        env.put("ESB_ALLOWED_PARTNERS", "TTHC,TMS");
        HddtProperties.Security.Esb esb = bind(env);
        assertThat(esb.enabled()).isTrue();
        List<String> partners = esb.allowedPartners();
        assertThat(partners).containsExactly("TTHC", "TMS");
        assertThat(esb.hmacKey()).isEqualTo("khoa-esb-gia-cho-test");
    }

    @Test
    void emptyEnvKey_turnsEsbOff() throws Exception {
        Map<String, Object> env = new HashMap<>();
        env.put("ESB_HMAC_KEY", "");
        assertThat(bind(env).enabled()).isFalse();
    }
}
