package com.osintgram.chronos.wellness

/**
 * The meditation practices the app can guide, start to finish.
 *
 * Each session is a complete practice: someone can sit down, press play, and be
 * taken through it by the step in front of them. The steps are short and
 * imperative on purpose — one thing to do, a length of time, and a line that
 * stays on screen while the eyes are closed.
 *
 * These are general practices, written for ordinary use. They are not therapy
 * and not a substitute for a teacher or a clinician: the note on each session
 * says where a practice can be difficult, and every note asks you to stop or
 * shorten the practice and speak to someone qualified if difficult material
 * keeps coming up.
 */
object GuidedMeditation {

    val sessions: List<GuidedSession> = listOf(
        GuidedSession(
            id = "settling_in",
            title = "Settling in",
            subtitle = "Six steps to arrive, in about five minutes",
            kind = GuidedKind.Meditation,
            level = "Beginner",
            steps = listOf(
                GuidedStep(
                    title = "Choose a steady seat",
                    instruction = "Sit cross-legged on a cushion, on a chair with both feet flat, or kneeling on a stool. " +
                        "Choose the position you can stay in without fidgeting for five minutes, and sit tall without stiffening. " +
                        "If you are uncomfortable, change position now, before you begin.",
                    seconds = 45,
                    cue = "Tall, not stiff",
                ),
                GuidedStep(
                    title = "Let the hands rest",
                    instruction = "Rest your hands on your knees or in your lap, palms down or up, whichever is quieter. " +
                        "Let the shoulders drop away from the ears.",
                    seconds = 30,
                    cue = "Hands heavy in the lap",
                ),
                GuidedStep(
                    title = "Soften the eyes",
                    instruction = "Lower or close the eyes. Let the muscles around the eyes and the jaw go slack, " +
                        "and keep the face soft for the rest of the practice.",
                    seconds = 30,
                    cue = "Soft face, soft jaw",
                ),
                GuidedStep(
                    title = "Take three long breaths",
                    instruction = "Breathe in slowly through the nose and let the out-breath be longer than the in-breath. " +
                        "Do this three times, then stop counting. If you lose track of which breath you are on, start again at one.",
                    seconds = 60,
                    cue = "In slowly, out longer",
                ),
                GuidedStep(
                    title = "Let the breath be natural",
                    instruction = "Stop shaping the breath and let the body breathe. " +
                        "Notice where you feel the breath most clearly: the nostrils, the chest, or the belly.",
                    seconds = 45,
                    cue = "Let the body breathe",
                ),
                GuidedStep(
                    title = "Rest attention at the breath",
                    instruction = "Place your attention on that one place and let it stay there. " +
                        "If you notice you are thinking, that noticing is the practice — return to the breath without scolding yourself. " +
                        "Stay with it, one breath at a time, until this step ends.",
                    seconds = 90,
                    cue = "Noticing is the practice",
                ),
            ),
            note = "This is a general practice, not therapy. Sitting still can bring up restlessness or difficult material; " +
                "if that keeps happening, shorten the session or stop, and speak to someone qualified. " +
                "Five minutes done is a real practice — there is nothing here to achieve.",
        ),
        GuidedSession(
            id = "body_scan",
            title = "Body scan",
            subtitle = "Feet to face, one region at a time",
            kind = GuidedKind.Meditation,
            level = "Beginner",
            steps = listOf(
                GuidedStep(
                    title = "Settle before you scan",
                    instruction = "Lie on your back with the legs long and the arms a little away from the body, or sit up if lying down makes you sleepy. " +
                        "Let the eyes close and take two slow breaths.",
                    seconds = 30,
                    cue = "Lie down, let go",
                ),
                GuidedStep(
                    title = "Feel the feet",
                    instruction = "Bring attention to both feet: the soles, the toes, the skin against the air. " +
                        "You are not changing anything, only noticing what is already there — warmth, tingling, pressure, or nothing at all. " +
                        "If you notice you are thinking, that noticing is the practice — return to the feet.",
                    seconds = 60,
                    cue = "Noticing, not changing",
                ),
                GuidedStep(
                    title = "Move up to the legs",
                    instruction = "Let attention move through the ankles, calves, knees and thighs, and feel the weight of the legs resting. " +
                        "If you notice you are thinking, name it quietly as thinking and come back to the legs.",
                    seconds = 60,
                    cue = "Thinking — back to the legs",
                ),
                GuidedStep(
                    title = "Notice the hips",
                    instruction = "Rest attention on the hips, the lower back, and the place where the body meets the floor. " +
                        "Let the hips be heavy. If the mind wanders, return to that contact with the floor.",
                    seconds = 60,
                    cue = "Hips heavy on the floor",
                ),
                GuidedStep(
                    title = "Notice the belly",
                    instruction = "Feel the belly rise and fall with the breath. " +
                        "If the mind wanders, come back to the movement of the belly.",
                    seconds = 60,
                    cue = "Rising, falling",
                ),
                GuidedStep(
                    title = "Notice the chest",
                    instruction = "Feel the chest widen on the in-breath and settle on the out-breath. " +
                        "Notice the heartbeat if it is there. If you notice you are thinking, return to the chest.",
                    seconds = 60,
                    cue = "Widening, settling",
                ),
                GuidedStep(
                    title = "Notice the hands",
                    instruction = "Bring attention to both hands: the fingers, the palms, the backs of the hands. " +
                        "Notice temperature and tingling, without moving them. If the mind wanders, return to the hands.",
                    seconds = 60,
                    cue = "Palms, fingers, backs of hands",
                ),
                GuidedStep(
                    title = "Notice the arms",
                    instruction = "Move attention through the wrists, forearms, elbows and upper arms. " +
                        "Let the arms be heavy. If you notice you are thinking, come back to the arms.",
                    seconds = 60,
                    cue = "Arms heavy",
                ),
                GuidedStep(
                    title = "Notice the shoulders and neck",
                    instruction = "Feel the shoulders, the neck and the throat. " +
                        "If you find tension, do not fight it; let it be seen, and let the shoulders drop on the next out-breath.",
                    seconds = 60,
                    cue = "Let the shoulders drop",
                ),
                GuidedStep(
                    title = "Notice the face",
                    instruction = "Feel the jaw, the tongue, the muscles around the eyes and the forehead. " +
                        "Let the jaw unclench and the space between the eyebrows soften. If the mind wanders, return to the face.",
                    seconds = 60,
                    cue = "Jaw loose, forehead smooth",
                ),
                GuidedStep(
                    title = "Feel the whole body",
                    instruction = "Widen attention to the whole body at once, lying here breathing. " +
                        "If the mind wanders, come back to the whole body rather than to one part of it.",
                    seconds = 90,
                    cue = "The whole body, breathing",
                ),
                GuidedStep(
                    title = "Come back to the room",
                    instruction = "Feel the floor under you, move the fingers and toes, and open the eyes when you are ready. " +
                        "Sit up slowly.",
                    seconds = 60,
                    cue = "Floor, fingers, eyes open",
                ),
            ),
            note = "This is a general practice, not therapy. Lying still and scanning the body can be uncomfortable or unsettling, " +
                "particularly if you live with trauma or dissociation: if that happens, open your eyes, sit up, and stop for today. " +
                "If a full scan is too much, do the feet and hands only and finish there. " +
                "If difficult material keeps coming up, speak to someone qualified.",
        ),
        GuidedSession(
            id = "anapanasati",
            title = "Mindfulness of breathing",
            subtitle = "Knowing the breath, counting, then letting go",
            kind = GuidedKind.Meditation,
            level = "Beginner",
            steps = listOf(
                GuidedStep(
                    title = "Settle into the seat",
                    instruction = "Sit tall on a chair or a cushion, hands in the lap, eyes closed or lowered. " +
                        "Take three ordinary breaths to arrive.",
                    seconds = 45,
                    cue = "Arrive here",
                ),
                GuidedStep(
                    title = "Know the in-breath",
                    instruction = "Notice the beginning, middle and end of one in-breath; you do not need to make it longer, only know that it is happening. " +
                        "If you notice you are thinking, that noticing is the practice — return to this breath.",
                    seconds = 75,
                    cue = "Knowing the in-breath",
                ),
                GuidedStep(
                    title = "Know the out-breath",
                    instruction = "Follow the out-breath from the nostrils to its end and let it be long and unforced. " +
                        "When the mind goes off, come back to the out-breath.",
                    seconds = 75,
                    cue = "Knowing the out-breath",
                ),
                GuidedStep(
                    title = "Rest in the pause",
                    instruction = "At the end of the out-breath, notice the small gap before the next in-breath and rest there without holding anything. " +
                        "If the gap is hard to find, simply wait for the next breath.",
                    seconds = 75,
                    cue = "The small gap",
                ),
                GuidedStep(
                    title = "Count the breaths to ten",
                    instruction = "Count each out-breath silently: one, two, three, up to ten, then start again at one. " +
                        "Keep the counting light, like a finger pointing at the breath. " +
                        "If you lose count, begin again at one without judging yourself.",
                    seconds = 90,
                    cue = "One, two, three",
                ),
                GuidedStep(
                    title = "Begin again at one",
                    instruction = "The count will break; that is expected and it is not a failure. " +
                        "Each time you notice the count has gone, that is one moment of mindfulness — take it and start again at one.",
                    seconds = 75,
                    cue = "Start again at one",
                ),
                GuidedStep(
                    title = "Drop the count",
                    instruction = "Let the counting go and stay with the plain sensation of breathing — nothing to follow, nothing to fix. " +
                        "If you notice you are thinking, return to the breath.",
                    seconds = 90,
                    cue = "Just this breath",
                ),
                GuidedStep(
                    title = "Stay with the breath",
                    instruction = "Continue with the breath at your own pace until this step ends. " +
                        "Before you open your eyes, notice how you feel, without deciding whether it is good or bad.",
                    seconds = 75,
                    cue = "Noticing is the practice",
                ),
            ),
            note = "This is a general practice, not therapy. Close attention to the breath can feel uncomfortable or anxious for some people; " +
                "if that happens, open the eyes, shorten the session, or stop. " +
                "If difficult material keeps coming up, speak to someone qualified.",
        ),
        GuidedSession(
            id = "loving_kindness",
            title = "Loving-kindness",
            subtitle = "Four phrases, offered outward",
            kind = GuidedKind.Meditation,
            level = "Beginner",
            steps = listOf(
                GuidedStep(
                    title = "Settle and close the eyes",
                    instruction = "Sit comfortably and let the eyes close. " +
                        "Put a hand on the chest if that helps you stay with the feeling.",
                    seconds = 60,
                    cue = "Hand on the chest",
                ),
                GuidedStep(
                    title = "Offer the phrases to yourself",
                    instruction = "Repeat silently: may I be safe, may I be at ease, may I be healthy, may I live with ease. " +
                        "Say them slowly and let each one land. If you feel nothing, that is fine — the words are enough, " +
                        "and if you notice you are thinking, return to the phrases.",
                    seconds = 100,
                    cue = "May I be at ease",
                ),
                GuidedStep(
                    title = "Offer to someone you love",
                    instruction = "Bring to mind someone you care about easily and repeat: may you be safe, may you be at ease, may you be healthy, may you live with ease. " +
                        "If the mind wanders, come back to their face and to the phrases.",
                    seconds = 90,
                    cue = "May you be at ease",
                ),
                GuidedStep(
                    title = "Offer them to someone neutral",
                    instruction = "Bring to mind someone you neither like nor dislike — a person you passed today, a shopkeeper, a neighbour. " +
                        "Repeat the same four phrases for them. If you lose the thread, start again from the first phrase.",
                    seconds = 80,
                    cue = "May you be at ease",
                ),
                GuidedStep(
                    title = "Offer them to someone difficult",
                    instruction = "Bring to mind someone mildly difficult, not the hardest person in your life, and offer the same phrases. " +
                        "If it becomes too much, go back to someone you love. If you notice you are thinking, return to the phrases.",
                    seconds = 90,
                    cue = "May you be at ease",
                ),
                GuidedStep(
                    title = "Widen to all beings",
                    instruction = "Let the phrases spread outward: may all beings be safe, may all beings be at ease, may all beings be healthy, may all beings live with ease. " +
                        "You do not need to picture anyone in particular.",
                    seconds = 90,
                    cue = "May all beings be at ease",
                ),
                GuidedStep(
                    title = "Close with a breath",
                    instruction = "Let the phrases go and sit with whatever is left — warmth, numbness, irritation, nothing. " +
                        "All of it is a valid result. Open the eyes when you are ready.",
                    seconds = 90,
                    cue = "Whatever is here is enough",
                ),
            ),
            note = "This is a general practice, not therapy. Offering these phrases to yourself, or to someone difficult, can bring up grief or anger; " +
                "if it becomes too much, go back to someone you love, or stop. " +
                "If difficult material keeps coming up, speak to someone qualified.",
        ),
        GuidedSession(
            id = "yoga_nidra_short",
            title = "Yoga Nidra (short)",
            subtitle = "Lying down, from resolve to return",
            kind = GuidedKind.Meditation,
            level = "All levels",
            steps = listOf(
                GuidedStep(
                    title = "Lie down and get warm",
                    instruction = "Lie on your back with the arms a little away from the body, palms up and feet falling open. " +
                        "Cover yourself if you may get cold, and use a thin support under the head if the neck complains.",
                    seconds = 60,
                    cue = "Lie down, arms open",
                ),
                GuidedStep(
                    title = "Let the body settle",
                    instruction = "Take three slow breaths and let the body sink into the floor. " +
                        "From here on you do not need to move; if you must move, do it slowly and then come back.",
                    seconds = 60,
                    cue = "Sinking into the floor",
                ),
                GuidedStep(
                    title = "Set a short resolve",
                    instruction = "Choose one short positive sentence for the practice, such as I will rest, or I am enough. " +
                        "Repeat it silently three times, then let it go.",
                    seconds = 60,
                    cue = "One short resolve",
                ),
                GuidedStep(
                    title = "Rotate through the right side",
                    instruction = "Move attention quickly down the right side: thumb, fingers, palm, wrist, elbow, shoulder, hip, knee, ankle, toes. " +
                        "Say each part silently and move on; do not linger to feel it. " +
                        "If you lose the thread, start again from the right thumb.",
                    seconds = 75,
                    cue = "Right side, quickly",
                ),
                GuidedStep(
                    title = "Rotate through the left side",
                    instruction = "Same order on the left: thumb, fingers, palm, wrist, elbow, shoulder, hip, knee, ankle, toes. " +
                        "Keep the attention quick and light. If the mind wanders, come back to the left thumb.",
                    seconds = 75,
                    cue = "Left side, quickly",
                ),
                GuidedStep(
                    title = "Rotate through back and front",
                    instruction = "Sweep the back of the body, then the front: heels, calves, back of the thighs, buttocks, spine, shoulder blades, " +
                        "then the belly, chest, throat and face. If you notice you are thinking, return to the part you had reached.",
                    seconds = 75,
                    cue = "Back, then front",
                ),
                GuidedStep(
                    title = "Feel the whole body",
                    instruction = "Let attention rest on the whole body lying here. " +
                        "Notice the body as one shape, breathing.",
                    seconds = 75,
                    cue = "The whole body, still",
                ),
                GuidedStep(
                    title = "Watch the breath",
                    instruction = "Notice the breath at the nostrils without counting or controlling it. " +
                        "Each time the mind wanders, come back to the nostrils.",
                    seconds = 90,
                    cue = "The breath at the nostrils",
                ),
                GuidedStep(
                    title = "Feel the body heavy",
                    instruction = "Feel the body heavy, as if it were sinking into the floor, and repeat silently: heavy, heavy, heavy. " +
                        "You do not need to make the feeling happen; the words are enough.",
                    seconds = 75,
                    cue = "Heavy",
                ),
                GuidedStep(
                    title = "Feel the body light",
                    instruction = "Now feel the body light, as if it could float above the floor, and repeat silently: light, light, light. " +
                        "Neither feeling needs to be real.",
                    seconds = 75,
                    cue = "Light",
                ),
                GuidedStep(
                    title = "Rest in the space",
                    instruction = "Stop directing attention and let the mind be quiet. " +
                        "If thoughts come, watch them pass like clouds and let them go.",
                    seconds = 90,
                    cue = "Watching thoughts pass",
                ),
                GuidedStep(
                    title = "Return slowly",
                    instruction = "Become aware of the room, the light and the sounds. " +
                        "Move the fingers and toes, roll onto one side if that is comfortable, and sit up slowly. " +
                        "Take the resolve you set with you.",
                    seconds = 120,
                    cue = "Coming back slowly",
                ),
            ),
            note = "This is a general practice, not therapy. Yoga nidra is done lying down and many people fall asleep; " +
                "that is not a failure, but if you want to stay awake, sit up or keep one arm raised. " +
                "If lying still or the body rotation brings up difficult material, or you feel spaced out or detached from the body, " +
                "open your eyes, move, and stop for today. Do not do this practice while driving or after alcohol or drugs. " +
                "If difficult material keeps coming up, speak to someone qualified.",
        ),
        GuidedSession(
            id = "walking_meditation",
            title = "Walking meditation",
            subtitle = "Lifting, moving, placing",
            kind = GuidedKind.Meditation,
            level = "Beginner",
            steps = listOf(
                GuidedStep(
                    title = "Choose a straight path",
                    instruction = "Find a stretch of ten to fifteen steps you can walk back and forth, indoors or out. " +
                        "Put the phone away and pick a time and place where you will not be interrupted.",
                    seconds = 45,
                    cue = "Ten steps, back and forth",
                ),
                GuidedStep(
                    title = "Stand at the start",
                    instruction = "Stand still with the feet hip-width apart, hands clasped in front or behind, and the eyes lowered. " +
                        "Feel the weight of the body through both feet.",
                    seconds = 60,
                    cue = "Feet on the ground",
                ),
                GuidedStep(
                    title = "Walk at an easy pace",
                    instruction = "Walk at a pace slightly slower than normal, to the far end and back. " +
                        "Keep the eyes low so the mind is not pulled outward. If you notice you are thinking, that noticing is the practice — " +
                        "return to the feeling of the feet.",
                    seconds = 90,
                    cue = "Slower than usual",
                ),
                GuidedStep(
                    title = "Note lifting, moving, placing",
                    instruction = "With each step, note silently: lifting, moving, placing — one word as the foot leaves the ground, one as it travels, one as it lands. " +
                        "If the words tangle, use just lifting and placing.",
                    seconds = 150,
                    cue = "Lifting, moving, placing",
                ),
                GuidedStep(
                    title = "Turn slowly at the end",
                    instruction = "At the end of the path, stop, feel both feet, and turn slowly and deliberately. " +
                        "Notice the turning as its own movement before you walk back.",
                    seconds = 105,
                    cue = "Stop, turn, walk",
                ),
                GuidedStep(
                    title = "Stop and stand",
                    instruction = "Finish by standing still for a minute or two with the eyes closed, feeling the feet and the breath. " +
                        "Notice how the body feels after walking, without judging it.",
                    seconds = 150,
                    cue = "Standing, breathing",
                ),
            ),
            note = "This is a general practice, not therapy. Walk somewhere safe — not beside traffic, on uneven ground, or in the dark with the eyes lowered. " +
                "If walking slowly makes you feel exposed or agitated, walk at a normal pace or stop. " +
                "If difficult material keeps coming up, speak to someone qualified.",
        ),
    )
}
