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

## Planned
- Repository hygiene: .gitignore, .gitattributes, remove tracked junk
- Pipeline: build and test stages, secret scanning, branch policies on main
- Agent API key for /api/devices/register and /api/devices/ingest
- STOMP authentication for /ws
- CORS restricted to the real frontend origin
- Frontend: login, role-aware UI, threshold management, analytics
- Predictive trend engine
- Simulation mode: smooth random-walk metrics with scripted incidents, SIMULATION_ENABLED switch, retention cleanup, "Simulated" badge in the UI
- Deployment (Render, later Oracle Cloud or Azure) with environment variables
- Database migrations (Flyway) instead of ddl-auto update

## Ideas
- (add new ideas here)
