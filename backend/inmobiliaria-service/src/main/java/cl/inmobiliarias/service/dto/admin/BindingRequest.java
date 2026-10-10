package cl.inmobiliarias.service.dto.admin;

/**
 * Cuerpo de POST /api/admin/rabbitmq/bindings.
 *
 * @param queue        cola destino del binding (obligatorio).
 * @param exchange     exchange origen (obligatorio).
 * @param routingKey   routing key (ignorada si el exchange es fanout).
 * @param exchangeType direct | fanout | topic.
 */
public record BindingRequest(
        String queue,
        String exchange,
        String routingKey,
        String exchangeType
) {
    public String exchangeTypeOrDefault() {
        return exchangeType == null || exchangeType.isBlank() ? "direct" : exchangeType;
    }
}
