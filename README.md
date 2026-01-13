# DevOps_PROJECT_FMI — Automated CI / SAST / CD to Kubernetes (Spring Boot)

This repository demonstrates a complete automated software delivery process for a Spring Boot application, implemented **as code** via GitHub Actions, Docker, and Kubernetes.

The solution provides a working “horizontal” delivery pipeline end-to-end (from repository → CI → image → CD → Kubernetes), plus a deeper “vertical” focus on **Security (SAST)** using **SonarCloud** and Quality Gate enforcement.

---

## Course requirements coverage

The project covers **at least 7 topics** from the course (in practice: more than 7):

- **Phases of SDLC / Workflow**: Issue → feature branch → PR → checks → merge → deploy
- **Collaborate**: PR-based collaboration and required checks
- **Source control**: GitHub repository
- **Branching strategies**: feature branches merged into `main`
- **Building pipelines**: GitHub Actions workflows (`ci.yml`, `sast.yml`, `cd.yml`)
- **Continuous Integration (CI)**: build + unit tests on PR and push
- **Security (SAST)**: SonarCloud analysis + Quality Gate checks
- **Docker**: multi-stage Docker build, non-root runtime user, push to GHCR
- **Kubernetes**: Deployment + Service, rolling update, liveness/readiness probes
- **Infrastructure as Code**: workflows + Dockerfile + Kubernetes manifests are versioned in Git

Mandatory components covered:
- **Continuous Integration** (CI workflow)
- **Deploy to Kubernetes** (CD workflow)

---

## Application overview

A simple Spring Boot REST API that is well-suited for pipeline demonstration (tests, containerization, k8s deployment).

### Endpoints

#### 1) Hello

- `GET /api/hello` → returns plain text:

```text
hello
```

#### 2) Version

- `GET /api/version` → returns the application version from config:
  - property: `app.version`
  - default: `dev` (if not set)

Example response:

```text
test-123
```

#### 3) Todos (in-memory)

- Base path: `/api/todos`

- `POST /api/todos`  
  Request JSON:

```json
{ "title": "Buy milk" }
```

Response JSON:

```json
{ "id": 1, "title": "Buy milk", "done": false }
```

- `GET /api/todos` → list all todos (sorted by id)

- `DELETE /api/todos/{id}`
  - returns `204 No Content` when deleted
  - returns `404 Not Found` when missing

Implementation detail: Todos are stored in-memory using a thread-safe ConcurrentHashMap and an AtomicLong id generator (no database).

---

## Tests

The project contains controller-level tests using Spring MVC test infrastructure:

- HelloControllerTest (@WebMvcTest)  
  Verifies GET /api/hello returns hello.

- VersionControllerTest (@WebMvcTest + @TestPropertySource)  
  Verifies GET /api/version returns configured app.version (example: test-123).

- TodoControllerTest (@WebMvcTest(TodoController.class))  
  Uses @MockitoBean TodoService and verifies:
  - create → 201 + JSON body
  - list → 200 + JSON array
  - delete existing → 204
  - delete missing → 404

---

## Docker

### Dockerfile highlights

The Dockerfile is multi-stage:

**Build stage**
- Base image: Maven + Temurin 17
- Warms dependency cache via dependency:go-offline
- Builds the jar with tests skipped:

```bash
mvn clean package -DskipTests
```

**Runtime stage**
- Base image: eclipse-temurin:17-jre
- Runs as non-root user (appuser, uid 10001)
- Copies the jar into /app/app.jar
- Exposes 8080
- Starts with: java -jar /app/app.jar

### Build locally

```bash
docker build -t devops_project_fmi:local .
```

### Run locally

```bash
docker run --rm -p 8080:8080 devops_project_fmi:local
```

---

## Kubernetes deployment (k8s/)

### Deployment (k8s/deployment.yml)

Key settings:

- replicas: 2 (two pods)
- revisionHistoryLimit: 2 (keeps last 2 ReplicaSets for history/rollback visibility)
- Rolling update strategy:
  - maxSurge: 1
  - maxUnavailable: 0 (zero downtime rollout)

Container:

- image: ghcr.io/zhozemir/devops_project_fmi:latest (base image value; CD overrides to SHA tag)
- imagePullPolicy: Always
- containerPort: 8080

Health probes (Actuator):

- readiness: GET /actuator/health/readiness
- liveness: GET /actuator/health/liveness

### Service (k8s/service.yml)

- Type: ClusterIP
- Exposes port 80 and forwards to container port 8080

---

## CI / SAST / CD pipelines

All automation is implemented as GitHub Actions workflows:

- .github/workflows/ci.yml — CI
- .github/workflows/sast.yml — SAST (SonarCloud)
- .github/workflows/cd.yml — CD (Kubernetes deploy)

### What runs on Pull Request (PR)

When you open or update a PR targeting main:

**CI workflow:**
- checks out code
- sets up Java 17
- runs:

```bash
./mvnw -B clean test
```

- builds Docker image locally (no push) for quick validation:

```bash
docker build -t devops_project_fmi:ci .
```

**SAST workflow:**
- checks out code
- sets up Java 17
- runs tests (also compiles code) to generate coverage:

```bash
./mvnw -B clean test
```

- runs SonarCloud scan using:
  - compiled classes: target/classes
  - coverage report path: target/site/jacoco/jacoco.xml

SonarCloud posts results and Quality Gate status back to the PR as a check/comment.

Important: CD does not run on PRs.

### What runs on merge / push to main

When changes are merged into main:

**CI workflow runs again on push to main:**
- runs tests (clean test)
- builds and pushes Docker image to GHCR with two tags:
  - :latest
  - :sha-<GITHUB_SHA> (immutable traceable tag)

**SAST workflow runs again on push to main:**
- runs tests + coverage
- runs SonarCloud scan and Quality Gate

**CD workflow runs automatically after CI completes successfully via workflow_run:**
- checks out the exact commit that CI built (head_sha)
- gates deployment by verifying SAST success for the same commit
- CD polls GitHub Actions API to find the matching sast.yml run for that SHA
- deployment proceeds only if SAST finished with conclusion=success
- deploys to Kubernetes using the SHA tag:

```bash
kubectl set image ... app=...:sha-<SHA>
```

- annotates deployment with change-cause:

```bash
deploy sha-<SHA>
```

- waits for rollout to complete:

```bash
kubectl rollout status ...
```

This ensures the cluster always runs a version that has:

- passed unit tests (CI)
- passed SonarCloud Quality Gate (SAST)
- been built/pushed as an immutable image tag (CI → GHCR)
- been deployed using rolling update (CD → Kubernetes)

---

## SonarCloud (SAST) deep dive

### Why tests are executed in SAST

SonarCloud Java analysis requires:

- compiled bytecode (target/classes)
- and for coverage metrics: JaCoCo XML report (target/site/jacoco/jacoco.xml)

Therefore, the SAST workflow runs:

```bash
./mvnw -B clean test
```

This:

- compiles the project
- executes unit tests
- generates coverage output (JaCoCo report)

Then the scan is executed with:

- sonar.java.binaries=target/classes
- sonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml

### Quality Gate and PR blocking

If the Quality Gate fails (e.g., insufficient coverage on new code), the SonarCloud check fails in the PR.  
When configured as a required check, it blocks merging until fixed.

---

## Continuous Delivery environment

### Self-hosted runner

CD runs on a self-hosted Windows runner labeled:

- [self-hosted, Windows, X64]

This runner must have:

- kubectl installed
- network access to the target cluster
- permission to apply manifests and update deployments
- access to GHCR images (either public image/package or properly configured Kubernetes pull secret)

### Required GitHub secrets

- SONAR_TOKEN — SonarCloud token used in sast.yml
- KUBECONFIG — kubeconfig file content used in cd.yml

---

## How to verify deployment

After a merge to main, you can verify the running image tag (example):

```powershell
kubectl get pods -l app=devops-project-fmi -o=jsonpath="{range .items[*]}{.metadata.name}{' -> '}{.spec.containers[0].image}{'\n'}{end}"
```

Rollout history (should show deploy sha-... annotations):

```powershell
kubectl rollout history deployment/devops-project-fmi
```

---

## Notes / Limitations

No database is used; todos are in-memory only (no SQL deltas).

Kubernetes environment is self-managed (via self-hosted runner access).

The project is designed primarily to demonstrate automated delivery and DevSecOps pipeline practices.

---

## Summary

This repository delivers a complete automated flow:  
GitHub repo → PR checks (CI + SAST) → merge → build/push image → gated CD → rolling deploy to Kubernetes, implemented fully as code with documentation included.
