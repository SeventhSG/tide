# Roadmap

Each phase ends with something installable on a phone. Nothing here has a date on it.

Status is recorded as the work happens, so this file says what is true rather than what was
planned. Where the two diverged, the divergence is written down with the reason.

## Where things stand

| | |
|---|---|
| Builds | `app-debug.apk`, 10 MB, minSdk 26, targetSdk 35 |
| Tests | 245 green, including 30 schedule and reconciler, 28 notify, and 3 on the migrations |
| Screens rendering | Today, Train, Body, Money, Ask, logger, picker, planner, history, settings |
| Screens wired to data | Today, Train, Body, Ask, logger, picker, planner, history, import, muscle map |

The session logger is real: it opens a session, prescribes from history, logs sets to the
database and reads them back. Today reads the database too, and shows nothing it cannot
read.

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

**Finishing a session.** The logger has a Finish control, placed in the header and away from
the thumb, since it is pressed once a session and Log set a hundred times. It asks once, then
runs the engine and shows what it decided, in the engine's own words, with next time's
target. Finishing is enforced to happen once: a second finish decides nothing, so a double
tap cannot add the increment twice. A session with nothing in it is deleted rather than
recorded, because opening the logger and backing out is not a workout.

**The exercise picker.** Start session opens the library instead of a hardcoded squat.
Recent lifts first, with the date each was really last trained, then grouped by muscle, with
search running in SQL so the screen still works at 1,300 rows rather than 38. Back from the
logger returns to the picker and the session stays open, so a second lift is logged into the
same session. The ocean is off here, as on the logger: this is a screen scrolled in a gym,
and eyebrow labels over moving water fail the contrast rule.

**Today, honest.** Every number on it now comes from the database, and everything without a
source is gone rather than invented: sleep and resting heart rate wait for Body, renewals for
Money, the backup age for something that backs up, and the inbox button for an inbox. What is
left is real: the date, whether a session is open or was logged today, how long ago the last
one was, seven day volume when there is any, and the days of this week that were trained.

Two removals worth recording. **"Skip today" is gone**, because skipping is only meaningful
against a plan and there is no planner. **The week strip has two states, not three**: a
planned day needs a plan, so an empty square means only that nothing was logged, and the count
reads "2 SESSIONS" rather than "2 of 4 done", since nothing has declared what the week was
meant to hold. The nav's TRAIN tab opens the picker; BODY, MONEY and ASK are inert until those
modules exist, rather than moving the highlight onto an empty screen.

**The logger, finished.** Three things it could not do and needed to. **A set can be marked
as a warm-up**, which is the flag the whole schema rests on and which the app previously could
not set at all, so every set logged in the app counted toward progression, a 1RM and the muscle
map. The toggle resets itself after one set, because a toggle left on is how a working set
disappears from progression unnoticed. **A logged set can be removed**, on a second tap, which
is the one destructive action on the screen and the only thing on it drawn in red. **Rest is
counted up from the last set**, not down toward a target, because nothing has said how long
your rest should be and ninety seconds would be an invented prescription.

**History.** A month of real sessions, a day at a time. Tapping a day shows what was done:
the exercises, their working sets, the top set of each and the day's volume. A day is filled
in only when a session finished, since an abandoned session is not training. No streak, no
flame, no percentage of a target: a blank day is drawn blank and nothing is said about it.

**Notifications, wired.** `:core:notify` decided correctly and nothing called it. Now the
channels are created at launch, there is a settings screen for the daily summary and quiet
hours, the runtime permission is asked for at the moment the summary is turned on rather than
at launch, and a WorkManager job runs once a day to post it. **The summary is off until it is
asked for**, it is silent, and with nothing outstanding it posts nothing at all: the app does
not check in. A test notification can be sent from settings, through the same path and ledger
as a real one, so it can be verified on a phone.

**3. Schedule and notify, the modules.** `:core:schedule` holds recurrence, occurrences and
the reconciler; `:core:notify` holds tiers, channels, quiet hours, the digest and the ledger.
Both are tested and neither is wired to a screen yet.

The notification stance, written down because it is easy to erode: an interruption has a
cost and the app pays it. The default is a quiet daily digest, `Default` waits for quiet
hours to end, and only `Urgent` may break them. There are no engagement notifications, and
there never will be: nothing here says you have not opened the app, nothing congratulates a
streak, and nothing is sent to bring you back.

**v0.1.1, the rest of the shape.** Five sections behind the bottom bar, which stays put on
all of them. **Train is a hub** rather than a door into a list: four counted figures over four
weeks, then the ways in. **The week planner** arranges exercises per day in the order they
will be done, and writes itself out as a schedule rule, which is what finally gives the daily
summary something true to say. **Body reads Health Connect**, read-only, with four honest
states. **Ask installs a model** onto the phone, resumable and removable, and says plainly
that it cannot answer anything yet. **Money is an empty section that says so.**

Smaller things in the same release: load and reps can be typed rather than only stepped, page
hops move like water with a single crest of light, the app opens through a boot wave of surf
and bubbles, settings is a glyph rather than a word, every exercise has a drawn mark, and the
sea can be played from settings, generated rather than sampled.

**Two licence decisions, written down.** The exercise images are **drawn in this repository**
because no photo or animation set cleared the check that its own licence permits
redistribution in an Apache-2.0 app. The ocean sound is **generated from noise** rather than
sampled, so no audio file ships and nothing repeats. Both are replaceable the day a licensed
source clears the check.

**One bug worth recording.** Two overlapping reloads in the planner could finish out of order
and leave the screen showing a plan that no longer existed. Both the planner and the calendar
now serialise their reads behind a mutex, which is cheap and removes the class of bug rather
than the instance.

## Next

### Before the next release

Checked on 2026-09-22 and found not usable yet. A version tag claims "this runs and does what
it says", and four things made that untrue. These come first, before anything new.

1. ~~**Finish a session.**~~ Done, see above.
2. ~~**An exercise picker.**~~ Done, see above.
3. ~~**Today shows real numbers or none.**~~ Done, see above.
4. **Launched once on a real phone.** Nothing has ever started `MainActivity`. Roborazzi
   renders screens, it does not prove the app opens. This step needs a person and a USB cable.

### The full exercise library, with images

All of it, not 38. Roughly 1,300 exercises with their muscles and equipment, seeded as an
asset, and **an image for each**.

The images are the part with a condition attached, and it is written here so it is not
quietly dropped later:

- **The source must have a licence that permits redistribution in an Apache-2.0 app, checked
  in the source's own licence file.** Popularity is not a licence. Images being widely used is
  evidence that they are popular, not that they are licensed, and an app shipping media it has
  no right to is an app that gets taken down.
- **openGym's media is out.** Its own notice records that the animation rights are unresolved.
  openGym is also AGPL, so neither its data nor its media may be copied into Tide regardless.
- **Several datasets are all called "ExerciseDB"** and they do not share a licence. One is a
  commercial API. The name is not enough; the specific repository and its licence are.
- **Bundled, never hotlinked.** The app is offline and keeps no telemetry. Fetching images from
  someone's CDN would break offline use and tell a third party which exercises you look at.
- **Measure the size before choosing a format.** A thousand animations and a thousand stills
  cost very different amounts of APK. Pick the format after measuring, not before.

If no source clears the licence check, the library ships with metadata only and images wait.

### A weekly planner

Decide which day is which: Monday is push, Wednesday is pull, Friday is legs, the rest are rest.
Then Today knows what is planned, starting a session opens that day's exercises, and the
hardcoded squat disappears on its own.

Half of this exists already. `RoutineEntity` and `RoutineExerciseEntity` are in the schema with
a `dayIndex` per exercise, and have been since version 1. What is missing is every screen.

Two shapes of plan, because people train both ways:

- **Fixed days.** Push on Monday, whatever happens. A `Weekly` rule in `:core:schedule`.
- **A rotation.** Push, pull, legs, in order, three times a week, on whichever days you get to
  the gym. A `TimesPerWeek` quota. Most schedulers cannot express this, and it is the one that
  stops the app telling you that you missed Monday when you train Tuesday.

A plan becomes schedule rules, so the reconciler resolves each day from the logged session
without asking. Moving a day never reorders the week.

### Also waiting

**Retroactive logging as an action.** The flag exists and the importer sets it, but there is
no way yet to log a session you did yesterday from inside the app.

**A rule editor.** Quiet hours and the digest have a screen; schedule rules do not. Until the
planner exists nothing creates a rule, so the daily summary has nothing to summarise and
correctly says nothing.

## Then

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
