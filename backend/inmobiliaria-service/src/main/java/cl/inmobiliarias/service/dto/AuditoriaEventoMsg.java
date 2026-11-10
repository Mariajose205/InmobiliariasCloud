package cl.inmobiliarias.service.dto;

/**
 * Cuerpo del mensaje que viaja por RabbitMQ cuando ocurre un evento del
 * dominio "propiedades" (crear, editar o eliminar).
 *
 * Se publica en el exchange topic `inmobiliaria.topic` con la routing key
 * propiedades.eventos.creada|editar|eliminar y lo consume la cola
 * `cola.propiedades.auditoria` (binding con el patron propiedades.eventos.#).
 *
 * Nunca se envian entidades JPA por el broker; solo datos planos.
 *
 * @param accion       que ocurrio: CREAR, EDITAR o ELIMINAR.
 * @param propiedadId  identificador de la propiedad afectada.
 * @param titulo       titulo de la propiedad (para la bitacora legible).
 * @param comuna       comuna canonica (Quillota, Vina del Mar, ...) o "OTROS".
 * @param usuario      correo del autor del cambio.
 * @param rol          rol resuelto del autor (ADMIN o CORREDOR).
 * @param instantIso   instante ISO-8601 en que se registro el evento.
 */
public record AuditoriaEventoMsg(
        String accion,
        Long propiedadId,
        String titulo,
        String comuna,
        String usuario,
        String rol,
        String instantIso
) {
}
