package cl.inmobiliarias.service.config.rabbitmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declaracion de exchanges, colas y bindings del flujo de informes y de
 * auditoria de propiedades.
 *
 * Se declara un bean por "caso de uso" para que el broker quede descrito de
 * forma explicita y versionada:
 *
 *   inmobiliaria.fanout  ->  cola.correos.notificaciones
 *   inmobiliaria.direct  ->  cola.pdf.procesamiento
 *   inmobiliaria.topic   ->  cola.propiedades.auditoria
 *                           (binding con patron propiedades.eventos.#)
 *
 * Cada cola de trabajo tiene una Dead Letter Queue asociada a traves de
 * `inmobiliaria.dlx`. Cuando un consumidor agota los reintentos y hace
 * NACK(requeue=false), RabbitMQ mueve el mensaje a la DLQ correspondiente.
 *
 * Los nombres NO estan dispersos como literales: salen de {@link RabbitProperties},
 * que los lee desde `application.yml`.
 */
@Configuration
public class RabbitMqConfig {

    private final RabbitProperties propiedades;

    public RabbitMqConfig(RabbitProperties propiedades) {
        this.propiedades = propiedades;
    }

    // ------------------------------------------------------------------
    // Exchanges
    // ------------------------------------------------------------------

    /** Fanout: reparte la solicitud inicial a todos los suscriptores. */
    @Bean
    public FanoutExchange inmobiliariaFanoutExchange() {
        return new FanoutExchange(propiedades.getExchanges().getFanout(), true, false);
    }

    /** Direct: enruta el trabajo de generacion de PDF por routing key. */
    @Bean
    public DirectExchange inmobiliariaDirectExchange() {
        return new DirectExchange(propiedades.getExchanges().getDirect(), true, false);
    }

    /**
     * Topic: enruta los eventos del dominio "propiedades" por patron de
     * routing key. El publicador usa claves propiedades.eventos.creada /
     * .editar / .eliminar y el binding de la cola de auditoria usa el patron
     * propiedades.eventos.#, que calza con las tres.
     */
    @Bean
    public TopicExchange inmobiliariaTopicExchange() {
        return new TopicExchange(propiedades.getExchanges().getTopic(), true, false);
    }

    /** Dead Letter Exchange: recibe los mensajes fallidos y los enruta a su DLQ. */
    @Bean
    public DirectExchange inmobiliariaDlx() {
        return new DirectExchange(propiedades.getExchanges().getDlx(), true, false);
    }

    // ------------------------------------------------------------------
    // Colas de trabajo (con su DLX configurado)
    // ------------------------------------------------------------------

    @Bean
    public Queue colaCorreosNotificaciones() {
        return QueueBuilder.durable(propiedades.getQueues().getCorreosNotificaciones())
                .withArgument("x-dead-letter-exchange", propiedades.getExchanges().getDlx())
                .withArgument("x-dead-letter-routing-key", propiedades.getRoutingKeys().getCorreosNotificacionesDlq())
                .build();
    }

    @Bean
    public Queue colaPdfProcesamiento() {
        return QueueBuilder.durable(propiedades.getQueues().getPdfProcesamiento())
                .withArgument("x-dead-letter-exchange", propiedades.getExchanges().getDlx())
                .withArgument("x-dead-letter-routing-key", propiedades.getRoutingKeys().getPdfProcesamientoDlq())
                .build();
    }

    /**
     * Tercera cola de trabajo: bitacora de eventos del dominio propiedades
     * (crear/editar/eliminar), alimentada por el exchange topic.
     */
    @Bean
    public Queue colaPropiedadesAuditoria() {
        return QueueBuilder.durable(propiedades.getQueues().getPropiedadesAuditoria())
                .withArgument("x-dead-letter-exchange", propiedades.getExchanges().getDlx())
                .withArgument("x-dead-letter-routing-key", propiedades.getRoutingKeys().getPropiedadesAuditoriaDlq())
                .build();
    }

    // ------------------------------------------------------------------
    // Dead Letter Queues
    // ------------------------------------------------------------------

    @Bean
    public Queue colaCorreosNotificacionesDlq() {
        return QueueBuilder.durable(propiedades.getQueues().getCorreosNotificacionesDlq())
                .withArgument("x-message-ttl", propiedades.getAdmin().getDlqTtl())
                .build();
    }

    @Bean
    public Queue colaPdfProcesamientoDlq() {
        return QueueBuilder.durable(propiedades.getQueues().getPdfProcesamientoDlq())
                .withArgument("x-message-ttl", propiedades.getAdmin().getDlqTtl())
                .build();
    }

    @Bean
    public Queue colaPropiedadesAuditoriaDlq() {
        return QueueBuilder.durable(propiedades.getQueues().getPropiedadesAuditoriaDlq())
                .withArgument("x-message-ttl", propiedades.getAdmin().getDlqTtl())
                .build();
    }

    // ------------------------------------------------------------------
    // Bindings: exchanges de negocio -> colas de trabajo
    // ------------------------------------------------------------------

    @Bean
    public Binding bindingNotificaciones(FanoutExchange inmobiliariaFanoutExchange,
                                         Queue colaCorreosNotificaciones) {
        return BindingBuilder.bind(colaCorreosNotificaciones).to(inmobiliariaFanoutExchange);
    }

    @Bean
    public Binding bindingPdf(DirectExchange inmobiliariaDirectExchange,
                              Queue colaPdfProcesamiento) {
        return BindingBuilder.bind(colaPdfProcesamiento)
                .to(inmobiliariaDirectExchange)
                .with(propiedades.getRoutingKeys().getPdf());
    }

    /**
     * Binding del exchange topic: el patron propiedades.eventos.# calza con
     * propiedades.eventos.creada, .editar y .eliminar (wildcard multi-nivel),
     * dejando todas las acciones del dominio en la misma cola de auditoria.
     */
    @Bean
    public Binding bindingAuditoria(TopicExchange inmobiliariaTopicExchange,
                                     Queue colaPropiedadesAuditoria) {
        return BindingBuilder.bind(colaPropiedadesAuditoria)
                .to(inmobiliariaTopicExchange)
                .with(propiedades.getRoutingKeys().getPropiedadesEventosPatron());
    }

    // ------------------------------------------------------------------
    // Bindings: DLX -> DLQ
    // ------------------------------------------------------------------

    @Bean
    public Binding bindingNotificacionesDlq(DirectExchange inmobiliariaDlx,
                                            Queue colaCorreosNotificacionesDlq) {
        return BindingBuilder.bind(colaCorreosNotificacionesDlq)
                .to(inmobiliariaDlx)
                .with(propiedades.getRoutingKeys().getCorreosNotificacionesDlq());
    }

    @Bean
    public Binding bindingPdfDlq(DirectExchange inmobiliariaDlx,
                                 Queue colaPdfProcesamientoDlq) {
        return BindingBuilder.bind(colaPdfProcesamientoDlq)
                .to(inmobiliariaDlx)
                .with(propiedades.getRoutingKeys().getPdfProcesamientoDlq());
    }

    @Bean
    public Binding bindingAuditoriaDlq(DirectExchange inmobiliariaDlx,
                                        Queue colaPropiedadesAuditoriaDlq) {
        return BindingBuilder.bind(colaPropiedadesAuditoriaDlq)
                .to(inmobiliariaDlx)
                .with(propiedades.getRoutingKeys().getPropiedadesAuditoriaDlq());
    }

    /**
     * Serializa los mensajes como JSON en lugar de Java serializado. Spring Boot
     * lo inyecta tanto en el RabbitTemplate como en el contenedor de listeners.
     */
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
