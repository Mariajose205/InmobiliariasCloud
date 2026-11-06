package cl.inmobiliarias.service.config.kafka;

import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

import static org.apache.kafka.clients.producer.ProducerConfig.ACKS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.RETRIES_CONFIG;
import static org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG;

/**
 * Configuracion del productor Kafka para publicar PropiedadCreadaEvent.
 *
 * Clave String (comuna canonica) y valor JSON tipado con acks=all,
 * reintentos e idempotencia. Si Kafka esta caido, el publicador no
 * lanza excepcion hacia el controlador: la creacion de la propiedad
 * debe seguir funcionando (el catalogo no puede depender del broker).
 */
@Configuration
public class KafkaProducerConfig {

    private final String bootstrapServers;

    public KafkaProducerConfig(@Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ACKS_CONFIG, "all");
        props.put(RETRIES_CONFIG, 3);
        props.put(ENABLE_IDEMPOTENCE_CONFIG, true);
        // JsonSerializer agrega el tipo Java en la cabecera para que wishlist
        // pueda mapearlo a su propio DTO.
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "cl.inmobiliarias.service.dto");
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, true);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
