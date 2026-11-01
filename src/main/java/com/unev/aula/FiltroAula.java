package com.unev.aula;

import com.unev.aula.Modelo.*;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

/**
 * Filtros de la pantalla principal. Se aplican en memoria sobre las publicaciones del usuario (que ya vienen
 * limitadas a sus secciones), de modo que nunca pueden mostrar más de lo permitido.
 */
public record FiltroAula(String texto, int seccion, String tipo, String estado) {
    public static final List<String> TIPOS = List.of("CLASE", "TAREA");
    public static final List<String> ESTADOS_PROFESOR = List.of("por-calificar", "sin-entregas", "al-dia");

    public static FiltroAula desde(Function<String, String> parametro, boolean profesor) {
        String q = parametro.apply("q");
        q = q == null ? "" : q.trim();
        if (q.length() > 100) q = q.substring(0, 100);
        int seccion = 0;
        try { seccion = Math.max(0, Integer.parseInt(parametro.apply("seccion"))); } catch (NumberFormatException ignorada) { }
        String tipo = parametro.apply("tipo");
        tipo = tipo != null && TIPOS.contains(tipo) ? tipo : "";
        String estado = parametro.apply("estado");
        boolean valido = estado != null && (profesor ? ESTADOS_PROFESOR.contains(estado) : EstadoTarea.porClave(estado) != null);
        return new FiltroAula(q, seccion, tipo, valido ? estado : "");
    }

    public boolean activo() { return !texto.isEmpty() || seccion != 0 || !tipo.isEmpty() || !estado.isEmpty(); }

    public List<Publicacion> aplicar(List<Publicacion> todas, Usuario u, LocalDate hoy) {
        return todas.stream().filter(p -> coincide(p, u, hoy)).toList();
    }

    boolean coincide(Publicacion p, Usuario u, LocalDate hoy) {
        if (seccion != 0 && p.idSeccion() != seccion) return false;
        if (!tipo.isEmpty() && !tipo.equals(p.tipo())) return false;
        if (!Busqueda.coincide(texto, p.titulo(), p.descripcion(), p.asignatura())) return false;
        if (estado.isEmpty()) return true;
        if (u.esProfesor()) {
            return switch (estado) {
                case "por-calificar" -> p.esTarea() && p.porCalificar() > 0;
                case "sin-entregas" -> p.esTarea() && p.totalEntregas() == 0;
                case "al-dia" -> p.esTarea() && p.totalEntregas() > 0 && p.porCalificar() == 0;
                default -> true;
            };
        }
        EstadoTarea e = EstadoTarea.de(p, hoy);
        return e != null && e.clave().equals(estado);
    }

    /** Tareas sin entregar que vencen en los próximos {@code dias} días (incluye hoy), de la más cercana a la más lejana. */
    public static List<Publicacion> proximas(List<Publicacion> pubs, LocalDate hoy, int dias) {
        return pubs.stream().filter(p -> EstadoTarea.de(p, hoy) == EstadoTarea.PENDIENTE && p.fechaLimite() != null
                        && EstadoTarea.diasRestantes(p.fechaLimite(), hoy) <= dias)
                .sorted(java.util.Comparator.comparing(Publicacion::fechaLimite)).toList();
    }

    public static long atrasadas(List<Publicacion> pubs, LocalDate hoy) {
        return pubs.stream().filter(p -> EstadoTarea.de(p, hoy) == EstadoTarea.ATRASADA).count();
    }

    public static int porCalificar(List<Publicacion> pubs) {
        return pubs.stream().filter(Publicacion::esTarea).mapToInt(Publicacion::porCalificar).sum();
    }
}
