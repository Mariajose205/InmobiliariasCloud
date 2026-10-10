package cl.inmobiliarias.service.dto.admin;

/**
 * Cuerpo de POST /api/admin/rabbitmq/queues.
 *
 * @param name                   nombre de la cola (obligatorio).
 * @param durable                sobrevive reinicios del broker (por defecto true).
 * @param exclusive              solo accesible por la conexion que la creo.
 * @param autoDelete             se borra al quedar sin consumidores.
 * @param deadLetterExchange     exchange de dead-letter (opcional).
 * @param deadLetterRoutingKey   routing key de dead-letter (opcional).
 */
public record QueueRequest(
        String name,
        Boolean durable,
        Boolean exclusive,
        Boolean autoDelete,
        String deadLetterExchange,
        String deadLetterRoutingKey
) {
    public boolean durableOrDefault() {
        return durable == null || durable;
    }

    public boolean exclusiveOrDefault() {
        return exclusive != null && exclusive;
    }

    public boolean autoDeleteOrDefault() {
        return autoDelete != null && autoDelete;
    }
}
