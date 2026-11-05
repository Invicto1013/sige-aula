package com.unev.aula;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class VisionTest {
    @Test void leeModoDeLaCookie() {
        assertEquals("daltonismo", Vision.modo("a=1; aula_vision=daltonismo; b=2"));
        assertEquals("normal", Vision.modo("aula_vision=hackeado"));
        assertEquals("normal", Vision.modo(null));
    }
    @Test void soloVuelveARutasLocales() {
        assertEquals("/materias", Vision.volverSeguro("/materias"));
        assertEquals("/", Vision.volverSeguro("//evil.com"));
        assertEquals("/", Vision.volverSeguro("https://evil.com"));
        assertEquals("/", Vision.volverSeguro("/a\r\nSet-Cookie: x=1"));
        assertEquals("/", Vision.volverSeguro(null));
    }
    @Test void atributoYSelector() {
        assertEquals("", Vision.atributo("normal"));
        assertEquals(" data-vision='contraste'", Vision.atributo("contraste"));
        assertEquals("", Vision.atributo("<script>"));
        String h = Vision.selector("daltonismo", "/x");
        assertTrue(h.contains("modo=daltonismo") && h.contains("aria-current='true'") && h.contains("volver=%2Fx"));
    }
}
