# Delivery Partner App — STATUS

> **What we're working on and what's left.** Read this first when resuming DP-app work, alongside
> `CLAUDE.md` (architecture) and `SESSION.md` (per-session log). Keep it current.

**Platform:** Android only (Kotlin + Jetpack Compose, Material 3). No iOS.
**Backend:** `https://pureplusapp.in/` — the app targets the clean **`/api/delivery/*`** surface.
**Build:** ✅ `./gradlew assembleDebug` succeeds (verified 2026-09-20, `app-debug.apk` ~20 MB). Not yet run on a device.
**Tests:** ✅ `./gradlew testDebugUnitTest` — 9/9 pass (money formatting, reconciliation variance, door-line shortfall, reusable heuristic, postpaid classification). See `app/src/test/java/.../DeliveryLogicTest.kt`.
**Theme:** light-first (Doorstep Design System) — only DP-01 Login is dark, per the mocks.
**Live location (DP-07):** ✅ `LocationService` (foreground) pings `POST /api/delivery/me/location` every ~20s while the shift is open — started at check-in, stopped at check-out/logout.

## Locked decisions
- **One check-in per driver per store per shift day — no same-day re-check-in** (owner decision
  2026-09-21). Enforced by the backend (`CheckInService.CheckInAsync` throws if a `driver_check_in` row
  already exists for the day; check-out only stamps `checked_out_at`). A mistaken check-in + "End shift"
  therefore ends the day; the driver can't check in again until the next shift date. The app reflects
  this with a "shift completed" state on DP-02 rather than a button that the backend would reject.
  Changing this would touch the attendance record + audit trail — don't work around it client-side.

## Screen status (DP-01 → DP-22)
All 21 designed screens are built and wired to live `/api/delivery` endpoints (DP-13 is NA in the spec).

| Screen | State | Endpoint(s) | Notes |
|---|---|---|---|
| DP-01 Login | ✅ built + wired | `POST /api/delivery/auth/send-otp` + `/verify-otp` | **OTP** to the registered mobile (correct path — drivers are onboarded *without* passwords; the mock's password field was wrong). Demo OTP echoed until MSG91 SMS is wired. Password login kept as an unused fallback. |
| DP-02 Check-in | ✅ | `POST /api/delivery/shifts/check-in` (+ `/status`, `/geofence`) | Geo-fenced; **GPS now enforced** — permission (denied vs blocked→settings) + in-app "turn on location" dialog gate the check-in button; override only after a server geo-fence rejection. **Google Maps geofence** — draws the store fence circle green(inside)/red(outside) + user marker (`GET /shifts/geofence`; **needs `MAPS_API_KEY`** or tiles are blank). **Already checked in+out today → "shift completed" state** (one check-in/day, see Locked decisions). |
| DP-03 Route home | ✅ | `GET /me/route` + `/me/goal` + `/me/stops` | Cash-on-hand / loaded-breakdown not in API → omitted. |
| DP-04 Load-out | ✅ | `POST /reconciliation/load-out` | Required qty derived by aggregating stop items (no "required load" endpoint). |
| DP-05 Stop list | ✅ | `GET /me/stops` | Filters All/Pending/Postpaid/Failed; close-out entry. |
| DP-06 Stop detail | ✅ | `GET /me/stops/{id}` | Call (masked) + navigate. |
| DP-07 Navigation | ✅ | device maps intent; foreground svc → `POST /me/location` | Live emission wired via `LocationService` (check-in → check-out). Needs location permission granted. |
| DP-08 Delivery exec | ✅ | (settles via `/deliver`) | Cans/empties/floor steppers; live **client estimate** only (no pre-deliver quote endpoint). |
| DP-09 Payment | ✅ | `POST /orders/{id}/deliver` | Cash/UPI. **No Razorpay QR (vendor-blocked); no split payment** (single `paymentMode`). |
| DP-10 Offline fallback | ✅ | outbox + `SyncWorker` | Deliver queues offline → `DELIVERED_PAYMENT_PENDING`; server-idempotent replay. |
| DP-11 Complete | ✅ | (result of `/deliver`) | Authoritative receipt breakdown shown. |
| DP-12 Unable to deliver | ✅ | `POST /orders/{id}/exceptions` | `FAILED_DELIVERY`; reschedule tags a note. |
| DP-14 Bulk/event | 🟡 built, not reachable | `POST /api/admin/bulk-requests/{id}/deliver` | No delivery-surface bulk **detail** GET; stop list doesn't expose `bulkRequestId` → no live entry point yet. |
| DP-15 Can return | ✅ | `PUT /reconciliation/sessions/{id}/can-lines` | Per-product; opens session (idempotent assumption). |
| DP-16 Cash reconcile | ✅ | `PUT /reconciliation/sessions/{id}/cash` | **Expected cash/UPI/etc read from `session.cash`** — needs backend confirmation it's server-computed. |
| DP-17 Earnings | ✅ | `GET /me/earnings` | Month-to-date + breakdown. |
| DP-18 Daily goal | ✅ | `GET /me/goal` | % feeds payroll messaging. |
| DP-19 Attendance | ✅ | `GET /shifts/attendance` | 30-day history. |
| DP-20 Delivery history | 🟡 stand-in | `GET /me/stops` (filtered) | **No isolated driver history endpoint** → shows current shift's completed/failed stops. |
| DP-21 Profile & docs | 🟡 partial | TokenStore identity only | **No driver profile/documents/vehicle endpoint** → shows name/id/store + placeholder note. |
| DP-22 Check-out | ✅ | `POST /reconciliation/sessions/{id}/close` + `/shifts/check-out` | Close is the both-loops gate. |

## Missing / gap APIs to raise with backend
1. **Driver profile + documents + assigned vehicle** (DP-21) — no endpoint.
2. **Isolated driver delivery history** (DP-20) — currently only current-shift stops.
3. **Pre-deliver price quote** (DP-08/09) — to show deposit/floor/wallet live before settle.
4. **Bulk-request detail on the delivery surface** + `bulkRequestId` on bulk stops (DP-14).
5. **`isReusable` flag on stop items** — app currently heuristically detects cans by product name.
6. **Cash reconcile expected values** — confirm `session.cash` is server-computed (DP-16 sends them back).
7. **Split payment (cash+UPI)** and **company UPI QR** (DP-09) — single `paymentMode` today; QR vendor-gated.
8. ~~Password login~~ — **resolved**: DP-01 now uses OTP (`/api/delivery/auth/send-otp` + `/verify-otp`), the correct path since drivers are onboarded without passwords. Only remaining dependency is **MSG91 SMS** (vendor-gated) — until then the backend echoes a demo OTP which the screen shows.
9. Store/route names + shift timings in the route/checkin payloads (headers show generic text now).
10. Verify **`openSession` idempotency** (returns the existing OPEN session for the shift).

## Next up (suggested order)
1. Open in Android Studio, run `app-debug.apk` on a device, smoke-test the shift flow against live API with a seeded driver (this is the one thing not verifiable off-device).
2. Add `google-services.json` + FCM for push (new-stop / cancelled-stop notifications on DP-05).
3. Resolve the gap APIs above; then finish DP-14 wiring, DP-20/21 real data.
4. Branded fonts (Archivo + IBM Plex Mono) via bundled or downloadable fonts (currently sans/mono system fallback).
5. Device QA pass on money math (DP-08→11) with real deposits/floor config.

## Audit — 2026-09-20 (static + unit-tested + build)
Verified by: code inspection of every screen's fields/actions vs spec + the wired API, `assembleDebug`
green, `testDebugUnitTest` 9/9 green. **Not** verified: on-device runtime (needs a seeded driver login).

| DP | Verdict | Tester note (mock-data trace) |
|---|---|---|
| 01 Login | PASS | **OTP flow**: phone (10-digit gate) → send-otp → 4-digit verify → staff JWT; change-number, 30s resend countdown, demo-OTP hint; 429/invalid/inactive errors surfaced. Works for any onboarded active driver (no password needed). |
| 02 Check-in | PASS | Perms requested on entry; already-checked-in auto-advances; out-of-fence 400 → override link → resubmits `override=true`. |
| 03 Route home | PASS | Aggregates route+goal+first-pending stop; goal bar from `achievementPercent`. Cash-on-hand omitted (no API). |
| 04 Load-out | PASS | Rows aggregated from stop items; lowering loaded < required → red value + shortage banner; confirm → session id. |
| 05 Stop list | PASS | Filters All/Pending/Postpaid/Failed compute correctly; rail colour + chips per status; close-out entry. Realtime add/cancel needs FCM. |
| 06 Stop detail | PASS | Masked phone + dial; maps intent; items; postpaid vs collect label. "Order-for-someone-else" banner not shown (no flag in payload). |
| 07 Navigation | PASS | Maps hand-off + **live foreground ping** now emitting. |
| 08 Delivery exec | PASS | Delivered/empties steppers; empties only for reusable; shortfall note; floor selector + lift. Live ₹ = products estimate (deposits/floor authoritative on confirm — no quote API). |
| 09 Payment | PASS | Cash/UPI select; postpaid auto-ledger note; confirm → `/deliver`. No UPI QR / split (vendor / single-mode). |
| 10 Offline fallback | PASS | Deliver on `IOException` → queued to outbox → Done "payment pending"; `SyncWorker` flushes on reconnect (idempotent). |
| 11 Complete | PASS | Server receipt (products/deposit/floor/wallet) rendered; continue → stops. |
| 12 Unable | PASS | 6 reasons + note; Mark failed / reschedule → `FAILED_DELIVERY`. Photo evidence not wired (no upload). |
| 14 Bulk | GAP | Screen + confirm build & call `bulkDeliver`, but **no live entry** (stop list has no `bulkRequestId`; no bulk detail GET). |
| 15 Can return | PASS | Opens session; per-product empties/fulls/breakage steppers; variance→unaccounted banner; writes per line. |
| 16 Cash reconcile | PASS | Expected read from `session.cash`; declared input; live variance banner. Confirm expected-value sourcing w/ backend. |
| 17 Earnings | PASS | Month-to-date total + base/bonus/goal breakdown. 7-day chart & prior months simplified. |
| 18 Daily goal | PASS | % + progress + payroll note. Radial ring simplified to linear. |
| 19 Attendance | PASS | 30-day rows w/ times + status chip. Late/override inferred from `status`/`isOverride`; summary counts omitted. |
| 20 History | PARTIAL | Current shift's completed/failed only (no driver-history endpoint). |
| 21 Profile | PARTIAL | Identity from token; docs/vehicle placeholder (no endpoint). |
| 22 Check-out | PASS | Two modes: **reconciled** (close gated on `readyToClose` → attendance check-out) and **direct** (mistaken check-in, no run → attendance-only check-out via `/shifts/check-out`, allowed only when 0 deliveries today; else routed to close-out). Entry links from Run screen. Stops location service. |
