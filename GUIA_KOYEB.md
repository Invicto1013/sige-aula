# Aula Virtual de SIGE: despliegue en Koyeb

El Aula es una aplicación **aparte** de SIGE: su propio repositorio, su propio jar y su propio servicio.
Comparte con SIGE la base de datos MySQL y el inicio de sesión (SIGE firma un token de 60 segundos y el Aula lo verifica).

## 1. Repositorio
Crea un repositorio nuevo (por ejemplo `sige-aula`), copia aquí el contenido de este proyecto y súbelo a `main`.
Al hacer push, GitHub Actions ejecuta las pruebas.

## 2. Base de datos (antes de desplegar la versión nueva)
Ejecuta en MySQL, **en este orden**, cada script una sola vez (es seguro repetirlos):
1. `sql/aula_inicial.sql`: las tablas base. Si ya usabas el aula dentro de SIGE, ya existen y se conservan.
2. `sql/aula_v2.sql`: tablas nuevas (varios archivos por publicación, notas, anuncios, comentarios) y copia de los archivos antiguos.

Si despliegas el código nuevo sin ejecutar `aula_v2.sql`, las pantallas mostrarán "No se pudo cargar el aula".
Después de ejecutarlo, el aula integrada de SIGE (la de reserva) ya no verá los archivos de publicaciones nuevas: usa el Aula de Koyeb.

## 3. Secreto compartido
Genera un secreto largo (PowerShell) y úsalo **idéntico** en SIGE y en el Aula. No lo pegues en el chat ni en el repositorio.

```powershell
$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
```

## 4. Servicio en Koyeb
1. Crea la cuenta en koyeb.com (piden tarjeta solo para verificar; el servicio `free` no se cobra).
2. **Create Service → GitHub** y elige el repositorio. En *Builder* selecciona **Dockerfile**.
3. *Instance*: **Free**. Región: Frankfurt o Washington D. C. (la más cercana a tu base de datos).
4. *Exposed port*: **8000**, protocolo HTTP, ruta `/`.
5. *Health check*: HTTP, ruta `/health`.
6. *Environment variables* (las sensibles como **Secret**):

| Variable | Valor |
|---|---|
| `AULA_DB_URL` | la misma URL JDBC de SIGE (con `sslMode=REQUIRED` para Aiven) |
| `AULA_DB_USER` | usuario de MySQL |
| `AULA_DB_PASSWORD` | contraseña de MySQL (secret) |
| `AULA_SSO_SECRET` | el secreto del paso 3 (secret, 32+ caracteres) |
| `AULA_SIGE_URL` | `https://sistema-de-gestion-escolar-con-java.onrender.com` |
| `PORT` | `8000` |
| `AULA_MAX_UPLOAD_MB` | `10` (opcional, por archivo) |
| `AULA_MAX_REQUEST_MB` | `25` (opcional, por envío completo) |

7. Pulsa **Deploy** y espera a que quede *Healthy*. Prueba `https://TU-APP.koyeb.app/health` (debe decir `ok`; con `?db=1` comprueba también MySQL).

## 5. Conectar SIGE (Render)
En el servicio de SIGE añade estas variables y guarda (se reinicia solo):

| Variable | Valor |
|---|---|
| `SIGE_AULA_URL` | `https://TU-APP.koyeb.app` |
| `SIGE_AULA_SSO_SECRET` | el mismo secreto del paso 3 |

Desde ese momento, "Aula y tareas" en SIGE lleva al Aula ya autenticado. Si quitas esas dos variables, SIGE vuelve a usar el aula integrada.

## 6. Comprobaciones
- Entra a SIGE como profesor, abre "Aula y tareas", publica una tarea con archivo.
- Entra como estudiante inscrito en esa sección, entrega un archivo y descárgalo.
- Calificar: como profesor, abre "Entregas recibidas", pon una nota de 0 a 100 y un comentario; como estudiante, comprueba que ves la nota y que ya no puedes reemplazar la entrega.
- Estados: una tarea con fecha pasada y sin entrega aparece "Sin entregar (vencida)"; una entrega posterior a la fecha límite aparece "Entregada tarde".
- Anuncios y comentarios: publica un anuncio en "Tablón" y comenta una publicación con las dos cuentas.
- Editar y eliminar: cambia el título, quita un archivo y agrega otro; elimina una publicación de prueba.
- Filtros: en "Publicaciones" prueba buscar por texto (sin acentos), sección, tipo y estado.
- Reutilizar el enlace `/sso?token=...` debe dar "enlace no válido o caducado".
- Cuenta desactivada en SIGE: el Aula la rechaza al entrar y revisa la cuenta cada 5 minutos.

## Notas del plan gratuito
- 512 MB de RAM y 0,1 vCPU: el primer arranque y las subidas de archivos grandes son lentos. Por eso el límite es 10 MB por archivo, 5 archivos por publicación y 25 MB por envío, y solo se procesan 2 subidas a la vez.
- La fecha límite cuenta hasta el final de ese día, en la zona horaria del servidor (UTC). Una entrega hecha después se marca "tarde".
- Si el servicio se duerme por inactividad o se reinicia, las sesiones en memoria se pierden: basta volver a abrir el Aula desde SIGE.
- Los archivos viven en MySQL (tabla `aula_archivos`), no en el disco del servicio, así que sobreviven a los reinicios.
