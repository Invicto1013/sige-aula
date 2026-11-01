package com.unev.aula;

import java.text.Normalizer;
import java.util.Locale;

/** Búsqueda de texto sin distinguir mayúsculas ni acentos; todas las palabras deben aparecer. */
public final class Busqueda {
    private Busqueda() { }

    public static String normalizar(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).trim();
    }

    public static boolean coincide(String busqueda, String... campos) {
        String q = normalizar(busqueda);
        if (q.isEmpty()) return true;
        StringBuilder sb = new StringBuilder();
        for (String c : campos) sb.append(normalizar(c)).append(' ');
        String texto = sb.toString();
        for (String palabra : q.split("\\s+")) if (!texto.contains(palabra)) return false;
        return true;
    }
}
