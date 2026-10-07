package cl.inmobiliarias.service.controller;

import cl.inmobiliarias.service.security.IdentidadInterceptor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Identidad del usuario autenticado.
 *
 * Antes lo servia el `MeController` del api-gateway de Spring Boot. Ahora lo
 * sirve el microservicio, porque es quien resuelve el rol a partir de la
 * identidad que API Gateway valido e inyecto.
 *
 * La forma de la respuesta se mantiene igual para que `obtenerUsuarioActual()`
 * del frontend no necesite cambios.
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    @GetMapping
    public Map<String, Object> me(
            @RequestAttribute(value = IdentidadInterceptor.ATTR_USUARIO, required = false) String usuario,
            @RequestAttribute(value = IdentidadInterceptor.ATTR_NOMBRE, required = false) String nombre,
            @RequestAttribute(value = IdentidadInterceptor.ATTR_OID, required = false) String oid,
            @RequestAttribute(value = IdentidadInterceptor.ATTR_ROL, required = false) String rol) {

        String rolEfectivo = rol == null || rol.isBlank() ? IdentidadInterceptor.ROL_PUBLIC : rol;

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("email", usuario);
        respuesta.put("name", nombre);
        respuesta.put("objectId", oid);
        respuesta.put("rol", rolEfectivo);
        respuesta.put("isAdmin", IdentidadInterceptor.ROL_ADMIN.equals(rolEfectivo));
        respuesta.put("isCorredor", IdentidadInterceptor.ROL_CORREDOR.equals(rolEfectivo));
        return respuesta;
    }
}
