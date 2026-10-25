# snippet-service

Servicio de Snippet Searcher dueño de los snippets. Es el orquestador: guarda la metadata en su Postgres
y el código en el bucket, le pide a `printscript-service` que valide cada snippet antes de guardarlo y a
`permission-service` que registre y chequee quién puede verlo o modificarlo.

También tiene el `docker-compose.yml` que levanta el sistema entero.

El diseño de la plataforma está en el repo `snippet-searcher-infra`.

## Correrlo

```
./gradlew bootRun
```

Levanta en el puerto `8080`. Necesita la base y los otros servicios arriba (por ejemplo, el compose sin
`snippet-service`). Para ver que está arriba: `GET http://localhost:8080/actuator/health`.

## Checks

```
./gradlew check
```

Corre ktlint y los tests. Es lo mismo que corre el CI en cada PR contra `dev` o `prod`.

## Publicación de la imagen (CD)

Cada push/merge a `dev` o a `prod` construye la imagen Docker y la sube a
`ghcr.io/ingsis-printscript-grupo3/snippet-service` con dos tags:

| Tag | Qué es |
| --- | --- |
| `dev` o `prod` | La última imagen de esa rama. Se pisa en cada merge |
| el hash del commit (`sha`) | Esa imagen exacta. Inmutable, sirve para rollbacks |

```bash
docker pull ghcr.io/ingsis-printscript-grupo3/snippet-service:dev
```

## Branching Strategy

Seguimos una estrategia basada en ramas cortas y dos ambientes principales:

- **Rama default (`prod`)**: Refleja lo que está desplegado en el ambiente de producción.
- **Rama de integración (`dev`)**: Integra los cambios continuos y se despliega en el ambiente de desarrollo.
- **Ramas de trabajo cortas** (`feat/`, `fix/`, `refactor/`): Salen de `dev` y entran a `dev` exclusivamente mediante Pull Request.
- **Releases a producción**: Se realizan mediante PRs desde `dev` hacia `prod`.
- **Criterios de Merge**:
  - Al menos 1 aprobación requerida (`Require approvals`).
  - Checks automáticos de CI en verde (`./gradlew check`).

## Docker

```
docker build -t snippet-service .
docker run -p 8080:8080 snippet-service
```

## Levantar el sistema entero

Los tres repos tienen que estar clonados uno al lado del otro (`snippet-service`, `printscript-service`
y `permission-service`).

```
cp .env.example .env
docker compose up --build
```

En `.env` hay que completar `GITHUB_ACTOR` y `GITHUB_TOKEN` (scope `read:packages`).
