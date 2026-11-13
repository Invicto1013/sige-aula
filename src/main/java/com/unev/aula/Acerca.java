package com.unev.aula;

/** Información del proyecto para la página «Acerca de» (ícono ❓ de la barra superior). */
public final class Acerca {
    private Acerca() { }

    public static final String NOMBRE = "SIGE · Aula Virtual";
    public static final String DESCRIPCION = "Subsistema del Sistema de Gestión Escolar (SIGE). Permite a los profesores publicar clases y tareas y a los estudiantes entregarlas, "
            + "con calificaciones, comentarios y anuncios por sección. Comparte con SIGE la base de datos y el inicio de sesión.";
    public static final String AUTOR = "Elian Castro";
    public static final String INSTITUCION = "Universidad Nacional Evangélica (UNEV)";
    public static final String TECNOLOGIA = "Java 21 · servidor com.sun.net.httpserver · MySQL (TiDB Cloud) · HikariCP · sin frameworks ni scripts en el navegador";
    public static final String REPOSITORIO = "https://github.com/Invicto1013/Sistema-de-Gestion-Escolar-con-Java";
    public static final String PIE = "SIGE · Aula Virtual · " + AUTOR + " · " + INSTITUCION;

    /** Cuerpo HTML de la página; solo texto fijo, sin datos del usuario. */
    public static String cuerpo() {
        return "<section class='card acerca'><h1>Acerca de</h1>"
                + "<h2>" + Html.esc(NOMBRE) + "</h2><p>" + Html.esc(DESCRIPCION) + "</p>"
                + "<dl><dt>Autor</dt><dd>" + Html.esc(AUTOR) + "</dd>"
                + "<dt>Institución</dt><dd>" + Html.esc(INSTITUCION) + "</dd>"
                + "<dt>Tecnología</dt><dd>" + Html.esc(TECNOLOGIA) + "</dd>"
                + "<dt>Código fuente</dt><dd><a href='" + REPOSITORIO + "' rel='noopener noreferrer' target='_blank'>" + Html.esc(REPOSITORIO) + "</a></dd></dl>"
                + "<p><a href='/'>← Volver al Aula</a></p></section>";
    }
}
