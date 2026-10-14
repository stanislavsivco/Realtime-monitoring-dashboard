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
- JWT hardening (branch 53_SecurityHardening):
  - JWT_SECRET read as UTF-8, backend fails at startup if it is blank or shorter than 32 bytes
  - Signing key and parser created once, token parsed once per request
  - Token lifetime configurable via JWT_EXPIRATION_MS (default 24 hours)
  - Removed unused extractRole and redundant isTokenValid
  - JwtServiceTest added (foreign key, tampered, expired, unsigned tokens, config validation)
- Security config hardening (branch 53_SecurityHardening):
  - AGENT_API_KEY required at startup with minimum 32 bytes (empty key no longer accepts empty header)
  - AgentApiKeyFilter strips context path and trailing slashes before matching protected paths
  - Unauthenticated requests return the same JSON ErrorResponse as the rest of the API (RestAuthenticationEntryPoint, SecurityErrorWriter)
  - FRONTEND_ORIGIN accepts a comma-separated list, trailing slashes are removed, wildcard is rejected
  - show-sql controlled by SHOW_SQL (default false)
  - Agent exits with a clear message when AGENT_API_KEY is missing
  - AgentApiKeyFilterTest, RestAuthenticationEntryPointTest and SecurityConfigTest added
- Agent API key and CORS hardening (branch 53_SecurityHardening):
  - AgentApiKeyFilter checks X-Agent-Key on /api/devices/register and /api/devices/ingest with constant-time comparison
  - CORS restricted to FRONTEND_ORIGIN instead of wildcard

## Planned
- Repository hygiene: .gitignore, .gitattributes, remove tracked junk
- Pipeline: build and test stages, secret scanning, branch policies on main
- docker-compose: read secrets from .env instead of hardcoded postgres password, pass JWT_SECRET, ADMIN_*, AGENT_API_KEY, FRONTEND_ORIGIN, use DB_* variable names
- Swagger disabled in production via SWAGGER_ENABLED switch
- Rate limiting for /api/auth/login and /api/auth/register (brute-force protection)
- STOMP authentication for /ws
- Token revocation: shorter access token with refresh token, or tokenVersion on User checked in JwtAuthenticationFilter (logout, password change, forced sign-out)
- Frontend: login, role-aware UI, threshold management, analytics
- Predictive trend engine
- Simulation mode: smooth random-walk metrics with scripted incidents, SIMULATION_ENABLED switch, retention cleanup, "Simulated" badge in the UI
- Deployment (Render, later Oracle Cloud or Azure) with environment variables
- Database migrations (Flyway) instead of ddl-auto update, including a unique index on lower(username)

## Ideas
- (add new ideas here)
