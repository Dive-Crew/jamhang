package pleos.jamhang.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth

/**
 * 항해 일지 로컬 저장 (JSON 파일 1개).
 * 하루 1~2건 규모라 Room 대신 파일로 둔다 — 프로필 분리 시 파일 이름만 프로필별로 나누면 된다.
 */
class DiveLogStore(context: Context) {

    private val file = File(context.filesDir, "dive_logs.json")
    private val _logs = MutableStateFlow(load())
    val logs: StateFlow<List<DiveLog>> = _logs.asStateFlow()

    val totalDepth: Int get() = depthOf(_logs.value)

    fun add(log: DiveLog) = save(_logs.value + log)

    fun updateMemo(id: Long, memo: String) = save(_logs.value.map { if (it.id == id) it.copy(memo = memo) else it })

    fun hasCreditOn(date: LocalDate) = _logs.value.any { it.credited && it.date == date }

    fun countOn(date: LocalDate) = _logs.value.count { it.date == date }

    fun replaceAll(logs: List<DiveLog>) = save(logs)

    @Synchronized
    private fun save(list: List<DiveLog>) {
        val sorted = list.sortedBy { it.startedAt }
        _logs.value = sorted
        val arr = JSONArray()
        sorted.forEach { arr.put(it.toJson()) }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file)
    }

    private fun load(): List<DiveLog> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map { arr.getJSONObject(it).toLog() }
    }.getOrDefault(emptyList())

    companion object {
        fun depthOf(logs: List<DiveLog>) = logs.count { it.credited } * UnlockConfig.DEPTH_PER_DAY

        /** 하루 1겹: 날짜별로 묶고 최신순 */
        fun layers(logs: List<DiveLog>): List<StrataLayer> =
            logs.groupBy { it.date }.map { (date, day) ->
                val last = day.maxBy { it.startedAt }
                StrataLayer(
                    date = date,
                    cabin = last.cabin,
                    count = day.size,
                    totalMin = day.sumOf { it.restMin },
                    lastEndType = last.endType,
                    credited = day.any { it.credited },
                )
            }.sortedByDescending { it.date }

        fun monthStats(logs: List<DiveLog>, month: YearMonth = YearMonth.now()): Pair<Int, Int> {
            val inMonth = logs.filter { YearMonth.from(it.date) == month }
            return inMonth.size to inMonth.sumOf { it.restMin }
        }

        private fun DiveLog.toJson() = JSONObject()
            .put("id", id).put("startedAt", startedAt).put("endedAt", endedAt)
            .put("plannedMin", plannedMin).put("cabin", cabin.name).put("restSec", restSec)
            .put("endType", endType.name).put("credited", credited).put("memo", memo)

        private fun JSONObject.toLog() = DiveLog(
            id = getLong("id"),
            startedAt = getLong("startedAt"),
            endedAt = getLong("endedAt"),
            plannedMin = getInt("plannedMin"),
            cabin = runCatching { Cabin.valueOf(getString("cabin")) }.getOrDefault(Cabin.LIGHT_SEA),
            restSec = getInt("restSec"),
            endType = runCatching { EndType.valueOf(getString("endType")) }.getOrDefault(EndType.COMPLETED),
            credited = getBoolean("credited"),
            memo = optString("memo", ""),
        )
    }
}

/**
 * 시연 시드 (PDF 피드백 1): 어제까지 36겹(3,600m), 오늘은 비어 있음
 * → 대표 흐름이 "오늘 첫 기록"으로 끝나며 지층 +1 모션, 3,700m가 된다.
 */
object DemoSeed {
    fun build(today: LocalDate = LocalDate.now()): List<DiveLog> {
        val zone = ZoneId.systemDefault()
        val out = mutableListOf<DiveLog>()
        var id = 1L
        // 최근 날짜는 Figma 01 예시와 같은 모양으로
        val recent = listOf(
            Triple(1, Cabin.LIGHT_SEA, listOf(10 to EndType.COMPLETED)),
            Triple(2, Cabin.LIGHT_SEA, listOf(10 to EndType.COMPLETED)),
            Triple(3, Cabin.SEAWEED, listOf(20 to EndType.COMPLETED)),
            Triple(5, Cabin.SEAWEED, listOf(15 to EndType.COMPLETED, 10 to EndType.COMPLETED)),
            Triple(6, Cabin.LIGHT_SEA, listOf(7 to EndType.EARLY)),
            Triple(8, Cabin.LIGHT_SEA, listOf(10 to EndType.COMPLETED)),
            Triple(10, Cabin.SEAWEED, listOf(15 to EndType.COMPLETED)),
            Triple(12, Cabin.LIGHT_SEA, listOf(10 to EndType.COMPLETED)),
        )
        val days = recent.map { it.first }.toMutableList()
        var d = 13
        while (days.size < 36) { days += d; d += 2 }
        days.forEach { ago ->
            val spec = recent.firstOrNull { it.first == ago }
                ?: Triple(
                    ago, if (ago % 3 == 0) Cabin.SEAWEED else Cabin.LIGHT_SEA,
                    // 항해 일지는 하루 1~2건만: 네 날에 한 번 두 번 쉰 날
                    if (ago % 4 == 1) listOf(10 to EndType.COMPLETED, 15 to EndType.COMPLETED) else listOf(10 to EndType.COMPLETED),
                )
            spec.third.forEachIndexed { i, (min, type) ->
                val start = today.minusDays(ago.toLong()).atTime(18 + i, 30).atZone(zone).toInstant().toEpochMilli()
                out += DiveLog(
                    id = id++, startedAt = start, endedAt = start + min * 60_000L,
                    plannedMin = if (type == EndType.EARLY) 10 else min, cabin = spec.second,
                    restSec = min * 60, endType = type, credited = i == 0,
                )
            }
        }
        return out
    }
}
