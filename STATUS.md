# Delivery Partner App — STATUS

> **What we're working on and what's left.** Read this first when resuming DP-app work, alongside
> `CLAUDE.md` (architecture) and `SESSION.md` (per-session log). Keep it current.
>
> **Re-verified 2026-10-10** against the backend **Operations Revamp** (Phases 0–7, migrations 043–051,
> built 2026-09-30). The backend — not the old business spec — is now the single source of truth. The
> authoritative driver contracts are:
> `web/PurePlus/projectdocuments/Frontend-Integration-Operations-Revamp.md` (newest, §2/§3/§6),
> `handoff/Driver-App-Endpoints.md`, `handoff/API-Readiness.md`.

**Platform:** Android only (Kotlin + Jetpack Compose, Material 3). No iOS.
**Backend:** `https://pureplusapp.in/` — the app targets the clean **`/api/delivery/*`** surface.
**Build:** ✅ `./gradlew assembleDebug` succeeds (2026-09-20, `app-debug.apk` ~20 MB). **Not yet run on a device.**
**Tests:** ✅ `./gradlew testDebugUnitTest` — 9/9 pass. See `app/src/test/java/.../DeliveryLogicTest.kt`.
**Theme:** light-first (Doorstep Design System); only DP-01 Login is dark.
**ApiConstants verified against controllers:** 2026-09-19 — **pre-revamp** (no `/shifts/end`, `/resume`, no reconciliation `/declare`). See §"Required rework".

---

## ⚠️ BIG PICTURE 2026-10-10 — the DP app is built against the PRE-revamp model

The backend shift + reconciliation model was rebuilt. **The app as-built still works against production
today** because every revamp behaviour is **flag-gated OFF** (`ShiftV2:Enabled`, `Dispatch:*`,
`Routing:SequenceStops`, `VehicleStock:Enabled`, `Inventory:Enabled` — all default off, cutover is
store-by-store). **But the app must be reworked before a store is flipped to `ShiftV2`.** Two areas change
materially; everything else is additive and degrades cleanly.

### What changed that the DP app must follow

| Area | Old model (app is built on this) | New model (`ShiftV2:Enabled`) | App impact |
|---|---|---|---|
| **Shift lifecycle** | single check-in; single "Clock out"; one check-in/day, no re-open | **DutyShift state machine** `ACTIVE → PENDING_RECON → CLOSED` (· `AUTO_CLOSED` · `FORCE_CLOSED`). On shift **iff `dutyStatus = ACTIVE`** (18h window killed). | **Rework DP-22** into a two-step flow: **End shift** (`POST /shifts/end` → PENDING_RECON) → reconcile → **Clock out** (`POST /shifts/check-out`, **hard-gated on store STORE_VERIFIED + no active orders**). Add **Resume** (`POST /shifts/resume`) to undo an accidental end. Drive the shift banner off `dutyStatus`, not a local boolean/elapsed-time. |
| **Reconciliation** | driver fills can-lines + cash then **`/close`** closes it (both-loops gate = driver-side) | **two-party handshake** `OPEN → DRIVER_DECLARED → STORE_VERIFIED → CLOSED` (· `DISPUTED`). Driver **declares**; **store** verifies + closes. | **Rework DP-15/16/22:** after can-lines + cash, call **`POST /reconciliation/sessions/{id}/declare`** (DRIVER_DECLARED). Then the session is **read-only for the driver** — show "Waiting for store verification". `/close`, `/verify`, `/dispute` are **store-admin (web) routes** now, not the driver's. |
| **Stop sequencing** | reads `sequence` + `legKm` | adds **`roadKm`** (≈ road dist ×1.3) + **`eta`** ("HH:mm" IST); stops **banded by delivery slot** (hard constraint) | **DP-05/06 enhancement:** render `eta` (label "~") + `roadKm`; **do not re-sort client-side by distance** (breaks slot banding). Hide when null (sequencing off). |
| **Cash "expected"** | app read `session.cash` and hoped it was server-computed | `ReconciliationCalculator` **derives expected cash/UPI from delivered orders' settlement modes** — authoritative; postpaid/wallet excluded; decimal, rounded once | **DP-16 resolved** — just display the server's expected; never type/guess it. |
| **Assignability** (dispatch) | opaque | `GET /api/admin/stores/{id}/driver-readiness` + blocker codes; must-have gate (shift+vehicle+zone+capacity+valid licence/insurance) | **Store-Admin (web) surface, not the DP app.** A driver with no vehicle/zone/licence simply won't get stops — DP app needs no change beyond showing an empty route. |

### Required rework — concrete checklist (do before `ShiftV2` cutover)
1. **`ApiConstants.kt`** — add `SHIFT_END = "api/delivery/shifts/end"`, `SHIFT_RESUME = "api/delivery/shifts/resume"`, and `fun reconDeclare(id) = "api/delivery/reconciliation/sessions/$id/declare"`. Keep `reconClose` only as a legacy/pre-ShiftV2 path.
2. **Check-in status** — read the new `dutyStatus` field from `GET /shifts/check-in/status`; branch the whole shift UI on it. **Feature-detect:** if `dutyStatus` is absent in the response, the store is still pre-ShiftV2 → keep the current single-close path.
3. **Close-out flow (DP-15 → DP-16 → DP-22)** — insert **End shift** before reconciliation; replace the driver `/close` with `/declare`; add the **"awaiting store verification"** read-only state; make **Clock out** call `/shifts/check-out` and surface its 400 messages verbatim ("store must verify…", "active orders remain").
4. **Resume** — expose a Resume action while `dutyStatus = PENDING_RECON`.
5. **AUTO_CLOSED / FORCE_CLOSED** — show a read-only banner; if the session is `DISPUTED`, surface it (dispute is arbitrated by Super Admin, out of the driver's hands).
6. **Outbox** — the offline `CLOSE_SESSION` op must become `DECLARE_SESSION` under ShiftV2 (declare is the driver's terminal write; close is no longer the driver's).

> **Decision needed from owner:** do we ship the ShiftV2 rework now (so the app is ready when stores flip),
> or keep the app on the legacy path and rework at cutover? Recommended: **build it behind the same
> feature-detection** (branch on `dutyStatus`) so one APK serves both pre- and post-cutover stores.

---

## Locked decisions (updated for the revamp)
- **One shift window per driver per day** (no split shifts) — **still true** under DutyShift. What changed:
  an accidental **End shift** is now **recoverable via `/resume`** (PENDING_RECON → ACTIVE) *before* the shift
  reaches CLOSED. So the old "shift completed = dead for the day, no way back" is **relaxed**: the app should
  offer **Resume** while PENDING_RECON, and only show the terminal "shift completed" state once `dutyStatus`
  is `CLOSED`/`AUTO_CLOSED`/`FORCE_CLOSED`. Clock-in grace = start−30m (earlier rejected; late allowed+flagged).
- **Clock-out is hard-gated** — needs reconciliation `STORE_VERIFIED` + no active orders. The driver can no
  longer self-close. Do not work around this client-side.

---

## Screen status (DP-01 → DP-22) — re-verified 2026-10-10
All 21 designed screens are built and wired to live `/api/delivery/*` endpoints (DP-13 is NA in the spec).
"Revamp impact" flags what changes under `ShiftV2`/`Routing`/`Inventory` flags.

| Screen | State (today, flags OFF) | Endpoint(s) | Revamp impact |
|---|---|---|---|
| DP-01 Login | ✅ built + wired | `POST /api/delivery/auth/send-otp` + `/verify-otp` | None. OTP to registered mobile (correct — drivers onboarded without passwords). Demo OTP echoed until MSG91 live. |
| DP-02 Check-in | ✅ | `POST /shifts/check-in` (+ `/check-in/status`, `/geofence`) | **Read `dutyStatus`** from `/check-in/status` and drive UI off it (not a local boolean). Geo-fenced; GPS enforced; Google Maps fence circle (needs `MAPS_API_KEY`). "Already checked in/out" → use `dutyStatus`. |
| DP-03 Route home | ✅ | `GET /me/route` + `/me/goal` + `/me/stops` | None. Cash-on-hand still omitted (no API). |
| DP-04 Load-out | ✅ | `POST /reconciliation/load-out` | Under `VehicleStock:Enabled`, pairs with `GET /vehicle-stock`; additive trips with `clientRef`. No change needed; optional: show vehicle-stock breakdown. |
| DP-05 Stop list | ✅ | `GET /me/stops` | **Render `eta` + `roadKm`** when present (`Routing:SequenceStops`); keep slot banding; don't re-sort by distance. |
| DP-06 Stop detail | ✅ | `GET /me/stops/{id}` | Optional: show `eta`/`roadKm`. No contract break. |
| DP-07 Navigation | ✅ | device maps intent; foreground svc → `POST /me/location` | None. Zero-cost maps handoff (per Google-Maps doc, DP nav is not metered). |
| DP-08 Delivery exec | ✅ | `POST /orders/{id}/deliver` (+ `/deliver/preview`) | **Pre-deliver price-quote RESOLVED:** `POST /orders/{id}/deliver/preview` now returns the full charge breakdown (floor delta, extra deposit, wallet credit, amount payable) WITHOUT committing. **App task:** wire the preview call so live ₹ is server-authoritative, not a client estimate. |
| DP-09 Payment | ✅ | `POST /orders/{id}/deliver` | Cash/UPI only. **Razorpay QR + split payment still vendor-blocked** (single `paymentMode`). |
| DP-10 Offline fallback | ✅ | outbox + `SyncWorker` | None. `/deliver` server-idempotent; `confirm-payment` bodyless. |
| DP-11 Complete | ✅ | (result of `/deliver`) | None. Authoritative server receipt rendered. |
| DP-12 Unable to deliver | 🟡 built, misses the fail-gate | `POST /orders/{id}/attempts` → `POST /orders/{id}/exceptions` | **NEW hard gate:** a `FAILED_DELIVERY` is now rejected unless (a) the order is OUT_FOR_DELIVERY, (b) ≥1 contact attempt is logged via **`POST /orders/{id}/attempts`**, and (c) unless the customer refused, ≥5 min have elapsed since OUT_FOR_DELIVERY (`OrderExceptionService.RaiseAsync`). **App task:** call `/attempts` (≥1) + enforce the 5-min wait (or "Customer Refused") before raising the exception — the app currently raises with no attempt and will be rejected. Photo evidence still not wired (no upload endpoint). |
| DP-14 Bulk/event | 🟡 built, on the admin path | `GET /bulk-jobs` · `POST /bulk-jobs/{id}/deliver` (app still on `POST /api/admin/bulk-requests/{id}/deliver`) | **Partially RESOLVED:** the delivery surface now has `GET /api/delivery/bulk-jobs?status=OUT_FOR_DELIVERY` (list — the **entry point**) + a clean `POST /api/delivery/bulk-jobs/{id}/deliver` (`BulkRequestService.ListDriverJobsAsync` / `BulkDeliveryService.FulfilAsync`). **App task:** wire the list as the entry point + move deliver off the `/api/admin` path. **Still missing:** a bulk-request **detail-by-id GET** on the delivery surface (+ `bulkRequestId` on stops). |
| DP-15 Can return | ✅ (legacy close path) | `PUT /reconciliation/sessions/{id}/can-lines` | **Revamp:** can-lines unchanged, but the terminal action becomes **`/declare`**, not `/close`; after declare the lines are **read-only for the driver** (store verifies with its own counts). |
| DP-16 Cash reconcile | ✅ (legacy close path) | `PUT /reconciliation/sessions/{id}/cash` | **Resolved gap:** expected cash/UPI is now **server-derived** (`ReconciliationCalculator`) — display it, don't type it. Terminal action → **`/declare`**. |
| DP-17 Earnings | ✅ | `GET /me/earnings` | None. |
| DP-18 Daily goal | ✅ | `GET /me/goal` | None. |
| DP-19 Attendance | ✅ | `GET /shifts/attendance` | Backend clean view exists. Under ShiftV2, attendance **unifies on DutyShift** (payroll reads the shift) — values should be consistent; no app change required. |
| DP-20 Delivery history | 🟡 stand-in → **unblocked** | `GET /me/deliveries` | **Was flagged as "no endpoint"; the endpoint now EXISTS** (`/me/deliveries`, built per API-Readiness). **App task:** wire `HistoryScreen` to `/me/deliveries` instead of the current `/me/stops` filter stand-in. |
| DP-21 Profile & docs | 🟡 partial → **unblocked** | `GET /me/profile · /documents · /vehicle · /store · /zone · /shift`, `PUT /me/profile`, `POST /me/documents` | **Was flagged as "no endpoint"; endpoints now EXIST** (constants already declared in `ApiConstants.kt`). **App task:** wire `ProfileScreen`/VM to real profile/documents/vehicle instead of token-only placeholder. |
| DP-22 Check-out | ✅ (legacy both-loops gate) | `POST /reconciliation/sessions/{id}/close` + `/shifts/check-out` | **Biggest rework.** New: **End shift** (`/shifts/end` → PENDING_RECON) → declare → store verifies → **Clock out** (`/shifts/check-out`, gated on STORE_VERIFIED + no active orders) + **Resume** (`/shifts/resume`). Driver no longer calls `/close`. |

---

## Endpoints to ADD to `ApiConstants.kt` (ShiftV2 rework)
```
api/delivery/shifts/end                                  // POST {shiftDate?} → PENDING_RECON
api/delivery/shifts/resume                               // POST {shiftDate?} → ACTIVE
api/delivery/reconciliation/sessions/{id}/declare        // POST (no body) → DRIVER_DECLARED
```

## Endpoints to ADD to `ApiConstants.kt` (backend-present, NOT wired — found 2026-10-16)
```
api/delivery/orders/{id}/deliver/preview                 // POST — charge breakdown, no commit (DP-08)
api/delivery/orders/{id}/attempts                        // POST {outcome,notes} — contact attempt, fail-gate (DP-12)
api/delivery/me/stops/completed                          // GET — "Completed today" list (DP-05)
api/delivery/me/documents                                // POST {…} — submit a doc, lands PENDING (DP-21)
api/delivery/bulk-jobs                                   // GET ?status= — driver bulk-job list / entry point (DP-14)
api/delivery/bulk-jobs/{id}/deliver                      // POST — clean driver bulk deliver (DP-14)
api/delivery/vehicle-stock                               // GET — on-board cans snapshot (DP-03/04/15)
api/delivery/vehicle-stock/trips                         // GET ?from=&to= — load-out trip history
api/delivery/vehicle-stock/returns                       // GET ?from=&to= — return history
api/delivery/vehicle-stock/return                        // POST {…} — DP-15 return fulls/empties to store
```
Already present and correct: send-otp/verify-otp, check-in(+status+geofence), check-out, load-out,
me/route|stops|location|goal|earnings|profile|documents(GET)|vehicle|store|zone|deliveries, orders/*,
reconciliation/sessions(+can-lines+cash+close), notifications.

## Remaining gap APIs to raise with backend (genuinely missing)
1. **Bulk-request detail-by-id on the delivery surface** + `bulkRequestId` on bulk stops (DP-14) — the list + deliver now exist (`/bulk-jobs`), only the per-id detail GET is absent. *(still missing)*
2. **`isReusable` flag on stop items** — app heuristically detects cans by product name. *(still missing)*
3. **Split payment (cash+UPI)** + **company UPI QR** (DP-09) — single `paymentMode` today; QR Razorpay-gated. *(vendor)*
4. **Photo evidence upload** for failed deliveries (DP-12). *(still missing)*
5. **Push device-token registration** — no endpoint for the app to register its FCM token; push can't reach a device even after FCM is bought (API-Readiness 🔴). *(still missing, non-vendor)*
6. Store/route names + shift timings in route/check-in payloads (headers show generic text). *(minor)*

**Resolved since the last audit:** ~~driver profile/documents/vehicle (DP-21)~~, ~~isolated delivery history
(DP-20)~~, ~~cash-reconcile expected-value sourcing (DP-16)~~ — all now backend-provided. ~~Pre-deliver price
quote (DP-08/09)~~ — now `orders/{id}/deliver/preview`. ~~Bulk list + deliver on the delivery surface (DP-14)~~
— now `/bulk-jobs` (+ `{id}/deliver`); only the detail-by-id GET remains. ~~Password login~~ — DP-01 is OTP.
OTP SMS remains MSG91-gated (demo OTP echoed).

---

## Next up (suggested order)
1. **Decide the ShiftV2 strategy** (owner, above) → implement the rework behind `dutyStatus` feature-detection: End/Resume/Declare + awaiting-verification state + hard-gated clock-out. **This is the #1 DP task.**
2. **Wire DP-20 history** (`/me/deliveries`) and **DP-21 profile/documents/vehicle** (`/me/*`) to real data — endpoints now exist.
3. **Add `eta`/`roadKm`** rendering to DP-05/06 (ready for `Routing:SequenceStops`).
4. **Run `app-debug.apk` on a device** against live API with a seeded driver — the one thing not verifiable off-device (shift → load-out → deliver → reconcile).
5. Add `google-services.json` + FCM for push (needs the device-token registration endpoint above too).
6. Resolve remaining gap APIs → finish DP-14 wiring.
7. Branded fonts (Archivo + IBM Plex Mono); device QA on money math (DP-08→11).

## Audit trail
- **2026-09-20** — static + unit-tested + build audit: every screen's fields/actions vs spec + wired API; `assembleDebug` green; `testDebugUnitTest` 9/9. Not verified: on-device runtime.
- **2026-10-10** — re-verified DP-01→DP-22 against the backend Operations Revamp (`Frontend-Integration-Operations-Revamp.md`, `Driver-App-Endpoints.md`, `API-Readiness.md`). Confirmed the app is on the **pre-revamp** shift/recon model (code: `ApiConstants.kt` has no `end`/`resume`/`declare`; DP-22 uses driver `/close`). Flags are OFF in prod so the app still works today; ShiftV2 rework required before cutover. Three former gap-APIs (DP-16/20/21) are now backend-built → downgraded to app-wiring tasks.
- **2026-10-16** — re-verified `ApiConstants` vs the backend delivery controllers (`Controllers/Delivery/*.cs`, the source of truth); found `deliver/preview`, `orders/{id}/attempts` (fail-gate), `bulk-jobs` list+deliver, `me/stops/completed`, `me/documents` POST, and the `vehicle-stock` controller all present in the backend but NOT wired in the app; corrected stale "missing" claims (DP-08 preview, DP-12 fail-gate, DP-14 bulk list/deliver).
