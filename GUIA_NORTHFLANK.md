# Aula Virtual de SIGE: despliegue en Northflank

El Aula es una aplicación **aparte** de SIGE: su propio repositorio, su propio jar y su propio servicio.
Comparte con SIGE la base de datos MySQL y el inicio de sesión (SIGE firma un token de 60 segundos y el Aula lo verifica).
Northflank se eligió porque su plan gratuito (Sandbox) no pide tarjeta y mantiene los servicios encendidos, sin dormirse.
Los límites de los planes gratuitos cambian: confirma en el panel cuánta CPU y RAM te da el servicio antes de desplegar.

## 1. Base de datos
Ejecuta en MySQL, **en este orden**, cada script una sola vez (es seguro repetirlos):
1. `sql/aula_inicial.sql`
2. `sql/aula_v2.sql`

### Usuario propio para el Aula (recomendado)
Así el Aula solo puede leer lo que necesita de SIGE y escribir en sus tablas. En Workbench, conectado como administrador:

```sql
-- Reemplaza TU_CLAVE_LARGA por una contraseña nueva y larga (no la que usa SIGE). No la compartas.
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

Si tu base no se llama `sige_db`, cambia el nombre. Si el servicio de MySQL no te deja crear usuarios o conceder permisos, usa el usuario actual y dilo en el informe como limitación.

## 2. Secreto compartido
Genera un secreto largo (PowerShell) y úsalo **idéntico** en SIGE y en el Aula. No lo pegues en el chat ni en el repositorio.

```powershell
$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
```

## 3. Servicio en Northflank
1. Entra en northflank.com con tu cuenta de GitHub. No pongas tarjeta; elige el plan gratuito (Sandbox).
2. Crea un **proyecto** (nombre: `sige`) y, dentro, un servicio nuevo de tipo **Combined** (construye desde un repositorio y despliega).
3. Conecta GitHub, autoriza el acceso **solo** al repositorio `sige-aula` y elige la rama `main`.
4. *Build*: método **Dockerfile**, ruta `/Dockerfile`, contexto `/`.
5. *Resources*: elige el recurso más pequeño que tenga **al menos 512 MB de RAM** (con menos, la JVM se queda sin memoria).
6. *Networking*: agrega el puerto **8080**, protocolo **HTTP**, y márcalo como **público**.
7. *Health checks*: HTTP en el puerto 8080, ruta `/health`.
8. *Runtime variables* (las sensibles como **secreto**):

| Variable | Valor |
|---|---|
| `AULA_DB_URL` | la misma URL JDBC de SIGE (con `sslMode=REQUIRED` para Aiven) |
| `AULA_DB_USER` | `aula_app` (o el usuario de MySQL que uses) |
| `AULA_DB_PASSWORD` | contraseña de ese usuario (secreto) |
| `AULA_SSO_SECRET` | el secreto del paso 2 (secreto, 32+ caracteres) |
| `AULA_SIGE_URL` | `https://sistema-de-gestion-escolar-con-java.onrender.com` |
| `PORT` | `8080` |
| `AULA_MAX_UPLOAD_MB` | `10` (opcional, por archivo) |
| `AULA_MAX_REQUEST_MB` | `25` (opcional, por envío completo) |

9. Pulsa **Create service** y espera a que la construcción termine y el servicio quede en ejecución (la primera vez tarda varios minutos).
10. Copia la **URL pública** que muestra Northflank (termina en `.code.run`) y prueba `https://TU-URL/health`: debe decir `ok`. Con `?db=1` comprueba también MySQL.

## 4. Conectar SIGE (Render)
En el servicio de SIGE añade estas variables y guarda (se reinicia solo):

| Variable | Valor |
|---|---|
| `SIGE_AULA_URL` | la URL pública del paso 3, sin barra final |
| `SIGE_AULA_SSO_SECRET` | el mismo secreto del paso 2 |

Desde ese momento, "Aula y tareas" en SIGE lleva al Aula ya autenticado. Si quitas esas dos variables, SIGE vuelve a usar el aula integrada.

## 5. Comprobaciones
- Entra a SIGE como profesor, abre "Aula y tareas" y publica una tarea con dos archivos.
- Entra como estudiante inscrito en esa sección, entrega un archivo y descárgalo.
- Calificar: como profesor, abre "Entregas recibidas", pon una nota de 0 a 100 y un comentario; como estudiante, comprueba que ves la nota y que ya no puedes reemplazar la entrega.
- Estados: una tarea con fecha pasada y sin entrega aparece "Sin entregar (vencida)"; una entrega posterior a la fecha límite aparece "Entregada tarde".
- Anuncios y comentarios: publica un anuncio en "Tablón" y comenta una publicación con las dos cuentas.
- Editar y eliminar: cambia el título, quita un archivo y agrega otro; elimina una publicación de prueba.
- Filtros: en "Publicaciones" prueba buscar por texto (sin acentos), sección, tipo y estado.
- Reutilizar el enlace `/sso?token=...` debe dar "enlace no válido o caducado".
- Cuenta desactivada en SIGE: el Aula la rechaza al entrar y revisa la cuenta cada 5 minutos.

## Notas del plan gratuito
- Poca CPU y 512 MB de RAM: el primer arranque y las subidas de archivos grandes pueden ser lentos. Por eso el límite es 10 MB por archivo, 5 archivos por publicación y 25 MB por envío, y solo se procesan 2 subidas a la vez.
- Si el servicio se reinicia, las sesiones en memoria se pierden: basta volver a abrir el Aula desde SIGE.
- Los archivos viven en MySQL (tabla `aula_archivos`), no en el disco del servicio, así que sobreviven a los reinicios y redespliegues.
- La fecha límite cuenta hasta el final de ese día, en la zona horaria del servidor (UTC). Una entrega hecha después se marca "tarde".
- Si Northflank cambia o retira el plan gratuito, el mismo `Dockerfile` sirve en Render, Cloud Run u otro servicio con Docker: solo cambia el paso 3.
