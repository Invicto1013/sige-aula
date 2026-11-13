package com.unev.aula;

import static org.junit.jupiter.api.Assertions.*;

import com.unev.aula.Domino.Estado;
import com.unev.aula.Domino.Ficha;
import com.unev.aula.Domino.Invalida;
import com.unev.aula.Domino.Partida;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DominoTest {
    private static Partida iniciada(long semilla) {
        Partida p = new Partida("t", 1, "Ana", 0);
        p.unir(2, "Luis", new Random(semilla), 0);
        return p;
    }

    @Test void juegoCompletoTiene28FichasDistintas() {
        List<Ficha> j = Domino.juegoCompleto();
        assertEquals(28, j.size());
        assertEquals(28, new HashSet<>(j).size());
    }

    @Test void alUnirseSeRepartenSieteYElPozoTieneCatorce() {
        Partida p = iniciada(1);
        assertEquals(Estado.JUGANDO, p.estado());
        assertEquals(14, p.pozo());
        assertEquals(1, p.mesa().size());
        assertEquals(28, p.mano(1).size() + p.mano(2).size() + p.pozo() + p.mesa().size());
        assertEquals(6, Math.min(p.mano(1).size(), p.mano(2).size()));
        assertEquals(p.mano(1).size() == 6 ? 2 : 1, p.turno());
    }

    @Test void noSePuedeUnirUnoMismoNiUnaPartidaLlena() {
        Partida p = new Partida("t", 1, "Ana", 0);
        assertEquals("propia", assertThrows(Invalida.class, () -> p.unir(1, "Ana", new Random(1), 0)).codigo());
        p.unir(2, "Luis", new Random(1), 0);
        assertEquals("llena", assertThrows(Invalida.class, () -> p.unir(3, "Eva", new Random(1), 0)).codigo());
    }

    @Test void soloJuegaQuienTieneElTurnoYSoloFichasPropias() {
        Partida p = iniciada(3);
        int turno = p.turno(), otro = turno == 1 ? 2 : 1;
        Ficha f = p.mano(turno).get(0);
        assertEquals("no-es-tu-turno", assertThrows(Invalida.class, () -> p.jugar(otro, p.mano(otro).get(0), true, 1)).codigo());
        assertEquals("no-participa", assertThrows(Invalida.class, () -> p.jugar(99, f, true, 1)).codigo());
        Ficha ajena = p.mano(otro).stream().filter(x -> p.mano(turno).stream().noneMatch(x::igual)).findFirst().orElseThrow();
        assertEquals("no-tienes", assertThrows(Invalida.class, () -> p.jugar(turno, ajena, true, 1)).codigo());
    }

    @Test void unaFichaQueNoEncajaSeRechazaYUnaQueEncajaSeGira() {
        Partida p = iniciada(5);
        int n = p.turno();
        for (Ficha f : p.mano(n)) {
            if (!p.encaja(f, true) && !p.encaja(f, false)) {
                assertEquals("no-encaja", assertThrows(Invalida.class, () -> p.jugar(p.turno() == 1 ? 1 : 2, f, true, 1)).codigo());
                break;
            }
        }
        for (Ficha f : p.mano(n)) {
            if (p.encaja(f, false)) {
                p.jugar(n, f, false, 2);
                List<Ficha> mesa = p.mesa();
                assertEquals(mesa.get(mesa.size() - 2).b(), mesa.get(mesa.size() - 1).a());
                return;
            }
        }
    }

    @Test void robarSoloSinJugadaYPasarSoloConPozoVacio() {
        Partida p = iniciada(7);
        for (int n = 1; n <= 2; n++) {
            final int jugador = n;
            if (p.puedeJugar(n)) {
                assertFalse(p.puedeRobar(n));
                assertFalse(p.puedePasar(n));
                if (p.turno() == n) {
                    assertEquals("tienes-jugada", assertThrows(Invalida.class, () -> p.robar(jugador, 1)).codigo());
                    assertEquals("tienes-jugada", assertThrows(Invalida.class, () -> p.pasar(jugador, 1)).codigo());
                }
            }
        }
    }

    @Test void partidasAleatoriasTerminanConservandoLasFichas() {
        Random r = new Random(11);
        for (int g = 0; g < 300; g++) {
            Partida p = new Partida("s", 1, "A", 0);
            p.unir(2, "B", r, 0);
            int guardia = 0;
            while (p.estado() == Estado.JUGANDO && guardia++ < 500) {
                int n = p.turno();
                boolean hecho = false;
                for (Ficha f : p.mano(n)) {
                    for (boolean izq : new boolean[] {true, false}) {
                        if (p.encaja(f, izq)) { p.jugar(n, f, izq, 1); hecho = true; break; }
                    }
                    if (hecho) break;
                }
                if (!hecho) { if (p.puedeRobar(n)) p.robar(n, 1); else p.pasar(n, 1); }
                assertEquals(28, p.mano(1).size() + p.mano(2).size() + p.pozo() + p.mesa().size());
                Set<String> vistas = new HashSet<>();
                List<Ficha> mesa = p.mesa();
                for (int i = 0; i < mesa.size(); i++) {
                    assertTrue(vistas.add(Math.min(mesa.get(i).a(), mesa.get(i).b()) + "-" + Math.max(mesa.get(i).a(), mesa.get(i).b())));
                    if (i > 0) assertEquals(mesa.get(i - 1).b(), mesa.get(i).a());
                }
            }
            assertEquals(Estado.TERMINADA, p.estado());
            if (p.motivo().equals("DOMINO")) assertTrue(p.mano(p.ganador()).isEmpty());
        }
    }

    @Test void abandonarDaLaVictoriaAlRival() {
        Partida p = iniciada(9);
        p.abandonar(1, 5);
        assertEquals(Estado.TERMINADA, p.estado());
        assertEquals(2, p.ganador());
        assertEquals("ABANDONO", p.motivo());
    }

    @Test void fichaSeLeeDelFormularioYSeValida() {
        assertEquals(new Ficha(3, 5), Ficha.de("3-5"));
        assertThrows(Invalida.class, () -> Ficha.de("9-1"));
        assertThrows(Invalida.class, () -> Ficha.de("x"));
        assertThrows(Invalida.class, () -> Ficha.de(null));
    }

    @Test void salaUneAdversariosYNoDuplicaPartidasDelMismoUsuario() {
        SalaDomino s = new SalaDomino(new Random(1));
        Partida p = s.crear(1, "Ana", 0);
        assertSame(p, s.crear(1, "Ana", 1));
        assertEquals(1, s.abiertas(2, 2).size());
        assertEquals(0, s.abiertas(1, 2).size());
        s.unir(p.id(), 2, "Luis", 3);
        assertEquals(0, s.abiertas(3, 4).size());
        assertEquals(1, s.deUsuario(2, 4).size());
        assertEquals("ya-juegas", assertThrows(Invalida.class, () -> {
            Partida otra = s.crear(3, "Eva", 5);
            s.unir(otra.id(), 2, "Luis", 6);
        }).codigo());
        assertEquals("no-existe", assertThrows(Invalida.class, () -> s.unir("nada", 4, "Zoe", 7)).codigo());
    }

    @Test void partidasViejasSeDescartan() {
        SalaDomino s = new SalaDomino(new Random(1));
        Partida p = s.crear(1, "Ana", 0);
        assertEquals(1, s.abiertas(2, 1).size());
        assertEquals(0, s.abiertas(2, SalaDomino.CADUCA_MS + 10).size());
        assertNull(s.obtener(p.id()));
    }
}
