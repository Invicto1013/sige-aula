package com.unev.aula;

import com.unev.aula.Modelo.MiEntrega;
import com.unev.aula.Modelo.Publicacion;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Estado de una tarea para un estudiante. La fecha límite cuenta hasta el final de ese día. */
public enum EstadoTarea {
    PENDIENTE("pendiente", "Pendiente", "pendiente"),
    ATRASADA("atrasada", "Sin entregar (vencida)", "mal"),
    ENTREGADA("entregada", "Entregada", "ok"),
    TARDE("tarde", "Entregada tarde", "aviso"),
    CALIFICADA("calificada", "Calificada", "ok");

    private final String clave, etiqueta, estilo;

    EstadoTarea(String clave, String etiqueta, String estilo) { this.clave = clave; this.etiqueta = etiqueta; this.estilo = estilo; }

    public String clave() { return clave; }
    public String etiqueta() { return etiqueta; }
    public String estilo() { return estilo; }

    /** @return null si la publicación es una clase (no se entrega). */
    public static EstadoTarea de(Publicacion p, LocalDate hoy) {
        if (!p.esTarea()) return null;
        MiEntrega e = p.miEntrega();
        if (e == null) return p.fechaLimite() != null && hoy.isAfter(p.fechaLimite()) ? ATRASADA : PENDIENTE;
        if (e.nota() != null) return CALIFICADA;
        return tarde(p.fechaLimite(), e.dia()) ? TARDE : ENTREGADA;
    }

    public static boolean tarde(LocalDate limite, LocalDate diaEntrega) {
        return limite != null && diaEntrega != null && diaEntrega.isAfter(limite);
    }

    public static EstadoTarea porClave(String clave) {
        for (EstadoTarea e : values()) if (e.clave.equals(clave)) return e;
        return null;
    }

    /** Días que faltan para el vencimiento (0 = vence hoy, negativo = vencida). */
    public static long diasRestantes(LocalDate limite, LocalDate hoy) { return ChronoUnit.DAYS.between(hoy, limite); }
}
