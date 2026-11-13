package cl.inmobiliarias.wishlist;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Punto de entrada del microservicio de favoritos (wishlist).
 *
 * Expone la lista de favoritos por usuario, las alertas por comuna que
 * suscriben a los usuarios a las nuevas propiedades y la administracion
 * de topics/grupos Kafka. El acceso publico se realiza unicamente a
 * traves del API Gateway de AWS, que valida el token de Azure Entra ID
 * y reenvia la identidad en las cabeceras X-User, X-User-Oid y X-User-Name.
 */
@SpringBootApplication
@EnableScheduling
public class WishlistServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WishlistServiceApplication.class, args);
    }
}
