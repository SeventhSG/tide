# Roadmap

Each phase ends with something installable on a phone. Nothing here has a date on it.

Status is recorded as the work happens, so this file says what is true rather than what was
planned. Where the two diverged, the divergence is written down with the reason.

## Where things stand

| | |
|---|---|
| Builds | `app-debug.apk`, 10 MB, minSdk 26, targetSdk 35 |
| Tests | 147 green, including 30 schedule and reconciler, and 2 on the version 1 to 2 migration |
| Screens rendering | Today, Session logger |
| Screens wired to data | Session logger, Import, Muscle map |

The session logger is real: it opens a session, prescribes from history, logs sets to the
database and reads them back. Today is still hardcoded, and connecting it waits on the
scheduler in phase 3.

## Done

**0. Repository.** Licence, README, design system, brand, ignore rules, the pre-ship gate.

**1. Foundations.** `:app` shell and `:core:design`: tokens sampled from the icon, the
Manrope and IBM Plex Mono pairing, `ContinuousCornerShape` (superellipse corners, because
Compose's are circular arcs), the ocean, the liquid glass nav, the reduced-motion hook, and
the button set. Screenshot tests over the whole layer via Roborazzi.

**2a. Progression engine.** Five rules: linear, Greyskull LP, double progression, timed,
bodyweight. Pure Kotlin, no Android. Two invariants tested directly: a missed rep never
advances the load, and warm-ups never count. Mutation checked.

**2b. Training schema.** Seven Room tables carrying the full set taxonomy, the warm-up flag
and the per-exercise stall count. Schemas exported and committed.

**2c. Repository.** The engine wired to the database, with round-trip tests for the three
things that pass in isolation and break in an app: warm-up exclusion, stall persistence
across sessions, and resuming rather than forking an open session.

**2d. Session logger and starter library.** The screen, and 38 exercises with a progression
rule chosen per lift.

**2e. The logger, wired.** `SessionViewModel` joining the screen to `TrainingRepository`.
A plain class rather than `androidx.lifecycle.ViewModel`, because session resumption already
lives in the repository, so a configuration change re-reads the open row instead of losing
it. Screenshot tests now render from a real database rather than sample state, which caught
a header reading "SET 3 OF 1" on an exercise with no history.

**2f. Importers, the data layer.** FitNotes and Strong CSV: a real CSV reader, format
detection by column rather than file name, pounds and distances converted, RPE read as reps
in reserve, and warm-up markers preserved. Unmatched names become custom exercises rather
than being dropped, and re-importing the same file does not double the history.

Progression is deliberately not replayed over imported sessions. Imported sessions carry an
`endedAt`, so `prescriptionFor` already falls back to the last session's working sets and
produces a real target. Replaying the engine would manufacture stall counts from sessions
whose warm-ups were never marked, and fire deloads from them.

**2f. The import screen.** The Storage Access Framework picker, and a screen that reads the
file, says what is in it, and writes nothing until the button is pressed. A preview is not
decoration here: committing a year of training sight unseen is not a decision anyone can
make well, and the part worth seeing is what could **not** be matched, since those lifts come
in under their own names and start their own history.

**2g. Muscle map.** Balance (volume per muscle over a window), fatigue (weighted by proximity
to estimated 1RM, decaying smoothly with a two day half life rather than dropping out of a
hard window), strength (days since last trained plus estimated 1RM).

Three readings on three tabs, never rolled into one recovery score. **Fatigue is drawn as a
bar and never as a number**: it is an index out of a model, comparable only against your own
other muscles, and printing a percentage would invent a measurement no app can take from a
set count. The conventions it does rest on are stated on the screen, not buried: a secondary
muscle counts at half, and the 1RM is an Epley estimate.

No body silhouette. A drawn figure implies the app knows where a muscle sits and how much of
it was worked. It knows volume attributed by a declared convention, so it shows a list.

## Next

**Retroactive logging as an action.** The flag exists and the importer sets it, but there is
no way yet to log a session you did yesterday from inside the app.

**An exercise picker.** The logger opens on a hardcoded squat, because choosing a lift needs
either routines or a browsable library and neither screen exists.

## Then

**3. Schedule and notify.** `:core:schedule` (recurrence rules, occurrences, and the
reconciler that resolves an occurrence from an existing record rather than asking) and
`:core:notify` (tiers, channels, quiet hours, digest, ledger).

**Deferred from its original position.** These were planned before Training, on the grounds
that four modules would otherwise each grow their own notification funnel. That reasoning
still holds, and they still come before Money, Body and Inbox. They were deferred because
Training is the pillar that gets daily use, and a scheduler with nothing to schedule is hard
to judge.

**4. Today, connected.** The home stack reading real data. "Needs you" driven entirely by
open occurrences from the scheduler.

**5. Onboarding.** Units, name, date of birth, body basics, the Health Connect handoff, the
import offer and the first schedule rule.

**6. Body.** The Health Connect read layer, plus the permission and denial states.

**7. Money.** Commitments, renewals and WorkManager reminders. The optional notification
listener comes last and stays isolated, because it is the one component that would have to be
removed if Tide were ever published to Play.

**8. Inbox.** The `:core:notify` rules engine, then the ntfy transport, then the source
manager. The persistent connection gets measured with Battery Historian over 24 hours before
it is called acceptable.

**9. Assistant.** The SQL-backed tool layer first, against a populated database and testable
without a model at all. Then the engine. Then the chat surface.

**10. Review.** The weekly, and the one screen that gets the motion budget.

## Deliberately later, or never

- **Bank connections.** The free open banking tier indie apps relied on (GoCardless Bank
  Account Data, formerly Nordigen) closed to new signups in 2026. Enable Banking in the EU
  and UK, or Teller in the US, are the remaining self-serve routes if this ever matters
  enough to justify the work.
- **Sync across devices.** It would require a server, and the absence of a server is the
  product.
- **A Play Store release.** The notification listener alone would need a policy answer first.
- **The full 1,300-exercise library.** The starter set of 38 is enough to train on. The
  ExerciseDB metadata (MIT) comes later as a seeded asset. No exercise media, ever: its
  rights are unresolved.
