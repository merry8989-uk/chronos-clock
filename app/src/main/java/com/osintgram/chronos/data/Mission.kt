package com.osintgram.chronos.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * A wake-up mission that has to be completed before an alarm can be dismissed.
 * [count] means "how much": problems to solve, tiles to tap, steps to walk.
 */
data class MissionSpec(
    val type: String,
    val count: Int = 1,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("type", type)
        .put("count", count)

    companion object {
        fun fromJson(o: JSONObject): MissionSpec = MissionSpec(
            type = o.optString("type", "math"),
            count = o.optInt("count", 1).coerceIn(1, 999),
        )

        fun listToJson(list: List<MissionSpec>): JSONArray =
            JSONArray().also { arr -> list.forEach { arr.put(it.toJson()) } }

        fun listFromJson(arr: JSONArray?): List<MissionSpec> {
            if (arr == null) return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { fromJson(it) }
            }.take(5)
        }

        /** Every mission the ring screen can enforce. */
        val catalog: List<MissionInfo> = listOf(
            MissionInfo(
                type = "math", title = "Math", subtitle = "Solve arithmetic",
                defaultCount = 2, unit = "problems", countOptions = listOf(1, 2, 3, 4, 5),
                group = MissionInfo.BRAIN,
            ),
            MissionInfo(
                type = "typing", title = "Typing", subtitle = "Type the phrase exactly",
                defaultCount = 1, unit = "phrases", countOptions = listOf(1, 2, 3),
                group = MissionInfo.BRAIN,
            ),
            MissionInfo(
                type = "color_tiles", title = "Color Tiles", subtitle = "Find the odd tile",
                defaultCount = 3, unit = "tiles", countOptions = listOf(1, 3, 5, 10),
                group = MissionInfo.BRAIN,
            ),
            MissionInfo(
                type = "arrange_shapes", title = "Arrange Shapes", subtitle = "Tap the shapes in order",
                defaultCount = 4, unit = "shapes", countOptions = listOf(3, 4, 5),
                group = MissionInfo.BRAIN,
            ),
            MissionInfo(
                type = "find_numbers", title = "Find Numbers", subtitle = "Tap the target number",
                defaultCount = 3, unit = "numbers", countOptions = listOf(1, 3, 5),
                group = MissionInfo.BRAIN,
            ),
            MissionInfo(
                type = "find_alphabets", title = "Find Alphabets", subtitle = "Tap the target letter",
                defaultCount = 3, unit = "letters", countOptions = listOf(1, 3, 5),
                group = MissionInfo.BRAIN,
            ),
            MissionInfo(
                type = "memory", title = "Memory Tiles", subtitle = "Repeat the sequence",
                defaultCount = 3, unit = "rounds", countOptions = listOf(1, 2, 3, 5),
                group = MissionInfo.BRAIN,
            ),
            MissionInfo(
                type = "shake", title = "Shake", subtitle = "Shake the phone",
                defaultCount = 15, unit = "shakes", countOptions = listOf(5, 10, 15, 20, 30),
                group = MissionInfo.BODY,
            ),
            MissionInfo(
                type = "step", title = "Step", subtitle = "Walk to switch it off",
                defaultCount = 25, unit = "steps", countOptions = listOf(10, 20, 25, 30, 50),
                group = MissionInfo.BODY,
            ),
        )

        fun info(type: String): MissionInfo? = catalog.firstOrNull { it.type == type }
    }
}

data class MissionInfo(
    val type: String,
    val title: String,
    val subtitle: String,
    val defaultCount: Int,
    val unit: String,
    val countOptions: List<Int>,
    val group: String,
) {
    companion object {
        const val BRAIN = "Wake up your brain"
        const val BODY = "Wake up your body"
    }
}
