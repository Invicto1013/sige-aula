package com.unev.aula;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.unev.aula.Modelo.*;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/** Servidor HTTP del Aula: rutas, sesión, protección CSRF y cabeceras de seguridad. */
public final class Servidor {
    private static final Logger LOG = Logger.getLogger(Servidor.class.getName());
    private static final String COOKIE = "aula_sid";
    private static final long REVISION_MS = 5 * 60 * 1000L;
    private static final Map<String, String> AVISOS = Map.ofEntries(
            Map.entry("publicado", "La publicación se creó correctamente."), Map.entry("editado", "Los cambios se guardaron."),
            Map.entry("eliminado", "La publicación se eliminó."), Map.entry("entregado", "Tu entrega se guardó correctamente."),
            Map.entry("calificado", "La nota se guardó."), Map.entry("comentado", "Comentario publicado."),
            Map.entry("comentario-eliminado", "Comentario eliminado."), Map.entry("anunciado", "Anuncio publicado."),
            Map.entry("anuncio-eliminado", "Anuncio eliminado."));
    private static final Map<String, String> ERRORES = Map.ofEntries(
            Map.entry("datos", "Revisa los datos: tipo, título (máx. 120), descripción (máx. 5000) y textos obligatorios."),
            Map.entry("fecha", "La fecha límite no es válida."), Map.entry("grande", "Un archivo supera el tamaño máximo permitido."),
            Map.entry("formato", "Tipo de archivo no permitido. Se aceptan: " + TiposArchivo.lista() + "."), Map.entry("sinarchivo", "Selecciona un archivo antes de enviar."), Map.entry("maximo", "Se permiten hasta " + AulaDao.MAX_ADJUNTOS + " archivos por publicación."),
            Map.entry("permiso", "No tienes permiso para esa acción (¿la sección es tuya o estás inscrito?)."),
            Map.entry("calificada", "Esa entrega ya fue calificada y no se puede reemplazar."), Map.entry("nota", "La nota debe ser un número entre 0 y 100."),
            Map.entry("ocupado", "El servidor está procesando otras subidas. Inténtalo de nuevo en unos segundos."),
            Map.entry("bd", "No se pudo completar la operación. Intenta de nuevo en un momento."));

    /** Una acción de escritura: devuelve la dirección a la que se redirige. */
    @FunctionalInterface
    private interface Accion { String ejecutar(Sesiones.Sesion s, Multipart.Formulario f) throws SQLException, IOException; }

    private final Config cfg;
    private final AulaDao dao;
    private final Sesiones sesiones = new Sesiones();
    private final Sso.UsoUnico usoUnico = new Sso.UsoUnico();
    private final Semaphore subidas = new Semaphore(2); // protege la memoria de la instancia gratuita
    private final String css;
    private HttpServer http;
    private ExecutorService hilos;

    public Servidor(Config cfg, AulaDao dao) {
        this.cfg = cfg;
        this.dao = dao;
        try (InputStream in = Servidor.class.getResourceAsStream("/web/styles.css")) {
            this.css = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) { throw new IllegalStateException(e); }
    }

    public void iniciar() throws IOException {
        http = HttpServer.create(new InetSocketAddress(cfg.puerto()), 0);
        hilos = Executors.newFixedThreadPool(16);
        http.setExecutor(hilos);
        http.createContext("/", this::atender);
        http.start();
        LOG.info("Aula Virtual escuchando en el puerto " + puerto());
    }

    public void detener() {
        if (http != null) http.stop(0);
        if (hilos != null) hilos.shutdownNow();
    }

    /** Puerto real en el que escucha (útil cuando se configura el puerto 0 en pruebas). */
    public int puerto() { return http.getAddress().getPort(); }

    private void atender(HttpExchange x) throws IOException {
        try {
            String ruta = x.getRequestURI().getPath(), metodo = x.getRequestMethod();
            if (metodo.equals("HEAD")) metodo = "GET";
            var cab = x.getRequestHeaders().get("Cookie");
            Html.VISION.set(Vision.modo(cab == null ? null : String.join("; ", cab)));
            Html.TEMA.set(Tema.modo(cab == null ? null : String.join("; ", cab)));
            Html.ESTACION.set(Estacion.modo(cab == null ? null : String.join("; ", cab)));
            Html.RUTA.set(ruta);
            switch (metodo + " " + ruta) {
                case "GET /health" -> salud(x);
                case "GET /styles.css" -> enviar(x, 200, "text/css; charset=utf-8", css, "public, max-age=3600");
                case "GET /icon.svg" -> icono(x);
                case "GET /sso" -> sso(x);
                case "GET /acerca" -> acerca(x);
                case "GET /vision" -> cambiarVision(x);
                case "GET /tema" -> cambiarTema(x);
                case "GET /estacion" -> cambiarEstacion(x);
                case "GET /" -> inicio(x);
                case "GET /tablon" -> tablon(x);
                case "GET /editar" -> editarPagina(x);
                case "GET /archivo" -> archivo(x);
                case "POST /publicar" -> accion(x, "PROFESOR", this::publicar);
                case "POST /editar" -> accion(x, "PROFESOR", this::editar);
                case "POST /eliminar" -> accion(x, "PROFESOR", this::eliminar);
                case "POST /entregar" -> accion(x, "ESTUDIANTE", this::entregar);
                case "POST /calificar" -> accion(x, "PROFESOR", this::calificar);
                case "POST /comentar" -> accion(x, null, this::comentar);
                case "POST /comentario-eliminar" -> accion(x, null, this::comentarioEliminar);
                case "POST /anuncio" -> accion(x, "PROFESOR", this::anuncio);
                case "POST /anuncio-eliminar" -> accion(x, "PROFESOR", this::anuncioEliminar);
                case "POST /salir" -> salir(x);
                default -> enviar(x, 404, "text/html; charset=utf-8", Html.pagina("No encontrada", cfg.urlSige(), null, "", Html.aviso("alert", "La página que buscas no existe.")), null);
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error atendiendo " + x.getRequestURI(), e);
            enviar(x, 500, "text/html; charset=utf-8", Html.pagina("Error", cfg.urlSige(), null, "", Html.aviso("alert", "Ocurrió un error inesperado.")), null);
        } finally {
            Html.VISION.remove();
            Html.TEMA.remove();
            Html.ESTACION.remove();
            Html.RUTA.remove();
            x.close();
        }
    }

    // ================= páginas =================

    private void salud(HttpExchange x) throws IOException {
        boolean pideBd = "db=1".equals(x.getRequestURI().getRawQuery());
        if (pideBd && !dao.haySesionBd()) { enviar(x, 503, "text/plain; charset=utf-8", "bd no disponible", "no-store"); return; }
        enviar(x, 200, "text/plain; charset=utf-8", "ok", "no-store");
    }

    private void sso(HttpExchange x) throws IOException {
        String token = consulta(x, "token");
        long ahora = System.currentTimeMillis() / 1000;
        Optional<Sso.Reclamo> r = Sso.verificar(cfg.secretoSso(), token, ahora);
        if (r.isEmpty() || !usoUnico.registrar(r.get().jti(), r.get().exp(), ahora)) {
            enviar(x, 401, "text/html; charset=utf-8", Html.pagina("Acceso", cfg.urlSige(), null, "",
                    Html.aviso("alert", "El enlace de acceso no es válido o ya caducó. Vuelve a SIGE y abre el aula de nuevo.") + Html.sinSesion(cfg.urlSige())), "no-store");
            return;
        }
        try {
            Optional<Usuario> u = dao.usuarioActivo(r.get().id(), r.get().rol());
            if (u.isEmpty() || !(u.get().esProfesor() || u.get().esEstudiante())) {
                enviar(x, 403, "text/html; charset=utf-8", Html.pagina("Acceso", cfg.urlSige(), null, "",
                        Html.aviso("alert", "El aula está disponible para profesores y estudiantes con cuenta activa.")), "no-store");
                return;
            }
            Sesiones.Sesion s = sesiones.crear(u.get().id(), u.get().rol(), u.get().nombre(), System.currentTimeMillis());
            x.getResponseHeaders().add("Set-Cookie", COOKIE + "=" + s.id() + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=" + Sesiones.DURACION_MS / 1000 + (cfg.cookieSegura() ? "; Secure" : ""));
            redirigir(x, "/");
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "SSO: base de datos no disponible", e);
            enviar(x, 503, "text/html; charset=utf-8", Html.pagina("Acceso", cfg.urlSige(), null, "",
                    Html.aviso("alert", "No se pudo conectar con la base de datos. Espera unos segundos y reintenta desde SIGE.")), "no-store");
        }
    }

    private String avisoDeConsulta(HttpExchange x) {
        String ok = consulta(x, "ok"), err = consulta(x, "error");
        if (ok != null && AVISOS.containsKey(ok)) return Html.aviso("exito", AVISOS.get(ok));
        if (err != null && ERRORES.containsKey(err)) return Html.aviso("alert", ERRORES.get(err));
        return null;
    }

    private static Usuario de(Sesiones.Sesion s) { return new Usuario(s.idUsuario(), s.rol(), s.nombre()); }

    private void inicio(HttpExchange x) throws IOException {
        Sesiones.Sesion s = sesionVigente(x);
        if (s == null) { enviar(x, 401, "text/html; charset=utf-8", Html.pagina("Aula", cfg.urlSige(), null, "", Html.sinSesion(cfg.urlSige())), "no-store"); return; }
        Usuario u = de(s);
        try {
            LocalDate hoy = LocalDate.now(cfg.zona());
            var secciones = dao.secciones(u);
            var todas = dao.publicaciones(u);
            FiltroAula filtro = FiltroAula.desde(k -> consulta(x, k), u.esProfesor());
            var visibles = filtro.aplicar(todas, u, hoy);
            Map<Integer, List<EntregaRecibida>> entregas = u.esProfesor()
                    ? dao.entregasDelProfesor(u.id()).stream().collect(Collectors.groupingBy(EntregaRecibida::idMaterial)) : Map.of();
            var comentarios = dao.comentarios(visibles.stream().map(Publicacion::id).toList());
            var vista = new Html.VistaInicio(u, s, secciones, todas, visibles, filtro, entregas, comentarios, cfg.maxArchivoBytes(), hoy, avisoDeConsulta(x));
            enviar(x, 200, "text/html; charset=utf-8", Html.pagina("Aula y tareas", cfg.urlSige(), s, "inicio", Html.inicio(vista)), "no-store");
        } catch (SQLException e) {
            falloBd(x, s, e);
        }
    }

    private void tablon(HttpExchange x) throws IOException {
        Sesiones.Sesion s = sesionVigente(x);
        if (s == null) { enviar(x, 401, "text/html; charset=utf-8", Html.pagina("Tablón", cfg.urlSige(), null, "", Html.sinSesion(cfg.urlSige())), "no-store"); return; }
        Usuario u = de(s);
        try {
            int seccion = 0;
            try { seccion = Math.max(0, Integer.parseInt(consulta(x, "seccion"))); } catch (NumberFormatException ignorada) { }
            String cuerpo = Html.tablon(u, s, dao.secciones(u), dao.anuncios(u), seccion, avisoDeConsulta(x));
            enviar(x, 200, "text/html; charset=utf-8", Html.pagina("Tablón", cfg.urlSige(), s, "tablon", cuerpo), "no-store");
        } catch (SQLException e) {
            falloBd(x, s, e);
        }
    }

    private void editarPagina(HttpExchange x) throws IOException {
        Sesiones.Sesion s = sesionVigente(x);
        if (s == null || !"PROFESOR".equals(s.rol())) { redirigir(x, "/"); return; }
        try {
            Optional<Publicacion> p = dao.publicacion(de(s), Integer.parseInt(consulta(x, "id")));
            if (p.isEmpty()) { redirigir(x, "/?error=permiso"); return; }
            enviar(x, 200, "text/html; charset=utf-8", Html.pagina("Editar", cfg.urlSige(), s, "inicio", Html.editar(s, p.get(), cfg.maxArchivoBytes(), avisoDeConsulta(x))), "no-store");
        } catch (NumberFormatException e) {
            redirigir(x, "/?error=datos");
        } catch (SQLException e) {
            falloBd(x, s, e);
        }
    }

    private void falloBd(HttpExchange x, Sesiones.Sesion s, SQLException e) throws IOException {
        LOG.log(Level.WARNING, "No se pudo cargar el aula", e);
        enviar(x, 503, "text/html; charset=utf-8", Html.pagina("Aula", cfg.urlSige(), s, "", Html.aviso("alert", "No se pudo cargar el aula. Verifica la conexión con MySQL e inténtalo de nuevo.")), "no-store");
    }

    private void archivo(HttpExchange x) throws IOException {
        Sesiones.Sesion s = sesionVigente(x);
        if (s == null) { enviar(x, 401, "text/plain; charset=utf-8", "Sesión requerida.", null); return; }
        Usuario u = de(s);
        try {
            String a = consulta(x, "adjunto"), e = consulta(x, "entrega");
            Optional<Descarga> d = a != null ? dao.archivoAdjunto(u, Integer.parseInt(a)) : e != null ? dao.archivoEntrega(u, Integer.parseInt(e)) : Optional.empty();
            byte[] datos = d.isPresent() ? dao.leerArchivo(d.get().key()) : null;
            if (datos == null) { enviar(x, 404, "text/plain; charset=utf-8", "Archivo no encontrado.", null); return; }
            var h = x.getResponseHeaders();
            h.set("Content-Disposition", "attachment; filename*=UTF-8''" + URLEncoder.encode(d.get().nombre(), StandardCharsets.UTF_8).replace("+", "%20"));
            cabecerasBase(x);
            h.set("Content-Type", "application/octet-stream");
            h.set("Cache-Control", "private, no-store");
            if ("HEAD".equals(x.getRequestMethod())) { x.sendResponseHeaders(200, -1); return; }
            x.sendResponseHeaders(200, datos.length);
            x.getResponseBody().write(datos);
        } catch (NumberFormatException ex) {
            enviar(x, 400, "text/plain; charset=utf-8", "Identificador inválido.", null);
        } catch (SQLException ex) {
            LOG.log(Level.WARNING, "Descarga fallida", ex);
            enviar(x, 503, "text/plain; charset=utf-8", "No se pudo leer el archivo.", null);
        }
    }

    private void salir(HttpExchange x) throws IOException {
        Sesiones.Sesion s = sesionVigente(x);
        if (s != null) {
            Multipart.Formulario f = leerFormulario(x);
            if (!csrfValido(x, s, f)) { enviar(x, 403, "text/plain; charset=utf-8", "Solicitud no válida.", null); return; }
            sesiones.cerrar(s.id());
        }
        x.getResponseHeaders().add("Set-Cookie", COOKIE + "=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0" + (cfg.cookieSegura() ? "; Secure" : ""));
        redirigir(x, "/");
    }

    // ================= acciones (POST) =================

    /** Sesión, rol, lectura del formulario, CSRF y manejo uniforme de errores. */
    private void accion(HttpExchange x, String rol, Accion a) throws IOException {
        Sesiones.Sesion s = sesionVigente(x);
        if (s == null || (rol != null && !rol.equals(s.rol()))) { redirigir(x, "/"); return; }
        String tipo = x.getRequestHeaders().getFirst("Content-Type");
        boolean subida = tipo != null && tipo.toLowerCase(Locale.ROOT).startsWith("multipart/form-data");
        boolean adquirido = false;
        try {
            if (subida) {
                try { adquirido = subidas.tryAcquire(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                if (!adquirido) { redirigir(x, "/?error=ocupado"); return; }
            }
            Multipart.Formulario f = leerFormulario(x);
            if (!csrfValido(x, s, f)) { enviar(x, 403, "text/plain; charset=utf-8", "Solicitud no válida.", null); return; }
            try {
                redirigir(x, a.ejecutar(s, f));
            } catch (Rechazo r) {
                redirigir(x, "/?error=" + r.codigo());
            } catch (SQLException e) {
                LOG.log(Level.WARNING, "Operación fallida en " + x.getRequestURI().getPath(), e);
                redirigir(x, "/?error=bd");
            }
        } catch (Multipart.ArchivoGrandeException e) {
            redirigir(x, "/?error=grande");
        } finally {
            if (adquirido) subidas.release();
        }
    }

    private String publicar(Sesiones.Sesion s, Multipart.Formulario f) throws SQLException {
        int seccion = entero(f.campo("seccion"));
        String tipo = f.campo("tipo"), titulo = f.campo("titulo").trim(), descripcion = f.campo("descripcion").trim();
        if (seccion <= 0 || !(tipo.equals("CLASE") || tipo.equals("TAREA")) || titulo.isBlank() || titulo.length() > 120 || descripcion.length() > 5000) throw new Rechazo("datos");
        LocalDate limite = fecha(f.campo("fecha_limite"));
        if (tipo.equals("CLASE")) limite = null;
        if (f.archivos("archivo").size() > AulaDao.MAX_ADJUNTOS) throw new Rechazo("maximo");
        List<ArchivoGuardado> guardados = guardar(f.archivos("archivo"));
        try {
            dao.publicar(s.idUsuario(), seccion, tipo, titulo, descripcion, limite, guardados);
            return "/?ok=publicado";
        } catch (RuntimeException | SQLException e) {
            descartar(guardados);
            throw e;
        }
    }

    private String editar(Sesiones.Sesion s, Multipart.Formulario f) throws SQLException {
        int id = entero(f.campo("id"));
        String titulo = f.campo("titulo").trim(), descripcion = f.campo("descripcion").trim();
        if (id <= 0 || titulo.isBlank() || titulo.length() > 120 || descripcion.length() > 5000) throw new Rechazo("datos");
        LocalDate limite = fecha(f.campo("fecha_limite"));
        Set<Integer> quitar = new HashSet<>();
        for (String v : f.valores("quitar")) { int q = entero(v); if (q > 0) quitar.add(q); }
        List<ArchivoGuardado> guardados = guardar(f.archivos("archivo"));
        try {
            dao.actualizar(s.idUsuario(), id, titulo, descripcion, limite, quitar, guardados);
            return "/?ok=editado#pub-" + id;
        } catch (RuntimeException | SQLException e) {
            descartar(guardados);
            throw e;
        }
    }

    private String eliminar(Sesiones.Sesion s, Multipart.Formulario f) throws SQLException {
        if (!"si".equals(f.campo("confirmar"))) throw new Rechazo("datos");
        dao.eliminarPublicacion(s.idUsuario(), entero(f.campo("id")));
        return "/?ok=eliminado";
    }

    private String entregar(Sesiones.Sesion s, Multipart.Formulario f) throws SQLException {
        int material = entero(f.campo("material"));
        var adj = f.archivos("archivo");
        if (material <= 0) throw new Rechazo("datos");
        if (adj.isEmpty()) throw new Rechazo("sinarchivo");
        List<ArchivoGuardado> guardados = guardar(adj.subList(0, 1));
        try {
            dao.entregar(s.idUsuario(), material, guardados.get(0));
            return "/?ok=entregado#pub-" + material;
        } catch (RuntimeException | SQLException e) {
            descartar(guardados);
            throw e;
        }
    }

    private String calificar(Sesiones.Sesion s, Multipart.Formulario f) throws SQLException {
        String texto = f.campo("comentario").trim();
        if (texto.length() > 1000) throw new Rechazo("datos");
        dao.calificar(s.idUsuario(), entero(f.campo("entrega")), nota(f.campo("nota")), texto);
        return "/?ok=calificado";
    }

    private String comentar(Sesiones.Sesion s, Multipart.Formulario f) throws SQLException {
        String texto = f.campo("texto").trim();
        int material = entero(f.campo("material"));
        if (material <= 0 || texto.isEmpty() || texto.length() > 1000) throw new Rechazo("datos");
        dao.comentar(de(s), material, texto);
        return "/?ok=comentado#pub-" + material;
    }

    private String comentarioEliminar(Sesiones.Sesion s, Multipart.Formulario f) throws SQLException {
        dao.eliminarComentario(de(s), entero(f.campo("id")));
        return "/?ok=comentario-eliminado#pub-" + entero(f.campo("material"));
    }

    private String anuncio(Sesiones.Sesion s, Multipart.Formulario f) throws SQLException {
        String texto = f.campo("texto").trim();
        int seccion = entero(f.campo("seccion"));
        if (seccion <= 0 || texto.isEmpty() || texto.length() > 2000) throw new Rechazo("datos");
        dao.anunciar(s.idUsuario(), seccion, texto);
        return "/tablon?ok=anunciado";
    }

    private String anuncioEliminar(Sesiones.Sesion s, Multipart.Formulario f) throws SQLException {
        if (!"si".equals(f.campo("confirmar"))) throw new Rechazo("datos");
        dao.eliminarAnuncio(s.idUsuario(), entero(f.campo("id")));
        return "/tablon?ok=anuncio-eliminado";
    }

    // ================= utilidades =================

    private List<ArchivoGuardado> guardar(List<Multipart.Archivo> archivos) throws SQLException {
        for (Multipart.Archivo a : archivos) if (!TiposArchivo.permitido(a.nombre(), a.datos())) throw new Rechazo("formato");
        List<ArchivoGuardado> hechos = new ArrayList<>();
        try {
            for (Multipart.Archivo a : archivos) hechos.add(new ArchivoGuardado(dao.guardarArchivo(a.datos()), a.nombre(), a.tipo(), a.datos().length));
            return hechos;
        } catch (SQLException e) {
            descartar(hechos);
            throw e;
        }
    }

    private void descartar(List<ArchivoGuardado> archivos) { for (ArchivoGuardado a : archivos) dao.eliminarArchivo(a.key()); }

    static int entero(String v) { try { return Integer.parseInt(v == null ? "" : v.trim()); } catch (NumberFormatException e) { return 0; } }

    static LocalDate fecha(String v) {
        if (v == null || v.isBlank()) return null;
        try { return LocalDate.parse(v.trim()); } catch (java.time.DateTimeException e) { throw new Rechazo("fecha"); }
    }

    /** Acepta coma o punto decimal; 0 a 100. */
    static BigDecimal nota(String v) {
        try {
            String t = v == null ? "" : v.trim().replace(',', '.');
            if (t.isEmpty() || t.length() > 10) throw new Rechazo("nota");
            BigDecimal n = new BigDecimal(t);
            if (n.signum() < 0 || n.compareTo(BigDecimal.valueOf(100)) > 0) throw new Rechazo("nota");
            return n;
        } catch (NumberFormatException e) {
            throw new Rechazo("nota");
        }
    }

    private Sesiones.Sesion sesionVigente(HttpExchange x) {
        String sid = null;
        var cookies = x.getRequestHeaders().get("Cookie");
        if (cookies != null) for (String h : cookies) for (String par : h.split(";")) {
            String[] kv = par.trim().split("=", 2);
            if (kv.length == 2 && kv[0].equals(COOKIE)) sid = kv[1];
        }
        long ahora = System.currentTimeMillis();
        Sesiones.Sesion s = sesiones.obtener(sid, ahora);
        if (s != null && ahora - s.revisada() > REVISION_MS) {
            try {
                if (dao.usuarioActivo(s.idUsuario(), s.rol()).isEmpty()) { sesiones.cerrar(s.id()); return null; }
                sesiones.marcarRevisada(s, ahora);
            } catch (SQLException e) { /* si MySQL duerme, se conserva la sesión hasta la próxima revisión */ }
        }
        return s;
    }

    private Multipart.Formulario leerFormulario(HttpExchange x) throws IOException {
        String tipo = x.getRequestHeaders().getFirst("Content-Type");
        long max = cfg.maxSolicitudBytes();
        String largo = x.getRequestHeaders().getFirst("Content-Length");
        try { if (largo != null && Long.parseLong(largo) > max) throw new Multipart.ArchivoGrandeException(); } catch (NumberFormatException ignorada) { }
        byte[] cuerpo;
        try (InputStream in = x.getRequestBody()) { cuerpo = in.readNBytes((int) max + 1); }
        if (cuerpo.length > max) throw new Multipart.ArchivoGrandeException();
        if (tipo != null && tipo.toLowerCase(Locale.ROOT).startsWith("multipart/form-data")) return Multipart.analizar(cuerpo, tipo, cfg.maxArchivoBytes());
        if (cuerpo.length > Multipart.MAX_CAMPO_TEXTO) throw new IOException("Formulario demasiado grande.");
        return Multipart.urlencoded(new String(cuerpo, StandardCharsets.UTF_8));
    }

    /** Token CSRF de la sesión y, si el navegador envía Origin, que sea el mismo sitio. */
    private boolean csrfValido(HttpExchange x, Sesiones.Sesion s, Multipart.Formulario f) {
        String enviado = f.campo("_csrf");
        if (!java.security.MessageDigest.isEqual(s.csrf().getBytes(StandardCharsets.UTF_8), enviado.getBytes(StandardCharsets.UTF_8))) return false;
        String origen = x.getRequestHeaders().getFirst("Origin"), host = x.getRequestHeaders().getFirst("Host");
        if (origen == null) return true;
        if (origen.equals("null") || host == null) return false;
        try { return host.equalsIgnoreCase(URI.create(origen).getAuthority()); } catch (IllegalArgumentException e) { return false; }
    }

    private static String consulta(HttpExchange x, String clave) {
        String q = x.getRequestURI().getRawQuery();
        if (q == null) return null;
        for (String par : q.split("&")) {
            String[] kv = par.split("=", 2);
            if (kv.length == 2 && kv[0].equals(clave)) return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
        }
        return null;
    }

    /** Guarda el tema (auto, claro, oscuro) en una cookie y vuelve a la página de origen (solo rutas locales). */
    private void cambiarTema(HttpExchange x) throws IOException {
        String modo = Tema.PREDETERMINADO, volver = null;
        String q = x.getRequestURI().getRawQuery();
        if (q != null) for (String par : q.split("&")) {
            String[] kv = par.split("=", 2);
            if (kv.length != 2) continue;
            String v = java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8);
            if (kv[0].equals("modo")) modo = Tema.valido(v) ? v : Tema.PREDETERMINADO;
            else if (kv[0].equals("volver")) volver = v;
        }
        x.getResponseHeaders().add("Set-Cookie", Tema.COOKIE + "=" + modo + "; Path=/; SameSite=Lax; Max-Age=31536000" + (cfg.cookieSegura() ? "; Secure" : ""));
        redirigir(x, Vision.volverSeguro(volver));
    }

    /** Guarda la preferencia de temporada (auto, una estación o sin efectos) y vuelve a la página de origen. */
    private void cambiarEstacion(HttpExchange x) throws IOException {
        String modo = Estacion.PREDETERMINADO, volver = null;
        String q = x.getRequestURI().getRawQuery();
        if (q != null) for (String par : q.split("&")) {
            String[] kv = par.split("=", 2);
            if (kv.length != 2) continue;
            String v = java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8);
            if (kv[0].equals("modo")) modo = Estacion.valido(v) ? v : Estacion.PREDETERMINADO;
            else if (kv[0].equals("volver")) volver = v;
        }
        x.getResponseHeaders().add("Set-Cookie", Estacion.COOKIE + "=" + modo + "; Path=/; SameSite=Lax; Max-Age=31536000" + (cfg.cookieSegura() ? "; Secure" : ""));
        redirigir(x, Vision.volverSeguro(volver));
    }

    private void icono(HttpExchange x) throws IOException {
        try (InputStream in = Servidor.class.getResourceAsStream("/web/icon.svg")) {
            if (in == null) { enviar(x, 404, "text/plain; charset=utf-8", "", null); return; }
            enviar(x, 200, "image/svg+xml; charset=utf-8", new String(in.readAllBytes(), StandardCharsets.UTF_8), "public, max-age=86400");
        }
    }

    /** Página pública «Acerca de»: texto fijo, se muestra con o sin sesión. */
    private void acerca(HttpExchange x) throws IOException {
        Sesiones.Sesion s = sesionVigente(x);
        enviar(x, 200, "text/html; charset=utf-8", Html.pagina("Acerca de", cfg.urlSige(), s, "", Acerca.cuerpo()), "no-store");
    }

    /** Guarda el modo de visión en una cookie y vuelve a la página de origen (solo rutas locales). */
    private void cambiarVision(HttpExchange x) throws IOException {
        String modo = "normal", volver = null;
        String q = x.getRequestURI().getRawQuery();
        if (q != null) for (String par : q.split("&")) {
            String[] kv = par.split("=", 2);
            if (kv.length != 2) continue;
            String v = java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8);
            if (kv[0].equals("modo")) modo = Vision.valido(v) ? v : "normal";
            else if (kv[0].equals("volver")) volver = v;
        }
        x.getResponseHeaders().add("Set-Cookie", Vision.COOKIE + "=" + modo + "; Path=/; SameSite=Lax; Max-Age=31536000" + (cfg.cookieSegura() ? "; Secure" : ""));
        redirigir(x, Vision.volverSeguro(volver));
    }

    private void redirigir(HttpExchange x, String destino) throws IOException {
        cabecerasBase(x);
        x.getResponseHeaders().set("Location", destino);
        x.getResponseHeaders().set("Cache-Control", "no-store");
        x.sendResponseHeaders(303, -1);
    }

    private void enviar(HttpExchange x, int codigo, String tipo, String cuerpo, String cache) throws IOException {
        byte[] datos = cuerpo.getBytes(StandardCharsets.UTF_8);
        cabecerasBase(x);
        x.getResponseHeaders().set("Content-Type", tipo);
        if (cache != null) x.getResponseHeaders().set("Cache-Control", cache);
        if ("HEAD".equals(x.getRequestMethod())) { x.sendResponseHeaders(codigo, -1); return; }
        x.sendResponseHeaders(codigo, datos.length);
        x.getResponseBody().write(datos);
    }

    private void cabecerasBase(HttpExchange x) {
        var h = x.getResponseHeaders();
        h.set("X-Content-Type-Options", "nosniff");
        h.set("X-Frame-Options", "DENY");
        h.set("Referrer-Policy", "same-origin");
        h.set("Content-Security-Policy", "default-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'none'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'");
        if (cfg.cookieSegura()) h.set("Strict-Transport-Security", "max-age=31536000");
    }
}
