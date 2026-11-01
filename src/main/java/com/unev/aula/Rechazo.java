package com.unev.aula;

/** Regla de negocio incumplida (sin permiso, tarea ya calificada, demasiados archivos...). El código se muestra como aviso. */
public final class Rechazo extends RuntimeException {
    private final String codigo;

    public Rechazo(String codigo) {
        super(codigo, null, false, false);
        this.codigo = codigo;
    }

    public String codigo() { return codigo; }
}
