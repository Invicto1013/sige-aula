package com.unev.aula;

import static org.junit.jupiter.api.Assertions.*;

import com.unev.aula.Modelo.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

/** Flujos completos contra un servidor real en un puerto libre y un DAO simulado (sin MySQL). */
class ServidorTest {
    static final String SECRETO = "secreto-de-prueba-con-mas-de-32-caracteres!";
    static final String LIM = "----prueba";

    static class DaoFalso extends AulaDao {
        final List<String> llamadas = new ArrayList<>();
        final List<ArchivoGuardado> publicados = new ArrayList<>();
        Set<Integer> quitados = new HashSet<>();
        int n = 0;
        DaoFalso() { super(null); }

        @Override public Optional<Usuario> usuarioActivo(int id, String rol) {
            return id == 1 ? Optional.of(new Usuario(1, "PROFESOR", "Ana Pérez")) : id == 2 ? Optional.of(new Usuario(2, "ESTUDIANTE", "Luis Gómez")) : Optional.empty();
        }
        @Override public boolean haySesionBd() { return true; }
        @Override public List<Seccion> secciones(Usuario u) { return List.of(new Seccion(3, "Matemática", "1.º", "Lun 8:00")); }
        @Override public List<Publicacion> publicaciones(Usuario u) {
            return List.of(
                    new Publicacion(10, 3, "Matemática", "1.º", "TAREA", "Fracciones", "Resolver", LocalDate.now().plusDays(30), "2026-10-01 08:00", List.of(new Adjunto(7, "guia.pdf", 100)), null, 0, 0),
                    new Publicacion(11, 3, "Matemática", "1.º", "CLASE", "Repaso general", "Leer", null, "2026-10-02 08:00", List.of(), null, 0, 0));
        }
        @Override public Optional<Publicacion> publicacion(Usuario u, int id) { return publicaciones(u).stream().filter(p -> p.id() == id).findFirst(); }
        @Override public List<EntregaRecibida> entregasDelProfesor(int id) { return List.of(); }
        @Override public Map<Integer, List<Comentario>> comentarios(Collection<Integer> ids) { return Map.of(); }
        @Override public List<Anuncio> anuncios(Usuario u) { return List.of(new Anuncio(1, 3, "Matemática", "Ana Pérez", 1, "Examen el lunes", "2026-10-30 09:00")); }
        @Override public String guardarArchivo(byte[] datos) { return "key-" + (++n); }
        @Override public void eliminarArchivo(String key) { llamadas.add("descartar:" + key); }
        @Override public int publicar(int p, int s, String t, String ti, String d, LocalDate l, List<ArchivoGuardado> a) {
            if (s == 99) throw new Rechazo("permiso");
            llamadas.add("publicar:" + ti + ":" + a.size()); publicados.addAll(a); return 1;
        }
        @Override public void actualizar(int p, int m, String ti, String d, LocalDate l, Collection<Integer> q, List<ArchivoGuardado> a) {
            llamadas.add("actualizar:" + m + ":" + ti); quitados = new HashSet<>(q);
        }
        @Override public void eliminarPublicacion(int p, int m) {
            if (m == 999) throw new Rechazo("permiso");
            llamadas.add("eliminar:" + m);
        }
        @Override public void entregar(int e, int m, ArchivoGuardado a) { llamadas.add("entregar:" + m + ":" + a.nombre()); }
        @Override public void calificar(int p, int e, BigDecimal nota, String c) { llamadas.add("calificar:" + e + ":" + nota.toPlainString() + ":" + c); }
        @Override public void comentar(Usuario u, int m, String t) { llamadas.add("comentar:" + u.rol() + ":" + m + ":" + t); }
        @Override public void eliminarComentario(Usuario u, int c) { llamadas.add("quitar-comentario:" + c); }
        @Override public void anunciar(int p, int s, String t) { llamadas.add("anunciar:" + s + ":" + t); }
        @Override public void eliminarAnuncio(int p, int a) { llamadas.add("quitar-anuncio:" + a); }
    }

    static Config cfg() { return new Config(0, "jdbc:x", "", "", SECRETO, "https://sige.example", false, 2, 1024 * 1024, 4 * 1024 * 1024, java.time.ZoneId.of("America/Santo_Domingo")); }

    record R(int codigo, String cuerpo, HttpResponse<String> r) { String h(String n) { return r.headers().firstValue(n).orElse(""); } }

    final HttpClient c = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    R enviar(HttpRequest.Builder b) throws Exception {
        var r = c.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new R(r.statusCode(), r.body(), r);
    }

    R get(String url, String cookie) throws Exception {
        var b = HttpRequest.newBuilder(URI.create(url)).GET();
        if (cookie != null) b.header("Cookie", cookie);
        return enviar(b);
    }

    R post(String url, String cookie, String cuerpo) throws Exception {
        var b = HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(cuerpo));
        if (cookie != null) b.header("Cookie", cookie);
        return enviar(b);
    }

    /** partes: "campo=valor" o "campo=@archivo.txt:contenido". */
    R multipart(String url, String cookie, String... partes) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (String p : partes) {
            String campo = p.substring(0, p.indexOf('=')), valor = p.substring(p.indexOf('=') + 1);
            sb.append("--").append(LIM).append("\r\n");
            if (valor.startsWith("@")) {
                String nombre = valor.substring(1, valor.indexOf(':')), contenido = valor.substring(valor.indexOf(':') + 1);
                sb.append("Content-Disposition: form-data; name=\"").append(campo).append("\"; filename=\"").append(nombre).append("\"\r\nContent-Type: text/plain\r\n\r\n").append(contenido).append("\r\n");
            } else {
                sb.append("Content-Disposition: form-data; name=\"").append(campo).append("\"\r\n\r\n").append(valor).append("\r\n");
            }
        }
        sb.append("--").append(LIM).append("--\r\n");
        var b = HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "multipart/form-data; boundary=" + LIM).header("Cookie", cookie)
                .POST(HttpRequest.BodyPublishers.ofString(sb.toString(), StandardCharsets.UTF_8));
        return enviar(b);
    }

    String entrar(String base, int id, String rol, String nombre) throws Exception {
        String t = Sso.crear(SECRETO, id, rol, nombre, System.currentTimeMillis() / 1000);
        R r = get(base + "/sso?token=" + URLEncoder.encode(t, StandardCharsets.UTF_8), null);
        assertEquals(303, r.codigo());
        String sc = r.h("Set-Cookie");
        assertTrue(sc.contains("HttpOnly") && sc.contains("SameSite=Lax"));
        return sc.split(";")[0];
    }

    static String csrf(String html) { return html.replaceAll("(?s).*name='_csrf' value='([^']+)'.*", "$1"); }

    interface Prueba { void ejecutar(String base, DaoFalso dao) throws Exception; }

    void conServidor(Prueba p) throws Exception {
        DaoFalso dao = new DaoFalso();
        Servidor srv = new Servidor(cfg(), dao);
        srv.iniciar();
        try { p.ejecutar("http://localhost:" + srv.puerto(), dao); } finally { srv.detener(); }
    }

    @Test void accesoYSeguridadBasica() throws Exception {
        conServidor((base, dao) -> {
            assertEquals(200, get(base + "/health", null).codigo());
            assertEquals(200, get(base + "/health?db=1", null).codigo());
            assertEquals(404, get(base + "/no-existe", null).codigo());
            R sin = get(base + "/", null);
            assertEquals(401, sin.codigo());
            assertTrue(sin.cuerpo().contains("Entra desde SIGE"));
            assertTrue(get(base + "/styles.css", null).h("Content-Type").startsWith("text/css"));
            String csp = sin.h("Content-Security-Policy");
            assertTrue(csp.contains("script-src 'none'") && csp.contains("frame-ancestors 'none'"));
            assertEquals(401, get(base + "/sso?token=basura", null).codigo());
            assertEquals(401, get(base + "/tablon", null).codigo());
            assertEquals(401, get(base + "/archivo?adjunto=7", null).codigo());
            String t = Sso.crear(SECRETO, 1, "PROFESOR", "Ana Pérez", System.currentTimeMillis() / 1000);
            String url = base + "/sso?token=" + URLEncoder.encode(t, StandardCharsets.UTF_8);
            assertEquals(303, get(url, null).codigo());
            assertEquals(401, get(url, null).codigo(), "un token solo sirve una vez");
            String t2 = Sso.crear(SECRETO, 99, "PROFESOR", "Fantasma", System.currentTimeMillis() / 1000);
            assertEquals(403, get(base + "/sso?token=" + URLEncoder.encode(t2, StandardCharsets.UTF_8), null).codigo());
        });
    }

    @Test void profesorPublicaConVariosArchivosYCsrf() throws Exception {
        conServidor((base, dao) -> {
            String cookie = entrar(base, 1, "PROFESOR", "Ana Pérez");
            R home = get(base + "/", cookie);
            assertEquals(200, home.codigo());
            assertTrue(home.cuerpo().contains("Publicar una clase o tarea") && home.cuerpo().contains("Fracciones"));
            String token = csrf(home.cuerpo());

            assertEquals(403, post(base + "/publicar", cookie, "seccion=3&tipo=CLASE&titulo=X").codigo());
            assertTrue(dao.llamadas.isEmpty());

            R pub = multipart(base + "/publicar", cookie, "_csrf=" + token, "seccion=3", "tipo=TAREA", "titulo=Repaso", "descripcion=ok",
                    "archivo=@a.txt:AAA", "archivo=@b.txt:BBB");
            assertEquals(303, pub.codigo());
            assertEquals("/?ok=publicado", pub.h("Location"));
            assertEquals(List.of("publicar:Repaso:2"), dao.llamadas);
            assertEquals(List.of("a.txt", "b.txt"), dao.publicados.stream().map(ArchivoGuardado::nombre).toList());

            dao.llamadas.clear();
            assertEquals("/?error=datos", post(base + "/publicar", cookie, "_csrf=" + token + "&seccion=3&tipo=OTRO&titulo=Z").h("Location"));
            assertEquals("/?error=fecha", post(base + "/publicar", cookie, "_csrf=" + token + "&seccion=3&tipo=TAREA&titulo=Z&fecha_limite=31-12-2026").h("Location"));
            R ajena = post(base + "/publicar", cookie, "_csrf=" + token + "&seccion=99&tipo=CLASE&titulo=Ajena");
            assertEquals("/?error=permiso", ajena.h("Location"));
            assertTrue(dao.llamadas.isEmpty());
        });
    }

    @Test void seisArchivosSeRechazan() throws Exception {
        conServidor((base, dao) -> {
            String cookie = entrar(base, 1, "PROFESOR", "Ana Pérez");
            String token = csrf(get(base + "/", cookie).cuerpo());
            String[] partes = new String[4 + 6];
            partes[0] = "_csrf=" + token; partes[1] = "seccion=3"; partes[2] = "tipo=CLASE"; partes[3] = "titulo=Muchos";
            for (int i = 0; i < 6; i++) partes[4 + i] = "archivo=@f" + i + ".txt:x";
            assertEquals("/?error=maximo", multipart(base + "/publicar", cookie, partes).h("Location"));
            assertTrue(dao.publicados.isEmpty());
        });
    }

    @Test void origenDeOtroSitioSeRechazaAunqueElTokenSeaCorrecto() throws Exception {
        conServidor((base, dao) -> {
            String cookie = entrar(base, 1, "PROFESOR", "Ana Pérez");
            String token = csrf(get(base + "/", cookie).cuerpo());
            var b = HttpRequest.newBuilder(URI.create(base + "/publicar")).header("Cookie", cookie).header("Origin", "https://malo.example")
                    .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString("_csrf=" + token + "&seccion=3&tipo=CLASE&titulo=Q"));
            assertEquals(403, enviar(b).codigo());
            assertTrue(dao.llamadas.isEmpty());
        });
    }

    @Test void filtrosDeLaPantallaPrincipal() throws Exception {
        conServidor((base, dao) -> {
            String cookie = entrar(base, 2, "ESTUDIANTE", "Luis Gómez");
            R todo = get(base + "/", cookie);
            assertTrue(todo.cuerpo().contains("Fracciones") && todo.cuerpo().contains("Repaso general"));
            R soloClases = get(base + "/?tipo=CLASE", cookie);
            assertTrue(soloClases.cuerpo().contains("Repaso general"));
            assertFalse(soloClases.cuerpo().contains("<h3>Fracciones</h3>"));
            assertTrue(soloClases.cuerpo().contains("Mostrando 1 de 2"));
            R texto = get(base + "/?q=" + URLEncoder.encode("FRACCIÓN", StandardCharsets.UTF_8), cookie);
            assertTrue(texto.cuerpo().contains("<h3>Fracciones</h3>"));
            assertFalse(texto.cuerpo().contains("<h3>Repaso general</h3>"));
            R estado = get(base + "/?estado=pendiente", cookie);
            assertTrue(estado.cuerpo().contains("<h3>Fracciones</h3>") && !estado.cuerpo().contains("<h3>Repaso general</h3>"));
            assertTrue(get(base + "/?q=zzz", cookie).cuerpo().contains("Ninguna publicación coincide"));
            assertEquals(200, get(base + "/?seccion=abc&tipo=%27%3E%3Cscript%3E", cookie).codigo());
        });
    }

    @Test void estudianteEntregaYComentaPeroNoPublicaNiCalifica() throws Exception {
        conServidor((base, dao) -> {
            String cookie = entrar(base, 2, "ESTUDIANTE", "Luis Gómez");
            R home = get(base + "/", cookie);
            assertFalse(home.cuerpo().contains("Publicar una clase o tarea"));
            assertTrue(home.cuerpo().contains("action='/entregar'"));
            String token = csrf(home.cuerpo());

            R ent = multipart(base + "/entregar", cookie, "_csrf=" + token, "material=10", "archivo=@tarea.txt:hola");
            assertEquals("/?ok=entregado#pub-10", ent.h("Location"));
            assertEquals(List.of("entregar:10:tarea.txt"), dao.llamadas);
            assertEquals("/?error=sinarchivo", multipart(base + "/entregar", cookie, "_csrf=" + token, "material=10").h("Location"));

            dao.llamadas.clear();
            assertEquals("/?ok=comentado#pub-10", post(base + "/comentar", cookie, "_csrf=" + token + "&material=10&texto=Gracias").h("Location"));
            assertEquals(List.of("comentar:ESTUDIANTE:10:Gracias"), dao.llamadas);
            assertEquals("/?error=datos", post(base + "/comentar", cookie, "_csrf=" + token + "&material=10&texto=+++").h("Location"));
            assertEquals("/?error=datos", post(base + "/comentar", cookie, "_csrf=" + token + "&material=10&texto=" + "x".repeat(1001)).h("Location"));

            dao.llamadas.clear();
            assertEquals("/", post(base + "/publicar", cookie, "_csrf=" + token + "&seccion=3&tipo=CLASE&titulo=Hack").h("Location"));
            assertEquals("/", post(base + "/calificar", cookie, "_csrf=" + token + "&entrega=5&nota=100").h("Location"));
            assertEquals("/", post(base + "/eliminar", cookie, "_csrf=" + token + "&id=10&confirmar=si").h("Location"));
            assertEquals("/", post(base + "/anuncio", cookie, "_csrf=" + token + "&seccion=3&texto=Hola").h("Location"));
            assertTrue(dao.llamadas.isEmpty());
        });
    }

    @Test void profesorCalificaConNotaValida() throws Exception {
        conServidor((base, dao) -> {
            String cookie = entrar(base, 1, "PROFESOR", "Ana Pérez");
            String token = csrf(get(base + "/", cookie).cuerpo());
            assertEquals("/?ok=calificado", post(base + "/calificar", cookie, "_csrf=" + token + "&entrega=5&nota=85%2C5&comentario=Bien").h("Location"));
            assertEquals(List.of("calificar:5:85.5:Bien"), dao.llamadas);
            dao.llamadas.clear();
            for (String mala : List.of("101", "-1", "abc", "", "1e3", "NaN"))
                assertEquals("/?error=nota", post(base + "/calificar", cookie, "_csrf=" + token + "&entrega=5&nota=" + URLEncoder.encode(mala, StandardCharsets.UTF_8)).h("Location"), "nota " + mala);
            assertTrue(dao.llamadas.isEmpty());
        });
    }

    @Test void editarYEliminar() throws Exception {
        conServidor((base, dao) -> {
            String cookie = entrar(base, 1, "PROFESOR", "Ana Pérez");
            R pag = get(base + "/editar?id=10", cookie);
            assertEquals(200, pag.codigo());
            assertTrue(pag.cuerpo().contains("name='quitar' value='7'"));
            assertEquals("/?error=permiso", get(base + "/editar?id=555", cookie).h("Location"));
            assertEquals("/?error=datos", get(base + "/editar?id=abc", cookie).h("Location"));
            String token = csrf(pag.cuerpo());

            R ok = post(base + "/editar", cookie, "_csrf=" + token + "&id=10&titulo=Nuevo&descripcion=d&quitar=7&quitar=8");
            assertEquals("/?ok=editado#pub-10", ok.h("Location"));
            assertEquals(Set.of(7, 8), dao.quitados);
            assertEquals("/?error=datos", post(base + "/editar", cookie, "_csrf=" + token + "&id=10&titulo=&descripcion=d").h("Location"));

            assertEquals("/?error=datos", post(base + "/eliminar", cookie, "_csrf=" + token + "&id=10").h("Location"), "sin confirmar no elimina");
            assertEquals("/?ok=eliminado", post(base + "/eliminar", cookie, "_csrf=" + token + "&id=10&confirmar=si").h("Location"));
            assertEquals("/?error=permiso", post(base + "/eliminar", cookie, "_csrf=" + token + "&id=999&confirmar=si").h("Location"));
        });
    }

    @Test void tablonYAnuncios() throws Exception {
        conServidor((base, dao) -> {
            String cookie = entrar(base, 1, "PROFESOR", "Ana Pérez");
            R t = get(base + "/tablon", cookie);
            assertEquals(200, t.codigo());
            assertTrue(t.cuerpo().contains("Examen el lunes") && t.cuerpo().contains("Publicar un anuncio"));
            assertTrue(get(base + "/tablon?seccion=77", cookie).cuerpo().contains("No hay anuncios"));
            String token = csrf(t.cuerpo());
            assertEquals("/tablon?ok=anunciado", post(base + "/anuncio", cookie, "_csrf=" + token + "&seccion=3&texto=Traer+regla").h("Location"));
            assertEquals(List.of("anunciar:3:Traer regla"), dao.llamadas);
            assertEquals("/?error=datos", post(base + "/anuncio", cookie, "_csrf=" + token + "&seccion=3&texto=").h("Location"));
            assertEquals("/tablon?ok=anuncio-eliminado", post(base + "/anuncio-eliminar", cookie, "_csrf=" + token + "&id=1&confirmar=si").h("Location"));

            String est = entrar(base, 2, "ESTUDIANTE", "Luis Gómez");
            assertFalse(get(base + "/tablon", est).cuerpo().contains("Publicar un anuncio"));
        });
    }

    @Test void archivoDeTipoNoPermitidoSeRechazaSinLlegarAlDao() throws Exception {
        conServidor((base, dao) -> {
            String cookie = entrar(base, 1, "PROFESOR", "Ana Pérez");
            String token = csrf(get(base + "/", cookie).cuerpo());
            assertEquals("/?error=formato", multipart(base + "/publicar", cookie, "_csrf=" + token, "seccion=3", "tipo=CLASE", "titulo=Malo", "archivo=@virus.exe:MZ").h("Location"));
            assertEquals("/?error=formato", multipart(base + "/publicar", cookie, "_csrf=" + token, "seccion=3", "tipo=CLASE", "titulo=Malo", "archivo=@falso.pdf:no-es-pdf").h("Location"));
            assertTrue(dao.llamadas.isEmpty());
        });
    }

    @Test void archivoGrandeSeRechazaSinLlegarAlDao() throws Exception {
        conServidor((base, dao) -> {
            String cookie = entrar(base, 1, "PROFESOR", "Ana Pérez");
            String token = csrf(get(base + "/", cookie).cuerpo());
            String enorme = "x".repeat(1024 * 1024 + 10);
            assertEquals("/?error=grande", multipart(base + "/publicar", cookie, "_csrf=" + token, "seccion=3", "tipo=CLASE", "titulo=Grande", "archivo=@g.bin:" + enorme).h("Location"));
            assertTrue(dao.llamadas.isEmpty());
        });
    }

    @Test void cerrarSesion() throws Exception {
        conServidor((base, dao) -> {
            String cookie = entrar(base, 1, "PROFESOR", "Ana Pérez");
            String token = csrf(get(base + "/", cookie).cuerpo());
            assertEquals(403, post(base + "/salir", cookie, "x=1").codigo());
            assertEquals(303, post(base + "/salir", cookie, "_csrf=" + token).codigo());
            assertEquals(401, get(base + "/", cookie).codigo());
        });
    }
}
