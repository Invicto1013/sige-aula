# SIGE · Aula Virtual v1.1.0 — Novedades

## Dominó para dos jugadores 🎲
- Nuevo juego en línea para profesores y estudiantes (menú «Dominó»): crea una partida o únete a una abierta.
- Dominó doble seis: 7 fichas por jugador, pozo de 14, robar o pasar cuando no hay jugada, victoria por quedarse sin fichas o por menos puntos si el juego se tranca, y opción de abandonar.
- Sin scripts en el navegador: cada jugada es un formulario con protección CSRF y la página se actualiza sola.
- Solo los dos jugadores acceden a su partida y solo juega quien tiene el turno.
- Las partidas viven en memoria: se pierden si el servicio se reinicia o duerme y caducan a las 3 h sin movimientos.

## Acerca de ❓
- Icono ❓ en la barra superior con la página pública `/acerca`: descripción del proyecto, autor (Elian Castro), institución (Universidad Nacional Evangélica, UNEV), tecnología y repositorio.
- Autor e institución también en el pie de página y el README.

## Infraestructura
- Base de datos compartida con SIGE migrada a **TiDB Cloud Starter** (MySQL compatible, TLS obligatorio). Variables: `AULA_DB_URL` (con `sslMode=VERIFY_IDENTITY`), `AULA_DB_USER`, `AULA_DB_PASSWORD`.
- README y guía de Render actualizados.

## Actualizar una instalación existente
Cambia las tres variables `AULA_DB_*` en Render si aún apuntan a la base anterior. No hay scripts SQL nuevos.
