# Chronos audit — 5.1.0

An honest pass over the codebase after the Settings crash report. Two parts: what
was wrong and got fixed, and what is still missing.

## Fixed in 5.1.0

| # | Issue | Severity | Fix |
|---|-------|----------|-----|
| 1 | Settings crashed and there was no way to learn why | Critical | Per-section `try/catch` is impossible — the Compose compiler rejects try/catch around composable calls — so the fix is defensive hardening of every risky call plus a trace the user can send |
| 2 | A crash left no visible trace on the next launch | Critical | Crash handler writes a marker; the app shows a "Chronos crashed last time" banner with **Send log** |
| 3 | Diagnostics lived *inside* Settings — unreachable exactly when it was needed | High | New **Diagnostics & logs** entry in the ⋮ menu: last errors, full log, share .txt / .zip, clear |
| 5 | `Settings.canDrawOverlays` can throw on some OEM ROMs, crashing the Timer section | Medium | Wrapped in `runCatching`; a failure is treated as "not allowed" |
| 6 | Widget refresh ran unguarded from the settings UI | Medium | Wrapped in `runCatching` and logged; a widget problem can no longer crash Settings |
| 7 | Widget face bitmap was rendered at 512×512 on every refresh (main thread) | Medium | Reduced to 320×320, still sharp at 2×2 |
| 8 | Widget text-size slider could pass an out-of-range value to Material3 | Low | Value clamped to 12–48 before the slider sees it |
| 9 | Time-format preview built a `DateTimeFormatter` on every frame | Low | Formatter cached per format, with a fallback if a pattern is invalid |
| 10 | Backup skipped timer presets and sleep sessions | Medium | Both are now exported and restored |

## Still open

- The exact Settings crash could not be reproduced without the device. With 5.1.0
  installed, opening Settings either works, or shows *which section* failed, or the
  crash banner offers the log. Either way the trace is now obtainable.
- Suspect list for that crash, in order of likelihood: the widget-style section's
  preview (invalid stored format string), an OEM overlay-permission call, or a
  widget-refresh failure triggered from a settings tap.

## Features still missing

1. **World clock widget** — the one widget family not built.
2. **Camera missions** — QR/barcode, take-a-photo, push-ups, squats, jumping jacks.
3. **Languages** — the interface is English only; no per-app language picker.
4. **Multiple simultaneous timers** — one timer at a time today.
5. **Alarm history and missed-alarm recovery** — no record of rings, snoozes or misses.
6. **Reliability check wizard** — exact alarms, notifications, battery optimisation, DND in one flow.
7. **Sleep trends** — no weekly/monthly averages yet.
8. **Live wallpaper clock** — not offered; the widget covers this use case.
9. **Wear OS companion**.
10. **Weather / sunrise-sunset on the world clock**.

Feature requests: [@osintgram_io](https://instagram.com/osintgram_io)
