package com.unev.aula;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Tipos de datos del Aula. */
public final class Modelo {
    private Modelo() { }

    public record Usuario(int id, String rol, String nombre) {
        public boolean esProfesor() { return "PROFESOR".equals(rol); }
        public boolean esEstudiante() { return "ESTUDIANTE".equals(rol); }
    }

    public record Seccion(int id, String asignatura, String grado, String horario) {
        public String titulo() { return asignatura + (grado == null || grado.isBlank() ? "" : " · " + grado); }
    }

    public record Adjunto(int id, String nombre, long bytes) { }

    /** Entrega de un estudiante. {@code dia} es la fecha (sin hora) en que se entregó; {@code nota} es null si aún no se califica. */
    public record MiEntrega(int id, String archivoNombre, long archivoBytes, String fecha, LocalDate dia, BigDecimal nota, String comentario) { }

    public record Publicacion(int id, int idSeccion, String asignatura, String grado, String tipo, String titulo, String descripcion,
                              LocalDate fechaLimite, String fechaPublicacion, List<Adjunto> adjuntos, MiEntrega miEntrega,
                              int totalEntregas, int porCalificar) {
        public boolean esTarea() { return "TAREA".equals(tipo); }
        public Publicacion conAdjuntos(List<Adjunto> a) {
            return new Publicacion(id, idSeccion, asignatura, grado, tipo, titulo, descripcion, fechaLimite, fechaPublicacion, a, miEntrega, totalEntregas, porCalificar);
        }
    }

    public record EntregaRecibida(int id, int idMaterial, String estudiante, String archivoNombre, long archivoBytes, String fecha,
                                  LocalDate dia, BigDecimal nota, String comentario) { }

    public record Comentario(int id, int idMaterial, int idUsuario, String autor, String rol, String texto, String fecha) { }

    public record Anuncio(int id, int idSeccion, String asignatura, String autor, int idAutor, String texto, String fecha) { }

    public record Descarga(String nombre, String key) { }

    /** Archivo ya guardado en MySQL, listo para enlazar a una publicación. */
    public record ArchivoGuardado(String key, String nombre, String tipo, long bytes) { }
}
