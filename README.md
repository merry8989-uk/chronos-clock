# Chronos

A full-screen Android clock: exact alarms with holiday skipping, a world clock, a
presets-based timer and a lap stopwatch. Kotlin + Jetpack Compose, no backend, no
account, no ads.

- **Package ID:** `com.osintgram.chronos` (permanent)
- **Min SDK:** 26 · **Target SDK:** 35
- **Public page:** https://merry8989-uk.github.io/chronos-clock/
- **Releases:** https://github.com/merry8989-uk/chronos-clock/releases

## Build

```bash
./gradlew assembleRelease   # signed with the release keystore from env vars
./gradlew assembleDebug     # debug build, no keystore needed
```

Release signing is read from environment variables so no key material is ever in
the repo:

| Variable | Meaning |
| --- | --- |
| `KEYSTORE_PATH` | path to `release.p12` |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | key alias (`release`) |
| `KEY_PASSWORD` | key password |

CI (`.github/workflows/release.yml`) decodes the keystore from `KEYSTORE_BASE64`
plus the three other secrets and publishes the signed APK and AAB on every `v*` tag.

## Layout

```
app/src/main/java/com/osintgram/chronos/
├── ChronosApp.kt            Application: init stores, logging, re-arm alarms
├── MainActivity.kt          Immersive full-screen host
├── alarm/                   AlarmManager scheduling, ringing service, ring screen
├── timer/                   Countdown engine, foreground service, presets
├── stopwatch/               Lap stopwatch with persisted start instant
├── data/                    Alarm model + store, settings, holidays, backup
├── diagnostics/             Rolling log file, .txt/.json/.md/.zip exporter
├── updater/                 GitHub Releases update check and in-app install
└── ui/                      Compose screens, components and theme
```

## Update the public page

`docs/index.html` is the public feature reference. Whenever features change,
update it in the same release — the Pages workflow deploys `docs/` on every push
to `main`.
