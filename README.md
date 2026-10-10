# snippet-service

Servicio de Snippet Searcher dueño de los snippets. Es el orquestador:

- guarda la **metadata** en su Postgres y el **código** en el bucket (asset service + Azurite);
- le pide a `printscript-service` que **valide** antes de guardar;
- le pide a `permission-service` que **registre y chequee** permisos.

También es el dueño del `docker-compose.yml` que levanta el sistema entero.

## Endpoints

Los consume la UI. Todos llevan el header `X-User-Id` con el id del usuario: es un placeholder hasta
que llegue Auth0, y lo lee un solo lugar (`CurrentUserResolver`).

| Método | Path | Body | Qué hace |
|---|---|---|---|
| POST | `/snippets` | JSON `{name, description?, language, version, content}` | Crea un snippet desde el editor (US3) |
| POST | `/snippets` | multipart: `file` + `name, description?, language, version` | Crea un snippet desde un archivo (US1) |
| PATCH | `/snippets/{id}` | JSON con cualquier campo de los de arriba | Actualiza desde el editor (US4). Solo el owner |
| PATCH | `/snippets/{id}` | multipart: `file` + campos opcionales | Actualiza desde un archivo (US2). Solo el owner |
| GET | `/snippets?relation=OWNED\|SHARED\|ALL&name=&language=` | — | Lista los snippets a los que tiene acceso, ordenados por nombre (US5) |
| GET | `/snippets/{id}` | — | Metadata + contenido (US6) |
| DELETE | `/snippets/{id}` | — | Borra la fila, el contenido y los permisos. Solo el owner |

La respuesta de un snippet es `{id, name, description, language, version, ownerId, content}`. El listado
devuelve lo mismo, pero sin `content`.

### Errores

| Status | Cuándo | Body |
|---|---|---|
| `422` | El snippet no cumple las reglas del lenguaje | `{error, errors: [{message, line, column, endLine, endColumn}]}` |
| `400` | Falta un campo, el lenguaje no está soportado o la versión no existe | `{error}` |
| `401` | Falta `X-User-Id` | `{error}` |
| `403` | Tiene permiso de lectura pero intenta modificar o borrar | `{error}` |
| `404` | El snippet no existe o el usuario no tiene ningún permiso sobre él | `{error}` |
| `502` | Un servicio de abajo no responde: se puede reintentar | `{error}` |

> **`errors` trae un solo elemento.** El Engine de PrintScript corta en el primer error. Es una lista
> para poder devolver varios más adelante sin romper el contrato, pero la UI no tiene que asumir que
> le llegan todos.

## Lo que consume de los otros servicios

| Servicio | Llamadas |
|---|---|
| `printscript-service` | `POST /validations {language, version, content}` → `{valid, errors}` |
| `permission-service` | `POST /internal/permissions`, `GET /internal/permissions/check`, `GET /internal/permissions/resources`, `DELETE /internal/permissions` (siempre con `resourceType=snippet`) |
| asset service | `PUT`, `GET` y `DELETE` sobre `/v1/asset/snippets/{snippetId}` |

## Variables de entorno

| Variable | Default (para correrlo local) |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/snippets` |
| `DB_USER` / `DB_PASSWORD` | `snippet` / `snippet` |
| `PRINTSCRIPT_SERVICE_URL` | `http://localhost:8082` |
| `PERMISSION_SERVICE_URL` | `http://localhost:8081` |
| `ASSET_SERVICE_URL` | `http://localhost:8083` |

## Levantar el sistema entero

Los tres repos tienen que estar clonados uno al lado del otro (`snippet-service`, `printscript-service`
y `permission-service`), porque el compose construye los otros dos desde su carpeta.

```bash
cp .env.example .env   # y completar GITHUB_ACTOR y GITHUB_TOKEN (scope read:packages)
docker compose up --build
```

| Contenedor | Puerto en el host |
|---|---|
| snippet-service | 8080 |
| permission-service | 8081 |
| printscript-service | 8082 |
| asset-service | 8083 |
| snippet-db / permission-db | 5432 / 5433 |
| azurite | 10000 |

El token solo lo usa `printscript-service` para bajar `org.printscript:runner` de GitHub Packages al
compilar. Entra por `.env`, que está en `.gitignore`, y no queda en ninguna imagen.

Para probarlo:

```bash
curl -X POST localhost:8080/snippets -H "X-User-Id: u1" -H "Content-Type: application/json" \
  -d '{"name":"hola","language":"printscript","version":"1.1","content":"println(\"hola\");"}'
```

## Desarrollo

```bash
./gradlew bootRun   # necesita las bases y los otros servicios (por ejemplo, el compose sin snippet-service)
./gradlew check     # ktlint + tests. Es lo mismo que corre el CI en cada PR contra main o dev
```

Los tests no necesitan red: usan H2 en lugar de Postgres y fakes en memoria en lugar de los tres
servicios externos.
