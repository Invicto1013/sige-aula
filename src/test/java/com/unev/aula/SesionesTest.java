package com.unev.aula;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SesionesTest {
    @Test void creaYRecuperaSesion() {
        var s = new Sesiones();
        var a = s.crear(5, "PROFESOR", "Ana", 1000);
        assertEquals(a, s.obtener(a.id(), 2000));
        assertNotEquals(a.id(), a.csrf());
    }

    @Test void expiraTrasLaDuracion() {
        var s = new Sesiones();
        var a = s.crear(5, "ESTUDIANTE", "Luis", 0);
        assertNotNull(s.obtener(a.id(), Sesiones.DURACION_MS));
        assertNull(s.obtener(a.id(), Sesiones.DURACION_MS + 1));
        assertEquals(0, s.activas());
    }

    @Test void cerrarEliminaLaSesion() {
        var s = new Sesiones();
        var a = s.crear(5, "ESTUDIANTE", "Luis", 0);
        s.cerrar(a.id());
        assertNull(s.obtener(a.id(), 1));
        assertNull(s.obtener(null, 1));
        assertNull(s.obtener("inventado", 1));
    }

    @Test void losIdentificadoresNoSeRepiten() {
        var s = new Sesiones();
        assertNotEquals(s.crear(1, "PROFESOR", "A", 0).id(), s.crear(1, "PROFESOR", "A", 0).id());
    }

    @Test void marcarRevisadaConservaElResto() {
        var s = new Sesiones();
        var a = s.crear(9, "PROFESOR", "Ana", 0);
        s.marcarRevisada(a, 777);
        var b = s.obtener(a.id(), 1000);
        assertEquals(777L, b.revisada());
        assertEquals(a.csrf(), b.csrf());
    }
}
