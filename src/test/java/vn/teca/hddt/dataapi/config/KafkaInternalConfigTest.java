package vn.teca.hddt.dataapi.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.common.config.SaslConfigs;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

class KafkaInternalConfigTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(KafkaInternalConfig.class);

    @Test
    void usesHddtInternalBrokerAndSecurityInsteadOfGenericKafkaConfig() {
        runner.withPropertyValues("hddt.job-result.enabled=true",
                "hddt.kafka.internal.bootstrap-servers=internal:9092",
                "spring.kafka.bootstrap-servers=external:9092",
                "hddt.kafka.security-protocol=SASL_PLAINTEXT",
                "hddt.kafka.sasl.mechanism=PLAIN",
                "hddt.kafka.internal.sasl.jaas-config=test-jaas")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasBean("kafkaInternalTemplate");
                    KafkaTemplate<?, ?> template = context.getBean("kafkaInternalTemplate", KafkaTemplate.class);
                    DefaultKafkaProducerFactory<?, ?> factory = (DefaultKafkaProducerFactory<?, ?>) template.getProducerFactory();
                    assertThat(factory.getConfigurationProperties())
                            .containsEntry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "internal:9092")
                            .containsEntry(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_PLAINTEXT")
                            .containsEntry(SaslConfigs.SASL_MECHANISM, "PLAIN")
                            .containsEntry(SaslConfigs.SASL_JAAS_CONFIG, "test-jaas")
                            .containsEntry(ProducerConfig.ACKS_CONFIG, "all")
                            .containsEntry(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
                });
    }

    @Test
    void genericBrokerCannotReplaceMissingInternalBroker() {
        runner.withPropertyValues("hddt.job-result.enabled=true", "spring.kafka.bootstrap-servers=external:9092")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void disabledDoesNotRequireKafkaConfiguration() {
        runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean("kafkaInternalTemplate"));
    }
}
