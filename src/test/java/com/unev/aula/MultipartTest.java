package com.unev.aula;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MultipartTest {
    static final String B = "----limite123";

    static byte[] cuerpo(String... partes) {
        StringBuilder sb = new StringBuilder();
        for (String p : partes) sb.append("--").append(B).append("\r\n").append(p).append("\r\n");
        sb.append("--").append(B).append("--\r\n");
        return sb.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    static final String TIPO = "multipart/form-data; boundary=" + B;

    @Test void leeCamposYArchivo() throws IOException {
        byte[] c = cuerpo("Content-Disposition: form-data; name=\"titulo\"\r\n\r\nHola",
                "Content-Disposition: form-data; name=\"archivo\"; filename=\"nota.txt\"\r\nContent-Type: text/plain\r\n\r\ncontenido");
        var f = Multipart.analizar(c, TIPO, 1000);
        assertEquals("Hola", f.campo("titulo"));
        assertEquals(1, f.archivos("archivo").size());
        assertEquals("nota.txt", f.archivos("archivo").get(0).nombre());
        assertEquals("contenido", new String(f.archivos("archivo").get(0).datos(), StandardCharsets.UTF_8));
    }

    @Test void variosArchivosEnElMismoCampo() throws IOException {
        byte[] c = cuerpo("Content-Disposition: form-data; name=\"archivo\"; filename=\"a.txt\"\r\n\r\nA",
                "Content-Disposition: form-data; name=\"archivo\"; filename=\"b.txt\"\r\n\r\nB");
        assertEquals(2, Multipart.analizar(c, TIPO, 1000).archivos("archivo").size());
    }

    @Test void archivoVacioSeIgnora() throws IOException {
        byte[] c = cuerpo("Content-Disposition: form-data; name=\"archivo\"; filename=\"\"\r\nContent-Type: application/octet-stream\r\n\r\n");
        assertTrue(Multipart.analizar(c, TIPO, 1000).archivos("archivo").isEmpty());
    }

    @Test void archivoDemasiadoGrandeLanzaExcepcion() {
        byte[] c = cuerpo("Content-Disposition: form-data; name=\"archivo\"; filename=\"a.bin\"\r\n\r\n" + "x".repeat(50));
        assertThrows(Multipart.ArchivoGrandeException.class, () -> Multipart.analizar(c, TIPO, 10));
    }

    @Test void nombreCampoNoSeConfundeConFilename() throws IOException {
        byte[] c = cuerpo("Content-Disposition: form-data; name=\"x\"; filename=\"name=\\\"y\\\"\"\r\n\r\nz");
        assertEquals(1, Multipart.analizar(c, TIPO, 100).archivos("x").size());
    }

    @Test void sinLimiteEsInvalido() {
        assertThrows(IOException.class, () -> Multipart.analizar(new byte[0], "multipart/form-data", 10));
    }

    @Test void partesIncompletasEsInvalido() {
        byte[] c = ("--" + B + "\r\nContent-Disposition: form-data; name=\"a\"\r\n\r\nsin cierre").getBytes(StandardCharsets.ISO_8859_1);
        assertThrows(IOException.class, () -> Multipart.analizar(c, TIPO, 100));
    }

    @Test void nombreSeguroQuitaRutasYControl() {
        assertEquals("secreto.txt", Multipart.nombreSeguro("..\\..\\windows/secreto.txt"));
        assertEquals("archivo", Multipart.nombreSeguro(".."));
        assertEquals("archivo", Multipart.nombreSeguro("   "));
        assertEquals("a_b.txt", Multipart.nombreSeguro("a\nb.txt"));
        assertTrue(Multipart.nombreSeguro("x".repeat(400)).length() <= 150);
    }

    @Test void camposRepetidosConservanTodosLosValores() throws IOException {
        byte[] c = cuerpo("Content-Disposition: form-data; name=\"quitar\"\r\n\r\n3", "Content-Disposition: form-data; name=\"quitar\"\r\n\r\n7");
        var f = Multipart.analizar(c, TIPO, 100);
        assertEquals(java.util.List.of("3", "7"), f.valores("quitar"));
        var u = Multipart.urlencoded("quitar=1&quitar=2&x=3");
        assertEquals(java.util.List.of("1", "2"), u.valores("quitar"));
        assertTrue(u.valores("no-existe").isEmpty());
    }

    @Test void urlencodedDecodifica() {
        var f = Multipart.urlencoded("a=1&b=hola+mundo&c=%C3%B1&_csrf=t");
        assertEquals("hola mundo", f.campo("b"));
        assertEquals("ñ", f.campo("c"));
        assertEquals("", f.campo("no-existe"));
    }
}
