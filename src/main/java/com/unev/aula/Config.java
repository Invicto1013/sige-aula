package com.unev.aula;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

/** Configuración del Aula Virtual, leída de variables de entorno (nada de secretos en el repositorio). */
public record Config(int puerto, String dbUrl, String dbUsuario, String dbClave, String secretoSso, String urlSige,
                     boolean cookieSegura, int tamanoPool, long maxArchivoBytes, long maxSolicitudBytes, ZoneId zona) {

    public static Config desdeEntorno(Map<String, String> env) {
        String dbUrl = requerido(env, "AULA_DB_URL");
        String secreto = requerido(env, "AULA_SSO_SECRET");
        if (secreto.length() < 32) throw new IllegalStateException("AULA_SSO_SECRET debe tener al menos 32 caracteres.");
        String sige = quitarBarra(requerido(env, "AULA_SIGE_URL"));
        ZoneId zona;
        try { zona = ZoneId.of(env.getOrDefault("AULA_ZONA", "America/Santo_Domingo").trim()); }
        catch (java.time.DateTimeException e) { throw new IllegalStateException("AULA_ZONA no es una zona horaria válida (ejemplo: America/Santo_Domingo)."); }
        return new Config(entero(env.get("PORT"), 8080), dbUrl, env.getOrDefault("AULA_DB_USER", ""), env.getOrDefault("AULA_DB_PASSWORD", ""),
                secreto, sige, !"false".equalsIgnoreCase(env.getOrDefault("AULA_SECURE_COOKIE", "true")),
                Math.max(1, Math.min(10, entero(env.get("AULA_DB_POOL_SIZE"), 4))),
                Math.max(1, entero(env.get("AULA_MAX_UPLOAD_MB"), 10)) * 1024L * 1024L,
                Math.max(1, entero(env.get("AULA_MAX_REQUEST_MB"), 25)) * 1024L * 1024L, zona);
    }

    /** Desplazamiento de la zona respecto a UTC en el formato de MySQL (por ejemplo, "-04:00"). Se calcula al arrancar. */
    public String desplazamientoSql() {
        ZoneOffset o = zona.getRules().getOffset(Instant.now());
        String id = o.getId();
        return "Z".equals(id) ? "+00:00" : id;
    }

    private static String requerido(Map<String, String> env, String nombre) {
        String v = env.get(nombre);
        if (v == null || v.isBlank()) throw new IllegalStateException("Falta la variable de entorno " + nombre + ".");
        return v.trim();
    }

    private static int entero(String v, int porDefecto) {
        try { return v == null || v.isBlank() ? porDefecto : Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return porDefecto; }
    }

    private static String quitarBarra(String url) { return url.endsWith("/") ? url.substring(0, url.length() - 1) : url; }
}
