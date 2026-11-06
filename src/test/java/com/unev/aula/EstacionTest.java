package com.unev.aula;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class EstacionTest {
    @Test void laEstacionCambiaSieteDiasAntesDelInicio() {
        assertEquals("otono", Estacion.deFecha(LocalDate.of(2026, 11, 6)));
        assertEquals("otono", Estacion.deFecha(LocalDate.of(2026, 9, 15)));
        assertEquals("verano", Estacion.deFecha(LocalDate.of(2026, 9, 14)));
        assertEquals("invierno", Estacion.deFecha(LocalDate.of(2026, 12, 14)));
        assertEquals("otono", Estacion.deFecha(LocalDate.of(2026, 12, 13)));
        assertEquals("invierno", Estacion.deFecha(LocalDate.of(2027, 1, 20)));
        assertEquals("invierno", Estacion.deFecha(LocalDate.of(2027, 3, 12)));
        assertEquals("primavera", Estacion.deFecha(LocalDate.of(2027, 3, 13)));
        assertEquals("verano", Estacion.deFecha(LocalDate.of(2027, 6, 14)));
        assertEquals("primavera", Estacion.deFecha(LocalDate.of(2027, 6, 13)));
    }
    @Test void laPreferenciaManda() {
        LocalDate hoy = LocalDate.of(2026, 11, 6);
        assertEquals("otono", Estacion.efectiva("auto", hoy));
        assertEquals("invierno", Estacion.efectiva("invierno", hoy));
        assertEquals("", Estacion.efectiva("ninguna", hoy));
        assertEquals("otono", Estacion.efectiva("<x>", hoy));
    }
    @Test void leeLaCookieYGeneraHtmlSeguro() {
        assertEquals("verano", Estacion.modo("a=1; aula_estacion=verano"));
        assertEquals("auto", Estacion.modo("aula_estacion=hack"));
        assertEquals("", Estacion.atributo(""));
        assertEquals(" data-estacion='otono'", Estacion.atributo("otono"));
        assertEquals("", Estacion.capa(""));
        assertTrue(Estacion.capa("otono").contains("estacion-fx"));
        assertTrue(Estacion.seccionMenu("invierno", "/x").contains("volver=%2Fx"));
    }
}
