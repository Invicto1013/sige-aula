package com.unev.aula;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Lectura de formularios: application/x-www-form-urlencoded y multipart/form-data (varios archivos por campo). */
public final class Multipart {
    public static final int MAX_CAMPO_TEXTO = 64 * 1024;

    public record Archivo(String nombre, String tipo, byte[] datos) { }

    public record Formulario(Map<String, String> campos, Map<String, List<String>> valores, Map<String, List<Archivo>> archivos) {
        public String campo(String nombre) { return campos.getOrDefault(nombre, ""); }
        /** Todos los valores de un campo repetido (por ejemplo varias casillas con el mismo nombre). */
        public List<String> valores(String nombre) { return valores.getOrDefault(nombre, List.of()); }
        public List<Archivo> archivos(String nombre) { return archivos.getOrDefault(nombre, List.of()); }
    }

    public static class ArchivoGrandeException extends IOException {
        public ArchivoGrandeException() { super("El archivo supera el tamaño máximo permitido."); }
    }

    private Multipart() { }

    public static Formulario urlencoded(String cuerpo) {
        Map<String, String> campos = new HashMap<>();
        Map<String, List<String>> valores = new HashMap<>();
        if (cuerpo != null && !cuerpo.isBlank()) {
            for (String par : cuerpo.split("&")) {
                String[] kv = par.split("=", 2);
                String k = URLDecoder.decode(kv[0], StandardCharsets.UTF_8), v = kv.length == 2 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
                campos.put(k, v);
                valores.computeIfAbsent(k, x -> new ArrayList<>()).add(v);
            }
        }
        return new Formulario(campos, valores, Map.of());
    }

    public static Formulario analizar(byte[] cuerpo, String contentType, long maxArchivo) throws IOException {
        String limite = limite(contentType);
        if (limite == null || limite.isBlank() || limite.length() > 100) throw new IOException("Formulario multipart inválido.");
        byte[] marca = ("--" + limite).getBytes(StandardCharsets.ISO_8859_1);
        byte[] separador = ("\r\n--" + limite).getBytes(StandardCharsets.ISO_8859_1);
        Map<String, String> campos = new HashMap<>();
        Map<String, List<String>> valores = new HashMap<>();
        Map<String, List<Archivo>> archivos = new HashMap<>();
        int pos = indice(cuerpo, marca, 0);
        while (pos >= 0) {
            int ini = pos + marca.length;
            if (ini + 2 <= cuerpo.length && cuerpo[ini] == '-' && cuerpo[ini + 1] == '-') break;
            if (ini + 2 > cuerpo.length || cuerpo[ini] != '\r' || cuerpo[ini + 1] != '\n') throw new IOException("Formulario multipart inválido.");
            int cabIni = ini + 2;
            int cabFin = indice(cuerpo, new byte[]{13, 10, 13, 10}, cabIni);
            if (cabFin < 0) throw new IOException("Encabezado multipart inválido.");
            String cabeceras = new String(cuerpo, cabIni, cabFin - cabIni, StandardCharsets.ISO_8859_1);
            int datosIni = cabFin + 4;
            int sig = indice(cuerpo, separador, datosIni);
            if (sig < 0) throw new IOException("Parte multipart incompleta.");
            String disposicion = "", tipo = "application/octet-stream";
            for (String linea : cabeceras.split("\r\n")) {
                String l = linea.toLowerCase(Locale.ROOT);
                if (l.startsWith("content-disposition:")) disposicion = linea;
                if (l.startsWith("content-type:")) tipo = linea.substring(linea.indexOf(':') + 1).trim();
            }
            String campo = valor(disposicion, "name");
            String archivo = valor(disposicion, "filename");
            int largo = sig - datosIni;
            if (campo != null) {
                if (archivo != null) {
                    if (largo > maxArchivo) throw new ArchivoGrandeException();
                    if (largo > 0 && !archivo.isBlank())
                        archivos.computeIfAbsent(campo, k -> new ArrayList<>()).add(new Archivo(nombreSeguro(archivo), tipo, Arrays.copyOfRange(cuerpo, datosIni, sig)));
                } else if (largo <= MAX_CAMPO_TEXTO) {
                    String texto = new String(cuerpo, datosIni, largo, StandardCharsets.UTF_8);
                    campos.put(campo, texto);
                    valores.computeIfAbsent(campo, k -> new ArrayList<>()).add(texto);
                }
            }
            pos = sig + 2;
        }
        return new Formulario(campos, valores, archivos);
    }

    /** Quita rutas y caracteres de control del nombre que envía el navegador. */
    public static String nombreSeguro(String nombre) {
        String n = nombre == null ? "" : nombre.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"<>|:*?]", "_").trim();
        if (n.isEmpty() || n.equals(".") || n.equals("..")) n = "archivo";
        return n.length() > 150 ? n.substring(n.length() - 150) : n;
    }

    private static String limite(String contentType) {
        if (contentType == null) return null;
        for (String p : contentType.split(";")) {
            String t = p.trim();
            if (t.toLowerCase(Locale.ROOT).startsWith("boundary=")) {
                String v = t.substring(9).trim();
                return v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"") ? v.substring(1, v.length() - 1) : v;
            }
        }
        return null;
    }

    private static String valor(String disposicion, String clave) {
        String buscado = clave + "=\"";
        int i = 0;
        while ((i = disposicion.indexOf(buscado, i)) >= 0) {
            if (i == 0 || disposicion.charAt(i - 1) == ' ' || disposicion.charAt(i - 1) == ';') {
                int ini = i + buscado.length(), fin = disposicion.indexOf('"', ini);
                if (fin < 0) return null;
                return new String(disposicion.substring(ini, fin).getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
            }
            i += buscado.length();
        }
        return null;
    }

    private static int indice(byte[] datos, byte[] patron, int desde) {
        outer:
        for (int i = Math.max(0, desde); i <= datos.length - patron.length; i++) {
            for (int j = 0; j < patron.length; j++) if (datos[i + j] != patron[j]) continue outer;
            return i;
        }
        return -1;
    }
}
