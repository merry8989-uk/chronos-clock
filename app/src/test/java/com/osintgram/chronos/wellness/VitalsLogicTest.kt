package com.osintgram.chronos.wellness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** BMI and the sleep score: the two new numbers the Body and Sleep sections show. */
class VitalsLogicTest {

    @Test
    fun `bmi needs both numbers and rounds to one decimal`() {
        assertNull(WellnessLogic.bmi(0.0, 175.0))
        assertNull(WellnessLogic.bmi(70.0, 0.0))
        assertEquals(22.9, WellnessLogic.bmi(70.0, 175.0)!!, 0.001)
        assertEquals(24.2, WellnessLogic.bmi(75.0, 176.0)!!, 0.001)
    }

    @Test
    fun `bmi bands read the way people talk`() {
        assertEquals("Underweight", WellnessLogic.bmiLabel(17.0))
        assertEquals("Healthy range", WellnessLogic.bmiLabel(22.0))
        assertEquals("Overweight", WellnessLogic.bmiLabel(27.0))
        assertEquals("Obese", WellnessLogic.bmiLabel(33.0))
    }

    @Test
    fun `a night with no sleep has no score`() {
        assertNull(WellnessLogic.sleepScore(0, 0, 0, 0))
        assertNull(WellnessLogic.sleepScore(0, 0, 0, 30))
    }

    @Test
    fun `a full night with no stage detail still scores well`() {
        // 7.5 hours asleep, no deep/REM data: score on duration alone.
        val score = WellnessLogic.sleepScore(0, 450, 0, 20)!!
        assertTrue("expected a high score, was $score", score >= 90)
    }

    @Test
    fun `a short night scores below a full one`() {
        val short = WellnessLogic.sleepScore(0, 240, 0, 10)!!
        val full = WellnessLogic.sleepScore(0, 480, 0, 10)!!
        assertTrue("short=$short full=$full", short < full)
    }

    @Test
    fun `stage detail adds weight but never breaks the hundred`() {
        val withStages = WellnessLogic.sleepScore(90, 270, 90, 15)!!
        val without = WellnessLogic.sleepScore(0, 450, 0, 15)!!
        assertTrue("with=$withStages without=$without", withStages >= without)
        assertTrue(withStages <= 100)
    }
}
