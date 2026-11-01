package com.unev.aula;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BusquedaTest {
    @Test void ignoraAcentosYMayusculas() {
        assertEquals("matematica", Busqueda.normalizar("  Matemática "));
        assertTrue(Busqueda.coincide("JOSÉ", "jose perez"));
    }

    @Test void busquedaVaciaCoincide() {
        assertTrue(Busqueda.coincide("", "x"));
        assertTrue(Busqueda.coincide(null, (String) null));
    }

    @Test void todasLasPalabrasDebenAparecer() {
        assertTrue(Busqueda.coincide("ana perez", "Ana", "Pérez"));
        assertFalse(Busqueda.coincide("ana gomez", "Ana", "Pérez"));
    }

    @Test void camposNulosNoRompen() {
        assertTrue(Busqueda.coincide("ana", null, "Ana"));
        assertFalse(Busqueda.coincide("ana", (String) null));
    }
}
