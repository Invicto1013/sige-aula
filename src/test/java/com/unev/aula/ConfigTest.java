package com.unev.aula;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigTest {
    static Map<String, String> base() {
        Map<String, String> m = new HashMap<>();
        m.put("AULA_DB_URL", "jdbc:mysql://host:3306/db");
        m.put("AULA_SSO_SECRET", "x".repeat(40));
        m.put("AULA_SIGE_URL", "https://sige.example.com/");
        return m;
    }

    @Test void valoresPorDefectoYBarraFinalQuitada() {
        Config c = Config.desdeEntorno(base());
        assertEquals(8080, c.puerto());
        assertEquals("https://sige.example.com", c.urlSige());
        assertTrue(c.cookieSegura());
        assertEquals(10L * 1024 * 1024, c.maxArchivoBytes());
        assertEquals(4, c.tamanoPool());
        assertEquals(25L * 1024 * 1024, c.maxSolicitudBytes());
    }

    @Test void leeValoresDelEntorno() {
        var m = base();
        m.put("PORT", "8000");
        m.put("AULA_SECURE_COOKIE", "false");
        m.put("AULA_MAX_UPLOAD_MB", "5");
        m.put("AULA_DB_POOL_SIZE", "99");
        Config c = Config.desdeEntorno(m);
        assertEquals(8000, c.puerto());
        assertFalse(c.cookieSegura());
        assertEquals(5L * 1024 * 1024, c.maxArchivoBytes());
        assertEquals(10, c.tamanoPool());
    }

    @Test void faltaVariableObligatoria() {
        var m = base();
        m.remove("AULA_DB_URL");
        assertThrows(IllegalStateException.class, () -> Config.desdeEntorno(m));
    }

    @Test void secretoCortoSeRechaza() {
        var m = base();
        m.put("AULA_SSO_SECRET", "corto");
        assertThrows(IllegalStateException.class, () -> Config.desdeEntorno(m));
    }

    @Test void puertoInvalidoUsaElPorDefecto() {
        var m = base();
        m.put("PORT", "abc");
        assertEquals(8080, Config.desdeEntorno(m).puerto());
    }

    @Test void zonaPorDefectoEsSantoDomingo() {
        Config c = Config.desdeEntorno(base());
        assertEquals("America/Santo_Domingo", c.zona().getId());
        assertEquals("-04:00", c.desplazamientoSql());
    }

    @Test void zonaConfigurableYUtcSeFormateaParaMySql() {
        var m = base();
        m.put("AULA_ZONA", "UTC");
        assertEquals("+00:00", Config.desdeEntorno(m).desplazamientoSql());
        m.put("AULA_ZONA", "Europe/Madrid");
        assertTrue(Config.desdeEntorno(m).desplazamientoSql().matches("[+-]\\d\\d:\\d\\d"));
    }

    @Test void zonaInvalidaSeRechaza() {
        var m = base();
        m.put("AULA_ZONA", "Marte/Olimpo");
        assertThrows(IllegalStateException.class, () -> Config.desdeEntorno(m));
    }
}
