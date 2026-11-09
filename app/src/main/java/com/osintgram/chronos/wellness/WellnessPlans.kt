package com.osintgram.chronos.wellness

/**
 * The routines, breathing patterns, habits and reading list the Wellness tab
 * offers. This is content, not user data: nothing here is generated or filled
 * in later, and every link opens a real search on the service named beside it.
 */
object WellnessPlans {

    /** One movement inside a plan, with the starting numbers for a beginner. */
    data class PlannedExercise(
        val name: String,
        val sets: Int,
        val reps: Int,
        val bodyweight: Boolean = true,
        val cue: String = "",
    )

    data class Plan(
        val id: String,
        val title: String,
        val focus: String,
        val minutes: Int,
        val level: String,
        val exercises: List<PlannedExercise>,
    )

    val plans: List<Plan> = listOf(
        Plan(
            id = "full_body_basics",
            title = "Full Body Basics",
            focus = "Whole body · no equipment",
            minutes = 30,
            level = "Beginner",
            exercises = listOf(
                PlannedExercise("Bodyweight squat", 3, 12, cue = "Knees track over the toes, chest up"),
                PlannedExercise("Incline push-up", 3, 8, cue = "Hands on a table or wall, straight line from head to heels"),
                PlannedExercise("Glute bridge", 3, 15, cue = "Squeeze the glutes at the top, ribs down"),
                PlannedExercise("Bird dog", 3, 10, cue = "Slow and steady, hips level"),
                PlannedExercise("Plank", 3, 1, cue = "Hold 20–30 seconds, breathe"),
                PlannedExercise("Standing calf raise", 2, 15),
            ),
        ),
        Plan(
            id = "mobility_stretch",
            title = "Mobility & Stretch",
            focus = "Hips, spine and shoulders",
            minutes = 15,
            level = "Any",
            exercises = listOf(
                PlannedExercise("Cat–cow", 2, 10, cue = "Move with the breath"),
                PlannedExercise("Hip flexor stretch", 2, 1, cue = "Hold 30 seconds each side"),
                PlannedExercise("Thoracic rotation", 2, 8, cue = "Each side, slow"),
                PlannedExercise("Hamstring stretch", 2, 1, cue = "Hold 30 seconds each side"),
                PlannedExercise("Shoulder wall slide", 2, 10),
            ),
        ),
        Plan(
            id = "upper_body",
            title = "Upper Body",
            focus = "Push, pull and carry",
            minutes = 25,
            level = "Beginner",
            exercises = listOf(
                PlannedExercise("Push-up", 3, 8, cue = "Knees down if needed"),
                PlannedExercise("Backpack row", 3, 12, bodyweight = false, cue = "Use a loaded backpack as the weight"),
                PlannedExercise("Shoulder press", 3, 10, bodyweight = false, cue = "Light weight, ribs stacked over hips"),
                PlannedExercise("Bicep curl", 3, 12, bodyweight = false),
                PlannedExercise("Farmer carry", 3, 1, bodyweight = false, cue = "Walk 30 seconds, tall posture"),
            ),
        ),
        Plan(
            id = "walk_and_core",
            title = "Walk & Core",
            focus = "Low impact, easy on the knees",
            minutes = 20,
            level = "Any",
            exercises = listOf(
                PlannedExercise("Brisk walk", 1, 1, cue = "10 minutes, comfortable pace"),
                PlannedExercise("Dead bug", 3, 8, cue = "Each side, lower back stays down"),
                PlannedExercise("Side plank", 2, 1, cue = "Hold 15–20 seconds each side"),
                PlannedExercise("Standing march", 2, 20),
            ),
        ),
    )

    fun plan(id: String): Plan? = plans.firstOrNull { it.id == id }

    /**
     * Breathing patterns. Each phase is a label and a length in seconds, so the
     * meditation screen can drive the circle and the text from one list.
     */
    data class Phase(val label: String, val seconds: Int)

    data class Pattern(
        val id: String,
        val title: String,
        val subtitle: String,
        val phases: List<Phase>,
        val caution: String = "",
    )

    val patterns: List<Pattern> = listOf(
        Pattern(
            id = "calm",
            title = "Calm breath",
            subtitle = "In 4 · out 6",
            phases = listOf(Phase("Breathe in", 4), Phase("Breathe out", 6)),
        ),
        Pattern(
            id = "box",
            title = "Box breathing",
            subtitle = "In 4 · hold 4 · out 4 · hold 4",
            phases = listOf(
                Phase("Breathe in", 4),
                Phase("Hold", 4),
                Phase("Breathe out", 4),
                Phase("Hold", 4),
            ),
        ),
        Pattern(
            id = "four_seven_eight",
            title = "4–7–8 breath",
            subtitle = "In 4 · hold 7 · out 8",
            phases = listOf(
                Phase("Breathe in", 4),
                Phase("Hold", 7),
                Phase("Breathe out", 8),
            ),
            caution = "Keep it comfortable. Shorten the counts or stop if you feel light-headed.",
        ),
        Pattern(
            id = "sigh",
            title = "Physiological sigh",
            subtitle = "Two inhales, one long out",
            phases = listOf(
                Phase("Breathe in", 3),
                Phase("Top-up breath", 1),
                Phase("Long breath out", 6),
            ),
        ),
    )

    fun pattern(id: String): Pattern = patterns.firstOrNull { it.id == id } ?: patterns.first()

    /** Habit ids the Today card can tick. Kept short on purpose. */
    data class Habit(val id: String, val title: String)

    val habits: List<Habit> = listOf(
        Habit("water", "Water goal"),
        Habit("walk", "Walk 30 min"),
        Habit("stretch", "Stretch"),
        Habit("sleep", "Lights out on time"),
        Habit("no_sugar", "No added sugar"),
    )

    /**
     * Reading and follow-along suggestions. Every entry points at a search
     * result page rather than a fixed video, so a link never rots and nothing
     * is claimed about content that has not been checked.
     */
    data class Suggestion(
        val title: String,
        val detail: String,
        val url: String,
        val source: String,
    )

    val suggestions: List<Suggestion> = listOf(
        Suggestion(
            title = "Beginner yoga, 15 minutes",
            detail = "Follow-along full-body session",
            url = "https://www.youtube.com/results?search_query=15+minute+beginner+yoga+full+body",
            source = "YouTube search",
        ),
        Suggestion(
            title = "Morning mobility routine",
            detail = "Five minutes to loosen up",
            url = "https://www.youtube.com/results?search_query=5+minute+morning+mobility+routine",
            source = "YouTube search",
        ),
        Suggestion(
            title = "Guided box breathing",
            detail = "Ten minutes with a voice guide",
            url = "https://www.youtube.com/results?search_query=guided+box+breathing+10+minutes",
            source = "YouTube search",
        ),
        Suggestion(
            title = "Bodyweight strength, no kit",
            detail = "20-minute follow-along",
            url = "https://www.youtube.com/results?search_query=20+minute+bodyweight+workout+no+equipment+beginner",
            source = "YouTube search",
        ),
        Suggestion(
            title = "Short yoga breaks",
            detail = "One-minute vertical clips",
            url = "https://www.youtube.com/results?search_query=yoga+stretch+break+%23shorts",
            source = "YouTube Shorts search",
        ),
        Suggestion(
            title = "Desk stretches",
            detail = "Neck, shoulders and wrists",
            url = "https://www.youtube.com/results?search_query=desk+stretches+neck+shoulders+wrists+5+minutes",
            source = "YouTube search",
        ),
        Suggestion(
            title = "Sleep wind-down",
            detail = "Body scan before bed",
            url = "https://www.youtube.com/results?search_query=body+scan+meditation+for+sleep+guided",
            source = "YouTube search",
        ),
    )

    /**
     * Pose reference cards. Each one is drawn from Material icons in the app
     * and described in words — no stock photo is bundled, so nothing here is a
     * stand-in for an image the app does not actually have.
     */
    data class Pose(val name: String, val benefit: String, val cue: String, val icon: String)

    val poses: List<Pose> = listOf(
        Pose("Mountain", "Posture and steady breathing", "Feet hip-width, arms relaxed, long spine", "mountain"),
        Pose("Tree", "Balance and focus", "Foot on the calf or thigh, never on the knee", "tree"),
        Pose("Downward dog", "Hamstrings, calves, shoulders", "Bend the knees as much as you need", "dog"),
        Pose("Child's pose", "Lower back release", "Knees wide, forehead down, breathe slowly", "child"),
        Pose("Cat–cow", "Spine mobility", "Move one vertebra at a time with the breath", "cat"),
        Pose("Legs up the wall", "Recovery after sitting", "Hips close to the wall, arms open", "wall"),
        Pose("Seated twist", "Mid-back rotation", "Turn from the ribs, not the neck", "twist"),
        Pose("Corpse", "Rest and integration", "Lie flat, palms up, let the breath be natural", "rest"),
    )
}
