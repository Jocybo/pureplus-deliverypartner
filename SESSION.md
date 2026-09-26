# Delivery Partner App — SESSION LOG

> One entry per working session on the DP app (newest first). Keeps continuity in Claude's context
> across sessions. Full state lives in `STATUS.md`; architecture in `CLAUDE.md`.

## 2026-09-21 (f) — Restructure: bottom nav is always home; check-in is the Run tab's state
Superseded (e)'s stop-gap: check-in is **no longer a gate to the whole app**. The bottom-nav shell is now
the home after login, always visible, so a driver can explore Earnings/Me/Stops without an active shift.
- **New `RunTabScreen` host** (owns `CheckInViewModel`): renders **`CheckInScreen` while off shift**
  (check-in flow / "shift completed" state, bottom nav shown) or **`RouteHomeScreen` once a shift is open**;
  a successful check-in flips the tab automatically and fires `onShiftOpen` → `LocationService.start`.
  Added `statusResolved` to `CheckInState` so the tab shows a spinner (not a check-in flash) until the
  status call returns.
- **Nav flow changed:** start = `run` (was `check_in`); the standalone `check_in` destination is gone;
  login and both check-out flows now route to `run` (post-checkout it shows the "shift completed" state).
  Removed (e)'s `onExplore` link — the bottom nav replaces it. Tab anchor is now `run` (always in stack),
  so tab switching is clean.
- **Logout icon button** (`LogoutIconButton`, icon + label pill) top-right on the dashboard (RouteHome)
  and check-in dark headers; Me tab keeps its logout button too. (Also carries the (e) header `weight(1f)`
  fix so it can't be pushed off-screen.)
- ⚠️ Stops tab while off shift still hits the check-in-gated `/me/stops` → shows a friendly error, not an
  empty state (minor; can add a "check in to see stops" state later).
- Build: `./gradlew assembleDebug` → **BUILD SUCCESSFUL**. Not yet run on a device.

## 2026-09-21 (e) — Fix: Check-in dead-end (can't log out or explore)
Reported blocker: on the Check-in screen (esp. the "shift completed" lock) the driver couldn't log out
or reach the rest of the app.
- **Log out invisible — root cause found:** `DarkHeader`'s title `Column` had no `weight`, so a long
  title ("Good afternoon, <name>") consumed the whole row and **pushed the trailing "Log out" link
  off-screen**. Fix: `Modifier.weight(1f)` on the title column → trailing action always pinned/visible.
  (Affects every DarkHeader, not just check-in.)
- **Can't explore:** self-service (Earnings/Profile/Attendance/History) sat behind the check-in gate but
  don't need an active shift. Added an `onExplore` callback + an always-visible "Explore app — earnings,
  attendance & profile" link on Check-in (shown in both the normal and shift-completed states) →
  `navController.navigate(ME)` (pushed on top of check-in; system back returns; no LocationService start).
  Check-in actions are still hidden when `shiftCompleted` (lock preserved). Build green.

## 2026-09-21 (d) — DP-02 geofence map (Google Maps) + new backend endpoint
Added a live geofence map to the check-in screen so the driver sees where they are relative to the store.
- **Backend (new endpoint):** `GET /api/delivery/shifts/geofence` → `{ storeId, latitude, longitude,
  radiusMeters, hasLocation }` (store from JWT; reuses `GetStoreCentreAsync` + `GeoFence:CheckInRadiusMeters`,
  default 300 m). Store coords already existed in `store_location` but were never exposed to the driver app.
  Added `GeofenceDto`, `ICheckInService.GetGeofenceAsync`, `CheckInService` impl, controller action.
  `dotnet build` → 0 errors. **Pending merge/deploy** like the rest of the branch.
- **App (Google Maps):** added `maps-compose` 6.1.2 + `play-services-maps` 19.0.0; new `GeofenceMap.kt`
  draws the fence **Circle green when inside / red when outside** (amber while locating), an azure store
  marker + a green/red "You" marker, and auto-frames both. `CheckInViewModel` fetches the geofence and
  computes inside/outside with a client-side haversine (mirrors backend); Check-in screen shows the map +
  a "You're inside/outside the N m fence · X m from centre" line. `Geofence` DTO + `SHIFT_GEOFENCE` path +
  `ApiService.geofence()` + `DriverRepository.geofence()`.
- **⚠️ Needs a Maps SDK for Android key.** Injected via the `MAPS_API_KEY` Gradle property →
  manifest `com.google.android.geo.API_KEY` placeholder. Builds & runs with **blank tiles** until a real
  (billing-enabled) key is supplied; circle/marker logic works regardless. Pass `-PMAPS_API_KEY=…` or add
  it to `gradle.properties` (do NOT commit a real key).
- **💳 Billing note:** the key currently in use is on a **developer/personal Google Cloud billing
  account** (for testing). **Before go-live it must be migrated to the OWNER's Google Cloud billing
  account** (owner-owned project + key, app-restricted to the package + release SHA-1). Action item for
  the owner.
- Build: `./gradlew assembleDebug` → **BUILD SUCCESSFUL**. Not yet run on a device.

## 2026-09-21 (c) — Decision: no same-day re-check-in + "shift completed" state
Follow-up to (b): should a driver re-check-in after a mistaken check-out? **Investigated the backend
first.** `CheckInService.CheckInAsync` enforces **one check-in per driver per store per shift** (throws
if a `driver_check_in` row already exists for the day); check-out only stamps `checked_out_at`, the row
stays. That row *is* the attendance record (DP-19 + WA/SA attendance + payroll reports) and every
check-in/out is audited. So allowing re-check-in would need a backend change that touches a documented
invariant + attendance/audit data — it can't be done app-side. **Owner decision: leave the invariant
as-is, do NOT allow same-day re-check-in.** (Options considered: cancel/undo the mistaken row · reopen
the row · allow multiple rows/day — all declined.)
- **Client polish (no backend change):** since a checked-out driver now stays on Check-in, the check-in
  button would otherwise hit the backend's "already checked in today" rejection. Added a `shiftCompleted`
  state (`checkedIn && checkedOutAt != null`) → Check-in screen shows a green "You've completed your shift
  for today… (one shift per day)" note and **hides the location card + check-in button**; only the header
  "Log out" remains. Build green.

## 2026-09-21 (b) — Fix: checked-out driver bounced back into the run
Reported: after "Checked in by mistake? End shift", the Run home still showed load-out/start-run/stop-list,
and re-triggering end-shift said "already checked out". **Same root cause:** `checkInStatus().checkedIn`
stays true for the whole day once a check-in row exists (the DTO carries a separate `checkedOutAt`), but
`CheckInViewModel` treated *any* `checkedIn=true` as "on shift" and auto-advanced to the run. So a
checked-out driver landed on Check-in, got bounced to Run, and a second end-shift hit the backend's
"already checked out". **Fix:** only auto-advance when the shift is genuinely **open** —
`checkedIn && checkIn?.checkedOutAt == null`. A checked-out driver now stays on the Check-in screen; the
double-checkout path is no longer reachable. Build green.

## 2026-09-21 — GPS enforcement, auto-logout on 401, human-readable API errors
Three device-testing bugs on the check-in screen + app-wide error UX, fixed as reported.
- **Enforce GPS with consent (DP-02):** the screen no longer falls back to "GPS unavailable → override".
  It now *gates* check-in on location: requests the runtime permission (distinguishing **denied** vs
  **permanently blocked** via `shouldShowRequestPermissionRationale` → routes to app settings), and when
  permission is held but the device location switch is off it fires the **in-app system "turn on
  location" dialog** (Play Services `SettingsClient` → `ResolvableApiException` → `StartIntentSenderForResult`).
  New `LocationGate` state drives an adaptive primary button (*Allow location access* / *Open settings* /
  *Turn on GPS* / *Check in*). Manual override link now appears **only after a genuine server geo-fence
  rejection**, not as an escape from turning GPS on. New: `LocationProvider.isLocationEnabled()` +
  `resolvableLocationSettings()`, `openAppSettings()` in `DeviceActions`.
- **Auto-logout on session expiry (401):** decided *automatic* over a manual button (a dead token can't
  be recovered) — `apiCall` now fires a new app-wide `SessionManager.notifyExpired()` on any 401, which
  `AppNavGraph` collects to clear the session and bounce to Login. The manual "Log out" link stays too.
  Fixes the "session expired, no way back" dead-end.
- **Human-readable API errors everywhere:** `apiCall` was dropping the server's message (on non-2xx it
  sits in `errorBody()`, which was never read) and printing `Request failed (HTTP $code)`. Now it parses
  the enveloped `errorBody` message and maps every status code (400/403/404/408/409/422/429/5xx) to a
  plain sentence — the raw code is never shown. Timeouts get their own message.
- Build: `./gradlew assembleDebug` → **BUILD SUCCESSFUL** (57s). Not yet run on a device.

## 2026-09-20 (e) — Mistaken check-in → direct check-out + black system bars
- **Edge case (mistaken check-in):** a driver who checks in but never runs was stuck — the only
  check-out went through the reconciliation `close` (cans+cash gate). Added a **direct mode** to
  `CheckOutViewModel`/`CheckOutScreen`: when reached via the new `end_shift` route (sessionId null) it
  does an **attendance-only** check-out (`POST /api/delivery/shifts/check-out`, independent of
  reconciliation) — but **only if `stopsCompletedToday == 0`**; if they've delivered anything it blocks
  and routes them to close-out. Entry: a "Checked in by mistake? End shift" link on the Run/home screen.
- **System bars:** parent `Scaffold` in `MainActivity` insets every screen once via
  `WindowInsets.safeDrawing` (fixes edge-to-edge collision on targetSdk 35); bars are **black**
  (`containerColor = Color.Black`) with **light icons** (`WindowInsetsControllerCompat`
  `isAppearanceLight*Bars = false`). Removed the now-redundant per-screen system-bar padding on Login.

## 2026-09-20 (d) — Close-out discoverability
Check-out (DP-22) was only reachable from the bottom of the Stops list. Added a **"Close out & check out"**
button to the Run/home screen (DP-03) too → same flow: Can return (DP-15) → Cash (DP-16) → Check out
(DP-22, gated on cans+cash balancing). Both entry points navigate to `can_return`.

## 2026-09-20 (c) — DP-01 corrected to OTP (was password)
**Why:** onboarding a delivery partner does **not** set a password — `AdminEmployeeServiceNew` only
creates a credential when username+password are both supplied (optional). The backend has a dedicated
driver OTP login (`DeliveryAuthService`, `IDeliveryAuthService` = "DP-01: OTP to the mobile"), gated to
an onboarded, active driver by their registered number. The design mock's password field was wrong.
**Change:** DP-01 rebuilt as a two-step OTP screen (phone → `send-otp` → 4-digit `verify-otp` → staff
JWT), with change-number, 30s resend countdown, and a demo-OTP hint (backend echoes it until MSG91 is
wired). Added `sendOtp`/`verifyOtp` to `ApiService`/`AuthRepository` + DTOs; password `login()` kept as
an unused fallback. `verify-otp` returns a bare `{success,token,employeeId,name,roleId,storeId}`.
Build + 9/9 tests green.

## 2026-09-20 (b) — Live location, unit tests, QA audit
**Outcome:** Finished DP-07 live-position emission, added executed unit tests, and audited all screens.
`assembleDebug` green; `testDebugUnitTest` **9/9 pass**.

- **DP-07 live location (was plumbing-only):** confirmed `ping()` was never called. Added
  `data/location/LocationService.kt` — a **foreground service** (FusedLocation, balanced power, ~20s)
  that posts to `POST /api/delivery/me/location` while the shift is open. Manifest: `FOREGROUND_SERVICE`
  + `FOREGROUND_SERVICE_LOCATION` perms + `<service foregroundServiceType="location">`. Started in
  `AppNavGraph` on check-in, stopped on check-out and logout. Business doc **does** require this
  (DP-07 / BRD 6.5 customer tracking).
- **Testability refactor:** moved `isReusable` → `util/isReusableProduct` and the pure `Stop`
  extensions (`isPostpaid`/`isFailed`/`addressText`) → `ui/stops/StopExt.kt` (Compose-free, same package).
- **Unit tests:** `app/src/test/.../DeliveryLogicTest.kt` (junit) — money() incl. Indian grouping +
  signed receipt amounts + paise rounding, `isReusableProduct`, `DoorLine.shortfall`/`lineTotal`,
  `CanRow.variance` (balanced + shrinkage), `Stop.isPostpaid`/`isFailed`. 9/9 green.
- **Audit:** full DP-01→DP-22 table added to `STATUS.md` (PASS / PARTIAL / GAP with mock-data traces).
  PARTIAL: DP-20 (no driver history endpoint), DP-21 (no docs/vehicle endpoint). GAP: DP-14 (no live
  entry / bulk detail). On-device runtime not yet verified (needs a seeded driver login).

## 2026-09-20 (a) — Full rebuild against the design + `/api/delivery`
**Outcome:** Every screen from `delivery-partner-mobile-app-design/` (DP-01→DP-22, DP-13 NA) built from
scratch and wired to the clean `/api/delivery/*` backend surface. Old scaffold screens replaced.
`./gradlew assembleDebug` → **BUILD SUCCESSFUL** (app-debug.apk ~20 MB). Not yet run on a device.

**What changed:**
- **Design system** (`ui/theme/` + `ui/components/`): light-first tokens (`PpColor`, `PpType`, `Theme`),
  and reusable components — `PrimaryButton/OutlineButton/TextLink`, `PpCard`, `NotePanel`/`Tone`,
  `StatusChip`/`Tag`/`FloorLiftTag`/`FilterChip`, `CountStepper`, `Receipt`, `BottomNavBar` (Run/Stops/
  Earnings/Me), `DarkHeader`/`BackHeader`, `LinearProgress`/`StatTile`, state views.
- **Data layer rewritten for `/api/delivery`**: `ApiConstants`, `ApiModels` (DTOs verified against the
  backend controllers/DTOs), `ApiService`, repositories (`Auth`, `Driver`, `Delivery`, `Reconciliation`,
  `Outbox`) returning a small `ApiResult` type. Kept the Room outbox + `SyncWorker`; adapted ops to the
  new DTOs (deliver is server-idempotent, confirm-payment is bodyless, can-lines/cash/close carry bodies).
- **DI**: hand-rolled `di/Deps` service locator; ViewModels use `viewModelFactory { initializer { … } }`.
- **Screens/VMs**: all DP-01→DP-22 (see STATUS.md table). Delivery DP-08→11 is one screen with
  Execution→Payment→Done steps + offline (DP-10) variant. Close-out DP-15→16→22 chained by session id.
- **Utils**: `money()` (Indian grouping), date formatters, `LocationProvider` (FusedLocation),
  `openMaps`/`dialPhone`. Added `lifecycle-runtime-compose` dependency (for `collectAsStateWithLifecycle`).
- Periodic outbox sync scheduled in `PurePlusDeliveryApp.onCreate`.

**Gotchas fixed during the build:** KDoc `**/` and `/*` sequences closed comments early in `ApiConstants`;
a bad `import …layout.weight` (scope member, not importable) across 18 files; two trailing-lambda
param-order mismatches (`FilterChip`, `TextLink`); a PowerShell re-encoding pass mangled non-ASCII
punctuation (reversed the mojibake).

**Flagged gaps → backend** (see STATUS.md "Missing / gap APIs"): driver profile/docs, isolated delivery
history, pre-deliver quote, bulk detail + bulkRequestId, `isReusable` on stop items, cash-expected
sourcing, split payment / UPI QR, store/route names.

**Next:** run on device; foreground location service (DP-07); FCM; close the gap APIs; branded fonts.
