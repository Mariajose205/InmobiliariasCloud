package cl.inmobiliarias.wishlist.service.messaging;

import cl.inmobiliarias.wishlist.dto.admin.ConsumerGroupInfoResponse;
import cl.inmobiliarias.wishlist.dto.admin.TopicInfoResponse;
import cl.inmobiliarias.wishlist.dto.admin.TopicRequest;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.ConsumerGroupState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Administracion de topics y grupos Kafka via AdminClient.
 *
 * Se usa KafkaAdmin (que crea los topics declarados al arrancar) y un
 * AdminClient propio para crear/borrar/describir en caliente, que es lo
 * que necesita la pantalla de admin.
 */
@Service
public class KafkaAdminService {

    private static final Logger log = LoggerFactory.getLogger(KafkaAdminService.class);

    private static final long TIMEOUT_SECONDS = 10;

    private final KafkaAdmin kafkaAdmin;
    private final String bootstrapServers;

    public KafkaAdminService(KafkaAdmin kafkaAdmin,
                             @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
        this.kafkaAdmin = kafkaAdmin;
        this.bootstrapServers = bootstrapServers;
    }

    /** Crea un AdminClient contra el broker configurado. Hay que cerrarlo. */
    private AdminClient adminClient() {
        return AdminClient.create(Map.of("bootstrap.servers", bootstrapServers));
    }

    /** Lista los nombres de todos los topics. */
    public List<String> listarTopicos() {
        try (AdminClient admin = adminClient()) {
            Set<String> nombres = admin.listTopics()
                    .names().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return nombres.stream().sorted().collect(Collectors.toList());
        } catch (Exception ex) {
            log.error("Error listando topicos: {}", ex.getMessage(), ex);
            throw new RuntimeException("No se pudieron listar los topicos: " + ex.getMessage(), ex);
        }
    }

    /** Describe un topico: particiones, factor y configuracion. */
    public TopicInfoResponse describirTopico(String nombre) {
        try (AdminClient admin = adminClient()) {
            Map<String, TopicDescription> descripciones = admin.describeTopics(List.of(nombre))
                    .allTopicNames().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            TopicDescription descripcion = descripciones.get(nombre);
            if (descripcion == null) {
                throw new RuntimeException("Topico no encontrado: " + nombre);
            }
            Map<String, String> configs = new LinkedHashMap<>();
            ConfigResource recurso = new ConfigResource(ConfigResource.Type.TOPIC, nombre);
            Config config = admin.describeConfigs(List.of(recurso))
                    .all().get(TIMEOUT_SECONDS, TimeUnit.SECONDS).get(recurso);
            if (config != null) {
                config.entries().forEach(e -> configs.put(e.name(), e.value() != null ? String.valueOf(e.value()) : ""));
            }
            // Valores por defecto de retencion/cleanup si el broker no los listo.
            configs.putIfAbsent(TopicConfig.RETENTION_MS_CONFIG, "604800000");
            configs.putIfAbsent(TopicConfig.CLEANUP_POLICY_CONFIG, "delete");
            // TopicDescription no expone el factor de replicacion directamente:
            // se deduce de las replicas de la primera particion.
            short factorReplicacion = descripcion.partitions().isEmpty()
                    ? 0
                    : (short) descripcion.partitions().get(0).replicas().size();
            return new TopicInfoResponse(nombre, descripcion.partitions().size(),
                    factorReplicacion, configs);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Error describiendo topico {}: {}", nombre, ex.getMessage(), ex);
            throw new RuntimeException("No se pudo describir el topico " + nombre + ": " + ex.getMessage(), ex);
        }
    }

    /** Crea un topico con valores por defecto si no se indican. */
    public TopicInfoResponse crearTopico(TopicRequest request) {
        int particiones = request.getParticiones() != null ? request.getParticiones() : 3;
        short factor = request.getFactorReplicacion() != null
                ? request.getFactorReplicacion().shortValue() : (short) 1;
        long retencion = request.getRetencionMs() != null ? request.getRetencionMs() : 604800000L;
        String cleanup = request.getCleanupPolicy() != null && !request.getCleanupPolicy().isBlank()
                ? request.getCleanupPolicy() : "delete";
        Map<String, String> configs = Map.of("retention.ms", String.valueOf(retencion), "cleanup.policy", cleanup);
        NewTopic topic = new NewTopic(request.getNombre(), particiones, factor).configs(configs);
        try (AdminClient admin = adminClient()) {
            admin.createTopics(List.of(topic)).all().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.info("Topico creado: {} (particiones={}, factor={})", request.getNombre(), particiones, factor);
            return describirTopico(request.getNombre());
        } catch (Exception ex) {
            log.error("Error creando topico {}: {}", request.getNombre(), ex.getMessage(), ex);
            throw new RuntimeException("No se pudo crear el topico " + request.getNombre() + ": " + ex.getMessage(), ex);
        }
    }

    /** Elimina un topico. */
    public void eliminarTopico(String nombre) {
        try (AdminClient admin = adminClient()) {
            admin.deleteTopics(List.of(nombre)).all().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.info("Topico eliminado: {}", nombre);
        } catch (Exception ex) {
            log.error("Error eliminando topico {}: {}", nombre, ex.getMessage(), ex);
            throw new RuntimeException("No se pudo eliminar el topico " + nombre + ": " + ex.getMessage(), ex);
        }
    }

    /** Lista los grupos de consumidores. */
    public List<ConsumerGroupInfoResponse> listarGrupos() {
        try (AdminClient admin = adminClient()) {
            Collection<ConsumerGroupListing> grupos = admin.listConsumerGroups()
                    .all().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            List<ConsumerGroupInfoResponse> resultado = new ArrayList<>();
            for (ConsumerGroupListing listing : grupos) {
                String estado = listing.state().map(ConsumerGroupState::name).orElse("UNKNOWN");
                resultado.add(new ConsumerGroupInfoResponse(listing.groupId(), estado, 0, List.of()));
            }
            return resultado;
        } catch (Exception ex) {
            log.error("Error listando grupos: {}", ex.getMessage(), ex);
            throw new RuntimeException("No se pudieron listar los grupos: " + ex.getMessage(), ex);
        }
    }

    /** Describe un grupo: estado, cantidad de miembros y topics que lee. */
    public ConsumerGroupInfoResponse describirGrupo(String groupId) {
        try (AdminClient admin = adminClient()) {
            Map<String, ConsumerGroupDescription> descripciones = admin.describeConsumerGroups(List.of(groupId))
                    .all().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            ConsumerGroupDescription descripcion = descripciones.get(groupId);
            if (descripcion == null) {
                throw new RuntimeException("Grupo no encontrado: " + groupId);
            }
            Set<String> topics = descripcion.members().stream()
                    .flatMap(miembro -> miembro.assignment().topicPartitions().stream())
                    .map(org.apache.kafka.common.TopicPartition::topic)
                    .collect(Collectors.toSet());
            // Si nadie tiene asignacion (grupo sin miembros), se listan los
            // offsets del log como topicos que el grupo esta consumiendo.
            if (topics.isEmpty()) {
                admin.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata()
                        .get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .keySet().forEach(tp -> topics.add(tp.topic()));
            }
            return new ConsumerGroupInfoResponse(groupId, descripcion.state().name(),
                    descripcion.members().size(), new ArrayList<>(topics));
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Error describiendo grupo {}: {}", groupId, ex.getMessage(), ex);
            throw new RuntimeException("No se pudo describir el grupo " + groupId + ": " + ex.getMessage(), ex);
        }
    }
}
