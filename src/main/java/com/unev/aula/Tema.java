package com.unev.aula;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Tema de la interfaz web (claro, oscuro o automático según el sistema), guardado en una cookie sin datos personales. */
public final class Tema {
    public static final String COOKIE = "aula_tema";
    public static final String PREDETERMINADO = "auto";
    private static final String[] MODOS = {"auto","claro","oscuro"};
    private static final String[] NOMBRES = {"Automático","Claro","Oscuro"};

    private Tema() { }

    public static boolean valido(String modo) {
        for (String m : MODOS) if (m.equals(modo)) return true;
        return false;
    }

    /** Lee el tema desde el encabezado Cookie; un valor desconocido equivale al predeterminado. */
    public static String modo(String cabeceraCookie) {
        if (cabeceraCookie == null) return PREDETERMINADO;
        for (String par : cabeceraCookie.split(";")) {
            String p = par.trim();
            if (p.startsWith(COOKIE + "=")) {
                String v = p.substring(COOKIE.length() + 1).trim();
                return valido(v) ? v : PREDETERMINADO;
            }
        }
        return PREDETERMINADO;
    }

    /** Atributo para la etiqueta html; vacío cuando se usa el comportamiento predeterminado. */
    public static String atributo(String modo) {
        return !valido(modo) || PREDETERMINADO.equals(modo) ? "" : " data-tema='" + modo + "'";
    }

    /** Enlaces para cambiar de tema (sin JavaScript). */
    public static String selector(String modoActual, String rutaActual) {
        String volver = URLEncoder.encode(Vision.volverSeguro(rutaActual), StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder("<nav class='vision tema' aria-label='Tema de color'><span>Tema:</span>");
        for (int i = 0; i < MODOS.length; i++) {
            boolean activo = MODOS[i].equals(modoActual);
            sb.append(" <a href='/tema?modo=").append(MODOS[i]).append("&amp;volver=").append(volver).append('\'')
              .append(activo ? " aria-current='true'" : "").append('>').append(NOMBRES[i]).append("</a>");
        }
        return sb.append("</nav>").toString();
    }
}
