package com.unev.aula;

import static org.junit.jupiter.api.Assertions.*;

import com.unev.aula.Modelo.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HtmlTest {
    static final Sesiones.Sesion S_PROF = new Sesiones.Sesion("id", "csrf-token", 1, "PROFESOR", "Ana", 0, 0);
    static final Sesiones.Sesion S_EST = new Sesiones.Sesion("id", "csrf-token", 2, "ESTUDIANTE", "Luis", 0, 0);
    static final LocalDate HOY = LocalDate.of(2026, 10, 31);
    static final Usuario PROF = new Usuario(1, "PROFESOR", "Ana");
    static final Usuario EST = new Usuario(2, "ESTUDIANTE", "Luis");
    static final FiltroAula SIN_FILTRO = new FiltroAula("", 0, "", "");

    static Publicacion tarea(String titulo, LocalDate limite, MiEntrega mia) {
        return new Publicacion(10, 3, "Matemática", "1.º", "TAREA", titulo, "desc", limite, "2026-10-01 08:00", List.of(new Adjunto(4, "guia.pdf", 2048)), mia, 0, 0);
    }

    static String vista(Usuario u, Sesiones.Sesion s, List<Publicacion> pubs, List<Seccion> secciones, Map<Integer, List<EntregaRecibida>> entregas, Map<Integer, List<Comentario>> coment, FiltroAula f) {
        return Html.inicio(new Html.VistaInicio(u, s, secciones, pubs, f.aplicar(pubs, u, HOY), f, entregas, coment, 10 * 1024 * 1024, HOY, null));
    }

    static String vistaEst(List<Publicacion> pubs) { return vista(EST, S_EST, pubs, List.of(), Map.of(), Map.of(), SIN_FILTRO); }

    @Test void escapaTextoDeUsuario() {
        assertEquals("&lt;script&gt;&amp;&quot;&#39;", Html.esc("<script>&\"'"));
        assertEquals("", Html.esc(null));
    }

    @Test void tituloMaliciosoNoInyectaHtml() {
        String h = vistaEst(List.of(tarea("<img src=x onerror=alert(1)>", null, null)));
        assertFalse(h.contains("<img src=x"));
        assertTrue(h.contains("&lt;img src=x"));
    }

    @Test void comentarioMaliciosoNoInyectaHtml() {
        var c = new Comentario(1, 10, 2, "<b>Luis</b>", "ESTUDIANTE", "<script>alert(1)</script>", "2026-10-02 10:00");
        String h = vista(EST, S_EST, List.of(tarea("T", null, null)), List.of(), Map.of(), Map.of(10, List.of(c)), SIN_FILTRO);
        assertFalse(h.contains("<script>alert"));
        assertTrue(h.contains("&lt;script&gt;alert(1)"));
    }

    @Test void estudianteVeFormularioDeEntregaConCsrf() {
        String h = vistaEst(List.of(tarea("T", null, null)));
        assertTrue(h.contains("action='/entregar'"));
        assertTrue(h.contains("value='csrf-token'"));
        assertFalse(h.contains("action='/publicar'"));
        assertTrue(h.contains("/archivo?adjunto=4"));
    }

    @Test void profesorVePublicarEntregasYNotasPeroNoEntregar() {
        var secciones = List.of(new Seccion(3, "Matemática", "1.º", "Lun 8:00"));
        var entregas = Map.of(10, List.of(new EntregaRecibida(5, 10, "Luis Gómez", "tarea.docx", 100, "2026-10-02 10:00", LocalDate.of(2026, 10, 2), null, null)));
        String h = vista(PROF, S_PROF, List.of(tarea("T", null, null)), secciones, entregas, Map.of(), SIN_FILTRO);
        assertTrue(h.contains("action='/publicar'"));
        assertTrue(h.contains("name='archivo' multiple"));
        assertTrue(h.contains("Entregas recibidas (1)"));
        assertTrue(h.contains("action='/calificar'"));
        assertTrue(h.contains("Guardar nota"));
        assertTrue(h.contains("href='/editar?id=10'"));
        assertTrue(h.contains("action='/eliminar'"));
        assertFalse(h.contains("action='/entregar'"));
    }

    @Test void entregaTardeSeMarcaEnLaVistaDelProfesor() {
        var pub = tarea("T", LocalDate.of(2026, 10, 1), null);
        var entregas = Map.of(10, List.of(new EntregaRecibida(5, 10, "Luis", "t.docx", 1, "2026-10-05 10:00", LocalDate.of(2026, 10, 5), null, null)));
        assertTrue(vista(PROF, S_PROF, List.of(pub), List.of(), entregas, Map.of(), SIN_FILTRO).contains("Tarde"));
    }

    @Test void marcaTareaVencidaYEstadoAtrasada() {
        String h = vistaEst(List.of(tarea("T", HOY.minusDays(1), null)));
        assertTrue(h.contains("· vencida"));
        assertTrue(h.contains("Sin entregar (vencida)"));
        String h2 = vistaEst(List.of(tarea("T", HOY.plusDays(1), null)));
        assertFalse(h2.contains("· vencida"));
        assertTrue(h2.contains("Pendiente"));
    }

    @Test void muestraNotaYComentarioDelProfesor() {
        var mia = new MiEntrega(8, "mi-tarea.pdf", 500, "2026-10-03 09:00", LocalDate.of(2026, 10, 3), new BigDecimal("87.50"), "Buen trabajo");
        String h = vistaEst(List.of(tarea("T", null, mia)));
        assertTrue(h.contains("87.50 / 100"));
        assertTrue(h.contains("Buen trabajo"));
        assertTrue(h.contains("Calificada"));
        assertFalse(h.contains("action='/entregar'"), "una entrega calificada no se reemplaza");
    }

    @Test void entregaSinCalificarPermiteReemplazar() {
        var mia = new MiEntrega(8, "mi-tarea.pdf", 500, "2026-10-03 09:00", LocalDate.of(2026, 10, 3), null, null);
        String h = vistaEst(List.of(tarea("T", null, mia)));
        assertTrue(h.contains("Reemplazar tu entrega"));
        assertTrue(h.contains("Entregada"));
    }

    @Test void recordatoriosParaElEstudiante() {
        var urgente = tarea("Urgente", HOY.plusDays(1), null);
        String h = vistaEst(List.of(urgente));
        assertTrue(h.contains("vence mañana"));
        assertTrue(h.contains("vencen en 3 días o menos"));
    }

    @Test void profesorSinSeccionesRecibeAviso() {
        String h = vista(PROF, S_PROF, List.of(), List.of(), Map.of(), Map.of(), SIN_FILTRO);
        assertTrue(h.contains("No tienes secciones asignadas"));
        assertFalse(h.contains("action='/publicar'"));
    }

    @Test void barraDeFiltrosMuestraConteoYLimpiar() {
        var f = new FiltroAula("fraccion", 0, "TAREA", "");
        var pubs = List.of(tarea("Fracciones", null, null), tarea("Otra cosa", null, null));
        String h = vista(EST, S_EST, pubs, List.of(), Map.of(), Map.of(), f);
        assertTrue(h.contains("Mostrando 1 de 2"));
        assertTrue(h.contains("Limpiar filtros"));
        assertTrue(h.contains("role='search'"));
        assertTrue(h.contains("value='fraccion'"));
    }

    @Test void listaVaciaPorFiltroDiceQueNadaCoincide() {
        var f = new FiltroAula("zzz", 0, "", "");
        assertTrue(vista(EST, S_EST, List.of(tarea("T", null, null)), List.of(), Map.of(), Map.of(), f).contains("Ninguna publicación coincide"));
    }

    @Test void tablonMuestraAnunciosYSoloElAutorPuedeEliminar() {
        var propio = new Anuncio(1, 3, "Matemática", "Ana Pérez", 1, "Examen el lunes", "2026-10-30 09:00");
        var ajeno = new Anuncio(2, 3, "Matemática", "Otro Profe", 99, "Otro aviso", "2026-10-29 09:00");
        String h = Html.tablon(PROF, S_PROF, List.of(new Seccion(3, "Matemática", "1.º", "")), List.of(propio, ajeno), 0, null);
        assertTrue(h.contains("Examen el lunes") && h.contains("Otro aviso"));
        assertEquals(1, h.split("action='/anuncio-eliminar'", -1).length - 1);
        String est = Html.tablon(EST, S_EST, List.of(), List.of(propio), 0, null);
        assertFalse(est.contains("action='/anuncio'"));
        assertFalse(est.contains("/anuncio-eliminar"));
    }

    @Test void paginaEditarListaAdjuntosParaQuitar() {
        String h = Html.editar(S_PROF, tarea("T", null, null), 1024, null);
        assertTrue(h.contains("name='quitar' value='4'"));
        assertTrue(h.contains("action='/editar'"));
        assertTrue(h.contains("name='id' value='10'"));
    }

    @Test void paginaIncluyeSaltarAlContenidoNavegacionYSalirConCsrf() {
        String h = Html.pagina("Aula", "https://sige.example", S_PROF, "inicio", "<p>x</p>");
        assertTrue(h.contains("href='#contenido'") && h.contains("id='contenido'"));
        assertTrue(h.contains("aria-current='page'"));
        assertTrue(h.contains("href='/tablon'"));
        assertTrue(h.contains("action='/salir'"));
        assertTrue(h.contains("lang='es'"));
    }

    @Test void formatoDeBytes() {
        assertEquals("512 B", Html.bytes(512));
        assertEquals("1.5 KB", Html.bytes(1536));
        assertEquals("10.0 MB", Html.bytes(10L * 1024 * 1024));
    }
}
