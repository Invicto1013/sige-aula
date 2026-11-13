package com.unev.aula;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Salas de dominó en memoria. Las partidas viven mientras el servicio esté despierto (Render gratis lo duerme tras un rato sin visitas);
 * una partida sin movimientos por más de {@value #CADUCA_MS} ms se descarta sola.
 */
public final class SalaDomino {
    static final long CADUCA_MS = 3 * 60 * 60 * 1000L;
    static final int MAXIMO = 200;

    private final Map<String, Domino.Partida> partidas = new ConcurrentHashMap<>();
    private final SecureRandom azar = new SecureRandom();
    private final Random barajar;

    public SalaDomino() { this(new SecureRandom()); }
    SalaDomino(Random barajar) { this.barajar = barajar; }

    private void limpiar(long ahora) {
        partidas.values().removeIf(p -> p.estado() == Domino.Estado.TERMINADA ? ahora - p.actualizada() > 30 * 60 * 1000L : ahora - p.actualizada() > CADUCA_MS);
    }

    /** Crea una partida; si el usuario ya tiene una sin terminar, devuelve esa. */
    public synchronized Domino.Partida crear(int idUsuario, String nombre, long ahora) {
        limpiar(ahora);
        for (Domino.Partida p : partidas.values()) {
            if (p.estado() != Domino.Estado.TERMINADA && (p.idJ1() == idUsuario || (p.estado() == Domino.Estado.JUGANDO && p.idJ2() == idUsuario))) return p;
        }
        if (partidas.size() >= MAXIMO) throw new Domino.Invalida("sala-llena");
        byte[] b = new byte[4];
        azar.nextBytes(b);
        Domino.Partida p = new Domino.Partida(HexFormat.of().formatHex(b), idUsuario, nombre, ahora);
        partidas.put(p.id(), p);
        return p;
    }

    public Domino.Partida obtener(String id) { return id == null ? null : partidas.get(id); }

    public synchronized Domino.Partida unir(String id, int idUsuario, String nombre, long ahora) {
        limpiar(ahora);
        Domino.Partida p = obtener(id);
        if (p == null) throw new Domino.Invalida("no-existe");
        for (Domino.Partida o : partidas.values()) {
            if (o != p && o.estado() == Domino.Estado.JUGANDO && (o.idJ1() == idUsuario || o.idJ2() == idUsuario)) throw new Domino.Invalida("ya-juegas");
        }
        p.unir(idUsuario, nombre, barajar, ahora);
        return p;
    }

    /** Partidas esperando rival, sin las del propio usuario. */
    public List<Domino.Partida> abiertas(int idUsuario, long ahora) {
        limpiar(ahora);
        List<Domino.Partida> l = new ArrayList<>();
        for (Domino.Partida p : partidas.values()) if (p.estado() == Domino.Estado.ESPERA && p.idJ1() != idUsuario) l.add(p);
        l.sort(Comparator.comparingLong(Domino.Partida::actualizada).reversed());
        return l;
    }

    /** Partidas del usuario (en espera, en juego o recién terminadas). */
    public List<Domino.Partida> deUsuario(int idUsuario, long ahora) {
        limpiar(ahora);
        List<Domino.Partida> l = new ArrayList<>();
        for (Domino.Partida p : partidas.values()) if (p.numero(idUsuario) != 0) l.add(p);
        l.sort(Comparator.comparingLong(Domino.Partida::actualizada).reversed());
        return l;
    }
}
