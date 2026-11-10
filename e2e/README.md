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

Copy `.env.example.tests` to `.env` (the real file must be named exactly `.env`) and fill in the passwords. `.env` is ignored by Git. Values set in the shell win over the file.

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

- The `setup` project signs in once as ADMIN and once as VIEWER through the login page and stores the sessions in `.auth/`. The `chromium` project starts from these sessions. T1, T2, T3 and T9 run without a session, T3 signs in with wrong credentials.
- Sign out calls `POST /api/auth/logout`, which raises the `tokenVersion` of the user and revokes all of the user's tokens, also the one stored in `.auth/admin.json`. That is why the tests that sign out (T4, T6, T10) are in `tests/signout.spec.ts`, which is a separate project `signout`. It depends on `chromium`, so it runs after all other tests, and every test in it signs in on its own. Nothing may sign out the ADMIN in the `chromium` project.
- Every test that needs a device creates it with a unique name starting with `e2e-` and removes it afterwards, also when the test fails. The setup project also removes leftovers of earlier crashed runs. Only devices with the `e2e-` prefix that are not simulated are ever deleted.
- Tests run in one worker, one after another, so the Dashboard checks see a stable list of devices.

## Scenarios

| File | Scenarios |
| --- | --- |
| `tests/login.spec.ts` | T1, T2, T3, T5, T9 |
| `tests/roles.spec.ts` | T7, T8 |
| `tests/devices.spec.ts` | T11 to T16 |
| `tests/dashboard.spec.ts` | T17, T18 |
| `tests/signout.spec.ts` | T4, T6, T10 (project `signout`, runs last) |

## Notes

- The backend limits sign-in to `LOGIN_RATE_LIMIT_PER_MINUTE` attempts per IP address per minute (10 by default). One run makes 9 sign-in requests: 2 in `setup`, 2 in T3, 2 in T4, 1 in T6 and 2 in T10. A second run within a minute, or one manual sign-in during a run, ends with 429 ("Login failed, try again later") and T3, T4 or T10 fail for no visible reason. For local testing set a higher limit in `devops/.env`, for example `LOGIN_RATE_LIMIT_PER_MINUTE=100`, and restart the backend (`docker compose -f docker-compose.yml -f docker-compose.local-db.yml up -d`).
- If a test in the `chromium` project fails, the `signout` project is skipped, because it depends on it. Run it alone with `npx playwright test --project=signout --no-deps`.
- T18 proves that changes arrive through the WebSocket. The Dashboard also reloads the device list every 10 seconds, so the test waits until the Dashboard has subscribed to the WebSocket topics and then blocks `GET /api/devices` in the viewer session. Only a WebSocket push can then update the list. It waits up to 15 seconds for each change.
- There are no `data-testid` attributes. The tests use labels, roles and texts. A few spots use a CSS class (`.device-row`, `.form-error`, `.login-error`, `.detail-name`, `.detail-sub`), so renaming those classes in the frontend requires updating `tests/support/pages.ts`.
- T12 does not submit a 256-character name. Angular signal forms put a native `maxlength` of 255 on the Name input, so the browser cuts the text at 255 characters and the message "Name can have at most 255 characters" cannot appear through the form. The test checks that the input keeps only 255 characters.
- T17 creates a device with the type `Laptop` to fill the Computers category, because the form offers only Server, Database, Storage and Router.