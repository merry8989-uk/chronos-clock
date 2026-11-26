package com.osintgram.chronos.wellness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The guided model: a session's length, and the shape every session must keep. */
class GuidedModelsTest {

    private fun session(vararg steps: GuidedStep) = GuidedSession(
        id = "test",
        title = "Test",
        subtitle = "Test session",
        kind = GuidedKind.Yoga,
        level = "Beginner",
        steps = steps.toList(),
    )

    @Test
    fun `a session's length is the sum of its steps`() {
        val s = session(
            GuidedStep("One", "do one", 30),
            GuidedStep("Two", "do two", 45, cue = "breathe"),
            GuidedStep("Three", "do three", 60),
        )
        assertEquals(135, s.totalSeconds)
        assertEquals("2 min 15 s", s.lengthLabel)
    }

    @Test
    fun `whole minutes read without seconds`() {
        val s = session(GuidedStep("Sit", "sit still", 600))
        assertEquals("10 min", s.lengthLabel)
    }

    @Test
    fun `a session under a minute reads in seconds`() {
        val s = session(GuidedStep("Stand", "stand tall", 45))
        assertEquals("45 s", s.lengthLabel)
    }

    @Test
    fun `every step has a title, an instruction and real time`() {
        val all = GuidedLibrary.all()
        assertTrue("the library should not be empty", all.isNotEmpty())
        all.forEach { session ->
            assertTrue("${session.id} has no steps", session.steps.isNotEmpty())
            session.steps.forEach { step ->
                assertTrue("a step in ${session.id} has no title", step.title.isNotBlank())
                assertTrue("a step in ${session.id} has no instruction", step.instruction.isNotBlank())
                assertTrue("a step in ${session.id} has no time", step.seconds > 0)
            }
            assertTrue("${session.id} has no note", session.note.isNotBlank())
        }
    }

    @Test
    fun `session ids are unique and both kinds are present`() {
        val all = GuidedLibrary.all()
        assertEquals(all.size, all.map { it.id }.toSet().size)
        assertTrue(GuidedLibrary.byKind(GuidedKind.Yoga).isNotEmpty())
        assertTrue(GuidedLibrary.byKind(GuidedKind.Meditation).isNotEmpty())
        all.forEach { assertEquals(it, GuidedLibrary.find(it.id)) }
    }
}
