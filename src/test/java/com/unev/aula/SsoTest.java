package com.unev.aula;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SsoTest {
    static final String SECRETO = "secreto-de-prueba-con-mas-de-32-caracteres!";
    static final long T0 = 1_800_000_000L;

    @Test void tokenValidoSeVerifica() {
        String t = Sso.crear(SECRETO, 42, "PROFESOR", "Ana Pérez", T0, "j1");
        var r = Sso.verificar(SECRETO, t, T0 + 5).orElseThrow();
        assertEquals(42, r.id());
        assertEquals("PROFESOR", r.rol());
        assertEquals("Ana Pérez", r.nombre());
        assertEquals("j1", r.jti());
    }

    @Test void vectorFijoCompatibleConSige() {
        // El mismo vector se prueba en SIGE (AulaSsoTest): si cambia el formato, fallan los dos.
        String t = Sso.crear(SECRETO, 7, "ESTUDIANTE", "Luis Gómez", T0, "jti-fijo");
        assertEquals("v1.aWQ9NyZyb2w9RVNUVURJQU5URSZub21icmU9THVpcytHJUMzJUIzbWV6JmV4cD0xODAwMDAwMDYwJmp0aT1qdGktZmlqbw.A1-ctKVpcH1vmllhKoPtEmqW_HWjRSRyPI13GP7ezz0", t);
    }

    @Test void firmaAlteradaSeRechaza() {
        String t = Sso.crear(SECRETO, 1, "PROFESOR", "A", T0, "j");
        assertTrue(Sso.verificar(SECRETO, t.substring(0, t.length() - 2) + "xx", T0).isEmpty());
    }

    @Test void cargaAlteradaSeRechaza() {
        String t = Sso.crear(SECRETO, 1, "ESTUDIANTE", "A", T0, "j");
        String otro = Sso.crear(SECRETO, 2, "PROFESOR", "B", T0, "j");
        String mezcla = "v1." + otro.split("\\.")[1] + "." + t.split("\\.")[2];
        assertTrue(Sso.verificar(SECRETO, mezcla, T0).isEmpty());
    }

    @Test void secretoDistintoSeRechaza() {
        String t = Sso.crear(SECRETO, 1, "PROFESOR", "A", T0, "j");
        assertTrue(Sso.verificar(SECRETO + "x", t, T0).isEmpty());
    }

    @Test void caducadoSeRechaza() {
        String t = Sso.crear(SECRETO, 1, "PROFESOR", "A", T0, "j");
        assertTrue(Sso.verificar(SECRETO, t, T0 + Sso.TTL_SEGUNDOS).isPresent());
        assertTrue(Sso.verificar(SECRETO, t, T0 + Sso.TTL_SEGUNDOS + 1).isEmpty());
    }

    @Test void tokenDelFuturoLejanoSeRechaza() {
        String t = Sso.crear(SECRETO, 1, "PROFESOR", "A", T0 + 10_000, "j");
        assertTrue(Sso.verificar(SECRETO, t, T0).isEmpty());
    }

    @Test void basuraSeRechazaSinExcepciones() {
        assertTrue(Sso.verificar(SECRETO, null, T0).isEmpty());
        assertTrue(Sso.verificar(SECRETO, "", T0).isEmpty());
        assertTrue(Sso.verificar(SECRETO, "v1.a.b", T0).isEmpty());
        assertTrue(Sso.verificar(SECRETO, "v2.a.b", T0).isEmpty());
        assertTrue(Sso.verificar(SECRETO, "x".repeat(3000), T0).isEmpty());
    }

    @Test void unTokenSoloSirveUnaVez() {
        var uso = new Sso.UsoUnico();
        assertTrue(uso.registrar("abc", T0 + 60, T0));
        assertFalse(uso.registrar("abc", T0 + 60, T0 + 1));
        assertTrue(uso.registrar("abc", T0 + 200, T0 + 100)); // el anterior ya caducó y se olvidó
    }

    @Test void nombresConCaracteresEspecialesViajanIntactos() {
        String nombre = "Mª José & Co = \"x\" ñ";
        String t = Sso.crear(SECRETO, 3, "PROFESOR", nombre, T0, "j");
        assertEquals(nombre, Sso.verificar(SECRETO, t, T0).orElseThrow().nombre());
    }
}
