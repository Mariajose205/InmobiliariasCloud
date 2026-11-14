package cl.inmobiliarias.service.config.rabbitmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declaracion de exchanges, colas y bindings del flujo de informes.
 *
 * Se declara un bean por "caso de uso" para que el broker quede descrito de
 * forma explicita y versionada:
 *
 *   inmobiliaria.fanout  ->  cola.correos.notificaciones
 *   inmobiliaria.direct  ->  cola.pdf.procesamiento
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

    /**
     * Serializa los mensajes como JSON en lugar de Java serializado. Spring Boot
     * lo inyecta tanto en el RabbitTemplate como en el contenedor de listeners.
     */
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
