# Dayflow

Dayflow is an Android day planner built with Kotlin and Jetpack Compose. It combines a Room-backed schedule, recurring routines, focus sessions, progress summaries, and an optional voice-enabled assistant called Lumi.

## What it does

- **Plan your day and week.** Create and edit dated or recurring tasks, set priorities and durations, review schedule conflicts, and track completion. Task data and occurrence status are stored locally with Room.
- **Build routines.** Save reusable task templates and apply them to a date. Dayflow checks for schedule conflicts and avoids adding duplicate occurrences.
- **Focus with a timer.** Start, pause, resume, or end a focus session. Session history is persisted and recovered after process restarts. Completing a full session can complete its linked task.
- **Use an optional focus guard.** If you enable Dayflow in Android Accessibility settings and choose apps, the guard can send those apps away while a focus session runs. This is a user-controlled interruption aid, not a device lock; Android or the selected apps may bypass it.
- **Review progress.** See completion, focus time, streaks, schedule allocation, and period comparisons derived from saved activity.
- **Ask Lumi for help.** Use Android speech recognition, download an optional on-device neural voice, or configure an OpenAI-compatible AI gateway in Settings. Gateway credentials are stored encrypted on the device. Lumi can answer and work with supported Dayflow task and routine operations.
- **Get reminders.** Opt in to notifications for task reminders and completed focus sessions. Android may delay alarms under battery restrictions, and notifications require OS permission.

The app includes dark and light themes, 12/24-hour time, week-start preferences, and a searchable launchable-app list for focus guard setup.

## Requirements

- Android Studio with Android SDK Platform 35
- JDK 21 (the project compiles Kotlin and Java to JVM 17 bytecode)
- Android Gradle Plugin 8.7.3 and Gradle 8.11.1, provided by the Gradle wrapper
- Android 8.0 (API 26) or newer

## Build and run

Clone the repository, then open it in Android Studio and let Gradle sync. Or build a debug APK from the command line:

```sh
./gradlew :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. To install it on a connected device with USB debugging enabled:

```sh
./gradlew :app:installDebug
```

Run the JVM unit tests with:

```sh
./gradlew :app:testDebugUnitTest
```

## First run and optional setup

Dayflow starts with an empty schedule. Add tasks to populate Today, Week, and Progress.

- **Notifications:** Allow notification permission in Android when prompted and enable reminders in the app settings.
- **Focus guard:** Choose apps in Focus settings, then enable **Dayflow Focus Guard** under Android Settings → Accessibility. Android may require you to allow restricted settings for a sideloaded build.
- **Lumi gateway:** In Settings, provide your gateway's OpenAI-compatible base URL and credentials. Use HTTPS, or a private/local network URL. A gateway is optional; supported task tools require a working configured service.
- **Lumi natural voice:** The on-device voice is optional and downloaded to app storage. The model download is separate from the APK and can be large (about 63 MB for Piper or 320 MB for Kokoro).

## Project layout

```text
.
├── app/
│   └── src/
│       ├── main/
│       │   ├── java/com/dayflow/
│       │   │   ├── data/       # Repositories, assistant, voice, and app settings
│       │   │   ├── domain/     # Task, recurrence, focus, and schedule rules
│       │   │   ├── ui/         # Compose screens and view models
│       │   │   └── ...         # Application, activity, services, and data layer
│       │   ├── res/            # Android resources, fonts, and notification assets
│       │   └── AndroidManifest.xml
│       └── test/               # JVM unit tests
├── gradle/                     # Wrapper and wrapper configuration
├── stitch_dayflow_focus_schedule_planner/  # Original UI reference screens
├── ARCHITECTURE.md             # Data and application design notes
└── IMPLEMENTATION_CHECKLIST.md # Feature audit and known verification limits
```

## Design references

The Compose UI was based on the Dayflow: Focus & Schedule Planner Stitch project. The source screens are included under [`stitch_dayflow_focus_schedule_planner/`](stitch_dayflow_focus_schedule_planner/). The implementation uses a charcoal interface with lavender focus accents, mint completion accents, and the bundled Inter font.

## Current limitations

- The focus guard depends on the user enabling an Accessibility service. It cannot guarantee that another app stays closed, and Android can stop or delay background work.
- Task and focus alarms use inexact idle-allowed alarms. Battery policies can delay them.
- Neural voice models require a separate download; native synthesis has not been confirmed on every device and ABI.
- Automated coverage currently focuses on JVM domain and repository behavior. Room migration, UI, and broader Android integration tests remain incomplete; see [`IMPLEMENTATION_CHECKLIST.md`](IMPLEMENTATION_CHECKLIST.md) for the audit.

## License

No license file is currently included. All rights reserved unless the repository owner adds a license.
