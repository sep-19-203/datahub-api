
package vn.teca.hddt.dataapi.config;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.util.StringUtils;

/** Cùng cấu hình trục internal với hddt-registration gửi TTHC. */
@Configuration
@ConditionalOnProperty(prefix = "hddt.job-result", name = "enabled", havingValue = "true")
public class KafkaInternalConfig {
    private static final Logger log = LoggerFactory.getLogger(KafkaInternalConfig.class);
    @Bean
    public DefaultKafkaProducerFactory<String, String> producerInternalFactory(
            @Value("${hddt.kafka.internal.bootstrap-servers:}") String bootstrapServers,
            @Value("${hddt.kafka.security-protocol:}") String securityProtocol,
            @Value("${hddt.kafka.sasl.mechanism:}") String saslMechanism,
            @Value("${hddt.kafka.internal.sasl.jaas-config:}") String saslJaas,
            @Value("${hddt.kafka.internal.producer.max-request-size:2000000}") int maxRequestSize) {
        if (!StringUtils.hasText(bootstrapServers)) {
            throw new IllegalArgumentException("Thiếu hddt.kafka.internal.bootstrap-servers");
        }
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, maxRequestSize);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        // Không hạ xuống PLAINTEXT khi chỉ khai báo một phần cấu hình bảo mật.
        if (StringUtils.hasText(securityProtocol)) {
            props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, securityProtocol);
        }
        if (StringUtils.hasText(saslMechanism)) {
            props.put(SaslConfigs.SASL_MECHANISM, saslMechanism);
        }
        if (StringUtils.hasText(saslJaas)) {
            props.put(SaslConfigs.SASL_JAAS_CONFIG, saslJaas);
        }
        log.info("JOB_RESULT_KAFKA_CONFIG bootstrapServers={} securityProtocol={} saslMechanism={} saslJaasConfigured={} acks=all idempotence=true",
                bootstrapServers, StringUtils.hasText(securityProtocol) ? securityProtocol : "PLAINTEXT(default)",
                saslMechanism, StringUtils.hasText(saslJaas));
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaInternalTemplate(
            @Qualifier("producerInternalFactory") DefaultKafkaProducerFactory<String, String> factory) {
        return new KafkaTemplate<>(factory);
    }
}
