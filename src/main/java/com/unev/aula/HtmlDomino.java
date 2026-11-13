package com.unev.aula;

import static com.unev.aula.Html.esc;

import com.unev.aula.Domino.Estado;
import com.unev.aula.Domino.Ficha;
import com.unev.aula.Domino.Partida;
import java.util.List;
import java.util.Map;

/** Páginas del dominó. Sin scripts: cada jugada es un formulario y la página se refresca sola con meta refresh. */
public final class HtmlDomino {
    private HtmlDomino() { }

    private static final Map<String, String> AVISOS = Map.ofEntries(
            Map.entry("no-es-tu-turno", "Todavía no es tu turno."),
            Map.entry("no-encaja", "Esa ficha no encaja en ese extremo."),
            Map.entry("no-tienes", "No tienes esa ficha."),
            Map.entry("tienes-jugada", "Tienes una jugada posible: no puedes robar ni pasar."),
            Map.entry("pozo-vacio", "El pozo está vacío."),
            Map.entry("roba-primero", "Todavía quedan fichas en el pozo: roba primero."),
            Map.entry("no-jugando", "La partida no está en juego."),
            Map.entry("ficha", "Ficha no válida."),
            Map.entry("llena", "Esa partida ya tiene dos jugadores."),
            Map.entry("propia", "No puedes unirte a tu propia partida."),
            Map.entry("ya-juegas", "Ya estás jugando otra partida."),
            Map.entry("no-existe", "Esa partida ya no existe."),
            Map.entry("sala-llena", "Hay demasiadas partidas abiertas; inténtalo en un rato."),
            Map.entry("no-participa", "No participas en esa partida."));

    private static String aviso(String codigo) {
        if (codigo == null || codigo.isBlank()) return "";
        return Html.aviso("alert", AVISOS.getOrDefault(codigo, "No se pudo completar la acción."));
    }

    /** Mitad de ficha con puntos (número) y texto accesible. */
    static String ficha(Ficha f, String clase) {
        return "<span class='ficha " + clase + "' role='img' aria-label='ficha " + f.a() + " con " + f.b() + "'><i>" + f.a() + "</i><i>" + f.b() + "</i></span>";
    }

    public static String lobby(Sesiones.Sesion s, List<Partida> abiertas, List<Partida> mias, String error) {
        StringBuilder sb = new StringBuilder("<section class='card'><h1>🎲 Dominó</h1>");
        sb.append("<p>Juega contra otro usuario del Aula. Cada jugador recibe 7 fichas; si no puedes jugar, robas del pozo, y gana quien se queda sin fichas primero.</p>")
          .append(aviso(error))
          .append("<form method='post' action='/domino-crear'>").append(Html.csrf(s)).append("<button type='submit' class='primary'>Crear una partida nueva</button></form></section>");
        sb.append("<section class='card'><h2>Mis partidas</h2>");
        if (mias.isEmpty()) sb.append("<p class='muted'>No tienes partidas activas.</p>");
        else {
            sb.append("<ul class='lista-domino'>");
            for (Partida p : mias) {
                int yo = p.numero(s.idUsuario());
                String rival = p.estado() == Estado.ESPERA ? "esperando rival" : "contra " + esc(p.nombreDe(yo == 1 ? 2 : 1));
                sb.append("<li><a href='/domino-partida?id=").append(esc(p.id())).append("'>Partida ").append(esc(p.id())).append("</a> · ").append(rival)
                  .append(" · ").append(estadoTexto(p, yo)).append("</li>");
            }
            sb.append("</ul>");
        }
        sb.append("</section><section class='card'><h2>Partidas abiertas</h2>");
        if (abiertas.isEmpty()) sb.append("<p class='muted'>Nadie está esperando rival ahora. Crea una partida y comparte con un compañero que entre al Aula.</p>");
        else {
            sb.append("<ul class='lista-domino'>");
            for (Partida p : abiertas) {
                sb.append("<li><form method='post' action='/domino-unir' class='inline'>").append(Html.csrf(s))
                  .append("<input type='hidden' name='id' value='").append(esc(p.id())).append("'>").append(esc(p.nombreJ1()))
                  .append(" espera rival ").append("<button type='submit'>Unirme</button></form></li>");
            }
            sb.append("</ul>");
        }
        return sb.append("</section>").toString();
    }

    static String estadoTexto(Partida p, int yo) {
        return switch (p.estado()) {
            case ESPERA -> "en espera";
            case JUGANDO -> p.turno() == yo ? "tu turno" : "turno del rival";
            case TERMINADA -> p.ganador() == 0 ? "empate" : p.ganador() == yo ? "ganaste" : "perdiste";
        };
    }

    public static String partida(Sesiones.Sesion s, Partida p, String error) {
        int yo = p.numero(s.idUsuario());
        int rival = yo == 1 ? 2 : 1;
        StringBuilder sb = new StringBuilder("<section class='card domino'><h1>🎲 Dominó</h1>").append(aviso(error));
        if (p.estado() == Estado.ESPERA) {
            sb.append("<p>Esperando a que otro usuario se una a tu partida <b>").append(esc(p.id())).append("</b>… Esta página se actualiza sola.</p>")
              .append("<p><a href='/domino'>← Volver a las partidas</a></p></section>");
            return sb.toString();
        }
        sb.append("<p class='dom-jugadores'><b>").append(esc(p.nombreDe(yo))).append("</b> (tú) contra <b>").append(esc(p.nombreDe(rival))).append("</b></p>");
        sb.append(Html.aviso("exito", mensaje(p, yo)));

        sb.append("<h2>Mesa</h2><div class='mesa' aria-label='Fichas en la mesa'>");
        for (Ficha f : p.mesa()) sb.append(ficha(f, ""));
        sb.append("</div><p class='muted'>Extremos: <b>").append(p.extremoIzq()).append("</b> y <b>").append(p.extremoDer()).append("</b> · Pozo: ")
          .append(p.pozo()).append(" fichas · ").append(esc(p.nombreDe(rival))).append(" tiene ").append(p.fichasRivalDe(yo)).append(" fichas</p>");

        boolean mio = p.estado() == Estado.JUGANDO && p.turno() == yo;
        sb.append("<h2>Tus fichas</h2><div class='mano'>");
        for (Ficha f : p.mano(yo)) {
            boolean izq = mio && p.encaja(f, true), der = mio && p.encaja(f, false);
            sb.append("<div class='celda'>").append(ficha(f, izq || der ? "jugable" : "")).append("<div class='lados'>");
            if (izq) sb.append(botonJugada(s, p, f, "I", "◀ Izquierda"));
            if (der) sb.append(botonJugada(s, p, f, "D", "Derecha ▶"));
            sb.append("</div></div>");
        }
        sb.append("</div>");
        if (p.puedeRobar(yo)) {
            sb.append("<form method='post' action='/domino-robar'>").append(Html.csrf(s)).append(oculto(p))
              .append("<p>No tienes jugada: roba una ficha del pozo.</p><button type='submit' class='primary'>Robar del pozo (").append(p.pozo()).append(")</button></form>");
        } else if (p.puedePasar(yo)) {
            sb.append("<form method='post' action='/domino-pasar'>").append(Html.csrf(s)).append(oculto(p))
              .append("<p>No tienes jugada y el pozo está vacío.</p><button type='submit' class='primary'>Pasar turno</button></form>");
        }
        if (p.estado() == Estado.JUGANDO) {
            sb.append("<form method='post' action='/domino-abandonar' class='abandonar'>").append(Html.csrf(s)).append(oculto(p))
              .append("<button type='submit' class='link'>Abandonar la partida</button></form>");
        }
        sb.append("<p><a href='/domino'>← Volver a las partidas</a></p></section>");
        return sb.toString();
    }

    private static String oculto(Partida p) { return "<input type='hidden' name='id' value='" + esc(p.id()) + "'>"; }

    private static String botonJugada(Sesiones.Sesion s, Partida p, Ficha f, String lado, String texto) {
        return "<form method='post' action='/domino-jugar' class='inline'>" + Html.csrf(s) + oculto(p)
                + "<input type='hidden' name='ficha' value='" + f.id() + "'><input type='hidden' name='lado' value='" + lado + "'>"
                + "<button type='submit'>" + texto + "</button></form>";
    }

    static String mensaje(Partida p, int yo) {
        int rival = yo == 1 ? 2 : 1;
        if (p.estado() == Estado.TERMINADA) {
            String como = switch (p.motivo()) {
                case "DOMINO" -> "se quedó sin fichas";
                case "TRANQUE" -> "el juego se trancó y tiene menos puntos";
                default -> "el rival abandonó";
            };
            if (p.ganador() == 0) return "Empate: el juego se trancó con los mismos puntos (" + p.puntosMano(yo) + ").";
            if (p.ganador() == yo) return "¡Ganaste! " + (p.motivo().equals("ABANDONO") ? "Tu rival abandonó." : "Te " + (p.motivo().equals("DOMINO") ? "quedaste sin fichas." : "quedaron menos puntos que a tu rival (" + p.puntosMano(yo) + " contra " + p.puntosMano(rival) + ")."));
            return "Perdiste: " + p.nombreDe(rival) + " " + como + ".";
        }
        return p.turno() == yo ? "Es tu turno." : "Turno de " + p.nombreDe(rival) + ". La página se actualiza sola.";
    }
}
