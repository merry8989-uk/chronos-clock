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

## Shipped in 2.0.0

- [x] Wake-up missions, up to five per alarm, run in order: Math, Typing, Color Tiles, Arrange Shapes, Find Numbers, Find Alphabets, Memory Tiles, Shake, Step
- [x] Missions that cannot run on a device (no step sensor, permission refused) step aside instead of trapping the user; a skip option appears after three minutes of ringing
- [x] Sleep and snoring recorder: microphone snore detection with per-event WAV clips, room-noise graph, movement index, seven-night strip and per-night report
- [x] Snore sensitivity (low / medium / high) and snore-clip saving toggle
- [x] Alarm wallpaper: up to eight images, random rotation per ring
- [x] Sound library: eight bundled tones in four categories, any device audio file, and recorded custom tones
- [x] Per-alarm mission, wallpaper and sound configuration

## Shipped in 2.1.0

- [x] Screen on/off tracking during a sleep session, registered at runtime inside the recorder
- [x] Total screen-off time as a sleep proxy, screen-on moments as wake-ups, unlock count
- [x] Sleep-onset marker (first screen-off) and a timeline bar of when the phone was in use
- [x] Live screen status while recording

## Shipped in 3.0.0 — visual and interaction upgrade

- [x] Motion system: shared durations/easings, haptics helper honouring the Haptic feedback setting, press-scale modifier
- [x] Custom spring toggle, sliding segmented control, circular progress ring, digit-ticker text, staggered list entry
- [x] Next-alarm hero card with a live countdown ring
- [x] Swipe-to-delete with undo snackbar, and a long-press menu (duplicate / turn off / delete)
- [x] Animated tab transitions and accent gradient background
- [x] Circular timer with a breathing ring and animated digits
- [x] Ring screen: pulsing clock, mission progress bar, cross-fading missions
- [x] World clock day/night tint and long-press reordering
- [x] Animated sleep graphs and level meter
- [x] Tabular figures so running clocks do not shift layout

## Shipped in 3.1.0

- [x] Live three-hand analog clock composable driven by the device time (hour, minute, second)
- [x] The app logo is that clock: shown in the top bar of every screen
- [x] Settings → Logo & clock: live preview, analog/digital style, second hand on/off, smooth sweep
- [x] Launcher icon redrawn as a three-hand clock face (10:10:30)

## Fixed in 3.1.1

- [x] Clock hands were sized as fractions of the radius, so at 38dp the second hand was under a pixel and the logo looked frozen — all strokes now have pixel minimums
- [x] Small sizes use a simplified face: no minute ticks, thicker hour/minute hands, longer accent second hand
- [x] Clock state refreshes five times a second (33ms with smooth sweep), so movement is visible
- [x] Tap the logo to open a full-screen clock with the ticking digital time

## Shipped in 4.0.0 — home-screen widgets

- [x] Clock widget: live analog clock (1×1 and larger), self-updating
- [x] Next alarm widget with a live countdown
- [x] Alarm list widget with up to three alarms and working on/off toggles
- [x] Timer widget with self-counting Chronometer, Start / Pause / Reset, and persisted state
- [x] Stopwatch widget with Start / Pause / Reset
- [x] Holidays widget with the next three holidays
- [x] Timer presets widget: one tap starts a saved preset
- [x] Once-a-minute refresh alarm for the text widgets; cancelled when no widgets are placed
- [x] Wi-Fi-only update option (uses the network-state permission)
- [x] USE_FULL_SCREEN_INTENT added for the alarm takeover on Android 14+

## Fixed in 4.1.0

- [x] **Bug:** the hour/minute/second wheels only changed the display — the timer that actually ran kept its old duration, so anything other than the last-set value "did not work". The engine is now the single source of truth and the wheels write straight into it.
- [x] **Bug:** Reset returned to the stale duration instead of what the wheels showed; it now returns to the configured duration and the wheels stay in step.
- [x] Added a − button next to +, with a configurable step (1/2/5/10/15 minutes) in Settings → Timer
- [x] Timer notification now ticks by itself and carries Pause, +N min, Stop and New timer actions
- [x] Optional floating timer bubble (draggable, tap to open, long-press to hide) with a permission shortcut in Settings

## Shipped in 4.2.0

- [x] Digital clock widget with seconds and date, drawn by TextClock so it ticks itself
- [x] One-tap widget pinning from Settings (requestPinAppWidget) with a clear fallback message
- [x] Widget guide dialog lists all eight widgets

## Shipped in 5.0.0 — widget styling

- [x] Analog widget redrawn by Chronos itself (ClockFaceRenderer) instead of the platform AnalogClock: brand rim, minute ticks, coloured hands
- [x] Digital widget: four time formats as separate layouts (12h / 12h+sec / 24h / 24h+sec)
- [x] Background styles: none, half-transparent, white, blue, custom colour
- [x] Text colour palette (8), automatic dark ink on a white background, text size 12–48sp
- [x] Live preview in Settings → Widget style, applied to widgets immediately

## Fixed in 5.1.0 — audit pass

- [x] Defensive hardening of every risky call in Settings (overlay check, widget refresh, slider value, time formatter)
- [x] Diagnostics moved to the ⋮ menu so it works even when Settings does not
- [x] Crash banner on next launch with a one-tap "Send log"
- [x] OEM overlay-permission check guarded, widget refresh guarded, slider clamped, formatter cached
- [x] Widget face bitmap reduced from 512px to 320px
- [x] Backup now includes timer presets and sleep sessions
- [x] `docs/AUDIT.md` records what was fixed and what is still missing

## Next — alarms

- [ ] **P1 M** Reliability check wizard: exact alarms, notifications, battery optimisation, DND — checked and explained in one screen
- [ ] **P1 M** Alarm history and missed-alarm recovery: record every ring, snooze and miss with the reason
- [x] **Done** Dismiss challenges (missions) — maths, typing, tiles, shapes, find, memory, shake, step
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

## Next — missions and sleep depth

- [ ] **P2 M** Camera missions: QR/barcode scan, take-a-photo, and pose-counted push-ups, squats and jumping jacks
- [ ] **P2 M** Sleep trends: weekly and monthly averages for snoring, movement and screen-off time
- [ ] **P2 S** Snore clip sharing and a "worst night" summary
- [ ] **P3 M** Bedtime reminder that pairs with the sleep recorder

## Next — outside the app window

- [x] **Done** Home-screen widgets: live analog clock, next alarm, alarm list, timer, stopwatch, holidays, presets
- [ ] **P2 M** World clock widget (multi-city)
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
