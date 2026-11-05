package cl.inmobiliarias.wishlist.controller;

import cl.inmobiliarias.wishlist.service.WishlistService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Catalogo publico de comunas soportadas.
 * GET /api/wishlist/comunas puede pedirse sin sesion iniciada.
 */
@RestController
@RequestMapping("/api/wishlist/comunas")
public class ComunaController {

    private final WishlistService service;

    public ComunaController(WishlistService service) {
        this.service = service;
    }

    @GetMapping
    public List<String> listar() {
        return service.listarComunas();
    }
}
