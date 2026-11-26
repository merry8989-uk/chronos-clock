package com.osintgram.chronos.wellness

/**
 * The guided yoga sessions: complete practices a beginner can follow alone, at
 * home, on a mat or on the floor, with nothing but a screen.
 *
 * Each session runs from the first posture to the last. Every step says what to
 * do, how to keep it safe, how long to stay, and one short line to keep on
 * screen while the eyes are on the posture. Nothing here is advanced: no
 * headstand, no shoulder stand, no deep backbends and no breath retention, and
 * every posture with a known caution carries it in the session's note and in
 * the step that uses it.
 *
 * This is a general practice, not a substitute for a teacher. Move only as far
 * as feels steady, and read the note before starting.
 */
object GuidedYoga {

    val sessions: List<GuidedSession> = listOf(

        GuidedSession(
            id = "surya_namaskar",
            title = "Surya Namaskar",
            subtitle = "One full round of twelve steps, with the breath",
            kind = GuidedKind.Yoga,
            level = "Beginner",
            steps = listOf(
                GuidedStep(
                    title = "Stand in prayer pose",
                    instruction = "Stand with the feet hip-width apart and the weight even on both feet, and bring the palms together at the chest with the elbows soft. Let the shoulders drop away from the ears before you begin.",
                    seconds = 20,
                    cue = "Breathe out, stand tall",
                ),
                GuidedStep(
                    title = "Raise the arms overhead",
                    instruction = "Breathe in and sweep the arms out and up, palms facing each other. Keep the knees soft and the ribs stacked over the hips rather than pushing the chest forward or tipping the head back.",
                    seconds = 25,
                    cue = "Breathe in, lengthen the waist",
                ),
                GuidedStep(
                    title = "Fold forward",
                    instruction = "Breathe out and hinge from the hips, letting the hands come down beside the feet or onto the shins. Bend the knees as much as you need so the back stays long, and let the head hang heavy instead of pulling it down.",
                    seconds = 25,
                    cue = "Breathe out, soften the knees",
                ),
                GuidedStep(
                    title = "Step the right foot back",
                    instruction = "Breathe in and step the right foot back into a lunge, right knee above the right ankle and the back knee off the floor if you can. Keep the front knee over the ankle, not past the toes, and the hips facing the front.",
                    seconds = 25,
                    cue = "Breathe in, knee over the ankle",
                ),
                GuidedStep(
                    title = "Step back to a plank",
                    instruction = "Breathe out and step the left foot back beside the right, so the body makes one straight line from the heels to the head. Hands under the shoulders, and keep the hips level with the shoulders rather than sagging towards the floor.",
                    seconds = 20,
                    cue = "Breathe out, one straight line",
                ),
                GuidedStep(
                    title = "Lower knees, chest, chin",
                    instruction = "Breathe out and bend the elbows close to the ribs, lowering the knees first, then the chest, then the chin. Keep the elbows tucked in against the body and the hips slightly lifted; if the shoulders are tight, come down only as far as is comfortable.",
                    seconds = 25,
                    cue = "Breathe out, elbows close to the ribs",
                ),
                GuidedStep(
                    title = "Rise into cobra",
                    instruction = "Breathe in and press the tops of the feet and the hands down, lifting the chest with the elbows soft and the shoulders sliding away from the ears. Lift only as high as the low back is happy with, and keep the pubic bone on the floor.",
                    seconds = 25,
                    cue = "Breathe in, shoulders down",
                ),
                GuidedStep(
                    title = "Lift into downward dog",
                    instruction = "Breathe out and press the hands down, lifting the hips up and back into an upside-down V. Bend the knees generously so the back can lengthen, and let the heels stay off the floor if the hamstrings are tight.",
                    seconds = 30,
                    cue = "Breathe out, bend the knees, back long",
                ),
                GuidedStep(
                    title = "Step the right foot forward",
                    instruction = "Breathe in and step the right foot forward between the hands, into the same lunge as before with the other leg back. Check that the front knee is over the ankle and the back leg is strong rather than collapsing.",
                    seconds = 25,
                    cue = "Breathe in, hips facing front",
                ),
                GuidedStep(
                    title = "Step in and fold forward",
                    instruction = "Breathe out and step the left foot forward to meet the right, then fold over the legs with the knees soft and the head heavy. Let the hands rest beside the feet or on the shins, and keep the neck relaxed.",
                    seconds = 25,
                    cue = "Breathe out, head heavy",
                ),
                GuidedStep(
                    title = "Rise with the arms overhead",
                    instruction = "Breathe in and stand up, sweeping the arms out and up, with the knees soft and the ribs over the hips. Do not arch back further than is comfortable for the low back.",
                    seconds = 20,
                    cue = "Breathe in, reach up",
                ),
                GuidedStep(
                    title = "Return to prayer pose",
                    instruction = "Breathe out and bring the palms together at the chest, standing tall with the weight even on both feet. Take one slow breath here before the next round.",
                    seconds = 20,
                    cue = "Breathe out, hands to the chest",
                ),
                GuidedStep(
                    title = "Take one more round",
                    instruction = "Take a second full round of the same twelve steps, this time stepping the left foot back first. Let the breath lead every movement, and stop after this round rather than adding a third.",
                    seconds = 45,
                    cue = "Left foot back first, breath leads",
                ),
            ),
            note = "Two rounds is plenty for a beginner. This round is taught without breath retention: never hold the breath, and if you cannot breathe smoothly through a step, come out of it. Keep the knees down and the elbows tucked in when you lower to the floor, spread the fingers and press through the base of each finger to protect the wrists, and keep the head heavy in the forward folds so the neck is never pulled. Stop if you feel pain, which is not the same as stretch, and check with a doctor or a qualified teacher before starting if you are pregnant, or if you have an injury or condition affecting the wrists, shoulders, low back, knees or blood pressure.",
        ),

        GuidedSession(
            id = "morning_warmup",
            title = "Morning warm-up",
            subtitle = "Ten small movements to wake the neck, shoulders and spine",
            kind = GuidedKind.Yoga,
            level = "Beginner",
            steps = listOf(
                GuidedStep(
                    title = "Sit tall and breathe",
                    instruction = "Sit on a chair with the feet flat, or cross-legged on the floor with the sitting bones grounded, and let the spine grow tall. Rest the hands on the thighs and take a few slow breaths in and out through the nose.",
                    seconds = 20,
                    cue = "Sit tall, breathe out slowly",
                ),
                GuidedStep(
                    title = "Tilt the head sideways",
                    instruction = "Let the right ear fall towards the right shoulder without lifting the shoulder to meet it, and keep the face pointing forward. Stay for a few breaths, then change sides halfway through; never pull the head down with the hand.",
                    seconds = 30,
                    cue = "Ear to shoulder, shoulder heavy",
                ),
                GuidedStep(
                    title = "Roll the shoulders",
                    instruction = "Circle both shoulders slowly, lifting them towards the ears, rolling them back and letting them drop. Keep the circles small and the arms heavy, and reverse the direction halfway through.",
                    seconds = 30,
                    cue = "Breathe out as they drop",
                ),
                GuidedStep(
                    title = "Circle the wrists",
                    instruction = "Stretch the arms forward and circle both wrists slowly, then reverse the direction halfway through. Keep the circles small and the fingers relaxed rather than clenched.",
                    seconds = 25,
                    cue = "Small circles, fingers soft",
                ),
                GuidedStep(
                    title = "Turn the chest right",
                    instruction = "Sit tall and turn the chest to the right, bringing the left hand to the right knee and the right hand behind you. Turn from the waist and keep the chin level with the chest; do not force the neck to look further round than the chest has turned.",
                    seconds = 20,
                    cue = "Chin level, turn from the waist",
                ),
                GuidedStep(
                    title = "Turn the chest left",
                    instruction = "Come back through the centre and turn to the left, right hand to the left knee and left hand behind you. Keep the spine tall and the breath steady, and turn only as far as is comfortable.",
                    seconds = 20,
                    cue = "Chin level, no forcing",
                ),
                GuidedStep(
                    title = "Come to all fours",
                    instruction = "Turn onto the hands and knees, with the wrists under the shoulders and the knees under the hips. Spread the fingers and press through the base of each finger, and fold a towel under the knees if they are tender.",
                    seconds = 10,
                    cue = "Wrists under shoulders, knees under hips",
                ),
                GuidedStep(
                    title = "Round and arch the spine",
                    instruction = "Breathe out and round the back up, tucking the chin gently towards the chest; breathe in and arch, lifting the chest and the tailbone. Move slowly with the breath and let the neck follow the spine rather than cranking it.",
                    seconds = 40,
                    cue = "Breathe out to round, in to arch",
                ),
                GuidedStep(
                    title = "Stand and bend sideways",
                    instruction = "Come up to standing with the feet hip-width apart, reach the right arm overhead and bend to the left, keeping the chest open and the hips steady. Swap sides halfway through, and do not lean forward or twist as you bend.",
                    seconds = 40,
                    cue = "Reach long, bend from the waist",
                ),
                GuidedStep(
                    title = "Stand tall and breathe",
                    instruction = "Stand with the weight even on both feet and the arms relaxed by the sides. Take three slow breaths and notice the shoulders drop a little further on each exhale.",
                    seconds = 20,
                    cue = "Feet rooted, shoulders soft",
                ),
            ),
            note = "These are warm-up movements, so keep every one small and slow, and keep the neck especially gentle: nothing here should be pulled or cranked, and the head never goes back further than the chest. Stop at the first sign of dizziness, tingling in the arms or pain, which is not the same as stretch. Check with a doctor or a qualified teacher before starting if you are pregnant, or if you have an injury or condition affecting the neck, shoulders, wrists or low back.",
        ),

        GuidedSession(
            id = "standing_flow",
            title = "Standing flow",
            subtitle = "Tree, warrior one and two, and triangle, both sides",
            kind = GuidedKind.Yoga,
            level = "Beginner",
            steps = listOf(
                GuidedStep(
                    title = "Stand in mountain pose",
                    instruction = "Stand with the feet hip-width apart and parallel, weight even, arms by the sides and the crown of the head lifting. Keep the knees soft rather than locked, and let the breath settle before you balance.",
                    seconds = 20,
                    cue = "Feet rooted, knees soft",
                ),
                GuidedStep(
                    title = "Balance in tree, right side",
                    instruction = "Shift the weight onto the left leg and place the sole of the right foot on the inner left calf, below the knee, with the toes pointing down. Bring the palms together at the chest and fix the eyes on a point at eye level; if you wobble, rest one hand on a wall.",
                    seconds = 35,
                    cue = "Foot below the knee, eyes on one point",
                ),
                GuidedStep(
                    title = "Balance in tree, left side",
                    instruction = "Release the right foot and shake the leg out, then shift the weight onto the right leg and place the sole of the left foot on the inner right calf, below the knee. Palms at the chest, gaze steady, and keep the standing knee soft.",
                    seconds = 35,
                    cue = "Never press on the knee joint",
                ),
                GuidedStep(
                    title = "Step the left foot back",
                    instruction = "From standing, step the left foot back about a metre, turn the left toes out slightly and bend the right knee towards a right angle. Keep the right knee over the ankle, not past the toes, and the hips facing the front.",
                    seconds = 10,
                    cue = "Right knee over the ankle",
                ),
                GuidedStep(
                    title = "Hold warrior one, right side",
                    instruction = "Sweep both arms overhead with the palms facing each other and square the hips towards the front of the mat. Keep the back leg strong and the ribs from flaring forward; if the low back complains, take the arms wider apart.",
                    seconds = 30,
                    cue = "Hips square, ribs down",
                ),
                GuidedStep(
                    title = "Turn out to warrior two",
                    instruction = "Bring the arms down to shoulder height, turn the back foot in and the hips to the side, and keep the front knee bent over the ankle. Keep the torso upright over the hips and check that the front knee does not roll inwards.",
                    seconds = 30,
                    cue = "Front knee over the ankle, arms level",
                ),
                GuidedStep(
                    title = "Straighten into triangle, right side",
                    instruction = "Straighten the front leg, tip the torso over the front leg and rest the lower hand on the shin or on a block, reaching the top arm up. Keep both legs strong and the chest open to the side; do not collapse onto the bottom hip or round the back.",
                    seconds = 30,
                    cue = "Long both sides, chest open",
                ),
                GuidedStep(
                    title = "Come up and swap legs",
                    instruction = "Press through the feet to come up, turn to face the front, then step the left foot forward and the right foot back, bending the left knee. Check that the left knee sits over the left ankle before you continue.",
                    seconds = 10,
                    cue = "Left knee forward this time",
                ),
                GuidedStep(
                    title = "Hold warrior one, left side",
                    instruction = "Sweep both arms overhead, square the hips to the front and keep the back leg firm. Hold only as long as you can keep the front knee over the ankle and the breath even.",
                    seconds = 30,
                    cue = "Hips square, breath even",
                ),
                GuidedStep(
                    title = "Open warrior two, left side",
                    instruction = "Bring the arms to shoulder height, turn the hips and the back foot out, and keep the left knee bent over the ankle. Keep the shoulders above the hips rather than leaning towards the front leg.",
                    seconds = 30,
                    cue = "Shoulders over hips, knee over ankle",
                ),
                GuidedStep(
                    title = "Straighten into triangle, left side",
                    instruction = "Straighten the left leg and tip the torso over it, resting the lower hand on the shin or a block with the top arm reaching up. Keep the chest open and the back long, and come out if the hamstring feels sharp rather than stretched.",
                    seconds = 30,
                    cue = "Chest open, back long",
                ),
                GuidedStep(
                    title = "Stand and breathe",
                    instruction = "Step the feet together and come back to standing with the arms by the sides and the weight even on both feet. Take three slow breaths and let the legs settle before you walk away.",
                    seconds = 20,
                    cue = "Stand tall, breathe out slowly",
                ),
            ),
            note = "These balancing postures are easier and safer with a wall within reach, and it is worth having one beside you for the tree. Never press the lifted foot against the side of the standing knee: place it on the calf below the knee or on the inner thigh, and keep the standing knee soft rather than locked. In every lunge, warrior and triangle, keep the front knee over the ankle and not past the toes, and come down if you feel dizzy or if the knee pinches. Stop if you feel pain, which is not the same as stretch. Check with a doctor or a qualified teacher before starting if you are pregnant, or if you have an injury or condition affecting the knees, hips, ankles or blood pressure.",
        ),

        GuidedSession(
            id = "seated_forward_folds",
            title = "Seated forward folds",
            subtitle = "Dandasana, janu sirsasana, paschimottanasana, baddha konasana, supine twist",
            kind = GuidedKind.Yoga,
            level = "Beginner",
            steps = listOf(
                GuidedStep(
                    title = "Sit in dandasana",
                    instruction = "Sit with both legs straight in front, sitting bones grounded and the spine tall, hands beside the hips. If the low back rounds, sit on a folded blanket and keep a small bend in the knees; do not let the legs roll outwards.",
                    seconds = 25,
                    cue = "Sit tall, legs active",
                ),
                GuidedStep(
                    title = "Fold over the left leg",
                    instruction = "Bend the right knee out to the side with the sole of the right foot against the inner left thigh, then hinge forward over the left leg from the hip. Keep the back long rather than rounding to reach the foot, and hold the shin or a strap instead of pulling on the toes.",
                    seconds = 35,
                    cue = "Hinge from the hip, back long",
                ),
                GuidedStep(
                    title = "Fold over the right leg",
                    instruction = "Come up slowly, change legs so the left foot is against the inner right thigh, and fold over the right leg. Keep the extended knee soft and the foot flexed, and stop the fold where the back still stays long.",
                    seconds = 35,
                    cue = "Knee soft, back long",
                ),
                GuidedStep(
                    title = "Stretch both legs forward",
                    instruction = "Come up and straighten both legs in front of you again, with the hands beside the hips. Sit tall, lengthen the waist, and take one slow breath before folding forward.",
                    seconds = 10,
                    cue = "Sit tall, lengthen the waist",
                ),
                GuidedStep(
                    title = "Fold over both legs",
                    instruction = "Hinge forward from the hips over both legs, keeping the knees slightly bent and the back long, and rest the hands on the shins or hold a strap around the feet. Do not round the low back or pull with the arms to go deeper.",
                    seconds = 35,
                    cue = "Knees soft, no pulling",
                ),
                GuidedStep(
                    title = "Bring the soles together",
                    instruction = "Come up, bend the knees and bring the soles of the feet together, letting the knees fall open and the hands rest on the feet or on the floor behind you. Do not press the knees down; keep the spine tall and the hips heavy.",
                    seconds = 30,
                    cue = "Let the knees fall, no pressing",
                ),
                GuidedStep(
                    title = "Lie down and twist right",
                    instruction = "Lower onto your back, draw the knees towards the chest, then let both knees fall to the right while the shoulders stay on the floor. Turn the head to the left only if the neck is comfortable, and keep the twist gentle rather than forcing the knees down.",
                    seconds = 35,
                    cue = "Shoulders down, knees heavy",
                ),
                GuidedStep(
                    title = "Twist to the left",
                    instruction = "Bring the knees back through the centre and let them fall to the left, with the shoulders on the floor and the arms open. Keep the breath slow and even, and come back through the centre before you sit up.",
                    seconds = 35,
                    cue = "Shoulders down, breathe slowly",
                ),
            ),
            note = "Forward folds load the hamstrings and the low back, so sit on a folded blanket or cushion if the back rounds when the legs are straight, and keep a small bend in the knees rather than forcing the legs flat. Never bounce, and never pull yourself deeper with the arms; come out slowly if the back of the knee feels pinched. In the supine twist, keep both shoulders on the floor and turn the head away from the knees only if the neck is comfortable. Stop if you feel pain, which is not the same as stretch. Check with a doctor or a qualified teacher before starting if you are pregnant, or if you have an injury or condition affecting the knees, hips, hamstrings, low back or sciatic nerve.",
        ),

        GuidedSession(
            id = "bedtime_stretch",
            title = "Bedtime stretch",
            subtitle = "Seven quiet shapes to let the day go",
            kind = GuidedKind.Yoga,
            level = "Beginner",
            steps = listOf(
                GuidedStep(
                    title = "Lie down and settle",
                    instruction = "Lie on your back with the legs long and the arms a little away from the body, palms up. Let the bed or the mat take the whole weight of the body and take three slow breaths before you move.",
                    seconds = 25,
                    cue = "Let the floor take your weight",
                ),
                GuidedStep(
                    title = "Hug the knees in",
                    instruction = "Draw both knees towards the chest and hold the shins or the backs of the thighs, keeping the low back and the neck on the floor. Rock gently from side to side if it feels good, and keep the hold light rather than squeezing.",
                    seconds = 35,
                    cue = "Hold lightly, low back down",
                ),
                GuidedStep(
                    title = "Let the knees fall right",
                    instruction = "Still holding the knees, let both knees fall to the right while the left shoulder stays on the floor. Keep the twist small and the breath slow, and slide a pillow under the knees if the low back or the knees complain.",
                    seconds = 35,
                    cue = "Left shoulder down, twist small",
                ),
                GuidedStep(
                    title = "Let the knees fall left",
                    instruction = "Bring the knees back through the centre and let them fall to the left, keeping the right shoulder on the floor. Do not force the knees down, and turn the head away only if the neck is comfortable.",
                    seconds = 35,
                    cue = "Right shoulder down, no forcing",
                ),
                GuidedStep(
                    title = "Bring the legs up",
                    instruction = "Come back to the centre, then bring the hips close to a wall and rest the legs up it with the arms open at the sides. If the hamstrings or hips are tight, move a little further from the wall or put a folded blanket under the hips, and come out slowly if you feel light-headed.",
                    seconds = 40,
                    cue = "Legs up, arms open, breathe",
                ),
                GuidedStep(
                    title = "Breathe out longer than in",
                    instruction = "Slide away from the wall and lie flat, then breathe in through the nose for a count of four and out for a count of six, without holding the breath at any point. Keep the exhale smooth rather than forced, and let the belly move with it.",
                    seconds = 45,
                    cue = "In for four, out for six",
                ),
                GuidedStep(
                    title = "Rest before sleep",
                    instruction = "Let the arms rest by the sides and the legs fall open, with the eyes closed and the jaw soft. Stay here as long as you like, then roll onto your side and get up slowly.",
                    seconds = 30,
                    cue = "Eyes closed, jaw soft",
                ),
            ),
            note = "Nothing here should feel like effort, and nothing needs a mat: every shape works on a bed or on the floor. Legs up the wall is a mild inversion, so come out of it slowly and keep it short, and skip it if you have uncontrolled high blood pressure, glaucoma or a history of stroke, or are in the later stages of pregnancy, and ask a doctor if you are unsure. Stop if you feel pain, which is not the same as stretch, and check with a doctor or a qualified teacher before starting if you are pregnant, or if you have an injury or condition affecting the neck, back, hips or blood pressure.",
        ),

        GuidedSession(
            id = "savasana",
            title = "Savasana",
            subtitle = "Settle, scan, rest, return",
            kind = GuidedKind.Yoga,
            level = "All levels",
            steps = listOf(
                GuidedStep(
                    title = "Lie down and settle",
                    instruction = "Lie on your back with the legs a little apart and the feet falling open, arms away from the body with the palms up. Move the shoulders down away from the ears and tuck the chin very slightly so the neck is long; use a thin pillow under the head or under the knees if the low back or the neck is uncomfortable.",
                    seconds = 45,
                    cue = "Legs heavy, palms up",
                ),
                GuidedStep(
                    title = "Scan from feet to head",
                    instruction = "Move your attention slowly from the feet up through the legs, hips, belly, chest, arms, neck and face, letting each part go heavy as you pass it. Do not try to fix or change anything you notice, and if the mind wanders, bring it back to the next body part.",
                    seconds = 90,
                    cue = "Move slowly, change nothing",
                ),
                GuidedStep(
                    title = "Rest in stillness",
                    instruction = "Let the breath be natural and the body completely still, with no effort to control the breathing. If thoughts come, let them pass without following them, and stay for the whole rest without checking the time.",
                    seconds = 120,
                    cue = "Natural breath, body still",
                ),
                GuidedStep(
                    title = "Return slowly",
                    instruction = "Wiggle the fingers and toes, bend the knees and roll onto one side for a few breaths, then press up to sitting slowly. Give yourself a moment before standing up so you do not get dizzy.",
                    seconds = 30,
                    cue = "Roll to the side, come up slowly",
                ),
            ),
            note = "Savasana is not sleep, but it is fine to drift; if you do fall asleep, simply come back to the practice when you wake. Keep the room warm enough and come up slowly at the end to avoid dizziness. Stop if you feel pain, which is not the same as stretch, and check with a doctor or a qualified teacher before starting if you are pregnant, or if you have an injury or a condition that makes lying flat uncomfortable, including some back and breathing conditions: prop the chest and knees up, or lie on your side instead.",
        ),
    )
}
