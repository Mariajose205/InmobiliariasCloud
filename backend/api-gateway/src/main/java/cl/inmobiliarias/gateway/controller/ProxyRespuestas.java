package cl.inmobiliarias.gateway.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Locale;
import java.util.Set;

/**
 * Utilidades comunes para los proxies del gateway.
 *
 * Cabeceras "hop-by-hop" (RFC 7230 §6.1) que un proxy NUNCA debe reenviar:
 * si se copiaran tal cual (p.ej. Transfer-Encoding: chunked del microservicio),
 * Tomcat anadira su propia cabecera de enmarcado al responder y el cliente
 * (nginx) veria dos Transfer-Encoding -> 502 "duplicate header line".
 */
public final class ProxyRespuestas {

    private static final Set<String> HOP_BY_HOP = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailer", "transfer-encoding", "upgrade");

    private ProxyRespuestas() {
    }

    /**
     * Envia al cliente la respuesta del microservicio SIN las cabeceras
     * hop-by-hop. El cuerpo y el status se conservan tal cual.
     */
    public static ResponseEntity<?> responder(ResponseEntity<?> respuesta) {
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
}