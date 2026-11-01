package com.unev.aula;

import com.unev.aula.Modelo.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * Acceso a MySQL. Lee las tablas de SIGE (usuarios, secciones, asignaturas, inscripciones) y escribe solo en
 * las suyas (aula_*). Cada consulta limita el resultado a las secciones del usuario que pregunta.
 * Los métodos de escritura lanzan {@link Rechazo} cuando se incumple una regla (permiso, tarea calificada...).
 */
public class AulaDao {
    public static final int MAX_ADJUNTOS = 5;
    private static final int MAX_PUBLICACIONES = 300;
    private static final String INSCRITO = "EXISTS(SELECT 1 FROM detalle_inscripciones d JOIN inscripciones i ON i.id_inscripcion=d.id_inscripcion "
            + "WHERE d.id_seccion=%s AND i.id_estudiante=?)";
    private static final String FECHA = "DATE_FORMAT(%s,'%%Y-%%m-%%d %%H:%%i')";
    private final DataSource ds;

    public AulaDao(DataSource ds) { this.ds = ds; }

    // ---------- cuenta y secciones ----------

    /** La cuenta debe existir, estar activa y conservar el rol con el que SIGE la identificó. */
    public Optional<Usuario> usuarioActivo(int id, String rol) throws SQLException {
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT id_usuario,rol,CONCAT(nombre,' ',apellido) FROM usuarios WHERE id_usuario=? AND rol=? AND activo=1")) {
            ps.setInt(1, id);
            ps.setString(2, rol);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(new Usuario(rs.getInt(1), rs.getString(2), rs.getString(3))) : Optional.empty();
            }
        }
    }

    public boolean haySesionBd() {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) { st.execute("SELECT 1"); return true; }
        catch (SQLException e) { return false; }
    }

    public List<Seccion> secciones(Usuario u) throws SQLException {
        String sql = "SELECT s.id_seccion,a.nombre,g.nombre,s.horario FROM secciones s JOIN asignaturas a ON a.id_asignatura=s.id_asignatura "
                + "LEFT JOIN curriculo_grados g ON g.id=s.id_grado WHERE " + (u.esProfesor() ? "s.id_profesor=?" : String.format(INSCRITO, "s.id_seccion"))
                + " ORDER BY a.nombre,s.id_seccion";
        List<Seccion> lista = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, u.id());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(new Seccion(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4)));
            }
        }
        return lista;
    }

    // ---------- publicaciones ----------

    public List<Publicacion> publicaciones(Usuario u) throws SQLException { return consultar(u, 0); }

    /** Una publicación concreta, solo si el usuario puede verla. */
    public Optional<Publicacion> publicacion(Usuario u, int id) throws SQLException {
        return consultar(u, id).stream().findFirst();
    }

    private List<Publicacion> consultar(Usuario u, int soloId) throws SQLException {
        boolean prof = u.esProfesor();
        String sql = "SELECT m.id_material,m.id_seccion,a.nombre,g.nombre,m.tipo,m.titulo,m.descripcion,m.fecha_limite," + String.format(FECHA, "m.fecha_publicacion")
                + ",e.id_entrega,e.archivo_nombre,e.archivo_bytes," + String.format(FECHA, "e.fecha_entrega") + ",DATE(e.fecha_entrega),k.nota,k.comentario,"
                + "(SELECT COUNT(*) FROM aula_entregas t WHERE t.id_material=m.id_material),"
                + "(SELECT COUNT(*) FROM aula_entregas t LEFT JOIN aula_calificaciones kk ON kk.id_entrega=t.id_entrega WHERE t.id_material=m.id_material AND kk.id_entrega IS NULL) "
                + "FROM aula_materiales m JOIN secciones s ON s.id_seccion=m.id_seccion JOIN asignaturas a ON a.id_asignatura=s.id_asignatura "
                + "LEFT JOIN curriculo_grados g ON g.id=s.id_grado "
                + (prof ? "LEFT JOIN aula_entregas e ON 1=0 LEFT JOIN aula_calificaciones k ON k.id_entrega=e.id_entrega WHERE m.id_profesor=? "
                        : "LEFT JOIN aula_entregas e ON e.id_material=m.id_material AND e.id_estudiante=? LEFT JOIN aula_calificaciones k ON k.id_entrega=e.id_entrega WHERE "
                                + String.format(INSCRITO, "m.id_seccion") + " ")
                + (soloId > 0 ? "AND m.id_material=? " : "") + "ORDER BY m.fecha_publicacion DESC,m.id_material DESC LIMIT " + MAX_PUBLICACIONES;
        List<Publicacion> lista = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            int n = 1;
            if (!prof) ps.setInt(n++, u.id());
            ps.setInt(n++, u.id());
            if (soloId > 0) ps.setInt(n, soloId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Date limite = rs.getDate(8), dia = rs.getDate(14);
                    MiEntrega mia = rs.getObject(10) == null ? null : new MiEntrega(rs.getInt(10), rs.getString(11), rs.getLong(12), rs.getString(13),
                            dia == null ? null : dia.toLocalDate(), rs.getBigDecimal(15), rs.getString(16));
                    lista.add(new Publicacion(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7),
                            limite == null ? null : limite.toLocalDate(), rs.getString(9), List.of(), mia, rs.getInt(17), rs.getInt(18)));
                }
            }
            if (lista.isEmpty()) return lista;
            Map<Integer, List<Adjunto>> adj = adjuntos(c, lista.stream().map(Publicacion::id).toList());
            lista.replaceAll(p -> p.conAdjuntos(adj.getOrDefault(p.id(), List.of())));
        }
        return lista;
    }

    private static Map<Integer, List<Adjunto>> adjuntos(Connection c, List<Integer> ids) throws SQLException {
        Map<Integer, List<Adjunto>> mapa = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT id_material,id_adjunto,archivo_nombre,archivo_bytes FROM aula_adjuntos WHERE id_material IN (" + marcas(ids.size()) + ") ORDER BY id_adjunto")) {
            for (int i = 0; i < ids.size(); i++) ps.setInt(i + 1, ids.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) mapa.computeIfAbsent(rs.getInt(1), k -> new ArrayList<>()).add(new Adjunto(rs.getInt(2), rs.getString(3), rs.getLong(4)));
            }
        }
        return mapa;
    }

    static String marcas(int n) { return String.join(",", Collections.nCopies(n, "?")); }

    public List<EntregaRecibida> entregasDelProfesor(int idProfesor) throws SQLException {
        String sql = "SELECT e.id_entrega,e.id_material,CONCAT(u.nombre,' ',u.apellido),e.archivo_nombre,e.archivo_bytes," + String.format(FECHA, "e.fecha_entrega")
                + ",DATE(e.fecha_entrega),k.nota,k.comentario FROM aula_entregas e JOIN aula_materiales m ON m.id_material=e.id_material "
                + "JOIN usuarios u ON u.id_usuario=e.id_estudiante LEFT JOIN aula_calificaciones k ON k.id_entrega=e.id_entrega "
                + "WHERE m.id_profesor=? ORDER BY u.apellido,u.nombre";
        List<EntregaRecibida> lista = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idProfesor);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Date dia = rs.getDate(7);
                    lista.add(new EntregaRecibida(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getString(4), rs.getLong(5), rs.getString(6),
                            dia == null ? null : dia.toLocalDate(), rs.getBigDecimal(8), rs.getString(9)));
                }
            }
        }
        return lista;
    }

    /** @return id de la publicación. @throws Rechazo si la sección no es del profesor o hay demasiados archivos. */
    public int publicar(int profesor, int seccion, String tipo, String titulo, String descripcion, LocalDate limite, List<ArchivoGuardado> archivos) throws SQLException {
        if (archivos.size() > MAX_ADJUNTOS) throw new Rechazo("maximo");
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                int id;
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO aula_materiales(id_seccion,id_profesor,tipo,titulo,descripcion,fecha_limite) "
                        + "SELECT s.id_seccion,s.id_profesor,?,?,?,? FROM secciones s WHERE s.id_seccion=? AND s.id_profesor=?", Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, tipo);
                    ps.setString(2, titulo);
                    ps.setString(3, descripcion);
                    if (limite == null) ps.setNull(4, Types.DATE); else ps.setDate(4, Date.valueOf(limite));
                    ps.setInt(5, seccion);
                    ps.setInt(6, profesor);
                    if (ps.executeUpdate() != 1) throw new Rechazo("permiso");
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (!rs.next()) throw new SQLException("MySQL no devolvió el identificador.");
                        id = rs.getInt(1);
                    }
                }
                insertarAdjuntos(c, id, archivos);
                c.commit();
                return id;
            } catch (RuntimeException | SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /** Edita título, descripción y fecha límite; quita y agrega archivos. El tipo (clase/tarea) no cambia. */
    public void actualizar(int profesor, int material, String titulo, String descripcion, LocalDate limite, Collection<Integer> quitar, List<ArchivoGuardado> nuevos) throws SQLException {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                if (!esDelProfesor(c, profesor, material)) throw new Rechazo("permiso");
                try (PreparedStatement ps = c.prepareStatement("UPDATE aula_materiales SET titulo=?,descripcion=?,fecha_limite=? WHERE id_material=?")) {
                    ps.setString(1, titulo);
                    ps.setString(2, descripcion);
                    if (limite == null) ps.setNull(3, Types.DATE); else ps.setDate(3, Date.valueOf(limite));
                    ps.setInt(4, material);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM aula_adjuntos WHERE id_material=? AND id_adjunto=?")) {
                    for (int a : quitar) { ps.setInt(1, material); ps.setInt(2, a); ps.addBatch(); }
                    ps.executeBatch();
                }
                insertarAdjuntos(c, material, nuevos);
                try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM aula_adjuntos WHERE id_material=?")) {
                    ps.setInt(1, material);
                    try (ResultSet rs = ps.executeQuery()) { if (rs.next() && rs.getInt(1) > MAX_ADJUNTOS) throw new Rechazo("maximo"); }
                }
                c.commit();
            } catch (RuntimeException | SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }

    public void eliminarPublicacion(int profesor, int material) throws SQLException {
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement("DELETE FROM aula_materiales WHERE id_material=? AND id_profesor=?")) {
            ps.setInt(1, material);
            ps.setInt(2, profesor);
            if (ps.executeUpdate() != 1) throw new Rechazo("permiso");
        }
    }

    private static boolean esDelProfesor(Connection c, int profesor, int material) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM aula_materiales WHERE id_material=? AND id_profesor=?")) {
            ps.setInt(1, material);
            ps.setInt(2, profesor);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    private static void insertarAdjuntos(Connection c, int material, List<ArchivoGuardado> archivos) throws SQLException {
        if (archivos.isEmpty()) return;
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO aula_adjuntos(id_material,archivo_key,archivo_nombre,archivo_tipo,archivo_bytes) VALUES(?,?,?,?,?)")) {
            for (ArchivoGuardado a : archivos) {
                ps.setInt(1, material);
                ps.setString(2, a.key());
                ps.setString(3, a.nombre());
                ps.setString(4, a.tipo());
                ps.setLong(5, a.bytes());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // ---------- entregas y notas ----------

    /** Guarda o reemplaza la entrega. Solo si el estudiante está inscrito en la sección de la tarea y no está ya calificada. */
    public void entregar(int estudiante, int material, ArchivoGuardado archivo) throws SQLException {
        try (Connection c = ds.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM aula_materiales m WHERE m.id_material=? AND m.tipo='TAREA' AND " + String.format(INSCRITO, "m.id_seccion"))) {
                ps.setInt(1, material);
                ps.setInt(2, estudiante);
                try (ResultSet rs = ps.executeQuery()) { if (!rs.next()) throw new Rechazo("permiso"); }
            }
            try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM aula_entregas e JOIN aula_calificaciones k ON k.id_entrega=e.id_entrega WHERE e.id_material=? AND e.id_estudiante=?")) {
                ps.setInt(1, material);
                ps.setInt(2, estudiante);
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) throw new Rechazo("calificada"); }
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO aula_entregas(id_material,id_estudiante,archivo_key,archivo_nombre,archivo_tipo,archivo_bytes) VALUES(?,?,?,?,?,?) "
                    + "ON DUPLICATE KEY UPDATE archivo_key=VALUES(archivo_key),archivo_nombre=VALUES(archivo_nombre),archivo_tipo=VALUES(archivo_tipo),archivo_bytes=VALUES(archivo_bytes),fecha_entrega=CURRENT_TIMESTAMP")) {
                ps.setInt(1, material);
                ps.setInt(2, estudiante);
                ps.setString(3, archivo.key());
                ps.setString(4, archivo.nombre());
                ps.setString(5, archivo.tipo());
                ps.setLong(6, archivo.bytes());
                ps.executeUpdate();
            }
        }
    }

    /** Pone o corrige la nota (0 a 100) y el comentario de una entrega de una tarea del profesor. */
    public void calificar(int profesor, int entrega, BigDecimal nota, String comentario) throws SQLException {
        if (nota == null || nota.signum() < 0 || nota.compareTo(BigDecimal.valueOf(100)) > 0) throw new Rechazo("nota");
        try (Connection c = ds.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM aula_entregas e JOIN aula_materiales m ON m.id_material=e.id_material WHERE e.id_entrega=? AND m.id_profesor=?")) {
                ps.setInt(1, entrega);
                ps.setInt(2, profesor);
                try (ResultSet rs = ps.executeQuery()) { if (!rs.next()) throw new Rechazo("permiso"); }
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO aula_calificaciones(id_entrega,nota,comentario,id_profesor) VALUES(?,?,?,?) "
                    + "ON DUPLICATE KEY UPDATE nota=VALUES(nota),comentario=VALUES(comentario),id_profesor=VALUES(id_profesor),fecha=CURRENT_TIMESTAMP")) {
                ps.setInt(1, entrega);
                ps.setBigDecimal(2, nota.setScale(2, java.math.RoundingMode.HALF_UP));
                if (comentario == null || comentario.isBlank()) ps.setNull(3, Types.VARCHAR); else ps.setString(3, comentario);
                ps.setInt(4, profesor);
                ps.executeUpdate();
            }
        }
    }

    // ---------- comentarios ----------

    public Map<Integer, List<Comentario>> comentarios(Collection<Integer> materiales) throws SQLException {
        Map<Integer, List<Comentario>> mapa = new HashMap<>();
        if (materiales.isEmpty()) return mapa;
        List<Integer> ids = new ArrayList<>(materiales);
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT c.id_comentario,c.id_material,c.id_usuario,CONCAT(u.nombre,' ',u.apellido),u.rol,c.texto," + String.format(FECHA, "c.fecha")
                        + " FROM aula_comentarios c JOIN usuarios u ON u.id_usuario=c.id_usuario WHERE c.id_material IN (" + marcas(ids.size()) + ") ORDER BY c.fecha,c.id_comentario")) {
            for (int i = 0; i < ids.size(); i++) ps.setInt(i + 1, ids.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) mapa.computeIfAbsent(rs.getInt(2), k -> new ArrayList<>())
                        .add(new Comentario(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7)));
            }
        }
        return mapa;
    }

    public void comentar(Usuario u, int material, String texto) throws SQLException {
        String acceso = u.esProfesor() ? "m.id_profesor=?" : String.format(INSCRITO, "m.id_seccion");
        try (Connection c = ds.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM aula_materiales m WHERE m.id_material=? AND " + acceso)) {
                ps.setInt(1, material);
                ps.setInt(2, u.id());
                try (ResultSet rs = ps.executeQuery()) { if (!rs.next()) throw new Rechazo("permiso"); }
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO aula_comentarios(id_material,id_usuario,texto) VALUES(?,?,?)")) {
                ps.setInt(1, material);
                ps.setInt(2, u.id());
                ps.setString(3, texto);
                ps.executeUpdate();
            }
        }
    }

    /** Lo puede borrar su autor o el profesor dueño de la publicación. */
    public void eliminarComentario(Usuario u, int comentario) throws SQLException {
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(
                "DELETE c FROM aula_comentarios c JOIN aula_materiales m ON m.id_material=c.id_material WHERE c.id_comentario=? AND (c.id_usuario=? OR m.id_profesor=?)")) {
            ps.setInt(1, comentario);
            ps.setInt(2, u.id());
            ps.setInt(3, u.id());
            if (ps.executeUpdate() != 1) throw new Rechazo("permiso");
        }
    }

    // ---------- anuncios ----------

    public List<Anuncio> anuncios(Usuario u) throws SQLException {
        String acceso = u.esProfesor() ? "s.id_profesor=?" : String.format(INSCRITO, "s.id_seccion");
        String sql = "SELECT n.id_anuncio,n.id_seccion,a.nombre,CONCAT(au.nombre,' ',au.apellido),n.id_autor,n.texto," + String.format(FECHA, "n.fecha")
                + " FROM aula_anuncios n JOIN secciones s ON s.id_seccion=n.id_seccion JOIN asignaturas a ON a.id_asignatura=s.id_asignatura "
                + "JOIN usuarios au ON au.id_usuario=n.id_autor WHERE " + acceso + " ORDER BY n.fecha DESC,n.id_anuncio DESC LIMIT 100";
        List<Anuncio> lista = new ArrayList<>();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, u.id());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) lista.add(new Anuncio(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getString(4), rs.getInt(5), rs.getString(6), rs.getString(7)));
            }
        }
        return lista;
    }

    public void anunciar(int profesor, int seccion, String texto) throws SQLException {
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO aula_anuncios(id_seccion,id_autor,texto) SELECT s.id_seccion,s.id_profesor,? FROM secciones s WHERE s.id_seccion=? AND s.id_profesor=?")) {
            ps.setString(1, texto);
            ps.setInt(2, seccion);
            ps.setInt(3, profesor);
            if (ps.executeUpdate() != 1) throw new Rechazo("permiso");
        }
    }

    public void eliminarAnuncio(int profesor, int anuncio) throws SQLException {
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement("DELETE FROM aula_anuncios WHERE id_anuncio=? AND id_autor=?")) {
            ps.setInt(1, anuncio);
            ps.setInt(2, profesor);
            if (ps.executeUpdate() != 1) throw new Rechazo("permiso");
        }
    }

    // ---------- archivos ----------

    public Optional<Descarga> archivoAdjunto(Usuario u, int adjunto) throws SQLException {
        String permiso = u.esProfesor() ? "m.id_profesor=?" : String.format(INSCRITO, "m.id_seccion");
        return descarga("SELECT a.archivo_nombre,a.archivo_key FROM aula_adjuntos a JOIN aula_materiales m ON m.id_material=a.id_material WHERE a.id_adjunto=? AND " + permiso, adjunto, u.id());
    }

    /** El profesor dueño de la tarea o el estudiante que la entregó. */
    public Optional<Descarga> archivoEntrega(Usuario u, int entrega) throws SQLException {
        String permiso = u.esProfesor() ? "m.id_profesor=?" : "e.id_estudiante=?";
        return descarga("SELECT e.archivo_nombre,e.archivo_key FROM aula_entregas e JOIN aula_materiales m ON m.id_material=e.id_material WHERE e.id_entrega=? AND " + permiso, entrega, u.id());
    }

    private Optional<Descarga> descarga(String sql, int id, int usuario) throws SQLException {
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setInt(2, usuario);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Optional.of(new Descarga(rs.getString(1), rs.getString(2))) : Optional.empty(); }
        }
    }

    public String guardarArchivo(byte[] datos) throws SQLException {
        String key = UUID.randomUUID().toString();
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement("INSERT INTO aula_archivos(archivo_key,contenido) VALUES(?,?)")) {
            ps.setString(1, key);
            ps.setBytes(2, datos);
            ps.executeUpdate();
        }
        return key;
    }

    public byte[] leerArchivo(String key) throws SQLException {
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT contenido FROM aula_archivos WHERE archivo_key=?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getBytes(1) : null; }
        }
    }

    public void eliminarArchivo(String key) {
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement("DELETE FROM aula_archivos WHERE archivo_key=?")) {
            ps.setString(1, key);
            ps.executeUpdate();
        } catch (SQLException ignorada) { /* la limpieza periódica lo recogerá */ }
    }

    /** Borra contenidos que ya no pertenecen a ninguna publicación, adjunto ni entrega (más de 1 hora de antigüedad). */
    public int limpiarHuerfanos() throws SQLException {
        String sql = "DELETE FROM aula_archivos WHERE fecha<NOW()-INTERVAL 1 HOUR "
                + "AND archivo_key NOT IN(SELECT archivo_key FROM aula_adjuntos) "
                + "AND archivo_key NOT IN(SELECT archivo_key FROM aula_materiales WHERE archivo_key IS NOT NULL) "
                + "AND archivo_key NOT IN(SELECT archivo_key FROM aula_entregas)";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) { return ps.executeUpdate(); }
    }
}
