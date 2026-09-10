package cl.inmobiliarias.gateway.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.Locale;
import java.util.Set;

import cl.inmobiliarias.gateway.security.PropiedadAuthInterceptor;

/**
 * Proxy del API Gateway: reenvia las peticiones del frontend hacia el
 * microservicio `inmobiliaria-service`, que NO es publico (solo loopback:
 * 127.0.0.1:8081). El gateway es la unica puerta de entrada.
 *
 * El interceptor PropiedadAuthInterceptor se ejecuta antes de estos metodos,
 * garantizando que solo un ADMIN puede crear/actualizar/eliminar, y guarda
 * quien realizo la accion (x-user, x-user-rol). Esos datos se reenvian al
 * microservicio para que registre la auditoria (quien y cuando).
 */
@RestController
@RequestMapping("/api/propiedades")
public class PropiedadProxyController {

    private final RestTemplate restTemplate;

    @Value("${gateway.inmobiliaria-service-url}")
    private String serviceBaseUrl;

    public PropiedadProxyController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @GetMapping
    public ResponseEntity<?> listar(@RequestParam(required = false) String categoria) {
        String url = serviceBaseUrl + "/api/propiedades";
        if (categoria != null && !categoria.isBlank()) {
            url += "?categoria=" + categoria;
        }
        return responder(restTemplate.exchange(url, HttpMethod.GET, null, Object.class));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtener(@PathVariable Long id) {
        return responder(restTemplate.exchange(serviceBaseUrl + "/api/propiedades/" + id,
                HttpMethod.GET, null, Object.class));
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody Object body, HttpServletRequest httpRequest) {
        HttpHeaders headers = headersConUsuario(httpRequest);
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        return responder(restTemplate.exchange(serviceBaseUrl + "/api/propiedades",
                HttpMethod.POST, entity, Object.class));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable Long id, @RequestBody Object body,
                                        HttpServletRequest httpRequest) {
        HttpHeaders headers = headersConUsuario(httpRequest);
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        return responder(restTemplate.exchange(serviceBaseUrl + "/api/propiedades/" + id,
                HttpMethod.PUT, entity, Object.class));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Long id, HttpServletRequest httpRequest) {
        HttpHeaders headers = headersConUsuario(httpRequest);
        HttpEntity<Object> entity = new HttpEntity<>(headers);
        return responder(restTemplate.exchange(serviceBaseUrl + "/api/propiedades/" + id,
                HttpMethod.DELETE, entity, Object.class));
    }

    /**
     * Cabeceras "hop-by-hop" (RFC 7230 §6.1) que un proxy NUNCA debe reenviar:
     * si se copiaran tal cual (p.ej. Transfer-Encoding: chunked del microservicio),
     * Tomcat anadiria su propia cabecera de enmarcado al responder y el cliente
     * (ngin x) veria dos Transfer-Encoding -> 502 "duplicate header line".
     */
    private static final Set<String> HOP_BY_HOP = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade");

    /**
     * Envia al cliente la respuesta del microservicio SIN las cabeceras
     * hop-by-hop. El cuerpo y el status se conservan tal cual.
     */
    private ResponseEntity<?> responder(ResponseEntity<?> respuesta) {
        HttpHeaders limpias = new HttpHeaders();
        respuesta.getHeaders().forEach((nombre, valores) -> {
            if (!HOP_BY_HOP.contains(nombre.toLowerCase(Locale.ROOT))) {
                limpias.put(nombre, valores);
            }
        });
        HttpStatus status = HttpStatus.resolve(respuesta.getStatusCode().value());
        return status != null
                ? ResponseEntity.status(status).headers(limpias).body(respuesta.getBody())
                : ResponseEntity.status(respuesta.getStatusCode().value()).headers(limpias).body(respuesta.getBody());
    }

    /**
     * Reenvia la identidad del usuario autenticado (guardada por el interceptor)
     * al microservicio via headers, para que quede registrada en la auditoria.
     */
    private HttpHeaders headersConUsuario(HttpServletRequest httpRequest) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        Object usuario = httpRequest.getAttribute(PropiedadAuthInterceptor.ATTR_USER);
        Object rol = httpRequest.getAttribute(PropiedadAuthInterceptor.ATTR_USER_ROL);
        if (usuario instanceof String u && !u.isBlank()) {
            headers.set("X-User", u);
        }
        if (rol instanceof String r && !r.isBlank()) {
            headers.set("X-User-Rol", r);
        }
        return headers;
    }
}