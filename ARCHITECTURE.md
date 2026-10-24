# Dayflow Android Architecture Overview

## 📱 Project Summary

**Dayflow** is a focus & schedule planner Android app built with Jetpack Compose, implementing a clean architecture with Room persistence. The app helps users manage tasks with sophisticated recurrence patterns, track completion history, and maintain habit streaks.

---

## 🏗️ Architecture Layers

```
┌─────────────────────────────────────────────────────────────────┐
│                        PRESENTATION LAYER                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │   Compose    │  │  ViewModels  │  │    NavHost   │          │
│  │  UI Screens  │──│   + State    │──│  Navigation  │          │
│  │   (8 files)  │  │  (4 files)   │  │ (MainActivity)│          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                         DOMAIN LAYER                             │
│  Pure Kotlin • No Android Dependencies • 100% Unit Testable     │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │Task Models   │  │  Business    │  │  Utilities   │          │
│  │• Task        │  │  Logic       │  │• WeekUtils   │          │
│  │• Recurrence  │  │• Recurrence  │  │• Validation  │          │
│  │• Occurrence  │  │• Overlap     │  │• Stats       │          │
│  │• Completion  │  │• Focus Timer │  │              │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                          DATA LAYER                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │  Repository  │  │     Room     │  │ Preferences  │          │
│  │  Interface   │──│  • Entities  │  │ • Settings   │          │
│  │  + Room Impl │  │  • DAOs      │  │ • Onboard    │          │
│  │              │  │  • Database  │  │              │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      SYSTEM / SERVICES                           │
│  • Notification Scheduling (ReminderCoordinator)                │
│  • Accessibility Service (FocusGuard - app blocking)            │
│  • Application Class (DI via lazy properties)                   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 📂 File Structure Map

```
app/src/main/java/com/dayflow/
│
├── MainActivity.kt                    # Navigation host + theme setup
├── DayflowApplication.kt             # App entry + dependency graph
│
├── domain/                            # ✅ Pure Kotlin, zero Android deps
│   ├── TaskModels.kt                 # Task, TaskType, Priority, Recurrence
│   ├── RecurrenceEngine.kt           # Occurrence math (daily/weekdays/custom)
│   ├── WeekUtils.kt                  # Date boundaries + formatting
│   ├── OverlapDetector.kt            # Schedule conflict detection
│   ├── TaskValidator.kt              # Input validation rules
│   ├── ScheduleStats.kt              # Completion %, streaks, aggregation
│   └── FocusTimerMath.kt             # Pomodoro timer calculations
│
├── data/
│   ├── TaskRepository.kt             # Interface + RoomTaskRepository impl
│   ├── FocusSessionRepository.kt     # Focus timer history
│   ├── RoutineRepository.kt          # Saved routine templates
│   ├── FocusProfileRepository.kt     # Pomodoro/Deep work/Flow profiles
│   ├── AppPreferences.kt             # Settings (24h time, theme, first day)
│   ├── TaskMappers.kt                # Entity ↔ Domain conversion
│   │
│   └── local/
│       ├── DayflowDatabase.kt        # Room DB + migrations (v1→v7)
│       ├── TaskEntity.kt             # Tasks + TaskOccurrenceEntity
│       ├── Daos.kt                   # TaskDao, TaskOccurrenceDao, etc.
│       ├── FocusSessionEntity.kt     # Focus session records
│       ├── RoutineEntity.kt          # Routine templates
│       ├── FocusProfileEntity.kt     # Focus/break duration profiles
│       └── AiEntities.kt             # (Future AI features)
│
├── ui/
│   ├── Repository.kt                 # ViewModel factory helper
│   ├── TodayViewModel.kt             # Today screen state + actions
│   ├── WeekViewModel.kt              # Week planner state
│   ├── EditorViewModel.kt            # Task create/edit with validation
│   ├── ProgressViewModel.kt          # Stats, streaks, heatmap
│   │
│   ├── Ui.kt                         # Shared components (TaskCard, etc.)
│   ├── TodayScreen.kt                # Today dashboard
│   ├── WeekScreen.kt                 # 7-day week view
│   ├── EditorScreen.kt               # Task editor with date/time pickers
│   ├── ProgressScreen.kt             # Progress + consistency tracking
│   ├── TaskDetailsSheet.kt           # Bottom sheet (complete/edit/delete)
│   ├── StaticScreens.kt              # Focus, Actions, Setup screens
│   ├── RoutinesScreen.kt             # Saved routines
│   ├── SanctuaryScreen.kt            # Daily ritual canvas
│   ├── SettingsScreen.kt             # App settings
│   └── ArchivedTasksScreen.kt        # Archived task list
│
├── notifications/
│   └── ReminderNotifications.kt      # Task reminder scheduling
│
└── blocking/
    ├── BlockedAppPreferences.kt      # Blocked app list storage
    └── FocusGuardService.kt          # Accessibility service for blocking

app/src/test/java/com/dayflow/
├── RecurrenceEngineTest.kt           # 40 passing unit tests across:
├── WeekUtilsTest.kt                  # • Recurrence logic
├── OverlapDetectorTest.kt            # • Date/week boundaries
├── TaskValidatorTest.kt              # • Overlap detection
├── TaskRepositoryTest.kt             # • Validation rules
├── ScheduleStatsTest.kt              # • Completion stats
├── FocusTimerMathTest.kt             # • Focus timer math
└── InMemoryTaskRepository.kt         # Test fake repository
```

---

## 🔄 Data Flow Diagram

### Creating a Task

```
┌─────────────┐
│ EditorScreen│ User fills form
└──────┬──────┘
       │ onTitle(), onDate(), onFrequency()
       ▼
┌──────────────────┐
│ EditorViewModel  │ Draft state + validation
└──────┬───────────┘
       │ save() → TaskValidator.validate()
       ▼
┌──────────────────┐
│  TaskRepository  │ saveTask(task)
└──────┬───────────┘
       │ toEntity() + Room transaction
       ▼
┌──────────────────┐
│   TaskDao.insert │ Room persists to SQLite
└──────┬───────────┘
       │ Flow emission
       ▼
┌──────────────────┐
│ TodayViewModel   │ observeTasks() + combine(completions)
│ WeekViewModel    │ → buildState() → StateFlow update
└──────┬───────────┘
       │ collectAsStateWithLifecycle()
       ▼
┌──────────────────┐
│ TodayScreen      │ UI recomposes with new task
│ WeekScreen       │
└──────────────────┘
```

### Completing a Task Occurrence

```
User taps checkbox → ViewModel.toggleCompletion(occurrence)
                   ↓
            repository.setCompleted(taskId, dateEpochDay, true)
                   ↓
         TaskOccurrenceEntity upserted (taskId, date) = COMPLETED
                   ↓
      occurrenceDao.observeAll() emits updated list
                   ↓
  ViewModel combine(tasks, completions) → ScheduleStats.summarize()
                   ↓
         UI shows updated completion ring + N of M count
```

---

## 🗄️ Database Schema (Room v7)

### `tasks` table
```sql
CREATE TABLE tasks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    description TEXT,
    type TEXT,                          -- REGULAR, DEEP_WORK, HABIT, etc.
    category TEXT,
    startEpochDay INTEGER,              -- LocalDate as epoch days
    startTimeMinutes INTEGER,           -- Minutes from midnight (0-1439)
    durationMinutes INTEGER,
    priority TEXT,
    destinationUrl TEXT,
    focusProfile TEXT,
    recurrenceFrequency TEXT,           -- NONE, DAILY, WEEKDAYS, SELECTED_DAYS
    recurrenceDaysMask INTEGER,         -- Bitmask: bit 0=Mon, bit 6=Sun
    recurrenceEndEpochDay INTEGER,
    createdAtMillis INTEGER,
    modifiedAtMillis INTEGER,
    archivedAtMillis INTEGER            -- NULL = active, timestamp = archived
);
```

### `task_occurrences` table (completion history)
```sql
CREATE TABLE task_occurrences (
    taskId INTEGER,
    dateEpochDay INTEGER,
    completed BOOLEAN,
    completedAtMillis INTEGER,
    status TEXT DEFAULT 'PLANNED',      -- PLANNED, COMPLETED, SKIPPED
    PRIMARY KEY (taskId, dateEpochDay)
);
CREATE INDEX idx_date ON task_occurrences(dateEpochDay);
```

**Key design:** Recurring tasks have **independent completion records** per date. A daily task can be completed Mon/Wed/Fri but not Tue/Thu.

### Other tables
- `focus_sessions` — Pomodoro timer history
- `routines` — Saved routine templates
- `focus_profiles` — Focus/break duration presets (Pomodoro=25/5, Deep work=50/10)

---

## 🧩 Key Design Patterns

### 1. **Recurrence as a Proper Model**
Not a boolean! Supports:
- **ONE_TIME** (single date)
- **DAILY** (every day)
- **WEEKDAYS** (Mon-Fri)
- **WEEKENDS** (Sat-Sun)
- **SELECTED_DAYS** (custom weekday set, e.g., Mon/Wed/Fri)

```kotlin
data class Recurrence(
    val frequency: RecurrenceFrequency,
    val daysOfWeek: Set<DayOfWeek>,
    val endEpochDay: Long?            // Optional end date
)
```

### 2. **Occurrence Materialization**
`RecurrenceEngine` expands abstract tasks into concrete occurrences:

```kotlin
// Task: "DSA Practice" daily 9:00-10:00 Mon-Fri
RecurrenceEngine.occursOn(task, LocalDate.of(2026, 10, 12))  // true (Sunday)
RecurrenceEngine.occurrenceFor(task, date)
  → TaskOccurrence(task, date, startMillis=..., endMillis=...)
```

### 3. **Completion History Per Occurrence**
`TaskOccurrenceEntity` primary key = `(taskId, dateEpochDay)`

- Recurring task ID=5 occurs Mon-Fri
- Complete Mon: insert (5, epochDay_Mon, completed=true)
- Complete Wed: insert (5, epochDay_Wed, completed=true)
- Tue/Thu/Fri remain incomplete (no row = planned)

### 4. **StateFlow + Combine Pattern**
ViewModels expose single observable state combining multiple flows:

```kotlin
val uiState: StateFlow<TodayUiState> = combine(
    repository.observeTasks(),
    repository.observeAllCompletions(),
    _selectedDate
) { tasks, completions, date -> buildState(...) }
    .stateIn(viewModelScope, ...)
```

UI collects: `val state by viewModel.uiState.collectAsStateWithLifecycle()`

### 5. **Validation Before Persistence**
`EditorViewModel.save()`:
1. `TaskValidator.validate(draft)` → List\<ValidationError\>
2. If errors: display field-level messages, return
3. If overlaps detected: show confirmation dialog
4. Only after confirmation: `repository.saveTask(task)`

---

## 🧪 Testing Strategy

### Unit Tests (40 passing, 0 failures)
All **domain logic** is pure Kotlin with zero Android dependencies:

- **RecurrenceEngineTest** — Daily/weekdays/custom recurrence, end dates
- **OverlapDetectorTest** — Schedule conflict detection
- **TaskValidatorTest** — Required fields, URL format, duration limits
- **WeekUtilsTest** — ISO week boundaries, Mon vs Sun first day
- **ScheduleStatsTest** — Completion %, streaks, habit adherence
- **TaskRepositoryTest** — Uses `InMemoryTaskRepository` fake

### Integration Tests (not yet implemented)
- Room DAO queries
- ViewModel state transitions
- End-to-end flows (create → complete → verify)

---

## 🚀 Key Features Implemented

### ✅ Core Scheduling
- [x] Create/edit/delete tasks
- [x] Full recurrence support (daily/weekdays/weekends/custom)
- [x] Per-occurrence completion (recurring tasks track each date independently)
- [x] Overlap detection with confirmation dialog
- [x] Field-level validation (title required, duration 1-1440 min, valid URLs)
- [x] Task archiving (soft delete with archivedAtMillis)

### ✅ Today Screen
- [x] Real date display with day navigation
- [x] Completion ring (N of M tasks)
- [x] Current/next task in focus button
- [x] Habit streak counter
- [x] Overlap warnings
- [x] Per-occurrence checkboxes (complete/reopen)
- [x] Task details bottom sheet (complete/edit/skip/duplicate/delete)

### ✅ Week Screen
- [x] Mon-Sun week strip (configurable first day)
- [x] Per-day completion dots
- [x] Week navigation (prev/next/today)
- [x] Selected day schedule
- [x] Week stats (planned/done/on-track)
- [x] Add task prefilled with selected date

### ✅ Editor Screen
- [x] Create + edit existing tasks
- [x] All 5 task types (Regular, Deep Work, Quick Action, Habit, Break)
- [x] Material DatePicker + TimePicker
- [x] Recurrence UI (radio + custom weekday picker)
- [x] Duration picker (hours + minutes)
- [x] Priority selection
- [x] Destination URL with "Test Link" button
- [x] Field validation messages
- [x] Overlap confirmation dialog
- [x] "Edit this occurrence" flow (creates one-time copy, skips original)

### ✅ Progress Screen
- [x] Period selection (Week/Month/Quarter/Custom)
- [x] Completion % vs previous period
- [x] Deep focus minutes from Pomodoro sessions
- [x] Habit streak days
- [x] 28-day heatmap
- [x] Time allocation by task type
- [x] Habit adherence tracking
- [x] Best day indicator

### ✅ Additional Features
- [x] Focus timer (Pomodoro/Deep work/Flow profiles)
- [x] Routines (saved task templates)
- [x] Sanctuary screen (daily ritual canvas)
- [x] Settings (24h time, light/dark theme, first day of week)
- [x] Archived tasks view
- [x] Reminder notifications (ReminderCoordinator observes tasks/completions)
- [x] App blocking via AccessibilityService (FocusGuardService)

---

## 🎨 Design System

### Color Palette (Dark Theme Default)
```kotlin
Ink       = #121316   // Background
Surface   = #1B1B1F   // Cards
Raised    = #292A2D   // Elevated components
Lavender  = #A78BFA   // Primary accent
Mint      = #73E2B3   // Success/completion
Blue      = #82B7FF   // Links/info
Muted     = #B8B8C0   // Secondary text
```

### Typography
- **Font:** Inter (loaded from `res/font/inter_regular.ttf`)
- **Style:** Compact uppercase metadata, 16-20dp card radii

---

## 🔮 Future Enhancements (Not Yet Implemented)

### High Priority
- [ ] Task reordering / manual sort
- [ ] Task search / filter by type
- [ ] Quick reschedule (drag-and-drop on week view)
- [ ] Task notes / attachments
- [ ] Custom categories beyond task type

### Medium Priority
- [ ] Cloud sync / backup
- [ ] Widgets (today schedule)
- [ ] Wear OS companion
- [ ] Export schedule (PDF/iCal)
- [ ] Collaboration / shared schedules

### Low Priority
- [ ] AI-powered scheduling suggestions
- [ ] Pomodoro auto-start when task begins
- [ ] Focus mode automation (DND + blocking)
- [ ] Integration with Google Calendar

---

## 📊 Code Statistics

```
Total Kotlin files:     49
  - Production:         41
  - Tests:              8

Lines of code (approx):
  - Domain:            ~1,200 LOC (pure, testable)
  - Data:              ~800 LOC (Room + repos)
  - UI:                ~3,500 LOC (Compose screens)
  - Tests:             ~600 LOC

Dependencies:
  - Jetpack Compose (Material3)
  - Room 2.6.1 + KSP
  - Navigation Compose
  - Lifecycle + Coroutines
  - JUnit 4 (testing)
```

---

## 🏃 Running the Project

### Build
```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

### Test
```bash
./gradlew :app:testDebugUnitTest
# Report: app/build/reports/tests/testDebugUnitTest/index.html
```

### Install
```bash
./gradlew :app:installDebug
# Or: adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 🎯 Key Takeaways

1. **Clean Architecture:** Domain layer is 100% pure Kotlin, independently testable
2. **Sophisticated Recurrence:** Not just "repeat daily" — supports weekdays, weekends, custom sets, end dates
3. **Per-Occurrence Completion:** Recurring tasks track each date separately (not just "task is done")
4. **Observable Everything:** ViewModels expose `StateFlow`, UI recomposes automatically
5. **Validation First:** Editor validates before saving, displays field-level errors
6. **Overlap Detection:** Scheduler warns about conflicts, requires confirmation
7. **Full Test Coverage:** 40 unit tests covering all domain logic

The architecture prioritizes **testability** (pure domain), **type safety** (no stringly-typed data), and **reactivity** (Flow-based state). No hardcoded demo data remains — everything is Room-backed and survives app restart.
