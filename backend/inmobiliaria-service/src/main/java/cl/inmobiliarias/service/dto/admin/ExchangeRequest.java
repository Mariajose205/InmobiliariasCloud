package cl.inmobiliarias.service.dto.admin;

/**
 * Cuerpo de POST /api/admin/rabbitmq/exchanges.
 *
 * @param name       nombre del exchange (obligatorio).
 * @param type       direct | fanout | topic (por defecto direct).
 * @param durable    sobrevive reinicios del broker (por defecto true).
 * @param autoDelete se borra al quedar sin bindings (por defecto false).
 */
public record ExchangeRequest(
        String name,
        String type,
        Boolean durable,
        Boolean autoDelete
) {
    public String typeOrDefault() {
        return type == null || type.isBlank() ? "direct" : type;
    }

    public boolean durableOrDefault() {
        return durable == null || durable;
    }

    public boolean autoDeleteOrDefault() {
        return autoDelete != null && autoDelete;
    }
}
