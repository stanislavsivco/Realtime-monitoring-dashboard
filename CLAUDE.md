# Real-time Monitoring Dashboard

Real-time monitoring of network devices. School project for the Deutsche Telekom / AT&T internship presentation. Goal: production-grade quality with edge cases handled.

## Structure
- backend/  Spring Boot 4.1.1, Java 21, Maven, PostgreSQL, Lombok, springdoc, jjwt 0.12.6
- frontend/ Angular
- agent/    Python agent (psutil, requests) with automatic device registration
- devops/   docker-compose, Azure Pipelines

## Backend architecture
- Package com.realtime_monitoring_dashboard.backend: controller, service, repository, model, dto, exception, security, config
- Layers: controller -> service -> repository. Controllers never return entities, always DTOs.
- Validation with @Valid / @Validated. Errors are handled by GlobalExceptionHandler (ErrorResponse).
- Every endpoint has @Operation with an explicit operationId. docs/openapi.json is generated from the API and checked by OpenApiSpecTest.
- Device status: ONLINE, WARNING, CRITICAL, computed from the per-device Threshold (DeviceStatusCalculator). After a threshold change it is recalculated from the latest metric (ThresholdUpdatedEvent, MetricService.onThresholdUpdated). OFFLINE is set by checkOfflineDevices when no metric arrives for 30 seconds.
- WebSocket (STOMP): endpoint /ws, topics /topic/metrics, /topic/devices, /topic/devices/delete, /topic/alerts. Only FRONTEND_ORIGIN may connect, CONNECT needs the JWT (Authorization: Bearer), clients may only SUBSCRIBE to /topic/**, SEND is rejected (StompAuthChannelInterceptor).
- Demo data (DataSeeder, MetricSimulator) runs only when SIMULATION_ENABLED=true, and the generator only for devices with simulated = true.
- Metrics older than METRIC_RETENTION_DAYS are deleted every hour (MetricRetentionService).
- POST /api/auth/login and /api/auth/register are rate limited per IP (AuthRateLimitFilter), over the limit they return 429 with Retry-After.

## Security rules (mandatory)
- No passwords, tokens or JWT secrets in Git. Everything comes from environment variables (.env is gitignored, template in .env.example).
- DB_PASSWORD and JWT_SECRET have no default value.
- Public registration (/api/auth/register) always creates the VIEWER role. Only ADMIN can change roles.
- The initial admin is created at startup from ADMIN_USERNAME and ADMIN_PASSWORD. There is no default password.
- VIEWER is read-only. Changes (devices, thresholds, users) are ADMIN-only via @PreAuthorize.
- /api/devices/register and /api/devices/ingest are public for now. An agent API key is planned.

## Code style and workflow
- Do not write comments in code.
- When changing code, provide complete files, not snippets.
- Task titles, branch names, commit messages and PR descriptions are in English. Branch format: <number>_feature/<name>.
- Think proactively about edge cases and security holes, not only the literal request.

## Commands
- Build: ./mvnw compile (run in backend/, without clean)
- Tests: ./mvnw test
- Run: ./mvnw spring-boot:run
- Update docs/openapi.json after an API change: ./mvnw test -Dtest=OpenApiSpecTest -Dopenapi.update=true

## Roadmap
@docs/ROADMAP.md

When you finish a task or the plan changes, update docs/ROADMAP.md. Mention what you changed.