# SIGE · Aula Virtual

Subsistema del Sistema de Gestión Escolar (SIGE). Permite a los profesores publicar clases y tareas, y a los estudiantes entregarlas.
Aplicación independiente (Java 21, sin frameworks, servidor `com.sun.net.httpserver`) que comparte con SIGE la base de datos y el inicio de sesión.

- Despliegue: [GUIA_RENDER.md](GUIA_RENDER.md)
- Esquema: [sql/aula_inicial.sql](sql/aula_inicial.sql) y [sql/aula_v2.sql](sql/aula_v2.sql)
- Pruebas: `mvn clean verify` (JUnit 5)

## Funciones
- Profesores: publicar clases y tareas con hasta 5 archivos, editar y eliminar publicaciones, calificar entregas (0 a 100, con comentario), publicar anuncios por sección y comentar.
- Estudiantes: ver clases y tareas de sus secciones, entregar y reemplazar su entrega hasta que se califique, ver su nota y el comentario del profesor, recordatorios de vencimiento y comentarios.
- Estados de tarea: pendiente, sin entregar (vencida), entregada, entregada tarde, calificada.
- Filtros de búsqueda: texto (sin distinguir acentos), sección, tipo y estado; el tablón filtra por sección. Se aplican en memoria sobre lo que el usuario ya tiene permiso de ver.
- Tablón de anuncios por sección.

## Arquitectura
```
Navegador ──► SIGE (Render) ──303 + token HMAC (180 s)──► Aula (Render) ──► MySQL (Aiven)
                    └────────────── misma base de datos ──────────────────┘
```
- `Sso`: crea y verifica el token (HMAC-SHA256, caduca en 180 s, un solo uso).
- `Sesiones`: sesión en memoria (8 h), cookie HttpOnly + SameSite=Lax, token CSRF por sesión.
- `AulaDao`: solo LEE las tablas de SIGE y escribe en `aula_*`; cada consulta se limita a las secciones del usuario.
- `Servidor`: rutas, cabeceras de seguridad (CSP sin scripts) y validación de origen en formularios.

## Variables de entorno
`AULA_DB_URL`, `AULA_DB_USER`, `AULA_DB_PASSWORD`, `AULA_SSO_SECRET` (32+), `AULA_SIGE_URL`, `PORT`,
`AULA_SECURE_COOKIE` (por defecto `true`), `AULA_MAX_UPLOAD_MB` (10), `AULA_MAX_REQUEST_MB` (25), `AULA_DB_POOL_SIZE` (4), `AULA_ZONA` (zona horaria para mostrar fechas y contar el día límite; por defecto `America/Santo_Domingo`).

## Accesibilidad visual y botones dinámicos

- **Modos de visión:** Normal, Daltonismo (azul y naranja, paleta Okabe-Ito, más símbolos ✔ ⚠ para no depender del color) y Alto contraste.
- **Web:** icono de ojo en la barra superior (menú con los modos de visión); la elección se guarda en una cookie (`aula_vision`) sin datos personales y no usa JavaScript.
- **Botones dinámicos:** el escritorio los anima con JavaFX (crecen al pasar el cursor y se hunden al pulsar) y la web con transiciones CSS; se desactivan con «reducir movimiento» del sistema. No requiere bibliotecas nuevas.
- **Iconos:** cada apartado del menú lleva un icono decorativo (oculto para lectores de pantalla) y la pestaña muestra el logo de SIGE.
- **Tema claro y oscuro:** un icono de brillo (luna/sol) en la barra superior cambia entre tema claro y oscuro; por defecto sigue el tema del sistema (`aula_tema`, por defecto Automático). El modo de visión (daltonismo, alto contraste) siempre prevalece sobre el tema.
