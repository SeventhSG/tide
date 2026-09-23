<div align="center">

<img src="brand/tide-192.png" alt="Tide" width="128" />

# Tide

**A private, offline-first life dashboard for Android.**

Training that decides what you lift today. Body, read from your own watch. No account, no server, nothing sent anywhere.

<br>

[![Release](https://img.shields.io/github/v/release/SeventhSG/tide?style=flat-square&color=2ED3B0&labelColor=0B1D25)](https://github.com/SeventhSG/tide/releases/latest)
[![Licence](https://img.shields.io/github/license/SeventhSG/tide?style=flat-square&color=2ED3B0&labelColor=0B1D25)](LICENSE)
[![Last commit](https://img.shields.io/github/last-commit/SeventhSG/tide?style=flat-square&color=2ED3B0&labelColor=0B1D25)](https://github.com/SeventhSG/tide/commits/main)
[![Commits](https://img.shields.io/github/commit-activity/t/SeventhSG/tide?style=flat-square&color=2ED3B0&labelColor=0B1D25&label=commits)](https://github.com/SeventhSG/tide/commits/main)
[![Code size](https://img.shields.io/github/languages/code-size/SeventhSG/tide?style=flat-square&color=2ED3B0&labelColor=0B1D25)](https://github.com/SeventhSG/tide)
[![Top language](https://img.shields.io/github/languages/top/SeventhSG/tide?style=flat-square&color=2ED3B0&labelColor=0B1D25)](https://github.com/SeventhSG/tide)
[![Stars](https://img.shields.io/github/stars/SeventhSG/tide?style=flat-square&color=2ED3B0&labelColor=0B1D25)](https://github.com/SeventhSG/tide/stargazers)

**306 tests green · minSdk 26 · Kotlin, Compose, Room · nothing in here phones home**

</div>

---

## Where it stands

**v0.1.3.** Training is real and usable: log a session, finish it, and the progression engine
decides next week's numbers, over a **1,311-exercise library** rather than a starter set.
Around it there is a week planner, a history calendar, a muscle map, importers, a quiet daily
summary with a sleep guard, a first-run onboarding screen, and a Body section reading Health
Connect. Ask can answer real questions about your training straight from the database.

**It has never run on a physical phone.** The machine it is built on has no usable emulator,
so every screen is verified by rendering it on the JVM and every rule by test. That is a real
gap, stated here rather than discovered on install.

| | |
|---|---|
| Builds | `app-debug.apk`, 13 MB, minSdk 26, targetSdk 35 |
| Tests | 306 green: progression, schedule, notify, importers, migrations, every viewmodel |
| Sections | Today, Train, Body, Money (empty on purpose), Ask |
| Not yet | Real generation in Ask, Money, retroactive logging, a licensed image library |

---

## What it does

### Training

- **A progression engine, not a notebook.** Five rules (linear, Greyskull LP, double
  progression, timed, bodyweight) decide what to lift today from what you lifted last time.
  Finishing a session runs it and says what it decided, in plain words, with next time's
  target.
- **A 1,311-exercise library**, MIT-licensed metadata bundled as an asset: name, muscles,
  equipment and step-by-step instructions. Its own images are not in here; see Licences.
- **Warm-ups never count** toward progression, an estimated 1RM, the muscle map or volume.
  The filter lives in SQL, so no caller can forget it.
- **A week you arrange.** Each day holds exercises in the order you will do them, and a line
  moves up, down, or into the day either side. The plan becomes a schedule rule, which is
  what gives the daily summary something true to say.
- **A history calendar**: the days you trained, what was on them, and nothing at all about
  the days you did not.
- **A muscle map**: balance, fatigue and strength on three tabs, never rolled into one
  recovery score. Fatigue is drawn as a bar and never printed as a number, because it is an
  index out of a model rather than a measurement.
- **Imports** from FitNotes and Strong, with a preview before anything is written, and no
  double import of the same file.

### Body

Steps, sleep, heart rate and weight from **Health Connect**, read-only. Tide measures none of
it and writes nothing back. Four honest states: not installed, not allowed, allowed and
empty, or readings.

### Notifications

One quiet summary a day, **off until you ask for it**, silent when on, and silent again when
nothing is outstanding. Quiet hours are yours, and only something with a real deadline may
break them. There are no engagement notifications, nothing about a missed day, and there
never will be.

### Ask

Answers real questions straight from your training data today, no model required: sessions
this week, volume, muscle balance, or the last time you did a named lift. A model installs
onto the phone separately, with progress, resumable and removable, but **nothing generates a
reply from it yet**. Real on-device generation turned out to need either a native library
(llama.cpp, hand rolled by every project that does this, nothing published) or a model gated
behind a licence (every capable MediaPipe-format model is Google's own Gemma). Building either
blind, with no device to load the result on, is how a native library ends up crashing on the
first phone that finally runs it, so the screen says exactly that instead of guessing.

### Onboarding, and a session that does not go missing

A first-run screen asks for notifications and Health Connect together, once, before Today.
Both stay skippable, and granting either opts into nothing else: the daily summary still
waits for its own switch in Settings. A session schedules one check two hours after it opens;
if it is somehow still the same session and still open, one notification asks whether
training is still going, since every set was already safe on disk regardless and the actual
risk was a forgotten Finish, not lost data.

---

## Principles it is built to

Most of these are enforced by a pre-ship gate rather than merely believed.

- **A number is real, labelled as sample data, or absent.** A week with no training shows no
  volume rather than `0 kg`.
- **No streaks, no flames, no guilt.** A missed day is a fact, and the copy states it as one.
- **One accent colour, whole app.** Hex literals live in `:core:design` and nowhere else.
- **Motion is gated by frequency.** A hop between sections gets a wave; a screen opened
  twenty times a day animates nothing on entry; everything collapses under reduced motion.
- **The data never leaves the phone.** The only network call in the app is the model
  download, and it happens because someone pressed a button.

---

## Building

The toolchain is user-local, so every Gradle command needs:

```powershell
$env:JAVA_HOME="C:\Users\<you>\dev\jdk17"
$env:ANDROID_HOME="C:\Users\<you>\dev\android-sdk"
.\gradlew.bat assembleDebug
```

| Task | What it does |
|---|---|
| `.\gradlew.bat testDebugUnitTest` | The whole suite |
| `.\gradlew.bat :app:recordRoborazziDebug` | Screenshots, on the JVM, no device |
| `bash tools/gate.sh` | The pre-ship gate. It must pass before a commit |

See [docs/building.md](docs/building.md). Screenshots come from Roborazzi because this
machine has no hypervisor driver and the emulator cannot boot.

---

## Architecture

```
:app                 navigation, screens, viewmodels, the model installer, the sea
:core:design         tokens, type, shape, motion, the ocean, nav, buttons, exercise art
:core:data           Room, the progression engine, importers, repositories
:core:schedule       recurrence, occurrences, the reconciler. Pure Kotlin, no Android
:core:notify         tiers, channels, quiet hours, the digest, the ledger
```

`:core:schedule` is pure Kotlin on purpose: recurrence is date arithmetic, so its tests run
in milliseconds without Robolectric. The database is at version 3 with hand-written
migrations tested against real version 1 and 2 databases, and there is **no destructive
migration fallback**: this database is the only copy of the data, so a missing migration must
fail loudly rather than quietly wipe a year of training.

There is **no DI framework**. `Tide` in `:core:data` is the object graph, written by hand.

Stack: Kotlin, Jetpack Compose, Material 3, Room, WorkManager, Health Connect.

---

## What it will not do

- No bank balances, no transaction import, no budgeting.
- No sync, no account, no server, no web app.
- No writing to Health Connect.
- No telemetry, ever.

---

## Licences, and what is deliberately not in here

Tide is **Apache-2.0**. See [LICENSE](LICENSE).

- The training module's design was informed by
  [openGym](https://gitlab.com/DuarteSantos8/opengym) by Duarte Santos, in particular its
  progression rules, set taxonomy and three-mode muscle map. openGym is AGPL-3.0 and **no
  openGym code is used here**: the behaviour was studied and reimplemented. It is a good
  piece of software and worth self-hosting if a web app suits you better.
- The exercise **metadata** (names, muscles, equipment, instructions) is the MIT half of
  [hasaneyldrm/exercises-dataset](https://github.com/hasaneyldrm/exercises-dataset), bundled
  as an asset. Its **media is not**: that repository's own `NOTICE.md` records the thumbnails
  and GIFs as © Gym visual, redistributed to it alone under a permission that does not extend
  further, so none of it is here.
- **Exercise images are drawn in this repository instead**, not licensed from anyone. No
  photo or animation set considered so far has cleared the check that its own licence permits
  redistribution in an Apache-2.0 app, so the app draws a mark per equipment type on a tile
  tinted by muscle. When a set does clear the check, it replaces them.
- **The ocean sound is generated**, not sampled: filtered noise under a slow swell, so no
  audio file ships and it never repeats.
- The model offered in Ask is **Qwen2.5 0.5B Instruct** (Apache-2.0), downloaded on request.

---

<div align="center">

The mark is one tidal cycle drawn against a chart datum, which is how a tide is plotted and
how this app reads a life: the line matters less than where it sits relative to the level.

Built by [SeventhSG](https://github.com/SeventhSG). No analytics, no crash reporting, no account.

</div>
