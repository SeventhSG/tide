package app.tide.core.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * When the app may interrupt you.
 *
 * Pure JUnit, no Robolectric: the policy has no Android in it, which is the
 * point of keeping it separate from the thing that posts.
 */
class NotifyPolicyTest {

    private val zone = ZoneId.of("UTC")
    private val settings = NotifySettings()

    private fun at(hour: Int, minute: Int = 0): Instant =
        LocalDate.of(2025, 9, 1).atTime(hour, minute).atZone(zone).toInstant()

    private fun note(
        tier: Tier = Tier.Quiet,
        key: String = "k",
        expiresAt: Instant? = null,
    ) = TideNotification(
        key = key,
        title = "Legs",
        body = "Scheduled for today.",
        tier = tier,
        createdAt = at(9),
        expiresAt = expiresAt,
    )

    private fun decide(
        notification: TideNotification,
        now: Instant,
        settings: NotifySettings = this.settings,
        lastPosted: Instant? = null,
    ) = NotifyPolicy.decide(notification, settings, now, zone, lastPosted)

    // --- quiet hours ------------------------------------------------------

    @Test
    fun `a window crossing midnight covers both sides of it`() {
        val quiet = QuietHours(LocalTime.of(22, 0), LocalTime.of(7, 0))
        assertTrue(quiet.contains(LocalTime.of(23, 30)))
        assertTrue(quiet.contains(LocalTime.of(3, 0)))
        assertTrue(quiet.contains(LocalTime.of(22, 0)))
        assertEquals(false, quiet.contains(LocalTime.of(7, 0)))
        assertEquals(false, quiet.contains(LocalTime.of(12, 0)))
    }

    @Test
    fun `a window inside one day does not leak past its end`() {
        val quiet = QuietHours(LocalTime.of(13, 0), LocalTime.of(14, 0))
        assertTrue(quiet.contains(LocalTime.of(13, 30)))
        assertEquals(false, quiet.contains(LocalTime.of(14, 30)))
        assertEquals(false, quiet.contains(LocalTime.of(2, 0)))
    }

    @Test
    fun `quiet hours turned off contain nothing`() {
        val quiet = QuietHours(enabled = false)
        assertEquals(false, quiet.contains(LocalTime.of(23, 0)))
    }

    @Test
    fun `quiet hours end this morning when it is the small hours`() {
        val quiet = QuietHours(LocalTime.of(22, 0), LocalTime.of(7, 0))
        val threeAm = at(3).atZone(zone)
        assertEquals(at(7), quiet.endsAfter(threeAm))
    }

    @Test
    fun `quiet hours end tomorrow morning when it is late tonight`() {
        val quiet = QuietHours(LocalTime.of(22, 0), LocalTime.of(7, 0))
        val elevenPm = at(23).atZone(zone)
        assertEquals(at(7).plus(Duration.ofDays(1)), quiet.endsAfter(elevenPm))
    }

    @Test
    fun `outside quiet hours there is nothing to wait for`() {
        val quiet = QuietHours(LocalTime.of(22, 0), LocalTime.of(7, 0))
        val noon = at(12).atZone(zone)
        assertEquals(at(12), quiet.endsAfter(noon))
    }

    // --- the decision -----------------------------------------------------

    @Test
    fun `a quiet notification waits for the digest rather than posting alone`() {
        val decision = decide(note(Tier.Quiet), now = at(12))
        assertTrue("this is the difference between telling and interrupting", decision is Delivery.Digest)
        // Digest is at 08:00, so the next one is tomorrow morning.
        assertEquals(at(8).plus(Duration.ofDays(1)), (decision as Delivery.Digest).at)
    }

    @Test
    fun `a default notification posts now when the hour is civilised`() {
        assertTrue(decide(note(Tier.Default), now = at(12)) is Delivery.Now)
    }

    @Test
    fun `a default notification is held through quiet hours, not dropped`() {
        val decision = decide(note(Tier.Default), now = at(23))
        assertTrue("you still get told, at a time you chose", decision is Delivery.Hold)
        assertEquals(at(7).plus(Duration.ofDays(1)), (decision as Delivery.Hold).until)
    }

    @Test
    fun `only urgent breaks quiet hours`() {
        assertTrue(decide(note(Tier.Urgent), now = at(3)) is Delivery.Now)
        assertTrue(decide(note(Tier.Default), now = at(3)) is Delivery.Hold)
        assertTrue(decide(note(Tier.Quiet), now = at(3)) is Delivery.Digest)
    }

    @Test
    fun `a muted key is dropped whatever its tier`() {
        val muted = settings.copy(muted = setOf("k"))
        val decision = decide(note(Tier.Urgent), now = at(12), settings = muted)
        assertEquals(
            "a mute is an instruction, not a preference",
            DropReason.Muted,
            (decision as Delivery.Drop).reason,
        )
    }

    @Test
    fun `an expired notification is not delivered late`() {
        val decision = decide(
            note(Tier.Default, expiresAt = at(11)),
            now = at(12),
        )
        assertEquals(
            "a reminder about a day that has gone is noise plus an accusation",
            DropReason.Expired,
            (decision as Delivery.Drop).reason,
        )
    }

    @Test
    fun `a quiet notification that would expire before the digest is dropped`() {
        // Digest is tomorrow at 08:00; this expires tonight.
        val decision = decide(
            note(Tier.Quiet, expiresAt = at(22)),
            now = at(12),
        )
        assertEquals(DropReason.Expired, (decision as Delivery.Drop).reason)
    }

    @Test
    fun `the same thing is not said twice inside the window`() {
        val decision = decide(note(Tier.Default), now = at(12), lastPosted = at(9))
        assertEquals(DropReason.AlreadySaid, (decision as Delivery.Drop).reason)
    }

    @Test
    fun `the same thing may be said again once the window has passed`() {
        val yesterday = at(9).minus(Duration.ofHours(21))
        assertTrue(decide(note(Tier.Default), now = at(12), lastPosted = yesterday) is Delivery.Now)
    }

    @Test
    fun `a digest due inside quiet hours waits for them to end`() {
        val nocturnal = settings.copy(
            digestAt = LocalTime.of(5, 0),
            quietHours = QuietHours(LocalTime.of(22, 0), LocalTime.of(7, 0)),
        )
        val decision = decide(note(Tier.Quiet), now = at(12), settings = nocturnal)
        assertEquals(
            "the summary must not be the one thing that wakes you",
            at(7).plus(Duration.ofDays(1)),
            (decision as Delivery.Digest).at,
        )
    }

    // --- the digest -------------------------------------------------------

    @Test
    fun `nothing to say produces no digest at all`() {
        assertNull(
            "the app does not check in to tell you it has nothing",
            NotifyPolicy.digest(emptyList(), at(8)),
        )
    }

    @Test
    fun `one thing is sent as itself, not wrapped in a summary`() {
        val single = NotifyPolicy.digest(listOf(note()), at(8))!!
        assertEquals("Legs", single.title)
    }

    @Test
    fun `several are counted and listed, not characterised`() {
        val items = listOf(
            note(key = "a").copy(title = "Legs"),
            note(key = "b").copy(title = "Rent"),
            note(key = "c").copy(title = "Weigh in"),
        )
        val digest = NotifyPolicy.digest(items, at(8))!!

        assertEquals("3 things need you", digest.title)
        assertEquals("Legs, Rent, Weigh in.", digest.body)
        assertEquals(Tier.Quiet, digest.tier)
    }

    @Test
    fun `a long digest names a few and counts the rest`() {
        val items = (1..6).map { note(key = "k$it").copy(title = "Item $it") }
        val digest = NotifyPolicy.digest(items, at(8))!!
        assertEquals("Item 1, Item 2, Item 3, and 3 more.", digest.body)
    }

    @Test
    fun `the digest never scolds`() {
        val items = (1..5).map { note(key = "k$it") }
        val digest = NotifyPolicy.digest(items, at(8))!!
        val text = "${digest.title} ${digest.body}".lowercase()

        listOf("behind", "failed", "missed", "streak", "don't", "should", "again").forEach {
            assertEquals("the copy must state facts, not pass judgement: found '$it'", false, text.contains(it))
        }
    }
}
