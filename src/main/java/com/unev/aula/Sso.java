package com.unev.aula;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Inicio de sesión único entre SIGE y el Aula. SIGE firma un token corto con un secreto compartido:
 * {@code v1.<payload base64url>.<firma HMAC-SHA256 base64url>}. El Aula lo verifica, comprueba que no haya
 * caducado y que no se haya usado antes. Esta misma lógica (crear) vive en SIGE en AulaSso.
 */
public final class Sso {
    public static final int TTL_SEGUNDOS = 60;
    private static final int MAX_FUTURO_SEGUNDOS = 300;
    private static final SecureRandom AZAR = new SecureRandom();

    public record Reclamo(int id, String rol, String nombre, String jti, long exp) { }

    private Sso() { }

    public static String crear(String secreto, int id, String rol, String nombre, long ahoraSeg) {
        byte[] azar = new byte[12];
        AZAR.nextBytes(azar);
        return crear(secreto, id, rol, nombre, ahoraSeg, Base64.getUrlEncoder().withoutPadding().encodeToString(azar));
    }

    /** Variante determinista (jti dado) usada por las pruebas. */
    public static String crear(String secreto, int id, String rol, String nombre, long ahoraSeg, String jti) {
        String carga = "id=" + id + "&rol=" + enc(rol) + "&nombre=" + enc(nombre) + "&exp=" + (ahoraSeg + TTL_SEGUNDOS) + "&jti=" + enc(jti);
        String b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(carga.getBytes(StandardCharsets.UTF_8));
        return "v1." + b64 + "." + firmar(secreto, b64);
    }

    public static Optional<Reclamo> verificar(String secreto, String token, long ahoraSeg) {
        if (token == null || token.length() > 2048) return Optional.empty();
        String[] partes = token.split("\\.", -1);
        if (partes.length != 3 || !partes[0].equals("v1")) return Optional.empty();
        byte[] esperada = firmar(secreto, partes[1]).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(esperada, partes[2].getBytes(StandardCharsets.US_ASCII))) return Optional.empty();
        try {
            String carga = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8);
            Map<String, String> c = new java.util.HashMap<>();
            for (String par : carga.split("&")) {
                String[] kv = par.split("=", 2);
                if (kv.length == 2) c.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            }
            int id = Integer.parseInt(c.getOrDefault("id", ""));
            long exp = Long.parseLong(c.getOrDefault("exp", ""));
            String rol = c.getOrDefault("rol", ""), jti = c.getOrDefault("jti", "");
            if (id <= 0 || rol.isBlank() || jti.isBlank() || ahoraSeg > exp || exp > ahoraSeg + MAX_FUTURO_SEGUNDOS) return Optional.empty();
            return Optional.of(new Reclamo(id, rol, c.getOrDefault("nombre", ""), jti, exp));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String firmar(String secreto, String dato) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(dato.getBytes(StandardCharsets.US_ASCII)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String enc(String s) { return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8); }

    /** Memoria de tokens ya usados: un token solo sirve una vez. */
    public static final class UsoUnico {
        private final ConcurrentHashMap<String, Long> usados = new ConcurrentHashMap<>();

        /** @return true si es la primera vez que se ve el jti. */
        public boolean registrar(String jti, long exp, long ahoraSeg) {
            usados.values().removeIf(v -> v < ahoraSeg);
            return usados.putIfAbsent(jti, exp) == null;
        }
    }
}
