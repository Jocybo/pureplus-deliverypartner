# Close-Out Flow Review

_Reviewed: 2026-11-26. All line references are to the files as they exist in the repo today._

---

## Flow Map

```
RunTabScreen / StopListScreen
    └── onCloseOut → navigate(Routes.CAN_RETURN)          [AppNavGraph.kt:137,147]
            │
            ▼
  CanReturnScreen  (DP-15)
    ├── init: POST /reconciliation/sessions  (openSession)
    ├── driver edits empties / fulls / breakage per product
    ├── submit: PUT /reconciliation/sessions/{id}/can-lines  (per row, may queue offline)
    └── onNext(sessionId) → navigate(Routes.cash(sessionId))   [AppNavGraph.kt:205-206]
            │
            ▼
  CashReconcileScreen  (DP-16)   [sessionId from nav arg]
    ├── init: GET  /shifts/check-in/status  (ShiftV2 detect)
    │         GET  /reconciliation/sessions/{id}  (load cash summary)
    ├── driver enters declared cash
    ├── submit step 1: PUT  /reconciliation/sessions/{id}/cash
    ├── submit step 2 (ShiftV2): POST /reconciliation/sessions/{id}/declare
    │         or step 2 (legacy): POST /reconciliation/sessions/{id}/close
    └── onNext(sessionId) → navigate(Routes.checkOut(sessionId))  [AppNavGraph.kt:209]
            │
            ▼
  CheckOutScreen  (DP-22)   [sessionId from nav arg]
    ├── init: GET  /shifts/check-in/status  (ShiftState)
    │         GET  /reconciliation/sessions/{id}  (session state)
    ├── EndShift: POST /shifts/end  (ShiftV2 ACTIVE→PENDING_RECON)
    ├── Resume:   POST /shifts/resume  (ShiftV2 PENDING_RECON→ACTIVE)
    ├── ClockOut (ShiftV2 / direct): POST /shifts/check-out
    ├── LegacyClose:  POST /reconciliation/sessions/{id}/close
    │                 then POST /shifts/check-out
    └── onCheckedOut → navigate(RUN, popUpTo(0))  [AppNavGraph.kt:216-218]

  END_SHIFT shortcut (mistaken check-in, no run):
    RunTabScreen → onEndShift → navigate(Routes.END_SHIFT)
    CheckOutScreen(sessionId = null) — attendance-only close
```

---

## DP-04 Load Out

**API called:**
1. `GET /api/delivery/vehicle-stock` — `DriverRepository.vehicleStock()` — to build the required-quantity rows from `waitingOrders`.
2. `POST /api/delivery/reconciliation/load-out` (`RecordLoadOutRequest`) — `ReconciliationRepository.loadOut()` — records the baseline and opens the reconciliation session.

**SessionId handling:**
- `loadOut()` returns a `ReconciliationSession`; the VM stores `r.data.id.toLong()` in `LoadOutState.sessionId` (line 89 of `LoadOutViewModel.kt`).
- `state.done` becomes `true` on success; `LaunchedEffect(state.done)` in `LoadOutScreen.kt` (line 42) fires `onLoadedOut()`.
- **The `sessionId` stored in `LoadOutState` is NEVER forwarded to the navigator.** `onLoadedOut` is a no-arg lambda (line 36 of `LoadOutScreen.kt`, line 169 of `AppNavGraph.kt`) that calls `toStopsFresh()`. The session ID produced at load-out is thrown away at this point.

**Issues:**
- None in the load-out step itself. The ID discard is intentional: `CanReturnViewModel` re-opens the session via `openSession()` (which is an idempotent POST that returns the existing session for today). The design relies on "one session per driver per shift-date" being enforced server-side, so the client does not need to carry the ID across a navigation to the Stops tab.
- Empty rows: if `waitingOrders` is empty (no assigned orders yet), `state.rows` is empty and the screen shows the neutral note panel. The driver can still tap "Confirm loaded quantities" with an empty list; `lines` will be `emptyList()` and the POST will still open the session. The backend must accept an empty load-out — if it does not, this will silently fail because there are no rows to validate client-side.
- Error state shows as a `NotePanel` at the bottom of the row list (line 88 of `LoadOutScreen.kt`). Loading state blocks the whole content area correctly (line 52-55).

---

## DP-15 Can Return

**API called to open/get session:**
- `POST /api/delivery/reconciliation/sessions` (`OpenSessionRequest{ shiftDate }`) — `ReconciliationRepository.openSession()` — called in `CanReturnViewModel.open()` (line 59 of `CloseOutViewModel.kt`).
- Each row edit submits `PUT /api/delivery/reconciliation/sessions/{id}/can-lines` (`SetCanLineRequest`) — iterated in `submit()` (line 76-79). Lines that fail because of an offline condition are queued to the outbox (`OutboxOperation.CAN_LINES`).

**Empty rows cause:**
`ReconciliationSession.lines` is `List<CanLine>` (default `emptyList()`, line 252 of `ApiModels.kt`). The `toRows()` extension on line 88-91 of `CloseOutViewModel.kt` maps `lines` 1-to-1 into `CanRow` objects. If the server returns a session with an empty `lines` array — which happens when the driver navigates directly to CAN_RETURN without ever completing a load-out (`loadOut` was never called, or the session has no line records yet) — then `state.rows` is empty, the `PpCard` forEach renders nothing, the submit button is reachable, and `submit()` iterates zero rows, sets `done = true` with no error, and calls `onNext(sessionId)`. The flow advances to Cash even though nothing was reconciled.

**SessionId source:**
- `CanReturnViewModel.open()` calls `recon.openSession()` and sets `_state.sessionId = r.data.id.toLong()` (line 60).
- `CanReturnScreen.kt` line 42: `LaunchedEffect(state.done) { if (state.done && state.sessionId > 0) onNext(state.sessionId) }` — the guard `state.sessionId > 0` is correct; it prevents forwarding when `sessionId` is still `-1` (the default from `CanReturnState`, line 44).
- `AppNavGraph.kt` line 205: `CanReturnScreen(onNext = { sessionId -> navController.navigate(Routes.cash(sessionId)) })` — the `sessionId` is baked into the route string `"cash/$sessionId"` as a path argument.

**Issues:**
1. **Silent advancement on empty rows** (described above). If `openSession` returns a session with no `lines`, the submit loop exits immediately, `done = true`, and `onNext` fires. The driver bypasses the can-reconciliation step without entering any data.
2. The `submit()` function (lines 70-82) breaks out of the per-row loop only on a **non-offline** failure (`if (res is ApiResult.Failure && !res.offline)`). An offline failure for any row queues to the outbox but does not stop the loop and does not set an error — the UI advances to Cash. This is intentional (offline-first design), but it means the driver proceeds with the declaration of cash before the queued can-lines have actually reached the server.
3. `state.error` is surfaced as a `NotePanel` inside the scrollable column only when `state.rows` is non-empty (see the `else ->` branch of the `when` block at line 56 of `CanReturnScreen.kt`). When `state.rows` is empty AND there is an error, the top-level `NotePanel` on line 55 catches it (`state.error != null && state.rows.isEmpty()`). This is correct.

---

## DP-16 Cash Reconcile

**API called:**
1. `GET /api/delivery/shifts/check-in/status` — `DriverRepository.checkInStatus()` — called in `CashReconcileViewModel.isShiftV2()` via `shiftLifecycle.currentState()` (line 157-160 of `CloseOutViewModel.kt`). Used to detect ShiftV2.
2. `GET /api/delivery/reconciliation/sessions/{sessionId}` — `ReconciliationRepository.getSession(sessionId)` — to load the `CashSummary` (expected cash, UPI, postpaid, pending, tolerance) and the read-only flag.
3. `PUT /api/delivery/reconciliation/sessions/{sessionId}/cash` (`SetCashRequest`) — `ReconciliationRepository.setCash()` — step 1 of submit (line 186 of `CloseOutViewModel.kt`). Queued offline.
4. ShiftV2: `POST /api/delivery/reconciliation/sessions/{sessionId}/declare` — `ReconciliationRepository.declare()` — step 2. Queued offline via outbox (`DECLARE_SESSION`).
   Legacy: `POST /api/delivery/reconciliation/sessions/{sessionId}/close` — `ReconciliationRepository.close()` — step 2.

**SessionId source:**
- Passed as a constructor parameter `sessionId: Long` to `CashReconcileViewModel` (line 126).
- In `CashReconcileScreen.kt` line 43: `viewModel(key = "cash_$sessionId", factory = CashReconcileViewModel.factory(sessionId))` — a keyed ViewModel instance is created per sessionId. The factory (line 209-213) injects `sessionId` directly.
- The `sessionId` originates from the nav arg parsed in `AppNavGraph.kt` line 207-209.
- The `onNext` callback in `CashReconcileScreen.kt` line 125 is `{ viewModel.submit { onNext(sessionId) } }` — it uses the `sessionId` received by the screen composable (not from the VM state), which is correct and consistent.

**Issues:**
1. **Race between ShiftV2 detection and session load.** `load()` launches `isShiftV2()` and `recon.getSession()` **sequentially** (lines 140-153 of `CloseOutViewModel.kt`): `v2` is awaited first, then the session GET happens. If `checkInStatus()` is slow (timeout), the loading spinner shows but the session GET is blocked. This is not a correctness issue but adds latency that could be eliminated by running them in parallel with `async/await`.
2. **`declaredCash` pre-fill uses only `expectedCash`.** Line 148: `r.data.cash?.expectedCash?.let { c -> if (c > 0) c.toLong().toString() else "" }`. The field is truncated to `Long` (coins discarded), and the pre-fill only applies when `expectedCash > 0`. This means if the driver is handing over cash with paise the pre-filled value is off by up to ₹0.99. Minor but visible.
3. **Variance note (line 99-101 of `CashReconcileScreen.kt`) uses only `expectedCash` minus `declared`, not the full receivable.** The backend computes the full variance across cash + UPI + postpaid; the client's live-preview note is a simplified cash-only view. This is a UI informational element only and does not affect what is submitted.
4. No error state for the loading path when `state.error != null && state.loading == false` — the `when` block in `CashReconcileScreen.kt` (line 56-58) only branches on `state.loading`; the error panel is inside the `else` column. This is handled correctly: if `load()` fails, `state.loading = false` and `state.error != null`, so the else-branch renders and the NotePanel at line 112 is shown.
5. The `onNext` lambda passed to `submit()` on line 125 of `CashReconcileScreen.kt` is `{ onNext(sessionId) }` — it ignores the `done` flag and calls `onNext` immediately inside the coroutine after `_state.update { ... done = true ... }` (line 204 of `CloseOutViewModel.kt`). This is structurally correct: `onNext` is only called if `_state.value.done` is `true` (line 204 is inside `if (_state.value.done) onNext()`). The screen does not use a `LaunchedEffect(state.done)` pattern like `CanReturnScreen` does; instead the navigation callback is invoked directly from the ViewModel coroutine through the lambda. This is functional but slightly unusual — it means the navigation fires from the coroutine thread, which is fine because `navigate()` is thread-safe on the main dispatcher (Compose Navigation queues it).

---

## DP-22 Check Out

**Phase logic (how `CheckOutPhase` is determined):**

`CheckOutViewModel.phaseFor(shiftState, session)` at lines 320-326 of `CloseOutViewModel.kt`:

```
ShiftState.Closed | AutoClosed | ForceClosed  → Completed
ShiftState.PendingRecon                        → PendingRecon
ShiftState.Active | OffShift                   → EndShift
null | Legacy | unrecognised                   → LegacyClose  (if session != null)
                                                 EndShift     (if session == null)
```

`ShiftState.OffShift` mapping to `EndShift` is worth noting: `OffShift` means no check-in row exists today (or the row has no `dutyStatus` yet). Sending an end-shift POST in this state will likely fail server-side (no active shift to end). The screen shows an "End shift" button, the driver taps it, the backend 400 is surfaced verbatim. This is handled correctly by the design (surface verbatim, no client-side gate), but the UX could confuse a driver.

**ShiftV2 vs Legacy path differences:**

| Phase | Action | API |
|---|---|---|
| `EndShift` (ShiftV2 active) | `endShift()` | `POST /shifts/end` → PENDING_RECON |
| `PendingRecon` | `checkOut()` / `resume()` | `POST /shifts/check-out` / `POST /shifts/resume` |
| `LegacyClose` | `checkOut()` | `POST /sessions/{id}/close` then `POST /shifts/check-out` |
| `EndShift` (direct, no run) | `endShift()` | same `POST /shifts/end` |
| `Completed` | "Done" button | `onCheckedOut()` directly — no API call |

For `LegacyClose`, the `clockOut()` after `recon.close()` succeeds is `shiftLifecycle.clockOut()` called without `await` being checked for success (line 375 of `CloseOutViewModel.kt`): `shiftLifecycle.clockOut() // attendance close; best-effort`. The comment says "best-effort" and the result is ignored. If the attendance clock-out fails, `done = true` is still set and the driver is sent back to the Run tab. The attendance row will remain open server-side until the next check-in or an admin force-close.

**Session load in `CheckOutViewModel`:**
- If `sessionId` is non-null and > 0, `recon.getSession(sessionId)` is called (line 310). The session is used to populate `ReconciledBody` and `PendingReconBody` with cash/can summary.
- The `sessionId` is passed to `CheckOutViewModel` via `CheckOutViewModel.factory(sessionId)` (line 391). In `AppNavGraph.kt` lines 211-221, `sessionId` is extracted from the nav arg and passed to the screen.
- The direct `END_SHIFT` route (lines 222-231) passes `sessionId = null` explicitly.

**Issues:**
1. **`LegacyClose` attendance clock-out is best-effort and silent** (line 375). If `POST /shifts/check-out` fails, the backend attendance row stays open. No error is surfaced to the driver and no retry is queued to the outbox. This should at minimum be logged or added to the outbox.
2. **`CheckOutPhase.Completed` button calls `onCheckedOut()` directly (line 81 of `CheckOutScreen.kt`)**, not `viewModel::checkOut`. This is intentional — the shift is already closed, there is nothing further to do — but it means `state.done` is never set to `true`. The `LaunchedEffect(state.done)` on line 53 of `CheckOutScreen.kt` will never fire for this phase. The `onCheckedOut()` is wired correctly for the `Completed` phase because it is called directly from the button `onClick`; this is not a bug but it makes the navigation pattern inconsistent across phases.
3. **`endShift()` and `resume()` use `ending: Boolean` (not `submitting`)** to track in-flight state (lines 332-336, 346-350). The check in `checkOut()` (line 362) only guards `submitting`, not `ending`. If the driver somehow taps the clock-out button while an end or resume is in flight, `checkOut()` can proceed concurrently. In practice the button for `checkOut()` is only shown in `PendingRecon` phase and the button for `endShift()` is only shown in `EndShift` phase, so both cannot be tapped simultaneously — this is not reachable in normal flow.
4. **`CheckOutViewModel.load()` ignores a `shiftLifecycle.currentState()` failure silently.** Line 296: `val shiftState = (shiftLifecycle.currentState() as? ApiResult.Success)?.data`. A failure (network down, 500) yields `null`. With `shiftState = null` and `sessionId` present, `phaseFor(null, session)` falls into the `else` branch (line 325) → `LegacyClose` if the session loaded, `EndShift` if not. The driver may be presented the wrong phase UI if the shift-status call fails offline.

---

## Critical Broken Links

**1. Load-out session ID is discarded — flow depends on server-side idempotency.**
`LoadOutViewModel.confirm()` stores `sessionId` in state (line 89) but the `onLoadedOut` callback carries no arguments (line 42 of `LoadOutScreen.kt`, line 169 of `AppNavGraph.kt`). The navigator calls `toStopsFresh()`. When the driver later navigates to `CAN_RETURN`, `CanReturnViewModel` calls `openSession()` (a fresh POST) to re-obtain the session. If the backend's idempotency guard works (returns the existing OPEN session for today), this is correct. If the server creates a second session on a second POST, `CanReturnViewModel` will work off a different session than the one `loadOut()` opened, and the can-line baseline (which was written to the first session) will be missing. This is a correctness dependency on the backend.

**2. Empty `lines` in `openSession` response allows silent bypass of can-reconciliation.**
If `POST /reconciliation/sessions` returns a session with `lines: []` (no load-out baseline recorded), `CanReturnViewModel.submit()` iterates zero rows and immediately sets `done = true`. The driver is advanced to `CashReconcileScreen` with no can data recorded. The backend's close/declare endpoint may then reject the session for being unbalanced, but that rejection surfaces only on the last screen (DP-22), not at DP-15 where the driver could fix it.

---

## Minor Issues

1. **`LoadOutViewModel.load()` (line 54):** product names come from `r.data.lines` (stock lines), but the row labels in `waitingOrders` have no name — the fallback is `"Product ${w.productId}"`. If a product appears in `waitingOrders` but has no entry in `lines`, the row shows a bare ID. This is cosmetic.

2. **`CashReconcileViewModel.load()` runs ShiftV2 detection and session GET sequentially** (lines 140-153 of `CloseOutViewModel.kt`). Both calls are independent and could run in parallel with `coroutineScope { async { } }` to shave latency on slow connections.

3. **`SetCashRequest` sends `expectedCash/UPI/postpaid/pending` back to the server** (line 177-184 of `CloseOutViewModel.kt`). These values were just read from the server. The server must accept them without mutation (they should be ignored or validated equal to what it already has). If the server recalculates them from the driver's submission, echoing stale values could create a discrepancy. This depends on backend behaviour not visible in the client.

4. **`CanReturnScreen.kt` line 72-77:** the `Breakage / loss` stepper is only shown when `row.variance != 0`. Variance is `loadedOut - (delivered + fullsReturned + breakageLoss)`. When `loadedOut == 0` (no load-out baseline for this product), variance is always 0, so the breakage stepper is permanently hidden even if the driver wants to record a loss on a product that was added after load-out.

5. **`CheckOutScreen.kt` line 110-113 (`LegacyClose` button):** the "Check out · end shift" button is `enabled = state.session?.readyToClose == true`. If `recon.getSession()` fails (network error), `state.session` is null, `readyToClose` defaults to `false`, the button is permanently disabled, and `state.error` from the load failure is shown. The driver is stuck with no way to retry the session load from the UI — there is no retry button on `CheckOutScreen`. The only way out is to navigate back (no back button on this screen) or kill the app.

6. **`CloseOutStepper` step 3 label is "Check out" (line 23 of `CloseOutSteps.kt`)** but the screen title on `CheckOutScreen` is "Shift summary" (not "Check out") when the phase is not `PendingRecon` or `Completed` and not `directMode` (line 65 of `CheckOutScreen.kt`). The stepper and screen title are inconsistent in the `EndShift` phase.

7. **`ReconciliationFlow.detectShiftV2()` constructs a new `ShiftLifecycle` instance inline** (line 115 of `ReconciliationFlow.kt`): `ShiftLifecycle(driver).isShiftV2(r.data.checkIn)`. The `ShiftLifecycle` class has no state, so this is functionally fine, but it bypasses `Deps.shiftLifecycle` and could cause confusion if `ShiftLifecycle` ever gains state.

---

## Verdict

**Partially broken — functionally complete but with two structural risks.**

The happy-path works end to end: session opens, can lines are PUT per row (with offline queuing), cash is PUT and the handover is declared or closed, and the check-out POST fires the correct endpoint for the detected store mode. The sessionId is correctly threaded from `CanReturnScreen` through `CashReconcileScreen` to `CheckOutScreen` via nav args.

The two structural risks are:
- The load-out → can-return session continuity relies entirely on backend idempotency (the POST that opens the session in `CanReturnViewModel` must return the same session that `loadOut` already created). There is no client-side verification or ID carry-through.
- If the server returns a session with an empty `lines` array, the can-reconciliation step silently passes through and the backend receives a declare/close for a session with no can data, which may be rejected at the final clock-out gate with a confusing error message.

The legacy attendance clock-out being best-effort (issue in DP-22, item 1) is a data-integrity gap worth fixing before go-live.
