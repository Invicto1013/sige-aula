package com.unev.aula;

import com.unev.aula.Modelo.*;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Generación de HTML. Todo texto de usuario pasa por esc(); no hay scripts ni estilos en línea. */
public final class Html {
    private Html() { }

    /** Modo de visión de la petición en curso (lo fija Servidor; vacío = normal). */
    static final ThreadLocal<String> VISION = ThreadLocal.withInitial(() -> "normal");
    static final ThreadLocal<String> TEMA = ThreadLocal.withInitial(() -> Tema.PREDETERMINADO);
    static final ThreadLocal<String> RUTA = ThreadLocal.withInitial(() -> "/");

    public record VistaInicio(Usuario usuario, Sesiones.Sesion sesion, List<Seccion> secciones, List<Publicacion> todas, List<Publicacion> visibles,
                              FiltroAula filtro, Map<Integer, List<EntregaRecibida>> entregas, Map<Integer, List<Comentario>> comentarios,
                              long maxArchivoBytes, LocalDate hoy, String aviso) { }

    public static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    public static String bytes(long n) {
        if (n < 1024) return n + " B";
        if (n < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", n / 1024.0);
        return String.format(Locale.ROOT, "%.1f MB", n / 1024.0 / 1024.0);
    }

    public static String pagina(String titulo, String urlSige, Sesiones.Sesion s, String activo, String cuerpo) {
        StringBuilder nav = new StringBuilder("<nav aria-label='Principal'>");
        if (s != null) {
            nav.append(enlace("/", "Publicaciones", "inicio".equals(activo))).append(enlace("/tablon", "Tablón", "tablon".equals(activo)));
        }
        nav.append("<a href='").append(esc(urlSige)).append("'>").append(ico("↩")).append("Volver a SIGE</a>");
        if (s != null) {
            nav.append("<span class='quien'>").append(esc(s.nombre())).append(" · ").append(esc(s.rol().toLowerCase(Locale.ROOT))).append("</span>")
                    .append("<form method='post' action='/salir' class='inline'>").append(csrf(s)).append("<button type='submit' class='link'>").append(ico("🚪")).append("Salir</button></form>");
        }
        nav.append("</nav>");
        return "<!doctype html><html lang='es'" + Vision.atributo(VISION.get()) + Tema.atributo(TEMA.get()) + "><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>" + esc(titulo) + " · Aula Virtual SIGE</title><link rel='icon' href='/icon.svg' type='image/svg+xml'><link rel='stylesheet' href='/styles.css'></head><body>"
                + "<a class='skip' href='#contenido'>Saltar al contenido</a><header><a class='marca' href='/'>SIGE <b>Aula Virtual</b></a>" + nav + "</header>"
                + "<main id='contenido'>" + cuerpo + "</main><footer>Subsistema del Sistema de Gestión Escolar " + Vision.selector(VISION.get(), RUTA.get()) + Tema.selector(TEMA.get(), RUTA.get()) + "</footer></body></html>";
    }

    private static String ico(String simbolo) { return "<span class='ico' aria-hidden='true'>" + simbolo + "</span> "; }

    private static String enlace(String href, String texto, boolean actual) {
        String simbolo = href.equals("/") ? "📚" : href.equals("/tablon") ? "📌" : "";
        return "<a href='" + href + "'" + (actual ? " aria-current='page' class='actual'" : "") + ">" + (simbolo.isEmpty() ? "" : ico(simbolo)) + texto + "</a>";
    }

    private static String csrf(Sesiones.Sesion s) { return "<input type='hidden' name='_csrf' value='" + esc(s.csrf()) + "'>"; }

    public static String aviso(String clase, String texto) {
        return "<div class='" + clase + "' role='" + ("alert".equals(clase) ? "alert" : "status") + "'>" + esc(texto) + "</div>";
    }

    public static String sinSesion(String urlSige) {
        return "<section class='card centro'><h1>Aula Virtual</h1><p>Entra desde SIGE para abrir tu aula. Tu sesión se comparte automáticamente.</p>"
                + "<a class='primary link-button' href='" + esc(urlSige) + "'>Ir a SIGE</a></section>";
    }

    // ================= Publicaciones =================

    public static String inicio(VistaInicio v) {
        Usuario u = v.usuario();
        StringBuilder b = new StringBuilder("<div class='encabezado'><div><div class='eyebrow'>ESPACIO ACADÉMICO</div><h1>Aula y tareas</h1>"
                + "<p class='muted'>" + (u.esProfesor() ? "Publica materiales, revisa entregas y pon notas." : "Consulta tus clases, entrega tus tareas y revisa tus notas.") + "</p></div></div>");
        if (v.aviso() != null) b.append(v.aviso());
        b.append(resumen(v));
        if (u.esProfesor()) b.append(formularioPublicar(v.secciones(), v.sesion(), v.maxArchivoBytes()));
        b.append(barraFiltros(v));
        b.append("<section class='lista'><h2>").append(u.esProfesor() ? "Mis publicaciones" : "Clases y tareas").append("</h2>");
        if (v.visibles().isEmpty()) {
            b.append("<p class='vacio'>").append(v.filtro().activo() ? "Ninguna publicación coincide con los filtros."
                    : v.secciones().isEmpty() ? "Todavía no tienes secciones asignadas." : "Aún no hay publicaciones.").append("</p>");
        }
        for (Publicacion p : v.visibles()) b.append(tarjeta(p, v));
        return b.append("</section>").toString();
    }

    private static String resumen(VistaInicio v) {
        if (v.usuario().esProfesor()) {
            int n = FiltroAula.porCalificar(v.todas());
            return "<section class='resumen' aria-label='Resumen'><div class='dato'><b>" + n + "</b><span>entrega(s) por calificar</span></div>"
                    + "<div class='dato'><b>" + v.todas().size() + "</b><span>publicación(es)</span></div></section>";
        }
        List<Publicacion> prox = FiltroAula.proximas(v.todas(), v.hoy(), 3);
        long atrasadas = FiltroAula.atrasadas(v.todas(), v.hoy());
        StringBuilder b = new StringBuilder("<section class='resumen' aria-label='Recordatorios'>");
        b.append("<div class='dato").append(atrasadas > 0 ? " mal" : "").append("'><b>").append(atrasadas).append("</b><span>tarea(s) vencidas sin entregar</span></div>");
        b.append("<div class='dato'><b>").append(prox.size()).append("</b><span>vencen en 3 días o menos</span></div>");
        if (!prox.isEmpty()) {
            b.append("<ul class='proximas'>");
            for (Publicacion p : prox) {
                long d = EstadoTarea.diasRestantes(p.fechaLimite(), v.hoy());
                b.append("<li><a href='#pub-").append(p.id()).append("'>").append(esc(p.titulo())).append("</a> · ").append(esc(p.asignatura())).append(" · ")
                        .append(d == 0 ? "vence hoy" : d == 1 ? "vence mañana" : "vence en " + d + " días").append("</li>");
            }
            b.append("</ul>");
        }
        return b.append("</section>").toString();
    }

    private static String barraFiltros(VistaInicio v) {
        FiltroAula f = v.filtro();
        StringBuilder secciones = new StringBuilder("<option value=''>Todas</option>");
        for (Seccion s : v.secciones()) secciones.append("<option value='").append(s.id()).append("'").append(s.id() == f.seccion() ? " selected" : "").append(">").append(esc(s.titulo())).append("</option>");
        Map<String, String> tipos = new LinkedHashMap<>();
        tipos.put("CLASE", "Clases y materiales");
        tipos.put("TAREA", "Tareas");
        Map<String, String> estados = new LinkedHashMap<>();
        if (v.usuario().esProfesor()) {
            estados.put("por-calificar", "Con entregas por calificar");
            estados.put("sin-entregas", "Sin entregas aún");
            estados.put("al-dia", "Todas calificadas");
        } else {
            for (EstadoTarea e : EstadoTarea.values()) estados.put(e.clave(), e.etiqueta());
        }
        return "<section class='card filtros' aria-label='Filtros de búsqueda'><form method='get' action='/' class='campos' role='search'>"
                + "<label>Buscar<input type='search' name='q' maxlength='100' value='" + esc(f.texto()) + "' placeholder='Título, descripción o materia'></label>"
                + "<label>Sección<select name='seccion'>" + secciones + "</select></label>"
                + "<label>Tipo" + lista("tipo", f.tipo(), tipos) + "</label>"
                + "<label>Estado" + lista("estado", f.estado(), estados) + "</label>"
                + "<div class='acciones'><button class='primary' type='submit'>Filtrar</button>" + (f.activo() ? "<a class='enlace' href='/'>Limpiar filtros</a>" : "") + "</div></form>"
                + "<p class='muted' role='status'>Mostrando " + v.visibles().size() + (f.activo() ? " de " + v.todas().size() : "") + " publicación(es).</p></section>";
    }

    private static String lista(String nombre, String actual, Map<String, String> opciones) {
        StringBuilder sb = new StringBuilder("<select name='" + nombre + "'><option value=''>Todos</option>");
        opciones.forEach((k, t) -> sb.append("<option value='").append(esc(k)).append("'").append(k.equals(actual) ? " selected" : "").append(">").append(esc(t)).append("</option>"));
        return sb.append("</select>").toString();
    }

    private static String formularioPublicar(List<Seccion> secciones, Sesiones.Sesion s, long max) {
        if (secciones.isEmpty()) return aviso("alert", "No tienes secciones asignadas todavía. Pide al administrador que te asigne una sección en SIGE.");
        StringBuilder op = new StringBuilder();
        for (Seccion x : secciones) op.append("<option value='").append(x.id()).append("'>").append(esc(x.titulo())).append("</option>");
        return "<details class='card publicar'><summary>Publicar una clase o tarea</summary><form method='post' action='/publicar' enctype='multipart/form-data' class='formulario'>"
                + csrf(s) + "<div class='campos'>"
                + "<label>Sección<select name='seccion' required>" + op + "</select></label>"
                + "<label>Tipo<select name='tipo' required><option value='CLASE'>Clase o material</option><option value='TAREA'>Tarea</option></select></label>"
                + "<label>Título<input name='titulo' maxlength='120' required></label>"
                + "<label>Fecha límite (opcional, solo tareas)<input type='date' name='fecha_limite'></label></div>"
                + "<label>Instrucciones o descripción<textarea name='descripcion' maxlength='5000' rows='4'></textarea></label>"
                + "<label>Archivos adjuntos (opcional)<input type='file' name='archivo' multiple><small>Hasta " + AulaDao.MAX_ADJUNTOS + " archivos, máximo " + bytes(max) + " cada uno.</small></label>"
                + "<button class='primary' type='submit'>Publicar</button></form></details>";
    }

    private static String tarjeta(Publicacion p, VistaInicio v) {
        Usuario u = v.usuario();
        Sesiones.Sesion s = v.sesion();
        EstadoTarea estado = u.esEstudiante() ? EstadoTarea.de(p, v.hoy()) : null;
        StringBuilder b = new StringBuilder("<article class='card publicacion' id='pub-").append(p.id()).append("'><div class='cabeza'><div><span class='etiqueta'>")
                .append(p.esTarea() ? "TAREA" : "CLASE").append("</span><h3>").append(esc(p.titulo())).append("</h3><p class='muted'>")
                .append(esc(p.asignatura())).append(" · ").append(esc(p.grado() == null ? "Grado no indicado" : p.grado()))
                .append(" · Publicado ").append(esc(p.fechaPublicacion())).append("</p></div><div class='lateral'>");
        if (estado != null) b.append("<span class='estado e-").append(estado.estilo()).append("'>").append(esc(estado.etiqueta())).append("</span>");
        if (p.fechaLimite() != null) {
            boolean vencida = p.fechaLimite().isBefore(v.hoy());
            b.append("<span class='limite").append(vencida ? " vencida" : "").append("'>Vence ").append(p.fechaLimite()).append(vencida ? " · vencida" : "").append("</span>");
        }
        b.append("</div></div>");
        if (p.descripcion() != null && !p.descripcion().isBlank()) b.append("<p class='descripcion'>").append(esc(p.descripcion()).replace("\n", "<br>")).append("</p>");
        if (!p.adjuntos().isEmpty()) {
            b.append("<ul class='adjuntos' aria-label='Archivos adjuntos'>");
            for (Adjunto a : p.adjuntos()) b.append("<li><a class='enlace' href='/archivo?adjunto=").append(a.id()).append("'>").append(esc(a.nombre())).append("</a> <small>(").append(bytes(a.bytes())).append(")</small></li>");
            b.append("</ul>");
        }
        if (u.esEstudiante() && p.esTarea()) b.append(bloqueEstudiante(p, estado, s, v.maxArchivoBytes()));
        if (u.esProfesor()) {
            if (p.esTarea()) b.append(bloqueEntregas(p, v.entregas().getOrDefault(p.id(), List.of()), s));
            b.append("<div class='acciones'><a class='enlace' href='/editar?id=").append(p.id()).append("'>Editar</a>")
                    .append("<form method='post' action='/eliminar' class='inline confirmar'>").append(csrf(s)).append("<input type='hidden' name='id' value='").append(p.id()).append("'>")
                    .append("<label class='check'><input type='checkbox' name='confirmar' value='si' required> Confirmar</label><button class='danger' type='submit'>Eliminar publicación</button></form></div>");
        }
        b.append(comentarios(p, v.comentarios().getOrDefault(p.id(), List.of()), u, s));
        return b.append("</article>").toString();
    }

    private static String bloqueEstudiante(Publicacion p, EstadoTarea estado, Sesiones.Sesion s, long max) {
        StringBuilder b = new StringBuilder("<div class='entrega'>");
        MiEntrega e = p.miEntrega();
        if (e != null) {
            b.append("<p class='propia'>Tu entrega: <a class='enlace' href='/archivo?entrega=").append(e.id()).append("'>").append(esc(e.archivoNombre())).append("</a> · ").append(esc(e.fecha())).append("</p>");
            if (e.nota() != null) {
                b.append("<p class='nota'>Nota: <b>").append(e.nota().toPlainString()).append(" / 100</b></p>");
                if (e.comentario() != null && !e.comentario().isBlank()) b.append("<p class='comentario-docente'><b>Comentario del profesor:</b> ").append(esc(e.comentario()).replace("\n", "<br>")).append("</p>");
            }
        }
        if (estado == EstadoTarea.CALIFICADA) return b.append("<small>Esta entrega ya fue calificada y no se puede reemplazar.</small></div>").toString();
        b.append("<form method='post' action='/entregar' enctype='multipart/form-data' class='formulario'>").append(csrf(s)).append("<input type='hidden' name='material' value='").append(p.id()).append("'>")
                .append("<label>").append(e == null ? "Archivo de tu entrega" : "Reemplazar tu entrega").append("<input type='file' name='archivo' required></label>")
                .append("<small>Máximo ").append(bytes(max)).append(estado == EstadoTarea.ATRASADA ? ". La fecha límite ya pasó: se marcará como entregada tarde" : "").append(".</small>")
                .append("<button class='primary' type='submit'>Subir entrega</button></form></div>");
        return b.toString();
    }

    private static String bloqueEntregas(Publicacion p, List<EntregaRecibida> entregas, Sesiones.Sesion s) {
        StringBuilder b = new StringBuilder("<details class='entregas'><summary>Entregas recibidas (").append(entregas.size()).append(")")
                .append(p.porCalificar() > 0 ? " · " + p.porCalificar() + " por calificar" : "").append("</summary>");
        if (entregas.isEmpty()) b.append("<p class='vacio'>Nadie ha entregado todavía.</p>");
        for (EntregaRecibida e : entregas) {
            boolean tarde = EstadoTarea.tarde(p.fechaLimite(), e.dia());
            b.append("<div class='entrega-fila'><div class='fila'><span><b>").append(esc(e.estudiante())).append("</b> · ").append(esc(e.archivoNombre())).append(" · ").append(esc(e.fecha()))
                    .append(tarde ? " <span class='estado e-aviso'>Tarde</span>" : "").append("</span><a class='enlace' href='/archivo?entrega=").append(e.id()).append("'>Descargar</a></div>")
                    .append("<form method='post' action='/calificar' class='calificar'>").append(csrf(s)).append("<input type='hidden' name='entrega' value='").append(e.id()).append("'>")
                    .append("<label>Nota (0–100)<input type='number' name='nota' min='0' max='100' step='0.01' required value='").append(e.nota() == null ? "" : e.nota().toPlainString()).append("'></label>")
                    .append("<label>Comentario<textarea name='comentario' maxlength='1000' rows='2'>").append(esc(e.comentario())).append("</textarea></label>")
                    .append("<button class='secondary' type='submit'>").append(e.nota() == null ? "Guardar nota" : "Actualizar nota").append("</button></form></div>");
        }
        return b.append("</details>").toString();
    }

    private static String comentarios(Publicacion p, List<Comentario> lista, Usuario u, Sesiones.Sesion s) {
        StringBuilder b = new StringBuilder("<details class='comentarios'><summary>Comentarios (").append(lista.size()).append(")</summary>");
        for (Comentario c : lista) {
            b.append("<div class='coment'><p><b>").append(esc(c.autor())).append("</b>").append("PROFESOR".equals(c.rol()) ? " <span class='etiqueta'>PROFESOR</span>" : "")
                    .append(" <small>").append(esc(c.fecha())).append("</small></p><p>").append(esc(c.texto()).replace("\n", "<br>")).append("</p>");
            if (c.idUsuario() == u.id() || u.esProfesor())
                b.append("<form method='post' action='/comentario-eliminar' class='inline'>").append(csrf(s)).append("<input type='hidden' name='id' value='").append(c.id())
                        .append("'><input type='hidden' name='material' value='").append(p.id()).append("'><button type='submit' class='link'>Eliminar comentario</button></form>");
            b.append("</div>");
        }
        b.append("<form method='post' action='/comentar' class='formulario'>").append(csrf(s)).append("<input type='hidden' name='material' value='").append(p.id()).append("'>")
                .append("<label>Escribe un comentario<textarea name='texto' maxlength='1000' rows='2' required></textarea></label><button class='secondary' type='submit'>Comentar</button></form></details>");
        return b.toString();
    }

    // ================= Tablón =================

    public static String tablon(Usuario u, Sesiones.Sesion s, List<Seccion> secciones, List<Anuncio> todos, int seccion, String aviso) {
        List<Anuncio> anuncios = seccion == 0 ? todos : todos.stream().filter(a -> a.idSeccion() == seccion).toList();
        StringBuilder b = new StringBuilder("<div class='encabezado'><div><div class='eyebrow'>COMUNICADOS</div><h1>Tablón de anuncios</h1><p class='muted'>Avisos de cada sección.</p></div></div>");
        if (aviso != null) b.append(aviso);
        StringBuilder op = new StringBuilder();
        for (Seccion x : secciones) op.append("<option value='").append(x.id()).append("'>").append(esc(x.titulo())).append("</option>");
        if (u.esProfesor() && !secciones.isEmpty()) {
            b.append("<details class='card publicar'><summary>Publicar un anuncio</summary><form method='post' action='/anuncio' class='formulario'>").append(csrf(s))
                    .append("<label>Sección<select name='seccion' required>").append(op).append("</select></label>")
                    .append("<label>Anuncio<textarea name='texto' maxlength='2000' rows='3' required></textarea></label><button class='primary' type='submit'>Publicar anuncio</button></form></details>");
        }
        StringBuilder filtro = new StringBuilder("<option value=''>Todas</option>");
        for (Seccion x : secciones) filtro.append("<option value='").append(x.id()).append("'").append(x.id() == seccion ? " selected" : "").append(">").append(esc(x.titulo())).append("</option>");
        b.append("<section class='card filtros' aria-label='Filtros'><form method='get' action='/tablon' class='campos' role='search'><label>Sección<select name='seccion'>").append(filtro)
                .append("</select></label><div class='acciones'><button class='primary' type='submit'>Filtrar</button>").append(seccion != 0 ? "<a class='enlace' href='/tablon'>Limpiar filtros</a>" : "")
                .append("</div></form></section>");
        if (anuncios.isEmpty()) b.append("<p class='vacio'>No hay anuncios.</p>");
        for (Anuncio a : anuncios) {
            b.append("<article class='card anuncio'><p class='muted'>").append(esc(a.asignatura())).append(" · ").append(esc(a.autor())).append(" · ").append(esc(a.fecha())).append("</p><p>")
                    .append(esc(a.texto()).replace("\n", "<br>")).append("</p>");
            if (u.esProfesor() && a.idAutor() == u.id())
                b.append("<form method='post' action='/anuncio-eliminar' class='inline confirmar'>").append(csrf(s)).append("<input type='hidden' name='id' value='").append(a.id())
                        .append("'><label class='check'><input type='checkbox' name='confirmar' value='si' required> Confirmar</label><button class='danger' type='submit'>Eliminar</button></form>");
            b.append("</article>");
        }
        return b.toString();
    }

    // ================= Editar =================

    public static String editar(Sesiones.Sesion s, Publicacion p, long max, String aviso) {
        StringBuilder b = new StringBuilder("<div class='encabezado'><div><div class='eyebrow'>EDITAR</div><h1>").append(esc(p.titulo())).append("</h1><p class='muted'>")
                .append(esc(p.asignatura())).append(" · ").append(p.esTarea() ? "Tarea" : "Clase o material").append("</p></div></div>");
        if (aviso != null) b.append(aviso);
        b.append("<section class='card'><form method='post' action='/editar' enctype='multipart/form-data' class='formulario'>").append(csrf(s)).append("<input type='hidden' name='id' value='").append(p.id()).append("'>")
                .append("<div class='campos'><label>Título<input name='titulo' maxlength='120' required value='").append(esc(p.titulo())).append("'></label>")
                .append("<label>Fecha límite (opcional)<input type='date' name='fecha_limite' value='").append(p.fechaLimite() == null ? "" : p.fechaLimite().toString()).append("'></label></div>")
                .append("<label>Instrucciones o descripción<textarea name='descripcion' maxlength='5000' rows='5'>").append(esc(p.descripcion())).append("</textarea></label>");
        if (!p.adjuntos().isEmpty()) {
            b.append("<fieldset><legend>Archivos actuales (marca los que quieres quitar)</legend>");
            for (Adjunto a : p.adjuntos()) b.append("<label class='check'><input type='checkbox' name='quitar' value='").append(a.id()).append("'> ").append(esc(a.nombre())).append(" <small>(").append(bytes(a.bytes())).append(")</small></label>");
            b.append("</fieldset>");
        }
        b.append("<label>Agregar archivos<input type='file' name='archivo' multiple><small>En total, hasta ").append(AulaDao.MAX_ADJUNTOS).append(" archivos, máximo ").append(bytes(max)).append(" cada uno.</small></label>")
                .append("<div class='acciones'><button class='primary' type='submit'>Guardar cambios</button><a class='enlace' href='/#pub-").append(p.id()).append("'>Cancelar</a></div></form></section>");
        return b.toString();
    }
}
