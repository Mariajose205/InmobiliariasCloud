package cl.inmobiliarias.service.controller;

import cl.inmobiliarias.service.dto.InformeSolicitudMsg;
import cl.inmobiliarias.service.security.IdentidadInterceptor;
import cl.inmobiliarias.service.service.messaging.InformePublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Punto de entrada del flujo asincrono.
 *
 * El frontend pide el informe y este controlador solo publica el mensaje en
 * RabbitMQ; la generacion del PDF ocurre en background. Devuelve 202 Accepted
 * porque el trabajo todavia no termino.
 */
@RestController
@RequestMapping("/api/informes")
public class InformeController {

    private final InformePublisher publisher;

    public InformeController(InformePublisher publisher) {
        this.publisher = publisher;
    }

    @PostMapping("/propiedades/pdf")
    public ResponseEntity<Map<String, Object>> solicitarInforme(
            @RequestAttribute(value = IdentidadInterceptor.ATTR_USUARIO, required = false) String email,
            @RequestAttribute(value = IdentidadInterceptor.ATTR_OID, required = false) String oid,
            @RequestAttribute(value = IdentidadInterceptor.ATTR_ROL, required = false) String rol) {

        String rolEfectivo = rol == null ? "" : rol;
        if (!IdentidadInterceptor.ROL_ADMIN.equals(rolEfectivo)
                && !IdentidadInterceptor.ROL_CORREDOR.equals(rolEfectivo)) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "Solo el administrador o el corredor pueden solicitar el informe de propiedades.");
            return ResponseEntity.status(403).body(error);
        }

        InformeSolicitudMsg mensaje = new InformeSolicitudMsg(
                email == null || email.isBlank() ? "desconocido" : email,
                oid == null ? "" : oid,
                rolEfectivo,
                Instant.now().toString());

        publisher.publicarSolicitud(mensaje);

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("status", "solicitud_enviada");
        respuesta.put("rol", rolEfectivo);
        respuesta.put("mensaje", IdentidadInterceptor.ROL_ADMIN.equals(rolEfectivo)
                ? "Se generara el informe con TODAS las propiedades y se enviara por correo."
                : "Se generara el informe con tus propiedades y se enviara por correo.");
        return ResponseEntity.accepted().body(respuesta);
    }
}
