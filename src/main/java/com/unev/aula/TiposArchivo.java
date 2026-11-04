package com.unev.aula;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Valida un archivo subido: la extension debe estar en la lista permitida Y el contenido debe empezar con la
 * firma que corresponde a esa extension (asi un .exe renombrado a .pdf se rechaza). Los archivos se descargan
 * siempre como adjunto con nosniff, pero esta lista evita guardar ejecutables o formatos inesperados.
 */
public final class TiposArchivo {

    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] GIF = {'G', 'I', 'F', '8'};
    private static final byte[] ZIP = {'P', 'K', 3, 4};
    private static final byte[] OLE = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0};
    private static final byte[] RTF = {'{', '\\', 'r', 't', 'f'};

    private static final Map<String, byte[]> FIRMAS = Map.ofEntries(
            Map.entry("pdf", PDF), Map.entry("png", PNG), Map.entry("jpg", JPG), Map.entry("jpeg", JPG),
            Map.entry("gif", GIF), Map.entry("docx", ZIP), Map.entry("xlsx", ZIP), Map.entry("pptx", ZIP),
            Map.entry("odt", ZIP), Map.entry("ods", ZIP), Map.entry("odp", ZIP),
            Map.entry("doc", OLE), Map.entry("xls", OLE), Map.entry("ppt", OLE), Map.entry("rtf", RTF));
    private static final Set<String> TEXTO = Set.of("txt", "csv", "md");

    private TiposArchivo() {
    }

    /** Extension en minusculas, sin punto; cadena vacia si no tiene. */
    static String extension(String nombre) {
        if (nombre == null) {
            return "";
        }
        int punto = nombre.lastIndexOf('.');
        return punto < 0 || punto == nombre.length() - 1 ? "" : nombre.substring(punto + 1).toLowerCase(Locale.ROOT).trim();
    }

    /** Verdadero si el archivo es de un tipo permitido y su contenido coincide con su extension. */
    public static boolean permitido(String nombre, byte[] datos) {
        if (nombre == null || nombre.isBlank() || nombre.length() > 200 || datos == null || datos.length == 0) {
            return false;
        }
        String ext = extension(nombre);
        if (TEXTO.contains(ext)) {
            return esTexto(datos);
        }
        byte[] firma = FIRMAS.get(ext);
        if (firma == null || datos.length < firma.length) {
            return false;
        }
        for (int i = 0; i < firma.length; i++) {
            if (datos[i] != firma[i]) {
                return false;
            }
        }
        return true;
    }

    /** Texto plano: sin bytes de control (salvo tabulacion y saltos de linea). */
    private static boolean esTexto(byte[] datos) {
        int revisar = Math.min(datos.length, 8192);
        for (int i = 0; i < revisar; i++) {
            int b = datos[i] & 0xFF;
            if (b < 32 && b != '\t' && b != '\n' && b != '\r') {
                return false;
            }
        }
        return true;
    }

    /** Lista de extensiones para mostrarla al usuario. */
    public static String lista() {
        return "PDF, imágenes (PNG, JPG, GIF), Word, Excel, PowerPoint, OpenDocument, RTF, TXT y CSV";
    }
}
