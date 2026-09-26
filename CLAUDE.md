# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> **Project state & cross-cutting decisions live in the repo-root brief: [`../../CLAUDE.md`](../../CLAUDE.md)** (read it first — source of truth for what's done, known bugs, and locked decisions). This file is the **Delivery Partner app architecture reference**. End sessions with `/save-session`.
>
> **This app is Android-only** (no iOS). It keeps its own two tracking docs so context survives across
> Claude sessions — **read both when resuming DP work:**
> - **[`STATUS.md`](./STATUS.md)** — per-screen state (DP-01→DP-22), missing/gap APIs, what's next.
> - **[`SESSION.md`](./SESSION.md)** — one entry per working session (newest first). Add an entry at the end of each session.

## Project Overview

Native **Android-only** app for delivery drivers — **Kotlin + Jetpack Compose (Material 3)**, single-activity, offline-first. Drives one shift end to end: check in → load out → work the stop list → deliver (cans/floor/cash) → close out (can return, cash reconcile, check out). Talks to the .NET backend at `https://pureplusapp.in/` on the clean **`/api/delivery/*`** surface (login + bulk are the only `/api/admin` calls left).

**Design source:** `delivery-partner-mobile-app-design/` (Doorstep Design System). The UI is **light-first** for outdoor legibility — the only dark screen is DP-01 Login. All 21 designed screens (DP-13 is NA) are built and wired. **Builds green** (`./gradlew assembleDebug` → `app-debug.apk`) as of 2026-09-20; **not yet run on a device** — that's the next job.

## Commands

```bash
# From Mobile/DeliveryPartner/ (uses the Gradle wrapper)
./gradlew assembleDebug        # build the debug APK
./gradlew installDebug         # build + install on a connected device/emulator
./gradlew lint                 # Android lint
./gradlew testDebugUnitTest    # pure-JVM unit tests (money math, reconciliation, product heuristics) — 9 green
```

Or open `Mobile/DeliveryPartner/` in Android Studio and let it sync. **`google-services.json` is missing** (FCM) — needed before push works. compileSdk/targetSdk 35, minSdk 24, JVM target 17. `BASE_URL` is a `buildConfigField` in `app/build.gradle.kts` (currently prod for all build types).

**Google Maps key (DP-02 geofence map):** the check-in map needs a *Maps SDK for Android* key (billing-enabled Google Cloud). Supply it via the `MAPS_API_KEY` Gradle property — `-PMAPS_API_KEY=…` or a line in `gradle.properties` (**never commit a real key**). It's injected into the manifest `com.google.android.geo.API_KEY` placeholder in `app/build.gradle.kts`. The app builds & runs without it, but map tiles are **blank** until a real key is set; the geofence circle/marker logic is unaffected. **Billing:** the key in use now is on a **developer/personal** Google Cloud billing account for testing — **migrate to the owner's Google Cloud billing account + owner-owned key before go-live** (restrict it to the package + release SHA-1).

## Architecture

### Stack

Compose UI + Navigation-Compose, **Retrofit + OkHttp + Gson**, **Room** (offline outbox), **DataStore** (token/prefs), **WorkManager** (background sync), Coroutines, Play Services Location (geo-fenced check-in). KSP for Room codegen. Icons via material-icons-extended. `java.time.*` on minSdk 24 via core-library desugaring.

### Package layout (`app/src/main/kotlin/com/pureplus/delivery/`)

- `data/remote/` — `ApiService` (Retrofit), `ApiModels` (DTOs verified against backend controllers), `ApiConstants` (all paths), `RetrofitClient`, `AuthInterceptor`.
- `data/local/` — Room `AppDatabase` + `OutboxDao`/`OutboxEntry`, `TokenStore` (DataStore).
- `data/repository/` — `AuthRepository`, `DriverRepository`, `DeliveryRepository`, `ReconciliationRepository`, `OutboxRepository`, plus `ApiResult`/`apiCall` (tiny Result type + enveloped-call unwrapper).
- `data/sync/` — `SyncWorker` (the outbox flusher).
- `data/location/` — `LocationService`, the **DP-07** foreground service that pings `POST /api/delivery/me/location` (~20s) while a shift is open. Started by `AppNavGraph` on check-in, stopped on check-out/logout. Manifest needs `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_LOCATION`.
- `di/Deps.kt` — hand-rolled service locator (no Hilt). Lazy singleton repositories; ViewModels read from here.
- `ui/theme/` — `PpColor`, `PpType`/`PpFont`, `Theme` (light-first Doorstep Design System).
- `ui/components/` — the shared kit: buttons, `PpCard`, `NotePanel`/`Tone`, chips (`StatusChip`/`Tag`/`FloorLiftTag`/`FilterChip`), `CountStepper`, `Receipt`, `BottomNavBar` (NavTab: Run/Stops/Earnings/Me), `DarkHeader`/`BackHeader`, `LinearProgress`/`StatTile`, state views.
- `ui/<feature>/` — one Compose `*Screen` + `*ViewModel` per feature (`auth`, `shift`, `stops`, `delivery`, `closeout`, `self`, `bulk`). MVVM: ViewModel holds `StateFlow`, actions call a repository. VMs expose a `companion` `Factory` (or `factory(id)`) built with `viewModelFactory { initializer { … } }`.
- `util/` — `money()` (Indian grouping) + date formatters, `LocationProvider` (FusedLocation), `DeviceActions` (`openMaps`/`dialPhone`).
- `ui/navigation/AppNavGraph.kt` — the authoritative screen list + flow.

### Screen flow (`AppNavGraph.kt` / `Routes`)

Start = `TokenStore.isLoggedIn()` ? `run` : `login`. **The bottom-nav shell is home — check-in is NOT a
gate to the app.** The 4-tab bar (`run` DP-03 · `stops` DP-05 · `earnings` DP-17 · `me` DP-21) is always
shown, so a driver can explore earnings/profile/attendance without checking in. The **`run` tab is a host**
(`RunTabScreen`): it owns the `CheckInViewModel` and renders **`CheckInScreen` (DP-02) while off shift**
(check-in flow / "shift completed" state, bottom nav still visible) or **`RouteHomeScreen` (DP-03) once a
shift is open** — a successful check-in flips the tab automatically and fires `onShiftOpen` to start
`LocationService`. Logout is a top-right icon button (`LogoutIconButton`) on the dark headers (+ the Me tab).
Tab switches use `popUpTo(run){saveState} + launchSingleTop + restoreState`. Pushed on top (no bottom bar):
`load_out` (DP-04); `stop_detail/{orderId}` (DP-06) → `deliver/{orderId}` (DP-08→09→10→11 as internal
steps in one screen/VM) **or** `unable/{orderId}` (DP-12); close-out `can_return` (DP-15, opens the session)
→ `cash/{sessionId}` (DP-16) → `check_out/{sessionId}` (DP-22, close = the both-loops gate) → back to
`run` (which now shows the "shift completed" check-in state). Self: `daily_goal` (DP-18) from earnings;
`attendance` (DP-19) + `history` (DP-20) from the Me tab. `bulk/{bulkId}` (DP-14) exists but has no live
entry point yet (see STATUS.md gaps).

### Offline-first — the outbox (the core pattern)

Doorstep/close-out writes go through the Room outbox when the network is down, flushed by **`SyncWorker`**
in creation order on reconnect (immediate on connect + periodic 15 min; backoff; 3 retries → `FAILED`).
Repositories call the API directly and, **only on an offline (`IOException`) failure**, enqueue to the
outbox (`OutboxRepository.enqueue`) and let the UI proceed (DP-10). Ops: `DELIVER`, `CONFIRM_PAYMENT`,
`EXCEPTION`, `CAN_LINES`, `CASH_TALLY`, `CLOSE_SESSION`. **Idempotency:** `/deliver` is server-idempotent
(a replay returns `alreadyRecorded=true`); `confirm-payment` is bodyless. Adding a mutating op → new
`OutboxOperation` + a `SyncWorker.dispatch()` branch (never call Retrofit inline from a screen/VM).

### Auth & API paths

Driver login is **OTP** → `POST api/delivery/auth/send-otp` then `/verify-otp` (both **bare** bodies, not
enveloped). Verify returns `{success,token,employeeId,name,roleId,storeId}` → persisted in `TokenStore`.
This is the correct path: **drivers are onboarded without a password** (the store admin only sets a
credential if it supplies username+password, which is optional), so the password route
(`api/admin/auth/login`) is kept only as an unused fallback. Until MSG91 SMS is wired the backend echoes
a demo OTP, which the login screen shows. `AuthInterceptor` attaches the bearer from `TokenStore`.
**Every path lives only in `ApiConstants.kt`** — the app is on the clean **`/api/delivery/*`** surface;
the only remaining `/api/admin` call is bulk fulfilment (DP-14). All `/api/delivery` operational
responses use the `{success,data,message}` envelope (unwrapped by `apiCall`); the two auth endpoints are
the bare-body exception.

## Conventions

- One `Screen` + `ViewModel` per feature folder; state is a `StateFlow`, actions call a repository; screens read it with `collectAsStateWithLifecycle()`.
- VMs are arg-less where possible (a `load(id)` in `LaunchedEffect`) or take the id via a `factory(id)`; they resolve repos from `Deps`.
- New backend path → add it to `ApiConstants` (never hardcode a URL in a screen/VM/repo).
- New offline action → new `OutboxOperation` + `SyncWorker.dispatch()` branch; queue only on an offline failure.
- Colours/spacing/type come from `PpColor`/`PpType` — screens don't hardcode hex or ad-hoc sizes.
- **Update `STATUS.md` + add a `SESSION.md` entry at the end of every session** (this is how DP context persists).
- ⚠️ Don't bulk-edit `.kt` files with PowerShell `Get-Content`/`Set-Content` — PS 5.1 defaults corrupt UTF-8 punctuation (·, →, —). Use the Edit/Write tools.
