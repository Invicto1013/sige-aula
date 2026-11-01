SET NAMES utf8mb4;
-- Aula Virtual, versión 2. Ejecutar DESPUÉS de aula_inicial.sql, una sola vez (es seguro repetirlo).
-- Solo crea tablas nuevas y copia los adjuntos antiguos: no modifica las tablas existentes.

-- Varios archivos por publicación.
CREATE TABLE IF NOT EXISTS aula_adjuntos (
    id_adjunto INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    id_material INT NOT NULL,
    archivo_key CHAR(36) NOT NULL,
    archivo_nombre VARCHAR(180) NOT NULL,
    archivo_tipo VARCHAR(255) NULL,
    archivo_bytes BIGINT UNSIGNED NOT NULL,
    CONSTRAINT fk_aula_adjunto_material FOREIGN KEY (id_material) REFERENCES aula_materiales(id_material) ON DELETE CASCADE,
    INDEX idx_aula_adjunto_material (id_material)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Los archivos de publicaciones antiguas (un solo archivo en aula_materiales) pasan a la tabla nueva.
INSERT INTO aula_adjuntos (id_material, archivo_key, archivo_nombre, archivo_tipo, archivo_bytes)
SELECT m.id_material, m.archivo_key, COALESCE(m.archivo_nombre,'archivo'), m.archivo_tipo, m.archivo_bytes
FROM aula_materiales m
WHERE m.archivo_key IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM aula_adjuntos a WHERE a.id_material = m.id_material AND a.archivo_key = m.archivo_key);

-- Nota y comentario del profesor sobre una entrega.
CREATE TABLE IF NOT EXISTS aula_calificaciones (
    id_entrega INT NOT NULL PRIMARY KEY,
    nota DECIMAL(5,2) NOT NULL,
    comentario TEXT NULL,
    id_profesor INT NOT NULL,
    fecha TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_aula_calif_entrega FOREIGN KEY (id_entrega) REFERENCES aula_entregas(id_entrega) ON DELETE CASCADE,
    CONSTRAINT fk_aula_calif_profesor FOREIGN KEY (id_profesor) REFERENCES usuarios(id_usuario),
    CONSTRAINT ck_aula_calif_nota CHECK (nota >= 0 AND nota <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Tablón de anuncios por sección.
CREATE TABLE IF NOT EXISTS aula_anuncios (
    id_anuncio INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    id_seccion INT NOT NULL,
    id_autor INT NOT NULL,
    texto VARCHAR(2000) NOT NULL,
    fecha TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_aula_anuncio_seccion FOREIGN KEY (id_seccion) REFERENCES secciones(id_seccion) ON DELETE CASCADE,
    CONSTRAINT fk_aula_anuncio_autor FOREIGN KEY (id_autor) REFERENCES usuarios(id_usuario),
    INDEX idx_aula_anuncio_seccion (id_seccion, fecha)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Comentarios en cada publicación.
CREATE TABLE IF NOT EXISTS aula_comentarios (
    id_comentario INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    id_material INT NOT NULL,
    id_usuario INT NOT NULL,
    texto VARCHAR(1000) NOT NULL,
    fecha TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_aula_coment_material FOREIGN KEY (id_material) REFERENCES aula_materiales(id_material) ON DELETE CASCADE,
    CONSTRAINT fk_aula_coment_usuario FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario),
    INDEX idx_aula_coment_material (id_material, fecha)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
