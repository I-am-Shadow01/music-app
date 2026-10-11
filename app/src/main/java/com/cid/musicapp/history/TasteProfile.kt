package com.cid.musicapp.history

import com.cid.musicapp.data.repository.Track
import com.cid.musicapp.radio.TrackIdentityFactory
import kotlin.math.pow

/** ศิลปินที่ฟังบ่อย: [name] เอาไปโชว์/ค้นหาได้, [score] คะแนนสะสม (ยิ่งฟังบ่อย/ใหม่ยิ่งสูง) */
data class ArtistTaste(val key: String, val name: String, val score: Double)

/**
 * ภาพรวมรสนิยมของผู้ใช้ ณ เวลาหนึ่ง คำนวณจากประวัติการฟัง + เพลงโปรด — immutable สร้างใหม่ทุกครั้งที่ต้องใช้
 * (ประวัติสูงสุดไม่กี่พันรายการ คำนวณเสร็จในไม่กี่มิลลิวินาที จึงไม่ต้องเก็บ aggregate แยกที่อาจเพี้ยนจากข้อมูลจริง)
 *
 * สร้างผ่าน [TasteProfileBuilder] เท่านั้น
 */
class TasteProfile internal constructor(
    private val artistScores: Map<String, Double>,
    private val artistNames: Map<String, String>,
    private val trackScores: Map<String, Double>,
    private val tracksById: Map<String, Track>,
    private val recentIds: Set<String>,
    private val dislikedIds: Set<String>,
    /** เพลงที่เพิ่งฟังจริงในช่วง cooldown (ใหม่สุดท้าย) — ใช้เทียบ "เพลงเดียวกันคนละคลิป" ข้ามเซสชัน */
    val recentTracks: List<Track>,
    private val identities: TrackIdentityFactory,
    private val config: TasteConfig
) {
    private val maxArtistScore: Double = artistScores.values.maxOrNull() ?: 0.0

    /** มีข้อมูลพอจะแนะนำอะไรได้ไหม */
    val hasData: Boolean get() = artistScores.isNotEmpty() || trackScores.values.any { it > 0.0 }

    /** ความชอบศิลปินของเพลงนี้ 0..1 (เทียบกับศิลปินที่ฟังบ่อยที่สุด = 1.0) — ไม่รู้จักศิลปิน = 0 */
    fun affinity(track: Track): Double {
        if (maxArtistScore <= 0.0) return 0.0
        val best = identities.artistKeys(track).maxOfOrNull { (artistScores[it.key] ?: 0.0) * it.weight } ?: 0.0
        return (best / maxArtistScore).coerceIn(0.0, 1.0)
    }

    /** เพิ่งฟังจริงไปในช่วง cooldown — ไม่ควรเสนอซ้ำ */
    fun isRecentlyPlayed(trackId: String): Boolean = trackId in recentIds

    /** ถูกข้ามบ่อยจนคะแนนต่ำกว่าเกณฑ์ และยังไม่เคยฟังจบเพื่อล้างข้อสงสัย — ไม่ควรเสนอ */
    fun isDisliked(trackId: String): Boolean = trackId in dislikedIds

    /** ศิลปินที่ฟังบ่อยที่สุด (เฉพาะที่คะแนนถึงเกณฑ์) เรียงจากมากไปน้อย */
    fun topArtists(limit: Int): List<ArtistTaste> =
        artistScores.entries
            .filter { it.value >= config.minArtistScore }
            .sortedByDescending { it.value }
            .take(limit)
            .map { ArtistTaste(it.key, artistNames[it.key] ?: it.key, it.value) }

    /** เพลงที่ชอบจริง (คะแนนบวก) เรียงจากมากไปน้อย ไม่รวมที่เพิ่งฟัง/ที่ไม่ชอบ — ไว้เลือกเป็นเพลงตั้งต้นของ Radio */
    fun seedCandidates(limit: Int): List<Track> =
        trackScores.entries
            .filter { it.value > 0.0 && it.key !in recentIds && it.key !in dislikedIds }
            .sortedByDescending { it.value }
            .take(limit)
            .mapNotNull { tracksById[it.key] }
}

/** คำนวณ [TasteProfile] จากประวัติ + เพลงโปรด ตามกฎใน [TasteConfig] */
class TasteProfileBuilder(
    private val config: TasteConfig = TasteConfig(),
    private val identities: TrackIdentityFactory
) {

    fun build(records: List<PlayRecord>, favorites: List<Track>, nowMillis: Long): TasteProfile {
        val artistScores = HashMap<String, Double>()
        val artistNames = HashMap<String, String>()
        val trackScores = HashMap<String, Double>()
        val tracksById = HashMap<String, Track>()
        val cooldownMillis = config.recentCooldownHours * MILLIS_PER_HOUR
        val recentIds = HashSet<String>()
        val recent = ArrayList<Track>()

        for (record in records) {
            val weight = weightOf(record)
            val decayed = weight * decay(nowMillis - record.playedAtMillis)
            val track = record.track
            tracksById[track.id] = track
            trackScores.merge(track.id, decayed, Double::plus)

            // ศิลปินได้คะแนนเฉพาะจากการฟังจริง — ข้ามเพลงเดียวไม่ควรทำให้ศิลปินทั้งคนเสียคะแนน
            if (weight > 0.0) {
                for (artist in identities.artistKeys(track)) {
                    artistScores.merge(artist.key, decayed * artist.weight, Double::plus)
                    artistNames[artist.key] = artist.displayName
                }
                if (nowMillis - record.playedAtMillis in 0..cooldownMillis) {
                    recentIds += track.id
                    recent += track
                }
            }
        }

        for (favorite in favorites) {
            tracksById.putIfAbsent(favorite.id, favorite)
            trackScores.merge(favorite.id, config.favoriteBoost, Double::plus)
            for (artist in identities.artistKeys(favorite)) {
                artistScores.merge(artist.key, config.favoriteBoost * artist.weight, Double::plus)
                artistNames.putIfAbsent(artist.key, artist.displayName)
            }
        }

        val disliked = trackScores.filterValues { it <= config.dislikeThreshold }.keys

        return TasteProfile(
            artistScores = artistScores,
            artistNames = artistNames,
            trackScores = trackScores,
            tracksById = tracksById,
            recentIds = recentIds,
            dislikedIds = disliked,
            recentTracks = recent.takeLast(config.recentTracksForIdentity),
            identities = identities,
            config = config
        )
    }

    /** ฟังจบ > ฟังจริง > ข้ามทิ้ง (ลบ) — ดูเกณฑ์ที่ [TasteConfig] */
    internal fun weightOf(record: PlayRecord): Double {
        val durationMs = (record.track.durationSeconds ?: config.fallbackDurationSeconds) * MILLIS_PER_SECOND
        val ratio = if (durationMs > 0) record.listenedMs.toDouble() / durationMs else 0.0
        return when {
            ratio >= config.completeRatio -> config.weightComplete
            ratio >= config.playRatio || record.listenedMs >= config.playMinListenMillis -> config.weightPlay
            else -> config.weightSkip
        }
    }

    /** ลดครึ่งหนึ่งทุก halfLifeDays; อายุติดลบ (นาฬิกาเครื่องเพี้ยน) ถือเป็น 0 ไม่ให้คะแนนเกินน้ำหนักจริง */
    private fun decay(ageMillis: Long): Double {
        val ageDays = ageMillis.coerceAtLeast(0L).toDouble() / MILLIS_PER_DAY
        return 0.5.pow(ageDays / config.halfLifeDays)
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
        const val MILLIS_PER_HOUR = 3_600_000L
        const val MILLIS_PER_DAY = 86_400_000.0
    }
}
