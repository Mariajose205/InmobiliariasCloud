package cl.inmobiliarias.wishlist.controller;

import cl.inmobiliarias.wishlist.security.IdentidadInterceptor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Perfil del cliente publico autenticado con Amazon Cognito.
 *
 * Es la ruta protegida minima para demostrar el flujo de T03/T05: el navegador
 * envia el access_token de Cognito como Bearer, API Gateway valida issuer y
 * audience con el autorizador `cognito-clientes-jwt`, y reenvia la identidad en
 * las cabeceras X-User*, incluido el rol del claim.
 *
 * GET /api/cliente/perfil
 */
@RestController
@RequestMapping("/api/cliente")
public class PerfilClienteController {

    @GetMapping("/perfil")
    public Map<String, Object> perfil(
            @RequestAttribute(value = IdentidadInterceptor.ATTR_USUARIO, required = false) String usuario,
            @RequestAttribute(value = IdentidadInterceptor.ATTR_NOMBRE, required = false) String nombre,
            @RequestAttribute(value = IdentidadInterceptor.ATTR_OID, required = false) String oid,
            @RequestAttribute(value = IdentidadInterceptor.ATTR_ROL, required = false) String rol) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", texto(usuario));
        body.put("nombre", texto(nombre));
        body.put("oid", texto(oid));
        body.put("rol", texto(rol));
        return body;
    }

    private String texto(String valor) {
        return valor == null ? "" : valor;
    }
}
