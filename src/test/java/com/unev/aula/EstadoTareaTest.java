package com.unev.aula;

import static org.junit.jupiter.api.Assertions.*;

import com.unev.aula.Modelo.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class EstadoTareaTest {
    static final LocalDate HOY = LocalDate.of(2026, 10, 31);

    static Publicacion pub(String tipo, LocalDate limite, MiEntrega e) {
        return new Publicacion(1, 1, "Mat", "1.º", tipo, "T", "", limite, "", List.of(), e, 0, 0);
    }

    static MiEntrega entrega(LocalDate dia, BigDecimal nota) { return new MiEntrega(1, "a", 1, "", dia, nota, null); }

    @Test void claseNoTieneEstado() { assertNull(EstadoTarea.de(pub("CLASE", null, null), HOY)); }

    @Test void sinEntregaYSinLimiteEsPendiente() { assertEquals(EstadoTarea.PENDIENTE, EstadoTarea.de(pub("TAREA", null, null), HOY)); }

    @Test void elDiaLimiteAunEsPendiente() {
        assertEquals(EstadoTarea.PENDIENTE, EstadoTarea.de(pub("TAREA", HOY, null), HOY));
        assertEquals(EstadoTarea.ATRASADA, EstadoTarea.de(pub("TAREA", HOY.minusDays(1), null), HOY));
    }

    @Test void entregaATiempoYElMismoDiaLimite() {
        assertEquals(EstadoTarea.ENTREGADA, EstadoTarea.de(pub("TAREA", HOY, entrega(HOY, null)), HOY));
        assertEquals(EstadoTarea.ENTREGADA, EstadoTarea.de(pub("TAREA", HOY, entrega(HOY.minusDays(3), null)), HOY));
    }

    @Test void entregaDespuesDelLimiteEsTarde() {
        assertEquals(EstadoTarea.TARDE, EstadoTarea.de(pub("TAREA", HOY.minusDays(2), entrega(HOY.minusDays(1), null)), HOY));
    }

    @Test void conNotaEsCalificadaAunqueFueraTarde() {
        assertEquals(EstadoTarea.CALIFICADA, EstadoTarea.de(pub("TAREA", HOY.minusDays(2), entrega(HOY, new BigDecimal("90"))), HOY));
    }

    @Test void sinLimiteNuncaEsTarde() {
        assertFalse(EstadoTarea.tarde(null, HOY));
        assertEquals(EstadoTarea.ENTREGADA, EstadoTarea.de(pub("TAREA", null, entrega(HOY, null)), HOY));
    }

    @Test void diasRestantes() {
        assertEquals(0L, EstadoTarea.diasRestantes(HOY, HOY));
        assertEquals(2L, EstadoTarea.diasRestantes(HOY.plusDays(2), HOY));
        assertEquals(-1L, EstadoTarea.diasRestantes(HOY.minusDays(1), HOY));
    }

    @Test void porClave() {
        assertEquals(EstadoTarea.TARDE, EstadoTarea.porClave("tarde"));
        assertNull(EstadoTarea.porClave("inventado"));
        assertNull(EstadoTarea.porClave(null));
    }
}
