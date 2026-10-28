# Chronos roadmap

What the app does today, and what is worth building next. The public page
(`docs/index.html`) is the feature reference; this file is the plan.

Priority: **P1** = build next, **P2** = worth it, **P3** = nice to have.
Effort: **S** ≈ half a day, **M** ≈ 1–3 days, **L** ≈ a week or more.

## Shipped in 1.0.0

- [x] Exact alarms through `AlarmManager.setAlarmClock`, with an inexact fallback when the permission is refused
- [x] Full-screen ring screen over the lock screen, with snooze and dismiss
- [x] Repeat patterns (once / daily / weekdays / weekends / custom days)
- [x] Skip public holidays — built-in Indian national holidays plus user-added dates
- [x] Per-alarm sound, gradual volume, vibrate, snooze length and snooze limit
- [x] Alarms re-armed after boot, app update, clock change and timezone change
- [x] World clock from the device timezone database, searchable with a letter rail
- [x] Timer with snapping wheels, presets, foreground service, add-a-minute
- [x] Stopwatch with laps, fastest/slowest highlighting, persisted start instant
- [x] Settings: theme, accents, text size, 24-hour time, AMOLED black, alarm defaults, timer and stopwatch sounds, keep screen on
- [x] Backup / restore / factory reset
- [x] Diagnostics: log collection on by default, share, export .txt/.json/.md/.zip, clear
- [x] In-app update check and install from GitHub Releases

## Next — alarms

- [ ] **P1 M** Reliability check wizard: exact alarms, notifications, battery optimisation, DND — checked and explained in one screen
- [ ] **P1 M** Alarm history and missed-alarm recovery: record every ring, snooze and miss with the reason
- [ ] **P1 M** Dismiss challenge: maths, typing, shake or scan-to-dismiss, per alarm
- [ ] **P2 L** Wake window: ring somewhere inside a range rather than on one minute
- [ ] **P2 M** Alarm groups and profiles (work / weekend / travel)
- [ ] **P2 M** Interval alarms: every N hours, every N days, medication schedules
- [ ] **P2 M** Holiday calendar import (.ics) so moving festivals fill themselves
- [ ] **P2 S** Pre-alarm heads-up notification
- [ ] **P3 S** Share one alarm as a QR code or deep link
- [ ] **P3 L** Location-aware alarms

## Next — timer, stopwatch, world clock

- [ ] **P1 M** Multiple simultaneous timers, each with its own notification
- [ ] **P1 M** Pomodoro / interval mode with rounds
- [ ] **P1 S** Timers that survive a reboot
- [ ] **P2 M** Meeting planner across the world-clock cities
- [ ] **P2 S** Sunrise and sunset per city, computed locally
- [ ] **P2 S** Stopwatch lap export as CSV or text
- [ ] **P3 S** Preset reordering, icons and colours

## Next — outside the app window

- [ ] **P1 M** Home-screen widgets: next alarm, world clock, one-tap timer
- [ ] **P2 S** Quick Settings tile and lock-screen shortcuts
- [ ] **P2 S** Ongoing notification for a running stopwatch
- [ ] **P3 L** Wear OS companion

## Next — personalisation and accessibility

- [ ] **P1 M** Hindi and regional languages with the Android 13 per-app language picker
- [ ] **P1 S** Any audio file as an alarm tone, with preview
- [ ] **P1 M** Accessibility pass: TalkBack labels, 200% text, high-contrast theme
- [ ] **P3 S** Per-alarm colour and icon
- [ ] **P3 S** Haptic pattern choice

## Next — data and care

- [ ] **P2 S** Password-protected backup
- [ ] **P2 S** Crash screen with a one-tap "send diagnostics"
- [ ] **P2 M** Compose UI smoke tests in CI
- [ ] **P3 S** Offline-only mode that disables the update check

## Release gate

- [ ] versionCode bumped, versionName updated
- [ ] CI green, `apksigner` reports the release certificate
- [ ] update installs over the previous version without uninstalling
- [ ] an alarm rings on a real device with the screen locked
- [ ] `docs/index.html` lists every new feature in the same release
- [ ] no placeholder, stub or sample data in the shipped build

Feature requests: [@osintgram_io](https://instagram.com/osintgram_io)
