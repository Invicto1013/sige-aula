package com.unev.aula;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TemaTest {
    @Test void leeElTemaDeLaCookie() {
        assertEquals("oscuro", Tema.modo("a=1; aula_tema=oscuro; b=2"));
        assertEquals(Tema.PREDETERMINADO, Tema.modo("aula_tema=hackeado"));
        assertEquals(Tema.PREDETERMINADO, Tema.modo(null));
    }
    @Test void atributoSoloSiCambiaElComportamiento() {
        assertEquals("", Tema.atributo(Tema.PREDETERMINADO));
        assertEquals(" data-tema='oscuro'", Tema.atributo("oscuro"));
        assertEquals("", Tema.atributo("<script>"));
    }
    @Test void selectorMarcaElActivoYVuelveALocal() {
        String h = Tema.selector("oscuro", "/x");
        assertTrue(h.contains("modo=oscuro") && h.contains("aria-current='true'") && h.contains("volver=%2Fx"));
    }
}
