# Roadmap

## Done
- Device, metric, alert and threshold APIs with pagination and filtering
- Python agent with auto-registration, ingest endpoint, OFFLINE detection
- Per-device thresholds and DeviceStatusCalculator
- WebSocket live updates
- Swagger / OpenAPI
- Dashboard in Angular showing devices and the agent
- Azure Pipelines mirror to GitHub for Render
- JWT authentication (branch 45_46_feature/jwt-auth):
  - User, Role, JwtService, JwtAuthenticationFilter, SecurityConfig, AuthService, AuthController, AdminSeeder
  - Admin-only endpoints to list users and change a user's role (UserController, UserService)
  - @PreAuthorize on all controllers (ADMIN-only for mutations)
  - Password validation in RegisterRequestDTO: min 8, max 72 characters
  - application.yaml uses only environment variables, no default secrets
  - .env.example committed with all required variable names
  - AuthControllerTest and UserControllerTest added
  - MetricServiceTest updated for findBySimulatedTrue()
  - Agent reports networkInMbps and networkOutMbps via psutil.net_io_counters()
- User hardening (branch 53_SecurityHardening):
  - User entity uses @Getter/@Setter instead of @Data, password excluded from toString (also in login/register DTOs)
  - Usernames normalized (trim + lowercase) via UsernameNormalizer, case-insensitive lookups and duplicate check
  - Username allowed characters: letters, digits, dot, underscore, hyphen
  - Concurrent duplicate registration returns 409 instead of 500
  - AdminSeeder skips creation if ADMIN_USERNAME is blank or already taken
  - AuthServiceTest added
  - JWT token excluded from AuthResponseDTO toString
  - The last ADMIN cannot be demoted (LastAdminException, 409), admin rows locked with PESSIMISTIC_WRITE during the check
  - UserServiceTest added
- Agent API key and CORS hardening (branch 53_SecurityHardening):
  - AgentApiKeyFilter checks X-Agent-Key on /api/devices/register and /api/devices/ingest with constant-time comparison
  - CORS restricted to FRONTEND_ORIGIN instead of wildcard

## Planned
- Repository hygiene: .gitignore, .gitattributes, remove tracked junk
- Pipeline: build and test stages, secret scanning, branch policies on main
- STOMP authentication for /ws
- Frontend: login, role-aware UI, threshold management, analytics
- Predictive trend engine
- Simulation mode: smooth random-walk metrics with scripted incidents, SIMULATION_ENABLED switch, retention cleanup, "Simulated" badge in the UI
- Deployment (Render, later Oracle Cloud or Azure) with environment variables
- Database migrations (Flyway) instead of ddl-auto update, including a unique index on lower(username)

## Ideas
- (add new ideas here)
