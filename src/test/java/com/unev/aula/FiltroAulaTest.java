package com.unev.aula;

import static org.junit.jupiter.api.Assertions.*;

import com.unev.aula.Modelo.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FiltroAulaTest {
    static final LocalDate HOY = LocalDate.of(2026, 10, 31);
    static final Usuario EST = new Usuario(2, "ESTUDIANTE", "L");
    static final Usuario PROF = new Usuario(1, "PROFESOR", "A");

    static Publicacion p(int id, int seccion, String tipo, String titulo, LocalDate limite, MiEntrega e, int total, int porCalificar) {
        return new Publicacion(id, seccion, "Matemática", "1.º", tipo, titulo, "descripción", limite, "", List.of(), e, total, porCalificar);
    }

    static FiltroAula de(Map<String, String> m, boolean prof) { return FiltroAula.desde(m::get, prof); }

    final List<Publicacion> datos = List.of(
            p(1, 3, "CLASE", "Repaso de fracciones", null, null, 0, 0),
            p(2, 3, "TAREA", "Fracciones", HOY.plusDays(2), null, 0, 0),
            p(3, 4, "TAREA", "Geometría", HOY.minusDays(1), null, 2, 2),
            p(4, 4, "TAREA", "Álgebra", HOY.plusDays(9), new MiEntrega(1, "a", 1, "", HOY, new BigDecimal("80"), null), 1, 0));

    @Test void sinParametrosNoFiltraNada() {
        var f = de(Map.of(), false);
        assertFalse(f.activo());
        assertEquals(4, f.aplicar(datos, EST, HOY).size());
    }

    @Test void porTextoSinAcentos() {
        assertEquals(List.of(3), de(Map.of("q", "geometria"), false).aplicar(datos, EST, HOY).stream().map(Publicacion::id).toList());
        assertEquals(2, de(Map.of("q", "FRACCIONES"), false).aplicar(datos, EST, HOY).size());
    }

    @Test void porSeccionYTipo() {
        assertEquals(2, de(Map.of("seccion", "4"), false).aplicar(datos, EST, HOY).size());
        assertEquals(1, de(Map.of("tipo", "CLASE"), false).aplicar(datos, EST, HOY).size());
        assertEquals(1, de(Map.of("seccion", "3", "tipo", "TAREA"), false).aplicar(datos, EST, HOY).size());
    }

    @Test void estadosDelEstudiante() {
        assertEquals(List.of(2), ids(de(Map.of("estado", "pendiente"), false)));
        assertEquals(List.of(3), ids(de(Map.of("estado", "atrasada"), false)));
        assertEquals(List.of(4), ids(de(Map.of("estado", "calificada"), false)));
    }

    @Test void estadosDelProfesor() {
        assertEquals(List.of(3), ids(de(Map.of("estado", "por-calificar"), true), PROF));
        assertEquals(List.of(2), ids(de(Map.of("estado", "sin-entregas"), true), PROF));
        assertEquals(List.of(4), ids(de(Map.of("estado", "al-dia"), true), PROF));
    }

    @Test void valoresInvalidosSeIgnoran() {
        var m = new HashMap<String, String>();
        m.put("seccion", "abc"); m.put("tipo", "HACK"); m.put("estado", "inventado");
        var f = de(m, false);
        assertFalse(f.activo());
        assertEquals("", de(Map.of("estado", "pendiente"), true).estado(), "un estado de estudiante no vale para el profesor");
        assertEquals("", de(Map.of("estado", "por-calificar"), false).estado());
        assertEquals(0, de(Map.of("seccion", "-5"), false).seccion());
    }

    @Test void textoMuyLargoSeRecorta() {
        assertEquals(100, de(Map.of("q", "x".repeat(500)), false).texto().length());
    }

    @Test void combinaVariosFiltros() {
        assertEquals(List.of(2), ids(de(Map.of("q", "fracciones", "tipo", "TAREA", "estado", "pendiente"), false)));
        assertTrue(ids(de(Map.of("q", "fracciones", "estado", "calificada"), false)).isEmpty());
    }

    @Test void proximasOrdenadasPorFecha() {
        var lista = List.of(p(1, 1, "TAREA", "B", HOY.plusDays(3), null, 0, 0), p(2, 1, "TAREA", "A", HOY, null, 0, 0),
                p(3, 1, "TAREA", "Lejana", HOY.plusDays(10), null, 0, 0), p(4, 1, "TAREA", "Vencida", HOY.minusDays(1), null, 0, 0));
        assertEquals(List.of(2, 1), FiltroAula.proximas(lista, HOY, 3).stream().map(Publicacion::id).toList());
        assertEquals(1L, FiltroAula.atrasadas(lista, HOY));
    }

    @Test void totalPorCalificar() { assertEquals(2, FiltroAula.porCalificar(datos)); }

    private List<Integer> ids(FiltroAula f) { return ids(f, EST); }
    private List<Integer> ids(FiltroAula f, Usuario u) { return f.aplicar(datos, u, HOY).stream().map(Publicacion::id).toList(); }
}
