package cl.inmobiliarias.service.service.messaging.admin;

import com.rabbitmq.client.AMQP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Encapsula toda la logica de administracion del broker: crear y eliminar
 * colas, exchanges y bindings, y consultar el estado de una cola.
 *
 * Es el unico punto que habla con {@link AmqpAdmin}, de modo que los
 * controladores REST no manejan detalles de RabbitMQ. La declaracion
 * declarativa de las colas del flujo vive en RabbitMqConfig; este servicio
 * permite ademas gestionarlas en caliente desde la API de administracion.
 */
@Service
public class RabbitAdminService {

    private static final Logger log = LoggerFactory.getLogger(RabbitAdminService.class);

    private final AmqpAdmin amqpAdmin;
    private final RabbitTemplate rabbitTemplate;

    public RabbitAdminService(AmqpAdmin amqpAdmin, RabbitTemplate rabbitTemplate) {
        this.amqpAdmin = amqpAdmin;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Crea (o verifica) una cola, opcionalmente con dead-letter configurado.
     */
    public void declararCola(String nombre, boolean durable, boolean exclusiva, boolean autoDelete,
                             String deadLetterExchange, String deadLetterRoutingKey) {
        Map<String, Object> args = new HashMap<>();
        if (deadLetterExchange != null && !deadLetterExchange.isBlank()) {
            args.put("x-dead-letter-exchange", deadLetterExchange);
        }
        if (deadLetterRoutingKey != null && !deadLetterRoutingKey.isBlank()) {
            args.put("x-dead-letter-routing-key", deadLetterRoutingKey);
        }
        Queue cola = new Queue(nombre, durable, exclusiva, autoDelete, args);
        amqpAdmin.declareQueue(cola);
        log.info("[RabbitAdmin] Cola declarada: {}", nombre);
    }

    public void eliminarCola(String nombre, boolean ifUnused, boolean ifEmpty) {
        amqpAdmin.deleteQueue(nombre, ifUnused, ifEmpty);
        log.info("[RabbitAdmin] Cola eliminada: {} (unused={}, empty={})", nombre, ifUnused, ifEmpty);
    }

    public void purgarCola(String nombre) {
        amqpAdmin.purgeQueue(nombre, false);
        log.info("[RabbitAdmin] Cola purgada: {}", nombre);
    }

    /** Declara un exchange indicando su tipo: fanout, direct o topic. */
    public void declararExchange(String nombre, String tipo, boolean durable, boolean autoDelete) {
        Exchange exchange = crearExchange(nombre, tipo, durable, autoDelete);
        amqpAdmin.declareExchange(exchange);
        log.info("[RabbitAdmin] Exchange declarado: {} (tipo={})", nombre, tipo);
    }

    public void eliminarExchange(String nombre) {
        amqpAdmin.deleteExchange(nombre);
        log.info("[RabbitAdmin] Exchange eliminado: {}", nombre);
    }

    public void bind(String cola, String exchange, String routingKey, String tipoExchange) {
        Exchange ex = crearExchange(exchange, tipoExchange, true, false);
        Binding binding;
        if ("fanout".equalsIgnoreCase(tipoExchange)) {
            binding = BindingBuilder.bind(new Queue(cola)).to((FanoutExchange) ex);
        } else {
            binding = BindingBuilder.bind(new Queue(cola)).to(ex).with(routingKey).noargs();
        }
        amqpAdmin.declareBinding(binding);
        log.info("[RabbitAdmin] Binding declarado: {} -> {} (rk={})", exchange, cola, routingKey);
    }

    private Exchange crearExchange(String nombre, String tipo, boolean durable, boolean autoDelete) {
        String t = tipo == null ? "direct" : tipo.toLowerCase();
        return switch (t) {
            case "fanout" -> new FanoutExchange(nombre, durable, autoDelete);
            case "topic" -> new TopicExchange(nombre, durable, autoDelete);
            default -> new DirectExchange(nombre, durable, autoDelete);
        };
    }

    /**
     * Devuelve informacion basica de una cola (existe y cantidad de mensajes)
     * usando un queueDeclarePassive, que no la crea si no existe.
     */
    public Map<String, Object> infoCola(String nombre) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("nombre", nombre);
        AMQP.Queue.DeclareOk ok = rabbitTemplate.execute(channel -> {
            try {
                return channel.queueDeclarePassive(nombre);
            } catch (IOException e) {
                return null;
            }
        });
        if (ok == null) {
            info.put("existe", false);
        } else {
            info.put("existe", true);
            info.put("mensajes", ok.getMessageCount());
            info.put("consumidores", ok.getConsumerCount());
        }
        return info;
    }
}
