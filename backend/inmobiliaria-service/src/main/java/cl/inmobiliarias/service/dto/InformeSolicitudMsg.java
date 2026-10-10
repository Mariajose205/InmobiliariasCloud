package cl.inmobiliarias.service.dto;

/**
 * Cuerpo del mensaje que viaja por RabbitMQ cuando un usuario pide un informe
 * PDF de propiedades.
 *
 * Se envia el mismo mensaje por los dos caminos del flujo:
 *   1. exchange fanout  -> cola.correos.notificaciones (aviso "en proceso")
 *   2. exchange direct  -> cola.pdf.procesamiento     (trabajo real)
 *
 * Nunca se envian entidades JPA por el broker; solo datos planos.
 *
 * @param solicitadoPorEmail correo del usuario que pidio el informe. Decide que
 *                           propiedades entran: ADMIN ve todas, CORREDOR solo
 *                           las que el mismo creo.
 * @param solicitadoPorOid   objectId (oid) del solicitante (respaldo del email).
 * @param rol                ADMIN o CORREDOR.
 * @param solicitadoEn       instante ISO-8601 de la solicitud.
 */
public record InformeSolicitudMsg(
        String solicitadoPorEmail,
        String solicitadoPorOid,
        String rol,
        String solicitadoEn
) {
}
