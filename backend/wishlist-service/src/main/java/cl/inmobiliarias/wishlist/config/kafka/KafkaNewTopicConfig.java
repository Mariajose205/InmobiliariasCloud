package cl.inmobiliarias.wishlist.config.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declaracion de los topicos Kafka del flujo.
 *
 * Los topicos se crean automaticamente al arrancar el servicio gracias a
 * KafkaAdmin (spring.kafka.admin.auto-create=true en application.yml).
 * Todos tienen retencion de 7 dias (604800000 ms) y cleanup delete.
 */
@Configuration
public class KafkaNewTopicConfig {

    /** 7 dias en milisegundos: 7 * 24 * 60 * 60 * 1000. */
    public static final String RETENTION_MS = "604800000";

    private final KafkaProperties properties;

    public KafkaNewTopicConfig(KafkaProperties properties) {
        this.properties = properties;
    }

    @Bean
    public NewTopic propiedadesNuevasTopic() {
        return new NewTopic(
                properties.getTopics().getPropiedadesNuevas(),
                3,
                (short) 1)
                .configs(java.util.Map.of("retention.ms", RETENTION_MS, "cleanup.policy", "delete"));
    }

    @Bean
    public NewTopic notificacionesSistemaTopic() {
        return new NewTopic(
                properties.getTopics().getNotificacionesSistema(),
                4,
                (short) 1)
                .configs(java.util.Map.of("retention.ms", RETENTION_MS, "cleanup.policy", "delete"));
    }

    @Bean
    public NewTopic propiedadesNuevasDlqTopic() {
        return new NewTopic(
                properties.getTopics().getPropiedadesNuevasDlq(),
                3,
                (short) 1)
                .configs(java.util.Map.of("retention.ms", RETENTION_MS, "cleanup.policy", "delete"));
    }

    @Bean
    public NewTopic notificacionesSistemaDlqTopic() {
        // Mismas particiones que notificaciones-sistema: el recoverer conserva
        // el numero de particion del mensaje original al moverlo a la DLQ.
        return new NewTopic(
                properties.getTopics().getNotificacionesSistemaDlq(),
                4,
                (short) 1)
                .configs(java.util.Map.of("retention.ms", RETENTION_MS, "cleanup.policy", "delete"));
    }
}
