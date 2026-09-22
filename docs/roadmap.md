# Roadmap

Each phase ends with something installable on a phone. Nothing here has a date on it.

**0. Repository.** Licence, README, design system, ignore rules. Done.

**1. Foundations.** The `:app` shell, `:core:design` (tokens, type, shape, motion, the
reduced-motion hook), `:core:data` (Room, DataStore, the profile row, encrypted export).
Screenshot tests over the token layer. No features.

**1b. Schedule and notify.** `:core:schedule` (recurrence rules, occurrences, and the
reconciler that resolves an occurrence from an existing record rather than asking) and
`:core:notify` (tiers, channels, quiet hours, digest, ledger). These come early because
Training, Money, Body and Inbox all depend on them, and retrofitting one notification funnel
after four modules have each grown their own is the expensive version of this work. Both are
pure Kotlin plus WorkManager and testable with no UI.

**2. Training.** The self-contained pillar and the one that gets daily use, so it proves the
design system under real density before anything depends on it. In order: schema and
progression rules first as pure Kotlin with no UI, then the FitNotes and Strong importers so
there is real history to look at, then the session logger, then the muscle map.

**2b. Onboarding.** Units, name, date of birth, body basics, the Health Connect handoff, the
import offer and the first schedule rule. It is built after Training so the last two steps
have somewhere real to land, and it reads height and weight back from Health Connect rather
than asking for what the device already knows.

**3. Today.** The home stack, once there is real data to put in it. Empty states written
first, and "Needs you" driven entirely by open occurrences from the scheduler.

**4. Body.** The Health Connect read layer, plus the permission and denial states.

**5. Money.** Commitments, renewals and WorkManager reminders. The optional notification
listener comes last and stays isolated, because it is the one component that would have to be
removed if Keel were ever published to Play.

**6. Inbox.** The `:core:notify` priority rules engine, then the ntfy transport, then the
source manager. The persistent connection gets measured with Battery Historian over 24 hours
before it is called acceptable.

**7. Assistant.** The SQL-backed tool layer first, against a populated database and testable
without a model at all. Then the engine. Then the chat surface.

**8. Review.** The weekly, and the one screen that gets the motion budget.

## Deliberately later, or never

- Bank connections. The free open banking tier that indie apps relied on (GoCardless Bank
  Account Data, formerly Nordigen) closed to new signups in 2026. Enable Banking in the EU
  and UK, or Teller in the US, are the remaining self-serve routes if this ever matters
  enough to justify the work.
- Sync across devices. It would require a server, and the absence of a server is the product.
- A Play Store release.
