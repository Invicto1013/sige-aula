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

    private static final String ICONO_LUNA = "<svg viewBox='0 0 24 24' width='20' height='20' aria-hidden='true' focusable='false'><path d='M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z' fill='none' stroke='currentColor' stroke-width='2' stroke-linejoin='round'/></svg>";
    private static final String ICONO_SOL = "<svg viewBox='0 0 24 24' width='20' height='20' aria-hidden='true' focusable='false'><circle cx='12' cy='12' r='4' fill='none' stroke='currentColor' stroke-width='2'/><path d='M12 2v2M12 20v2M2 12h2M20 12h2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4' stroke='currentColor' stroke-width='2' stroke-linecap='round'/></svg>";

    /** Botón de brillo: muestra la luna en tema claro y el sol en tema oscuro (el CSS elige cuál se ve). */
    public static String boton(String rutaActual) {
        String volver = URLEncoder.encode(Vision.volverSeguro(rutaActual), StandardCharsets.UTF_8);
        return "<span class='tema-toggle'><a class='to-oscuro' href='/tema?modo=oscuro&amp;volver=" + volver
                + "' aria-label='Cambiar a modo oscuro' title='Modo oscuro'>" + ICONO_LUNA + "</a>"
                + "<a class='to-claro' href='/tema?modo=claro&amp;volver=" + volver
                + "' aria-label='Cambiar a modo claro' title='Modo claro'>" + ICONO_SOL + "</a></span>";
    }
}
