package com.unev.aula;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TiposArchivoTest {

    private static byte[] b(int... v) {
        byte[] r = new byte[v.length];
        for (int i = 0; i < v.length; i++) {
            r[i] = (byte) v[i];
        }
        return r;
    }

    @Test
    void aceptaArchivosValidos() {
        assertTrue(TiposArchivo.permitido("tarea.pdf", "%PDF-1.7 ...".getBytes(StandardCharsets.ISO_8859_1)));
        assertTrue(TiposArchivo.permitido("FOTO.JPG", b(0xFF, 0xD8, 0xFF, 0xE0, 1)));
        assertTrue(TiposArchivo.permitido("foto.png", b(0x89, 'P', 'N', 'G', 13, 10)));
        assertTrue(TiposArchivo.permitido("informe.docx", b('P', 'K', 3, 4, 0)));
        assertTrue(TiposArchivo.permitido("viejo.doc", b(0xD0, 0xCF, 0x11, 0xE0, 0)));
        assertTrue(TiposArchivo.permitido("notas.txt", "Hola, ñandú\r\nlínea 2".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rechazaEjecutablesYFormatosNoPermitidos() {
        assertFalse(TiposArchivo.permitido("virus.exe", b('M', 'Z', 0x90, 0)));
        assertFalse(TiposArchivo.permitido("script.js", "alert(1)".getBytes(StandardCharsets.UTF_8)));
        assertFalse(TiposArchivo.permitido("pagina.html", "<script>".getBytes(StandardCharsets.UTF_8)));
        assertFalse(TiposArchivo.permitido("sin_extension", b('%', 'P', 'D', 'F', '-')));
        assertFalse(TiposArchivo.permitido("archivo.", b('%', 'P', 'D', 'F', '-')));
    }

    @Test
    void rechazaContenidoQueNoCoincideConLaExtension() {
        assertFalse(TiposArchivo.permitido("falso.pdf", b('M', 'Z', 0x90, 0, 3)));
        assertFalse(TiposArchivo.permitido("falso.png", "%PDF-1.4".getBytes(StandardCharsets.ISO_8859_1)));
        assertFalse(TiposArchivo.permitido("binario.txt", b('a', 0, 'b')));
    }

    @Test
    void rechazaVaciosYNombresInvalidos() {
        assertFalse(TiposArchivo.permitido("a.pdf", new byte[0]));
        assertFalse(TiposArchivo.permitido("a.pdf", null));
        assertFalse(TiposArchivo.permitido(null, b('%', 'P', 'D', 'F', '-')));
        assertFalse(TiposArchivo.permitido("x".repeat(250) + ".pdf", b('%', 'P', 'D', 'F', '-')));
    }

    @Test
    void usaSoloLaUltimaExtension() {
        assertEquals("pdf", TiposArchivo.extension("a.b.PDF"));
        assertFalse(TiposArchivo.permitido("foto.png.exe", b(0x89, 'P', 'N', 'G')));
    }
}
