# Tide

A private, offline-first life dashboard for Android. Kotlin, Jetpack Compose, Room. No
account, no server, no telemetry. Repo: https://github.com/SeventhSG/tide

Read this first, then `docs/roadmap.md` for what is done and what is next.

## Things that will trip you up

**The folder is called `MyLife`, the app is called Tide.** The project was MyLife, then Keel,
then Tide. The on-disk rename is still pending because Windows will not rename a directory a
running process is sitting in. Do not assume the repo is missing because there is no `tide`
folder. Offer the rename at the start of a session, before working in it, and note that it
changes the session's project key.

**There is no usable emulator.** This machine has no hypervisor driver and installing one
needs admin, which is not available. `-accel off` does not work either: the emulator process
starts, burns zero CPU and never boots. **Screenshots come from Roborazzi on the JVM.** Do
not try to start an emulator.

**The toolchain is user-local and not on PATH.** Every Gradle command needs:

```powershell
$env:JAVA_HOME="C:\Users\gamin\dev\jdk17"
$env:ANDROID_HOME="C:\Users\gamin\dev\android-sdk"
.\gradlew.bat <task>
```

See `docs/building.md`.

## Before every commit

```
bash tools/gate.sh
```

It must pass. It checks: zero en dash and em dash in tracked text, no hex colour literals
outside `:core:design`, no `NotificationManager` use outside `:core:notify`, no assistant
attribution. It exits non-zero and blocks.

## Writing rules

These are not stylistic preferences, they are enforced.

- **No em dashes or en dashes.** Anywhere on screen, in code, in comments, in commit
  messages. Use a hyphen, a comma, a period, or rewrite the sentence.
- **Never mention the assistant** in commits, code or docs. No `Co-Authored-By: Claude`. The
  signature on this work is **SeventhSG**, https://github.com/SeventhSG
- Everything user facing ships in **English**.
- **No invented precision.** A number is real, labelled as sample data, or absent.
- **No streaks, no flames, no guilt.** A missed day is a fact and the copy states it as one.
  Nothing in this app scolds.

## Architecture

```
:app                 navigation host, screens, theme application
:core:design         tokens, type, shape, motion, ocean, nav, buttons
:core:data           Room, the progression engine, the importer, the repositories
:core:schedule       recurrence, occurrences, the reconciler. Pure Kotlin, no Android
:core:notify         tiers, channels, quiet hours, digest, ledger
```

Planned and not yet built: `:feature:*`.

**`:core:schedule` is pure Kotlin on purpose.** Recurrence is date arithmetic, so it has no
Android in it and its tests run in milliseconds without Robolectric. Anything needing a
database goes in `:core:data`, which owns the one table set.

**The database is at version 3**, with hand-written migrations that are tested against real
version 1 and 2 databases with data in them. Add a table, add a migration, and add it to
`MigrationTest` before committing.

**No DI framework.** `Tide` in `:core:data` is the object graph, by hand. One database, one
repository, one process. If it grows past a handful of dependencies it should become Hilt.

**Room has no destructive migration fallback, deliberately.** This database is the only copy
of the data and there is no server to restore from. A missing migration must fail loudly
rather than quietly wipe a year of training.

## Design rules that are easy to break

Full detail in `docs/design-system.md`. The ones most often violated:

- **One accent, whole app, no user picker.** Hex literals live in `:core:design` and nowhere
  else; the gate enforces it.
- **The ocean is never behind a number.** Anything carrying text sits on a scrim, and
  contrast is measured against the scrim, not the water.
- **The ocean is off entirely on the session logger.** Mid set you do not need bubbles. This
  is a decision, not an omission.
- **Corners are superellipse, not circular.** Use `ContinuousCornerShape`, never
  `RoundedCornerShape`. Material's `Shapes` requires a `CornerBasedShape`, which is why it
  extends that rather than plain `Shape`.
- **Motion is gated by frequency.** Today is opened twenty-plus times a day and animates
  nothing on entry. The logger animates nothing but the press. The nav's bubbles are the one
  ambient loop in the app.
- **Warm-ups never count** toward progression, 1RM or fatigue. This filter lives in SQL, not
  Kotlin, because every caller wants it and the one that forgets corrupts a decision
  silently.

## Reference, not dependency

The training module's design was informed by [openGym](https://gitlab.com/DuarteSantos8/opengym),
which is **AGPL-3.0**. Its behaviour was studied and reimplemented. **No openGym code is
used here**, and none may be: Tide is Apache-2.0 and copying would force a licence change.

**Exercise data and images need a verified licence.** Both are wanted, the full library and an
image per exercise, but nothing goes in until the licence has been read in the source's own
licence file and it permits redistribution in an Apache-2.0 app.

- **openGym's media is out.** Its own notice records that the animation rights are
  unresolved. Images being widely used shows they are popular, not that they are licensed.
- **"ExerciseDB" is not one thing.** Several datasets share the name and not all are open;
  one is a commercial API. Check the specific repository, not the name.
- **Bundled, never hotlinked.** Fetching from a third-party CDN breaks offline use and tells
  someone else which exercises you look at.
- If no source clears the check, ship metadata only and leave images for later.
