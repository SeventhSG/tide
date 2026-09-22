# Keel

A private life dashboard for Android. Training, body, commitments and a small local model,
in one app, on one device.

The data never leaves the phone. There is no account, no server and no sync. The network is
optional: online means a connector refreshes when there is signal. Offline is the normal
case, not the degraded one.

Status: early. Nothing is installable yet.

---

## Why

The information already exists. It is spread across a health app, a notes app, a bank app,
a spreadsheet and four renewal emails, and nothing joins it up. The products that try to join
it up want the data on their servers first.

Keel inverts that. The database is local, the model that reads it runs on the same device,
and the only thing that ever leaves is a backup file you export yourself.

## What it does

- **Schedule.** Keel holds what you intend to do and what actually happened, and most of what
  it is useful for is the gap between the two. Recurring commitments, daily or weekly, with a
  look-ahead the night before, a nudge inside the window and a confirmation after it closes.
  It only asks what it does not already know: if you logged the session, it does not ask
  whether you went. No streaks, no flames, no guilt.
- **Training.** Routines, sessions, sets. Progression rules per exercise, so the prefilled
  numbers are what you should lift today rather than what you lifted last time. Imports from
  FitNotes, Strong and Hevy.
- **Body.** Reads steps, sleep, heart rate, weight and workouts from Health Connect, so
  whatever your watch already writes shows up without logging it twice. Read-only.
- **Commitments.** Subscriptions and recurring charges, with renewal reminders and a monthly
  burn figure. Not a finance app, deliberately.
- **Inbox.** Your own servers push notifications in over a topic you control, each source
  with its own priority and quiet-hours policy, so a failed backup escalates and a finished
  deploy does not.
- **Notifications, as a budget.** This app notifies a lot, which only works if the
  notifications are not all at the same volume. Everything goes through one funnel with four
  tiers, low-value events collapse into a daily digest, and Settings keeps a ledger of what
  fired and what you dismissed so you can turn down whatever you keep ignoring.
- **Ask.** A small model running on the phone, answering questions about your own data. It
  does not read the database directly: it calls a fixed set of SQL-backed tools and phrases
  the result, so every number on screen came out of the database rather than out of the
  model.

## What it will not do

- No bank balances, no transaction import, no budgeting.
- No sync, no account, no server, no web app.
- No writing to Health Connect.
- No telemetry, ever.

## Design

Dark-first, one locked accent, mono numerals throughout, and as few cards as the layout can
get away with. The reference is a ship's instrument panel: legible at arm's length in bad
light, and honest when the reading is boring. Built on Material 3 Expressive rather than a
custom system, because the native one is better than anything worth reinventing here.

See [docs/design-system.md](docs/design-system.md).

## Stack

Kotlin, Jetpack Compose, Material 3 Expressive, Room, WorkManager, Health Connect. The
on-device model runs through llama.cpp via JNI, behind an interface that also accepts the
MediaPipe LLM Inference API.

## Credits

The training module's design was informed by [openGym](https://gitlab.com/DuarteSantos8/opengym)
by Duarte Santos, in particular its progression rules, its set taxonomy and its three-mode
muscle map. openGym is AGPL-3.0 and **no openGym code is used here**; the behaviour was
studied and reimplemented. It is a good piece of software and worth self-hosting if a web
app suits you better than a native one.

Exercise metadata comes from the MIT-licensed ExerciseDB dataset. No exercise media is
bundled or fetched, because its rights are unresolved.

## Licence

Apache-2.0. See [LICENSE](LICENSE).
