package cl.inmobiliarias.wishlist.config.kafka;

import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.ContainerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOffInterval;

import java.util.HashMap;
import java.util.Map;

import static org.apache.kafka.clients.consumer.ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG;

/**
 * Configuracion de los consumidores Kafka.
 *
 * Se usa ACK manual (los consumidores confirman solo cuando terminan de
 * procesar) y un DefaultErrorHandler con reintentos que, al agotar los
 * intentos, publica el mensaje en la DLQ correspondiente segun el topic
 * de origen (propiedades-nuevas.dlq o notificaciones-sistema.dlq).
 */
@Configuration
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    private final String bootstrapServers;
    private final KafkaProperties properties;

    public KafkaConsumerConfig(@Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
                               KafkaProperties properties) {
        this.bootstrapServers = bootstrapServers;
        this.properties = properties;
    }

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        // Grupo por defecto; cada @KafkaListener puede definir el suyo.
        props.put(GROUP_ID_CONFIG, "wishlist-notifier-group");
        props.put(KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(ENABLE_AUTO_COMMIT_CONFIG, false);
        // JsonDeserializer solo puede instanciar DTO de este paquete.
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "cl.inmobiliarias.wishlist.dto");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "cl.inmobiliarias.wishlist.dto.PropiedadCreadaEvent");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Recoverer que manda el mensaje fallido a la DLQ correcta segun el
     * topic de origen: propiedades-nuevas -> propiedades-nuevas.dlq y
     * notificaciones-sistema -> notificaciones-sistema.dlq.
     */
    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(KafkaTemplate<String, Object> kafkaTemplate) {
        return new DeadLetterPublishingRecoverer(kafkaTemplate, (record, exception) -> {
            String origen = record.topic();
            String dlq;
            if (origen != null && origen.equals(properties.getTopics().getPropiedadesNuevas())) {
                dlq = properties.getTopics().getPropiedadesNuevasDlq();
            } else if (origen != null && origen.equals(properties.getTopics().getNotificacionesSistema())) {
                dlq = properties.getTopics().getNotificacionesSistemaDlq();
            } else {
                // Topic desconocido: se usa el patron <origen>.dlq como respaldo.
                dlq = origen != null ? origen + ".dlq" : properties.getTopics().getPropiedadesNuevasDlq();
            }
            log.error("Mandando mensaje a DLQ {} (origen={}, partition={}, offset={}) por: {}",
                    dlq, origen, record.partition(), record.offset(), exception.getMessage());
            return new TopicPartition(dlq, record.partition());
        });
    }

    @Bean
    public DefaultErrorHandler defaultErrorHandler(
            DeadLetterPublishingRecoverer deadLetterPublishingRecoverer) {
        // Reintenta 3 veces esperando 2 segundos entre intentos y, si sigue
        // fallando, el recoverer lo publica en la DLQ correspondiente.
        return new DefaultErrorHandler(deadLetterPublishingRecoverer, new FixedBackOffInterval(2000L, 3));
    }

    @Bean(name = "kafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory, DefaultErrorHandler defaultErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        // ACK manual: cada consumidor llama a ack.acknowledge() solo cuando
        // termina. Esto evita perder mensajes si el proceso cae a mitad.
        factory.setAckMode(ContainerFactory.AckMode.MANUAL);
        // Los errores de deserializacion y de negocio pasan por el recoverer
        // que lleva a la DLQ segun el topic de origen.
        factory.setCommonErrorHandler(defaultErrorHandler);
        return factory;
    }
}
