package com.unev.aula;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Dominó doble seis para dos jugadores (lógica pura, sin HTTP ni base de datos).
 * Se reparten 7 fichas a cada uno y las 14 restantes forman el pozo. Empieza quien tiene el doble más alto
 * (o, sin dobles, la ficha de más puntos), que juega esa ficha automáticamente. Si no se puede jugar se roba del pozo;
 * con el pozo vacío se pasa. Gana quien se queda sin fichas o, si el juego se tranca, quien suma menos puntos.
 */
public final class Domino {
    private Domino() { }

    public enum Estado { ESPERA, JUGANDO, TERMINADA }

    /** Error de regla: el código se muestra como aviso en la partida. */
    public static final class Invalida extends RuntimeException {
        private final String codigo;
        public Invalida(String codigo) { super(codigo, null, false, false); this.codigo = codigo; }
        public String codigo() { return codigo; }
    }

    public record Ficha(int a, int b) {
        public Ficha {
            if (a < 0 || a > 6 || b < 0 || b > 6) throw new IllegalArgumentException("ficha fuera de rango");
        }
        public int puntos() { return a + b; }
        public boolean doble() { return a == b; }
        public boolean tiene(int n) { return a == n || b == n; }
        public Ficha girada() { return new Ficha(b, a); }
        /** Misma ficha sin importar la orientación. */
        public boolean igual(Ficha o) { return (a == o.a && b == o.b) || (a == o.b && b == o.a); }
        public String id() { return a + "-" + b; }

        public static Ficha de(String id) {
            if (id == null) throw new Invalida("ficha");
            String[] p = id.split("-");
            if (p.length != 2) throw new Invalida("ficha");
            try { return new Ficha(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim())); }
            catch (IllegalArgumentException e) { throw new Invalida("ficha"); }
        }
    }

    public static List<Ficha> juegoCompleto() {
        List<Ficha> l = new ArrayList<>();
        for (int a = 0; a <= 6; a++) for (int b = a; b <= 6; b++) l.add(new Ficha(a, b));
        return l;
    }

    public static final class Partida {
        private final String id;
        private final int idJ1;
        private final String nombreJ1;
        private int idJ2;
        private String nombreJ2 = "";
        private Estado estado = Estado.ESPERA;
        private final List<Ficha> mano1 = new ArrayList<>(), mano2 = new ArrayList<>(), pozo = new ArrayList<>();
        private final List<Ficha> mesa = new ArrayList<>();   // de izquierda a derecha, ya orientadas
        private int turno;            // 1 o 2
        private int pasesSeguidos;
        private int ganador;          // 0 = nadie / empate, 1 o 2
        private String motivo = "";   // DOMINO | TRANQUE
        private long actualizada;

        public Partida(String id, int idJ1, String nombreJ1, long ahora) {
            this.id = id; this.idJ1 = idJ1; this.nombreJ1 = nombreJ1; this.actualizada = ahora;
        }

        public synchronized String id() { return id; }
        public synchronized Estado estado() { return estado; }
        public synchronized long actualizada() { return actualizada; }
        public synchronized int idJ1() { return idJ1; }
        public synchronized int idJ2() { return idJ2; }
        public synchronized String nombreJ1() { return nombreJ1; }
        public synchronized String nombreJ2() { return nombreJ2; }
        public synchronized int turno() { return turno; }
        public synchronized int ganador() { return ganador; }
        public synchronized String motivo() { return motivo; }
        public synchronized int pozo() { return pozo.size(); }
        public synchronized List<Ficha> mesa() { return List.copyOf(mesa); }

        public synchronized boolean participa(int idUsuario) { return idUsuario == idJ1 || (estado != Estado.ESPERA && idUsuario == idJ2); }

        /** Número de jugador (1 o 2) de ese usuario, o 0 si no participa. */
        public synchronized int numero(int idUsuario) { return idUsuario == idJ1 ? 1 : (estado != Estado.ESPERA && idUsuario == idJ2) ? 2 : 0; }

        public synchronized List<Ficha> mano(int numero) { return List.copyOf(numero == 1 ? mano1 : mano2); }
        public synchronized int fichasRivalDe(int numero) { return (numero == 1 ? mano2 : mano1).size(); }
        public synchronized int puntosMano(int numero) { return (numero == 1 ? mano1 : mano2).stream().mapToInt(Ficha::puntos).sum(); }

        public synchronized int extremoIzq() { return mesa.isEmpty() ? -1 : mesa.get(0).a(); }
        public synchronized int extremoDer() { return mesa.isEmpty() ? -1 : mesa.get(mesa.size() - 1).b(); }

        public synchronized String nombreDe(int numero) { return numero == 1 ? nombreJ1 : nombreJ2; }

        /** El segundo jugador entra, se reparten las fichas y se juega la primera. */
        public synchronized void unir(int idUsuario, String nombre, Random rnd, long ahora) {
            if (estado != Estado.ESPERA) throw new Invalida("llena");
            if (idUsuario == idJ1) throw new Invalida("propia");
            idJ2 = idUsuario; nombreJ2 = nombre;
            List<Ficha> baraja = juegoCompleto();
            Collections.shuffle(baraja, rnd);
            mano1.addAll(baraja.subList(0, 7));
            mano2.addAll(baraja.subList(7, 14));
            pozo.addAll(baraja.subList(14, 28));
            estado = Estado.JUGANDO;
            iniciar();
            actualizada = ahora;
        }

        private void iniciar() {
            int mejor = -1, quien = 1;
            Ficha primera = null;
            for (int n = 1; n <= 2; n++) {
                for (Ficha f : n == 1 ? mano1 : mano2) {
                    int valor = (f.doble() ? 100 : 0) + f.puntos();
                    if (valor > mejor) { mejor = valor; quien = n; primera = f; }
                }
            }
            (quien == 1 ? mano1 : mano2).remove(primera);
            mesa.add(primera);
            turno = quien == 1 ? 2 : 1;
        }

        public synchronized boolean puedeJugar(int numero) {
            for (Ficha f : numero == 1 ? mano1 : mano2) if (encaja(f, true) || encaja(f, false)) return true;
            return false;
        }

        /** Puede robar: es su turno, no tiene jugada y queda pozo. */
        public synchronized boolean puedeRobar(int numero) { return estado == Estado.JUGANDO && turno == numero && !puedeJugar(numero) && !pozo.isEmpty(); }

        public synchronized boolean puedePasar(int numero) { return estado == Estado.JUGANDO && turno == numero && !puedeJugar(numero) && pozo.isEmpty(); }

        /** ¿Encaja la ficha en el extremo indicado (true = izquierda)? */
        public synchronized boolean encaja(Ficha f, boolean izquierda) {
            if (mesa.isEmpty()) return true;
            return f.tiene(izquierda ? extremoIzq() : extremoDer());
        }

        public synchronized void jugar(int idUsuario, Ficha ficha, boolean izquierda, long ahora) {
            int n = verificarTurno(idUsuario);
            List<Ficha> mano = n == 1 ? mano1 : mano2;
            Ficha propia = null;
            for (Ficha f : mano) if (f.igual(ficha)) { propia = f; break; }
            if (propia == null) throw new Invalida("no-tienes");
            if (!encaja(propia, izquierda)) throw new Invalida("no-encaja");
            if (izquierda) {
                mesa.add(0, propia.b() == extremoIzq() ? propia : propia.girada());
            } else {
                mesa.add(propia.a() == extremoDer() ? propia : propia.girada());
            }
            mano.remove(propia);
            pasesSeguidos = 0;
            actualizada = ahora;
            if (mano.isEmpty()) { terminar(n, "DOMINO"); return; }
            turno = n == 1 ? 2 : 1;
        }

        public synchronized void robar(int idUsuario, long ahora) {
            int n = verificarTurno(idUsuario);
            if (puedeJugar(n)) throw new Invalida("tienes-jugada");
            if (pozo.isEmpty()) throw new Invalida("pozo-vacio");
            (n == 1 ? mano1 : mano2).add(pozo.remove(pozo.size() - 1));
            actualizada = ahora;
        }

        public synchronized void pasar(int idUsuario, long ahora) {
            int n = verificarTurno(idUsuario);
            if (puedeJugar(n)) throw new Invalida("tienes-jugada");
            if (!pozo.isEmpty()) throw new Invalida("roba-primero");
            pasesSeguidos++;
            actualizada = ahora;
            if (pasesSeguidos >= 2) {
                int p1 = puntosMano(1), p2 = puntosMano(2);
                terminar(p1 == p2 ? 0 : p1 < p2 ? 1 : 2, "TRANQUE");
                return;
            }
            turno = n == 1 ? 2 : 1;
        }

        /** Un jugador abandona: gana el rival. */
        public synchronized void abandonar(int idUsuario, long ahora) {
            if (estado != Estado.JUGANDO) throw new Invalida("no-jugando");
            int n = numero(idUsuario);
            if (n == 0) throw new Invalida("no-participa");
            actualizada = ahora;
            terminar(n == 1 ? 2 : 1, "ABANDONO");
        }

        private int verificarTurno(int idUsuario) {
            if (estado != Estado.JUGANDO) throw new Invalida("no-jugando");
            int n = numero(idUsuario);
            if (n == 0) throw new Invalida("no-participa");
            if (turno != n) throw new Invalida("no-es-tu-turno");
            return n;
        }

        private void terminar(int quien, String porque) { estado = Estado.TERMINADA; ganador = quien; motivo = porque; }
    }
}
