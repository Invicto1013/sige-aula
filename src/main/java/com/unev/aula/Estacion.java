package com.unev.aula;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.MonthDay;

/**
 * Estación del año de la interfaz: fondos y animaciones decorativas que cambian solas cuando se acerca cada estación
 * (hemisferio norte, 7 días antes del inicio astronómico). También se puede fijar una estación o desactivar los efectos.
 */
public final class Estacion {
    public static final String COOKIE = "aula_estacion";
    public static final String PREDETERMINADO = "auto";
    public static final int PARTICULAS = 14;
    private static final String[] MODOS = {"auto", "primavera", "verano", "otono", "invierno", "ninguna"};
    private static final String[] NOMBRES = {"Automática", "Primavera", "Verano", "Otoño", "Invierno", "Sin efectos"};

    private Estacion() { }

    public static boolean valido(String modo) {
        for (String m : MODOS) if (m.equals(modo)) return true;
        return false;
    }

    /** Lee la preferencia desde el encabezado Cookie; un valor desconocido equivale a "auto". */
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

    /** Estación que corresponde a una fecha: cambia 7 días antes de los equinoccios y solsticios (20-mar, 21-jun, 22-sep, 21-dic). */
    public static String deFecha(LocalDate fecha) {
        MonthDay d = MonthDay.from(fecha);
        if (!d.isBefore(MonthDay.of(12, 14)) || d.isBefore(MonthDay.of(3, 13))) return "invierno";
        if (d.isBefore(MonthDay.of(6, 14))) return "primavera";
        if (d.isBefore(MonthDay.of(9, 15))) return "verano";
        return "otono";
    }

    /** Estación a mostrar ("" si los efectos están desactivados). */
    public static String efectiva(String modo, LocalDate hoy) {
        if ("ninguna".equals(modo)) return "";
        if (valido(modo) && !"auto".equals(modo)) return modo;
        return deFecha(hoy);
    }

    public static String atributo(String efectiva) {
        return efectiva == null || efectiva.isEmpty() ? "" : " data-estacion='" + efectiva + "'";
    }

    /** Capa decorativa con las partículas animadas (los símbolos y movimientos los define el CSS). */
    public static String capa(String efectiva) {
        if (efectiva == null || efectiva.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("<div class='estacion-fx' aria-hidden='true'>");
        for (int i = 0; i < PARTICULAS; i++) sb.append("<span></span>");
        return sb.append("</div>").toString();
    }

    /** Sección del menú de apariencia para elegir la temporada (sin JavaScript). */
    public static String seccionMenu(String modoActual, String rutaActual) {
        String volver = URLEncoder.encode(Vision.volverSeguro(rutaActual), StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder("<strong>Temporada</strong>");
        for (int i = 0; i < MODOS.length; i++) {
            sb.append("<a href='/estacion?modo=").append(MODOS[i]).append("&amp;volver=").append(volver).append('\'')
              .append(MODOS[i].equals(modoActual) ? " aria-current='true'" : "").append('>').append(NOMBRES[i]).append("</a>");
        }
        return sb.toString();
    }
}
