package com.avdbuddy

import com.composeunstyled.AnnouncementPolicy
import com.composeunstyled.AnnouncementResult
import com.composeunstyled.AnnouncementState
import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlin.time.Duration

class AnnouncementStateTest {
    private suspend fun <T> AnnouncementState<T>.awaitCurrent(value: T) { while (current?.value != value) yield() }

    @Test fun `announce suspends until the action is used and reports it`() = runBlocking {
        val state = AnnouncementState<String>()
        val result = async { state.announce("Deleted", duration = null, interactive = true) }
        state.awaitCurrent("Deleted")
        state.current!!.performAction()
        assertEquals(AnnouncementResult.ActionPerformed, result.await())
        assertNull(state.current)
    }

    @Test fun `dismissing ends the announcement as dismissed`() = runBlocking {
        val state = AnnouncementState<String>()
        val result = async { state.announce("Saved") }
        state.awaitCurrent("Saved")
        state.dismiss()
        assertEquals(AnnouncementResult.Dismissed, result.await())
    }

    @Test fun `queued announcements wait for the current one`() = runBlocking {
        val state = AnnouncementState<String>(AnnouncementPolicy.Queue)
        val first = async { state.announce("First") }
        state.awaitCurrent("First")
        val second = async { state.announce("Second") }
        yield()
        assertEquals("First", state.current?.value)
        state.dismiss()
        assertEquals(AnnouncementResult.Dismissed, first.await())
        state.awaitCurrent("Second")
        state.dismiss()
        assertEquals(AnnouncementResult.Dismissed, second.await())
    }

    @Test fun `a replacing announcement dismisses the current one right away`() = runBlocking {
        val state = AnnouncementState<String>(AnnouncementPolicy.Replace)
        val first = async { state.announce("First") }
        state.awaitCurrent("First")
        val second = async { state.announce("Second") }
        assertEquals(AnnouncementResult.Dismissed, first.await())
        state.awaitCurrent("Second")
        state.dismiss()
        second.await()
        assertNull(state.current)
    }

    @Test fun `cancelling the caller removes its announcement`() = runBlocking {
        val state = AnnouncementState<String>()
        val shown = launch { state.announce("Saving") }
        state.awaitCurrent("Saving")
        shown.cancelAndJoin()
        assertNull(state.current)
    }

    @Test fun `durations must be positive or absent`(): Unit = runBlocking {
        val state = AnnouncementState<String>()
        assertFailsWith<IllegalArgumentException> { state.announce("Never", duration = Duration.ZERO) }
    }
}
