package cl.inmobiliarias.wishlist.service;

import cl.inmobiliarias.wishlist.model.AlertaComuna;
import cl.inmobiliarias.wishlist.model.Favorito;
import cl.inmobiliarias.wishlist.model.ResumenNovedades;
import cl.inmobiliarias.wishlist.repository.AlertaComunaRepository;
import cl.inmobiliarias.wishlist.repository.FavoritoRepository;
import cl.inmobiliarias.wishlist.repository.ResumenNovedadesRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Metricas reales del lado del cliente para el dashboard del administrador.
 *
 * Las cifras del catalogo (propiedades, precios, comunas) las entrega el
 * inmobiliaria-service; estas son las que solo conoce este microservicio:
 * cuantos favoritos hay, cuantos clientes los guardan, que propiedades y que
 * comunas generan mas interes, y cuantos resumenes de novedades se enviaron.
 *
 * El calculo se hace en memoria porque el volumen del laboratorio es chico
 * (unas pocas filas por cliente) y asi se evita una consulta por metrica. Si
 * el volumen creciera, cada uno de estos numeros pasa a ser un `count` o un
 * `group by` en el repositorio.
 */
@Service
public class EstadisticasService {

    private static final int TOP = 5;

    private final FavoritoRepository favoritoRepository;
    private final AlertaComunaRepository alertaRepository;
    private final ResumenNovedadesRepository resumenRepository;

    public EstadisticasService(FavoritoRepository favoritoRepository,
                               AlertaComunaRepository alertaRepository,
                               ResumenNovedadesRepository resumenRepository) {
        this.favoritoRepository = favoritoRepository;
        this.alertaRepository = alertaRepository;
        this.resumenRepository = resumenRepository;
    }

    /** Lee las tres tablas y devuelve el resumen que consume el dashboard. */
    public Map<String, Object> resumen() {
        return calcular(favoritoRepository.findAll(), alertaRepository.findAll(), resumenRepository.findAll());
    }

    /** Calculo puro: recibe las filas ya leidas (permite testear sin base). */
    Map<String, Object> calcular(List<Favorito> favoritos, List<AlertaComuna> alertas,
                                 List<ResumenNovedades> resumenes) {
        Map<String, Object> body = new LinkedHashMap<>();

        body.put("totalFavoritos", favoritos.size());
        body.put("clientesConFavoritos", contarClientes(favoritos, Favorito::getUsuarioEmail));
        body.put("totalAlertas", alertas.size());
        body.put("clientesConAlertas", contarClientes(alertas, AlertaComuna::getUsuarioEmail));
        body.put("totalResumenes", resumenes.size());
        body.put("resumenesEnviados",
                resumenes.stream().filter(ResumenNovedades::isCorreoEnviado).count());

        body.put("topPropiedadesGuardadas", topPropiedades(favoritos));
        body.put("topComunasFavoritos",
                topComunas(favoritos.stream().map(Favorito::getComuna).toList(), "guardados"));
        body.put("topComunasAlertas",
                topComunas(alertas.stream().map(AlertaComuna::getComuna).toList(), "seguidores"));

        return body;
    }

    private <T> long contarClientes(List<T> filas, Function<T, String> email) {
        return filas.stream()
                .map(email)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(correo -> !correo.isEmpty())
                .map(String::toLowerCase)
                .distinct()
                .count();
    }

    private List<Map<String, Object>> topPropiedades(List<Favorito> favoritos) {
        return favoritos.stream()
                .collect(Collectors.groupingBy(Favorito::getPropiedadId))
                .entrySet().stream()
                .map(entry -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("propiedadId", entry.getKey());
                    item.put("titulo", entry.getValue().get(0).getTitulo());
                    item.put("guardados", entry.getValue().size());
                    return item;
                })
                .sorted(Comparator.comparingInt((Map<String, Object> m) -> ((Number) m.get("guardados")).intValue())
                        .reversed())
                .limit(TOP)
                .toList();
    }

    private List<Map<String, Object>> topComunas(List<String> comunas, String etiqueta) {
        return comunas.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(comuna -> !comuna.isEmpty())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .map(entry -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("comuna", entry.getKey());
                    item.put(etiqueta, entry.getValue());
                    return item;
                })
                .sorted(Comparator.comparingLong((Map<String, Object> m) -> ((Number) m.get(etiqueta)).longValue())
                        .reversed())
                .limit(TOP)
                .toList();
    }
}
