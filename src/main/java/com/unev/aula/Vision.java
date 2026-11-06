package com.unev.aula;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Modo de visión de la interfaz (daltonismo / alto contraste), guardado en una cookie sin datos personales. */
public final class Vision {
    public static final String COOKIE = "aula_vision";
    private static final String[] MODOS = {"normal", "daltonismo", "contraste"};
    private static final String[] NOMBRES = {"Normal", "Daltonismo", "Alto contraste"};

    private Vision() { }

    public static boolean valido(String modo) {
        for (String m : MODOS) if (m.equals(modo)) return true;
        return false;
    }

    /** Lee el modo desde el encabezado Cookie; cualquier valor desconocido es "normal". */
    public static String modo(String cabeceraCookie) {
        if (cabeceraCookie == null) return "normal";
        for (String par : cabeceraCookie.split(";")) {
            String p = par.trim();
            if (p.startsWith(COOKIE + "=")) {
                String v = p.substring(COOKIE.length() + 1).trim();
                return valido(v) ? v : "normal";
            }
        }
        return "normal";
    }

    /** Solo se permite volver a una ruta local, para evitar redirecciones a otros sitios. */
    public static String volverSeguro(String v) {
        if (v == null || v.isEmpty() || v.length() > 200) return "/";
        if (!v.startsWith("/") || v.startsWith("//") || v.contains("\\") || v.contains("\r") || v.contains("\n")) return "/";
        for (int i = 0; i < v.length(); i++) if (v.charAt(i) < 0x20) return "/";
        return v;
    }

    /** Atributo para la etiqueta html; vacío en modo normal. */
    public static String atributo(String modo) {
        return "normal".equals(modo) || !valido(modo) ? "" : " data-vision='" + modo + "'";
    }

    /** Enlaces para cambiar de modo (sin JavaScript). */
    public static String selector(String modoActual, String rutaActual) {
        String volver = URLEncoder.encode(volverSeguro(rutaActual), StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder("<nav class='vision' aria-label='Modo de visión'><span>Visión:</span>");
        for (int i = 0; i < MODOS.length; i++) {
            boolean activo = MODOS[i].equals(modoActual);
            sb.append(" <a href='/vision?modo=").append(MODOS[i]).append("&amp;volver=").append(volver).append('\'')
              .append(activo ? " aria-current='true'" : "").append('>').append(NOMBRES[i]).append("</a>");
        }
        return sb.append("</nav>").toString();
    }

    private static final String ICONO_OJO = "<svg viewBox='0 0 24 24' width='20' height='20' aria-hidden='true' focusable='false'><path d='M1 12s4-7 11-7 11 7 11 7-4 7-11 7S1 12 1 12z' fill='none' stroke='currentColor' stroke-width='2' stroke-linejoin='round'/><circle cx='12' cy='12' r='3' fill='none' stroke='currentColor' stroke-width='2'/></svg>";

    /** Menú desplegable con el icono de un ojo que agrupa los modos de visión (sin JavaScript). */
    public static String menu(String modoActual, String rutaActual) {
        String volver = URLEncoder.encode(volverSeguro(rutaActual), StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder("<details class='vision-menu'><summary aria-label='Modo de visión' title='Modo de visión'>")
                .append(ICONO_OJO).append("</summary><div class='vision-panel'><strong>Modo de visión</strong>");
        for (int i = 0; i < MODOS.length; i++) {
            boolean activo = MODOS[i].equals(modoActual);
            sb.append("<a href='/vision?modo=").append(MODOS[i]).append("&amp;volver=").append(volver).append('\'')
              .append(activo ? " aria-current='true'" : "").append('>').append(NOMBRES[i]).append("</a>");
        }
        return sb.append("</div></details>").toString();
    }
}
