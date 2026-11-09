# RMD frontend end-to-end tests (Playwright)

Automated tests for the critical frontend flows: sign in, roles and the Devices page. Everything else stays manual, see the manual test plan. Scenario IDs `T1` to `T18` come from the automated test plan, the manual plan IDs (L6, V6 and so on) are in the plan document.

## Requirements

- Node.js 20.19 or newer (the same as the Angular app)
- The whole app running as for manual testing: Docker with the backend and the database, `npm start` in `frontend/`. The frontend must be on http://localhost:4200 and the backend on http://localhost:8080.
- An ADMIN account (the `ADMIN_USERNAME` and `ADMIN_PASSWORD` from `devops/.env`) and a VIEWER account (create it with the command from the manual plan).

## Setup

```bash
cd e2e
npm install
npx playwright install chromium
```

Copy `.env.example` to `.env` and fill in the passwords. `.env` is ignored by Git. Values set in the shell win over the file.

| Variable | Meaning | Default |
| --- | --- | --- |
| `RMD_BASE_URL` | frontend address | `http://localhost:4200` |
| `RMD_API_URL` | backend address, used for test data and cleanup | `http://localhost:8080` |
| `RMD_ADMIN_USERNAME` | ADMIN user name | `admin` |
| `RMD_ADMIN_PASSWORD` | ADMIN password | required |
| `RMD_VIEWER_USERNAME` | VIEWER user name | required |
| `RMD_VIEWER_PASSWORD` | VIEWER password | required |

PowerShell without a `.env` file:

```powershell
$env:RMD_ADMIN_PASSWORD = "..."
$env:RMD_VIEWER_USERNAME = "..."
$env:RMD_VIEWER_PASSWORD = "..."
npx playwright test
```

## Running

```bash
npx playwright test
npx playwright test tests/devices.spec.ts
npx playwright test -g "T13"
npx playwright show-report
```

A failed test keeps a trace in the report (open it with `show-report`). Attach the trace and the scenario ID (for example T13) to the bug.

## How it works

- The `setup` project signs in once as ADMIN and once as VIEWER through the login page and stores the sessions in `.auth/`. The other tests start from these sessions, so only T2 to T4 and T10 sign in again. T1, T2, T3, T4 and T9 run without a session.
- Every test that needs a device creates it with a unique name starting with `e2e-` and removes it afterwards, also when the test fails. The setup project also removes leftovers of earlier crashed runs. Only devices with the `e2e-` prefix that are not simulated are ever deleted.
- Tests run in one worker, one after another, so the Dashboard checks see a stable list of devices.
- Sign out in the frontend only clears the browser storage, it does not call `POST /api/auth/logout`. If that changes, the tokens stored in `.auth/` are revoked by T4, T6 and T10 and the tests that follow need their own sign in.

## Scenarios

| File | Scenarios |
| --- | --- |
| `tests/login.spec.ts` | T1 to T6, T9 |
| `tests/roles.spec.ts` | T7, T8, T10 |
| `tests/devices.spec.ts` | T11 to T16 |
| `tests/dashboard.spec.ts` | T17, T18 |