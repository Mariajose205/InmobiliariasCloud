package cl.inmobiliarias.service.service.messaging;

import org.slf4j.Logger;

/**
 * Utilidad para reintentar una accion con backoff exponencial.
 *
 * Si la accion falla siempre, al agotar los intentos se relanza la ultima
 * excepcion para que el llamador decida (tipicamente NACK a la DLQ).
 */
public final class Reintentos {

    @FunctionalInterface
    public interface Accion {
        void ejecutar() throws Exception;
    }

    private Reintentos() {
    }

    public static void conReintentos(String dominio, int maxIntentos, long intervaloInicialMs,
                                     double multiplicador, Logger log, Accion accion) {
        long intervalo = intervaloInicialMs;
        Exception ultimoError = null;
        for (int intento = 1; intento <= maxIntentos; intento++) {
            try {
                accion.ejecutar();
                return;
            } catch (Exception ex) {
                ultimoError = ex;
                if (intento >= maxIntentos) {
                    log.error("[{}] Intento {}/{} fallo (intentos agotados). Causa: {}",
                            dominio, intento, maxIntentos, ex.getMessage());
                    break;
                }
                log.warn("[{}] Intento {}/{} fallo. Reintento en {} ms. Causa: {}",
                        dominio, intento, maxIntentos, intervalo, ex.getMessage());
                dormir(dominio, intervalo);
                intervalo = (long) (intervalo * multiplicador);
            }
        }
        throw new IllegalStateException("Fallo definitivo tras " + maxIntentos + " intentos en " + dominio, ultimoError);
    }

    private static void dormir(String dominio, long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Reintentos interrumpidos en " + dominio, ex);
        }
    }
}