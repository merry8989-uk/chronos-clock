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

## Fixed in 5.1.1

- [x] **Root cause of the Settings crash:** a date pattern was formatted from a `LocalTime`, which has no day-of-week field. Now formatted from `LocalDate`, and guarded.
- [x] Same latent bug fixed in the full-screen clock dialog (opened by tapping the logo)

## Fixed in 5.2.0 — audit round

- [x] Timer-finished notification moved to a loud IMPORTANCE_HIGH channel (it was silent)
- [x] Step mission now requests ACTIVITY_RECOGNITION when it appears
- [x] FileJanitor removes orphaned wallpapers and snore clips on launch
- [x] Alarm ids are timestamp-based and never reused
- [x] Back on the ring screen snoozes instead of leaving it ringing
- [x] Non-null assertions removed, widget refresh guarded

## Changed in 6.0.0

- [x] **Removed** the entire sleep tracker: SleepService, SleepStore, the Sleep tab, snore clips, sleep settings, sleep backup entries and the microphone foreground service
- [x] **Added bedtime mode:** scheduled bedtime and wake-up alarms, reminder notification, optional Do Not Disturb with permission shortcut, and a dark nightstand screen with the next alarm and a dim toggle
- [x] Bedtime re-armed on boot, clock change and timezone change

## Shipped in 6.1.0 — wellness

- [x] New **Wellness** tab with five sections: Today, Workout, Breathe, Progress, Learn
- [x] Today: steps against a goal ring, water in 250/500 ml taps, sleep in 30-minute steps, five tickable habits, one-tap workout and breathe
- [x] Step count from the device step counter, with a per-day baseline so a reboot moves the baseline instead of the number, and ±500 manual correction when there is no sensor
- [x] Workout: four routines with sets, reps and form cues; a session is persisted the moment it starts; log a set in one tap or add a custom exercise with sets, reps and weight; duration and volume are computed from what was logged
- [x] Breathe: four patterns (calm 4–6, box, 4–7–8, physiological sigh) driving the circle and the cue, 3–20 minute sessions, bell from the bundled tones
- [x] Progress: seven-day totals, day-by-day step chart, and a streak that counts only days with something logged
- [x] Learn: eight pose references drawn in the app, plus follow-along suggestions that open a search on the service named beside them
- [x] Optional Health Connect sync for steps, water and sleep; a sync only raises a value and never overwrites a manual entry
- [x] Wellness goals and bell settings, wellness records in the backup file and in factory reset
- [x] Unit tests for the step baseline, week totals, streak rule and JSON round-trip

## Shipped in 6.2.0 — deeper health data

- [x] Heart-rate card on Today: reads the phone's own sensor while the card is on screen, shows bpm, band and the time of the last reading, and stops after 30 seconds
- [x] `BODY_SENSORS` requested at the moment the user taps Allow, never at launch; the background body-sensor permission is deliberately not requested
- [x] Background sync: a WorkManager job every six hours folds today's steps, water and sleep in while the app is closed, needs the Health Connect background read permission, skips itself when the permission is missing, and is cancelled when the sync setting is turned off
- [x] History import: one tap pulls the last 84 days, which needs the Health Connect history permission (past the default 30-day window)
- [x] A sync only raises a value; a hand-typed number is never lowered
- [x] Unit tests for the merge rule, the heart-rate bands and the JSON round-trip with heart rate

## Fixed in 6.2.1 — in-app update

- [x] **Bug:** a failed update check was reported as "You're on the latest version", because every exception was turned into `null`. The check now returns a result, names the cause (no connection, timeout, GitHub rate limit, HTTP error, release without an APK) and logs it
- [x] **Bug:** the updater never checked whether Android allows Chronos to install packages, so on a device where that switch was off the installer opened and nothing happened. Settings → About now shows "Allow app installs" and opens the right system screen
- [x] **Bug:** a proxy or Wi-Fi login page served as `200` was written to disk as an `.apk` and handed to the installer. The download is now checked for the zip header and for the size GitHub reports
- [x] Redirects are followed by hand, so a refused hop is reported instead of silently producing a truncated file
- [x] Unit tests for the version comparison and the APK check

## Shipped in 6.3.0 — tasks, calendar and special access

- [x] Life hub: one bottom-bar entry holding Tasks and Wellness, so the bar stays at five items
- [x] Four task levels that read the same list: Today → Week (Monday–Sunday) → Month (whole weeks) → Year (twelve months), each with done/total progress
- [x] Projects: tasks grouped by project with their own progress; deleting a project unlinks rather than deletes its tasks
- [x] Task details: notes, date picker, all-day or 09:00/12:00/18:00, priority, project and repeat (daily/weekly/monthly); completing a repeating task creates the next occurrence
- [x] Reminders through the same exact-alarm path as the clock, with an inexact fallback when the permission is refused
- [x] Calendar view: month grid with tasks and phone-calendar events, day detail, and "add to phone calendar" on a task
- [x] Calendar read/write permissions, requested in context and reported when refused
- [x] Special access screen: every switch from Android's special-access list with its real state and a deep link
- [x] Tasks and projects in the backup file and in factory reset
- [x] Unit tests for the week/month/year maths, rollups, repeats and the JSON round-trip

## Fixed in 6.3.1 — update progress

- [x] **Bug:** the update row said "Downloading update…" with no progress at all, because the download ran as a single blocking copy. It now reads the file in chunks and reports bytes written, and the row shows a real progress bar with a percentage and "x of y MB"
- [x] Progress is throttled to about a hundred updates per download (1% or 256 KB), so a 14 MB APK drives a smooth bar without a state write per chunk
- [x] A late progress update can no longer overwrite the finished or failed state
- [x] Unit tests for the copy loop: monotonic, ends at the full size, throttled, and still reports when the total size is unknown

## Fixed in 6.4.0 — Health Connect connect flow

- [x] **Bug:** connecting asked for the optional background and history permissions in the same request as the record reads. A Health Connect build that does not know those strings rejects the whole request, so the button appeared to do nothing. The full set is tried first and the request is retried with the required set alone
- [x] **Bug:** the manifest was missing the `androidx.health.ACTION_HEALTH_CONNECT_SETTINGS` intent query, which Health Connect needs on Android 14+ to be visible at all
- [x] Health Connect now asks for **read and write** together: `WRITE_HYDRATION` and `WRITE_EXERCISE`
- [x] A glass of water logged in Chronos is written back as a hydration record; a finished workout is written as an exercise session
- [x] "Connected" now means read and write are both granted, and the card says which one is missing
- [x] **Bug:** the Special access rows kept showing the old state after the user changed a switch in system settings — the refresh counter was never read, so nothing recomposed. Rows now refresh when the app returns to the foreground

## Shipped in 6.5.0 — checklists in tasks

- [x] A task can carry a checklist: add a step, tick it (struck through), remove it
- [x] The task row shows "2/4 steps" when a checklist exists; tapping the task opens it
- [x] Steps can be added while creating a task, or later from the task itself
- [x] Checklists are stored with the task, so they survive backup, restore and factory reset
- [x] Older records with no checklist still load unchanged
- [x] Unit tests for ticking, adding, removing, the progress count and the JSON round-trip

## Fixed in 6.5.1 — Health Connect crash

- [x] **Bug:** opening the Wellness tab or tapping Connect could crash the app. Every Health Connect entry point now fails soft: the status check, the client creation, the permission read, the reads and both writes are all wrapped, and a failure becomes a message on the card plus a line in Diagnostics
- [x] Permission names are built lazily and guarded — a client older than the one the app was compiled against used to throw while the object initialised, which took the whole app down on first use
- [x] An empty permission set can no longer count as "connected"
- [x] Connect says "This Health Connect build is too old for Chronos — update Health Connect" instead of failing silently when the permission names cannot be resolved

## Fixed in 6.5.2 — About & updates shortcut

- [x] **Bug:** "About & updates" in the ⋮ menu opened Settings at the very top, so the update row was several screens down. It now opens Settings already scrolled to the About card; the plain "Settings" entry still opens at the top

## Shipped in 6.6.0 — realtime steps

- [x] **Bug:** today's steps only counted from the moment the app was opened, because the first reading of the day became the baseline and everything walked before that was invisible
- [x] The baseline is now resolved properly: a stored baseline is used; a phone that rebooted after midnight counts every step since boot; otherwise the card says plainly that the count started when Chronos was opened
- [x] The listener runs at the sensor's UI rate while the card is on screen, and the value is flushed when the card closes so no steps are lost
- [x] A six-hourly worker records the day's baseline even when the app is never opened, so the next day starts at zero
- [x] With Health Connect connected, today's totals are re-read every ten seconds while the card is open — realtime, not on a button press
- [x] Unit tests for the baseline resolution (stored / rebooted / started now) and the step delta

## Shipped in 6.7.0 — universal logging

- [x] Rolling log: three files of 2 MB each, ISO-8601 timestamps with milliseconds and the device offset, a session id per launch, thread-safe writes
- [x] Masking on the way in: passwords, tokens, authorization headers, GitHub tokens, JWTs, long hex keys, emails, Indian phone numbers and card-like numbers become `[redacted]` before anything is written
- [x] Crashes write the full stack trace, not just the exception's message
- [x] Platform buffer: `logcat -d` for this app's own process, with a switch and a "Capture now" action
- [x] Activity lifecycle: created / started / resumed / paused / stopped / destroyed, including whether an activity was recreated
- [x] Breadcrumbs: tab switches and the actions that matter, in order
- [x] Network: every request with URL, status, bytes and duration
- [x] Device snapshot at launch and at export: Android version, model, ABI, battery, charging, memory, low-memory, network, storage
- [x] Exports (.txt / .md / .json / .zip) carry the device and session header
- [x] Unit tests for the masking rules

## Fixed in 6.7.1 — what the first real log showed

- [x] **Bug:** 96 update-check failures in 67 seconds filled a third of the log. The check is now throttled to one per two seconds, and a repeated identical failure is written once and then counted instead of repeated
- [x] **Bug:** masking redacted epoch timestamps, so `Alarm 1 scheduled for 1794303000000` lost its only useful value. Card numbers are now masked only when they pass the Luhn check
- [x] Alarm lines carry the ISO time next to the epoch
- [x] Step readings are logged (first reading, then every 250 steps) — the log had no step values at all, so a "steps are wrong" report could not be checked
- [x] The Health Connect card logs its status when shown, so "cannot connect" is visible without a tap

## Shipped in 6.8.0 — screen time

- [x] Screen time area in the Life hub: total time, unlocks, screen-on count and apps used, from Android's own usage database
- [x] Most-used apps with their share of the day, plus a full per-app list with time and open counts
- [x] Notifications per app per day, from a notification listener that stores **only counts** — never a title, text or sender — for 14 days
- [x] Daily limits (15–120 min) per app, enforced by a foreground watcher that polls the foreground app and shows a full-screen blocker with "Go home" / "5 more minutes"
- [x] Usage access and Notification access are now used for real, and the Special access screen says so
- [x] Without the grants the tab explains what is missing and opens the right system screen — no fake zeros
- [x] Unit tests for the duration formatting, limit maths, ordering and share-of-day

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

## Next — wellness depth

- [ ] **P2 M** Write workouts to Health Connect as an exercise session, not just read
- [ ] **P3 S** Heart-rate trend across the imported history (needs per-reading storage, not one value a day)
- [ ] **P2 M** Reminders for water, a walk and bedtime breathing
- [ ] **P2 S** Body measurements (weight, waist) with a trend line
- [ ] **P3 M** Guided audio meditation — needs recorded voice content, so it is not bundled today
- [ ] **P3 S** Wellness widget: today's steps and water on the home screen

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
