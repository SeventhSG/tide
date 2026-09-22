# Roadmap

Each phase ends with something installable on a phone. Nothing here has a date on it.

Status is recorded as the work happens, so this file says what is true rather than what was
planned. Where the two diverged, the divergence is written down with the reason.

## Where things stand

| | |
|---|---|
| Builds | `app-debug.apk`, 10 MB, minSdk 26, targetSdk 35 |
| Tests | 22 green: 15 pure progression, 7 round-trip on real SQLite |
| Screens rendering | Today, Session logger |
| Screens wired to data | None yet |

The app is not usable yet. Both screens render from hardcoded state; the repository
underneath them is tested and working but nothing joins them.

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

## Next

**2e. Wire the logger to the database.** The ViewModel joining `SessionScreen` to
`TrainingRepository`. This is the step that turns rendered screens into an app you can train
with, and everything below it is less valuable until it exists.

**2f. Importers.** FitNotes and Strong CSV, matched against the exercise library, unmatched
names becoming custom exercises. Plus retroactive logging. This is the difference between
opening the app on day one to your training history and opening it to an empty screen.

**2g. Muscle map.** Balance (volume per muscle over a window), fatigue (weighted by proximity
to estimated 1RM, decaying smoothly rather than dropping out of a hard window), strength
(days since last trained plus estimated 1RM).

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
