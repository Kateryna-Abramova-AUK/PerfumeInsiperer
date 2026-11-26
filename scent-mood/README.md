# Scent Mood

Find Your Scent recommends fragrances by mood and explains why they fit. The standalone app uses a Java 21, Spring Boot, and Maven backend and a React, JSX, and Vite frontend.

## Run locally

With Docker Desktop running, open this project directory and run:

```sh
docker compose up --build
```

Open [Find Your Scent](http://localhost:15173/finder). Choose **Mysterious**, **Cherry**, and **Evening**, then select **Find fragrances**. **Lost Cherry** comes first, with Black Cherry and its curated Mysterious tag listed in the explanation.

Compose starts the frontend and backend. The API is at <http://localhost:18080/api/health>. The finder and [catalog](http://localhost:15173/catalog) share the nine fragrances in `backend/src/main/resources/fragrances.json`. Both ports bind to loopback. Copy `.env.example` to `.env` to override them. Stop the stack with `docker compose down`.

## Development and tests

- `frontend/` contains the React and Vite site. Run `npm ci` and `npm run dev` there. Vite proxies `/api` to port 8080 for a locally started Spring Boot process. Set `API_PROXY_TARGET=http://localhost:18080` to use the Compose backend.
- `backend/` contains the Java 21 and Spring Boot API. Run `mvn test` there for the ranking and filtering tests. The backend Docker build also runs these tests.
- `e2e/` contains browser tests that use the real API. From that directory, run `npm ci`, `npx playwright install chromium`, then `npm test` with Compose running. Set `SCENT_MOOD_URL` if you changed the frontend port.
- The illustrated landing page uses local WebP art and bundled typefaces. The font licenses are in `frontend/public/assets/`.

The shortlist uses an HttpOnly cookie to identify the browser. The two-service quick start stores saves in a local Compose volume. Start the PostgreSQL demo from the same directory with the overlay below. Flyway applies the shortlist migrations, and PostgreSQL stores saves on its own volume.

```sh
docker compose -f docker-compose.yml -f docker-compose.postgres.yml up --build --wait
```

Catalog search matches a partial fragrance name or brand without regard to case. Select exact notes to require every selected note, and combine them with the season, family, and time-of-day filters. Year-round fragrances match any season, and fragrances tagged for both times match either time.

## API

| Method and path | Parameters or behavior |
| --- | --- |
| `GET /api/health` | Backend readiness |
| `GET /api/options` | Moods, note choices, families, seasons, and times |
| `POST /api/recommendations` | JSON body with one or two mood IDs and optional preferences |
| `GET /api/catalog` | Optional `q`, `notes`, `family`, `season`, and `time` |
| `GET /api/shortlist` | Loads saved fragrances and sets the visitor cookie on the first request |
| `PUT /api/shortlist/{id}` | Saves the fragrance for the current visitor |
| `DELETE /api/shortlist/{id}` | Removes the fragrance for the current visitor |

Send `Content-Type: application/json` with the recommendation request:

```json
{"moods":["mysterious"],"preferredNote":"Cherry","timeOfDay":"Evening"}
```

Use `family` for the optional fragrance family. The catalog endpoint accepts comma-separated `notes`. Recommendations return the matching moods and the actual notes, accords, or tags that earned points. The Cherry preference matches Black Cherry or Wild Cherry and reports the catalog's exact note.

## Catalog snapshots

Both shortlist modes use the bundled JSON for the finder and catalog. The separate `etl/fragrances.json` collection feeds the catalog publishing workflow. Keep stable IDs attached to the same fragrances when editing either file.

```sh
python etl/catalog.py --output catalog.json --sql-output catalog-import.sql
```

`python etl/catalog.py --check-seed` checks the original collection against the first-release migration.

## Minikube

From the project root, start a demo cluster and load the two application images.

```sh
minikube start -p scent-mood-demo --driver=docker
docker build -f backend/Dockerfile -t scent-mood-backend:local .
docker build -f frontend/Dockerfile -t scent-mood-frontend:local .
minikube -p scent-mood-demo image load scent-mood-backend:local
minikube -p scent-mood-demo image load scent-mood-frontend:local
```

Copy `k8s/secret.example.yaml` to `k8s/secret.local.yaml` and replace the password. Deploy into a dedicated namespace.

```sh
kubectl --context scent-mood-demo create namespace scent-mood
kubectl --context scent-mood-demo -n scent-mood apply -f k8s/secret.local.yaml
kubectl --context scent-mood-demo -n scent-mood apply -k k8s
kubectl --context scent-mood-demo -n scent-mood rollout status deployment/scent-mood-frontend
python k8s/demo_shortlist.py --context scent-mood-demo
```

The script saves Lost Cherry, replaces the Spring Boot pod, and checks the same visitor's shortlist through the new pod. The PostgreSQL StatefulSet keeps its data on a persistent volume claim. To open the demo in a browser, forward the frontend service and visit <http://localhost:15174/catalog>.

```sh
kubectl --context scent-mood-demo -n scent-mood port-forward service/scent-mood-frontend 15174:80
```

## Release workflows and S3

CI validates the catalog, builds the application, runs the browser suite against PostgreSQL, and checks that a save survives backend container replacement. The image workflow publishes container images to GitHub Container Registry on a version tag or manual run.

`infra/terraform` defines a private, versioned S3 bucket with encryption and a TLS-only bucket policy. Supply `aws_region` and a globally unique `catalog_bucket_name` when planning. After provisioning, attach the output publisher policy to a GitHub OIDC role whose trust is limited to this repository's `catalog-publishing` environment. In that GitHub environment set `AWS_REGION`, `CATALOG_BUCKET`, and `CATALOG_PUBLISH_ROLE_ARN`.

The manual catalog workflow validates and writes `catalog/latest.json`. S3 retains earlier object versions. To release a snapshot to a database, retrieve the chosen object version with an authorized reader and use `etl/catalog.py --input` to generate its SQL import.

