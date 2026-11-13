# Aula Virtual de SIGE: despliegue en Render (segundo servicio)

El Aula es una aplicación **aparte** de SIGE (otro repositorio, otro jar, otro servicio). Comparte la base de datos MySQL y el inicio de sesión: SIGE firma un token de **180 segundos** y el Aula lo verifica.

## Qué esperar del plan gratuito
- Cada servicio gratuito se duerme tras 15 min sin tráfico y tarda 30-60 s en despertar. Si SIGE y el Aula están dormidos, la primera entrada puede tardar hasta 2 minutos. Por eso el token dura 180 s.
- Las 750 horas gratuitas del mes se reparten entre **todos** los servicios. Un servicio siempre encendido consume ~744 h. Si SIGE sigue con UptimeRobot, el Aula agotaría las horas y Render suspendería los servicios. **Pausa el monitor de UptimeRobot de SIGE** (o déjalo solo en horas de clase).
- Los archivos viven en MySQL, no en el disco: sobreviven a los reinicios.
- Las sesiones están en memoria: si el Aula se duerme, basta volver a entrar desde SIGE.

## 1. Base de datos
Ejecuta, en este orden, `sql/aula_inicial.sql` y `sql/aula_v2.sql` (ya los corriste; es seguro repetirlos). Comprueba con `SHOW TABLES LIKE 'aula_%';` que salen 7 tablas.

### Usuario propio para el Aula (recomendado)
```sql
-- Reemplaza TU_CLAVE_LARGA por una contraseña nueva y larga. No la compartas.
CREATE USER 'aula_app'@'%' IDENTIFIED BY 'TU_CLAVE_LARGA';

GRANT SELECT ON sige_db.usuarios TO 'aula_app'@'%';
GRANT SELECT ON sige_db.secciones TO 'aula_app'@'%';
GRANT SELECT ON sige_db.asignaturas TO 'aula_app'@'%';
GRANT SELECT ON sige_db.inscripciones TO 'aula_app'@'%';
GRANT SELECT ON sige_db.detalle_inscripciones TO 'aula_app'@'%';
GRANT SELECT ON sige_db.curriculo_grados TO 'aula_app'@'%';

GRANT SELECT, INSERT, UPDATE, DELETE ON sige_db.aula_archivos TO 'aula_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sige_db.aula_materiales TO 'aula_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sige_db.aula_adjuntos TO 'aula_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sige_db.aula_entregas TO 'aula_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sige_db.aula_calificaciones TO 'aula_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sige_db.aula_anuncios TO 'aula_app'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON sige_db.aula_comentarios TO 'aula_app'@'%';
```
Si tu base no se llama `sige_db`, cambia el nombre. Si TiDB no te deja, usa el usuario actual y anótalo como limitación.

## 2. Secreto compartido
```powershell
$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
```
Úsalo idéntico en el Aula y en SIGE. Pégalo solo en los paneles de Render, nunca en el chat ni en el repositorio.

## 3. Crear el servicio en Render
1. Dashboard → **New → Web Service** → conecta el repositorio `sige-aula` (rama `main`).
2. *Name*: `sige-aula`. *Language*: **Docker**. *Instance type*: **Free**.
3. *Advanced → Health Check Path*: `/health`.
4. *Environment variables* (no definas `PORT`; Render lo pone solo y el Aula lo lee):

| Variable | Valor |
|---|---|
| `AULA_DB_URL` | la misma URL JDBC de SIGE (con `sslMode=REQUIRED` para TiDB, que exige TLS; usa `sslMode=VERIFY_IDENTITY`) |
| `AULA_DB_USER` | `aula_app` (o el usuario que uses) |
| `AULA_DB_PASSWORD` | contraseña de ese usuario |
| `AULA_SSO_SECRET` | el secreto del paso 2 |
| `AULA_SIGE_URL` | `https://sistema-de-gestion-escolar-con-java.onrender.com` |
| `AULA_MAX_UPLOAD_MB` | `10` (opcional) |
| `AULA_MAX_REQUEST_MB` | `25` (opcional) |
| `AULA_ZONA` | `America/Santo_Domingo` (opcional; ya es el valor por defecto) |

5. **Create Web Service** y espera el build (varios minutos). Copia la URL (`https://sige-aula-xxxx.onrender.com`) y abre `/health` (debe decir `ok`; con `?db=1` prueba MySQL).

## 4. Conectar SIGE
Antes, sube a SIGE el parche `sige-sso-aula.zip` actualizado (token de 180 s). Después, en el servicio de SIGE en Render:

| Variable | Valor |
|---|---|
| `SIGE_AULA_URL` | la URL del Aula, sin barra final |
| `SIGE_AULA_SSO_SECRET` | el mismo secreto del paso 2 |

Si quitas esas dos variables, SIGE vuelve a usar el aula integrada.

## 5. Comprobaciones
- Profesor: abre "Aula y tareas" en SIGE y publica una tarea con dos archivos.
- Estudiante inscrito: entrega un archivo y descárgalo.
- Calificar: profesor pone nota 0-100 y comentario; el estudiante ve la nota y ya no puede reemplazar la entrega.
- Estados: tarea vencida sin entrega = "Sin entregar (vencida)"; entrega posterior a la fecha = "Entregada tarde".
- Tablón y comentarios con las dos cuentas; editar (quitar/agregar archivo) y eliminar.
- Filtros por texto sin acentos, sección, tipo y estado.
- Reutilizar `/sso?token=...` debe dar "enlace no válido o caducado".
