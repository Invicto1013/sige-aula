package com.unev.aula;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/** Sesiones en memoria. Si el servicio se reinicia o se duerme, se vuelve a entrar desde SIGE con un clic. */
public final class Sesiones {
    public static final long DURACION_MS = 8L * 60 * 60 * 1000;
    private static final int MAX_SESIONES = 5000;
    private static final SecureRandom AZAR = new SecureRandom();

    public record Sesion(String id, String csrf, int idUsuario, String rol, String nombre, long expira, long revisada) { }

    private final ConcurrentHashMap<String, Sesion> mapa = new ConcurrentHashMap<>();

    public Sesion crear(int idUsuario, String rol, String nombre, long ahoraMs) {
        purgar(ahoraMs);
        if (mapa.size() >= MAX_SESIONES) throw new IllegalStateException("Demasiadas sesiones activas.");
        Sesion s = new Sesion(token(), token(), idUsuario, rol, nombre, ahoraMs + DURACION_MS, ahoraMs);
        mapa.put(s.id(), s);
        return s;
    }

    public Sesion obtener(String id, long ahoraMs) {
        if (id == null) return null;
        Sesion s = mapa.get(id);
        if (s == null) return null;
        if (s.expira() < ahoraMs) { mapa.remove(id); return null; }
        return s;
    }

    public void marcarRevisada(Sesion s, long ahoraMs) {
        mapa.replace(s.id(), s, new Sesion(s.id(), s.csrf(), s.idUsuario(), s.rol(), s.nombre(), s.expira(), ahoraMs));
    }

    public void cerrar(String id) { if (id != null) mapa.remove(id); }

    public int activas() { return mapa.size(); }

    private void purgar(long ahoraMs) { mapa.values().removeIf(s -> s.expira() < ahoraMs); }

    private static String token() {
        byte[] b = new byte[32];
        AZAR.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
