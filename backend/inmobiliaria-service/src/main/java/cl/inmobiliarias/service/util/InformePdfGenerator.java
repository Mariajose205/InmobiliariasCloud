package cl.inmobiliarias.service.util;

import cl.inmobiliarias.service.model.Propiedad;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;

/**
 * Genera el informe PDF de propiedades con OpenPDF. Es una clase puramente
 * utilitaria (sin estado) para que el consumidor no mezcle logica de negocio
 * con la maquetacion del documento.
 */
public final class InformePdfGenerator {

    private static final Logger log = LoggerFactory.getLogger(InformePdfGenerator.class);

    private InformePdfGenerator() {
    }

    /**
     * @param titulo      encabezado del informe (varia segun el rol).
     * @param solicitante correo del usuario que lo pidio.
     * @param propiedades filas a incluir. El consumidor decide si son todas
     *                    (ADMIN) o solo las propias (CORREDOR).
     */
    public static byte[] generar(String titulo, String solicitante, List<Propiedad> propiedades) {
        Document documento = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(documento, salida);
            documento.open();

            Font tituloFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Font subtituloFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.DARK_GRAY);
            Font cabeceraFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
            Font celdaFont = FontFactory.getFont(FontFactory.HELVETICA, 8);

            Paragraph encabezado = new Paragraph(titulo, tituloFont);
            encabezado.setAlignment(Element.ALIGN_CENTER);
            documento.add(encabezado);

            Paragraph meta = new Paragraph(
                    "Solicitado por: " + solicitante + "\n"
                            + "Generado: " + Instant.now() + "\n"
                            + "Total de propiedades: " + propiedades.size(), subtituloFont);
            meta.setSpacingAfter(12f);
            documento.add(meta);

            PdfPTable tabla = new PdfPTable(new float[]{0.6f, 3f, 1.6f, 1.6f, 1.5f, 3f, 1f, 1f, 1.4f});
            tabla.setWidthPercentage(100);

            String[] cabeceras = {"ID", "Titulo", "Categoria", "Operacion", "Precio",
                    "Ubicacion", "Piezas", "Banos", "Creado por"};
            for (String cabecera : cabeceras) {
                PdfPCell celda = new PdfPCell(new Phrase(cabecera, cabeceraFont));
                celda.setBackgroundColor(new Color(41, 65, 114));
                celda.setHorizontalAlignment(Element.ALIGN_CENTER);
                celda.setPadding(4f);
                tabla.addCell(celda);
            }

            for (Propiedad p : propiedades) {
                agregar(tabla, p.getId() == null ? "" : String.valueOf(p.getId()), celdaFont);
                agregar(tabla, texto(p.getTitulo()), celdaFont);
                agregar(tabla, texto(p.getCategoria()), celdaFont);
                agregar(tabla, texto(p.getTipoOperacion()), celdaFont);
                agregar(tabla, precio(p), celdaFont);
                agregar(tabla, texto(p.getUbicacion()), celdaFont);
                agregar(tabla, numero(p.getPiezas()), celdaFont);
                agregar(tabla, numero(p.getBanos()), celdaFont);
                agregar(tabla, texto(p.getCreadoPorEmail()), celdaFont);
            }

            documento.add(tabla);
            documento.close();
            return salida.toByteArray();
        } catch (DocumentException e) {
            log.error("[PDF] Error generando el informe: {}", e.getMessage(), e);
            throw new IllegalStateException("No se pudo generar el informe PDF", e);
        } finally {
            if (documento.isOpen()) {
                documento.close();
            }
        }
    }

    private static void agregar(PdfPTable tabla, String valor, Font font) {
        PdfPCell celda = new PdfPCell(new Phrase(valor, font));
        celda.setPadding(3f);
        tabla.addCell(celda);
    }

    private static String texto(String valor) {
        return valor == null ? "" : valor;
    }

    private static String numero(Integer valor) {
        return valor == null ? "0" : String.valueOf(valor);
    }

    private static String precio(Propiedad p) {
        if (p.getPrecio() == null) {
            return "";
        }
        return p.getPrecio() + " " + texto(p.getTipoPrecio());
    }
}
