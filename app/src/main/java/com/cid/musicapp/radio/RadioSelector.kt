package com.cid.musicapp.radio

import com.cid.musicapp.data.repository.Track
import com.cid.musicapp.history.TasteProfile

/**
 * คัดเพลงแนะนำที่ "ควรเล่นต่อ" จากรายชื่อผู้สมัคร — pure logic ล้วน ไม่แตะเครือข่าย/Android
 *
 * กฎ (เรียงตามลำดับที่ผู้สมัครมา เพราะแหล่งอย่าง YouTube Mix เรียงความเกี่ยวข้องมาให้แล้ว):
 *  1. ไม่เอาเพลงที่อยู่ในคิวแล้ว (id เดียวกัน) หรือเป็น "เพลงเดียวกัน" กับที่เคยเล่น/รอเล่น แม้คนละช่อง
 *  2. ไม่เอาที่ไม่ใช่เพลงทั่วไป: ไม่รู้ความยาว (สตรีมสด), สั้น/ยาวเกินกำหนด, ชื่อเป็นอัลบั้มรวม/คาราโอเกะ ฯลฯ
 *  3. กระจายศิลปิน: รอบแรกไม่เอาศิลปินที่เต็มโควตาในหน้าต่างล่าสุดแล้ว; ถ้ายังไม่ครบ ค่อยเติมจากที่ถูกเลื่อนไว้
 *     (ดีกว่าคิวว่างในกรณีมีแต่ศิลปินเดียว)
 */
class RadioSelector(
    private val config: RadioConfig = RadioConfig(),
    private val identities: TrackIdentityFactory = TrackIdentityFactory(config)
) {

    // วลีต้องห้าม normalize เป็นคำคั่นด้วยช่องว่างครั้งเดียว (ใช้เทียบแบบเต็มวลี ไม่ใช่แค่เป็นส่วนของคำ)
    private val rejectPhrases: List<String> = config.rejectTitlePhrases
        .map { identities.titleTokens(it).joinToString(" ") }
        .filter { it.isNotEmpty() }

    /**
     * @param alreadyQueued เพลงที่อยู่ในคิวทั้งหมด **เรียงตามลำดับเล่น** (ท้ายสุด = เพลงที่จะเล่นหลังสุดตอนนี้)
     *                      ใช้ทั้งกันซ้ำและเป็นหน้าต่างนับศิลปินล่าสุด
     */
    fun select(
        candidates: List<Track>, alreadyQueued: List<Track>, limit: Int, taste: TasteProfile? = null
    ): List<Track> = selectWithRemainder(candidates, alreadyQueued, limit, taste).accepted

    /**
     * ผลคัดเลือก: [accepted] = เพลงที่รับ, [remainder] = เพลงที่ "ยังไม่ได้ตัดสิน" และควรเก็บไว้ใช้รอบหน้า
     * (ถูกเลื่อนเพราะโควตาศิลปิน หรือยังไม่ได้พิจารณาเพราะรับครบ [limit] ก่อน) — เพลงที่ถูกปฏิเสธเพราะซ้ำ/เล่นไม่ได้ไม่อยู่ในนี้
     *
     * @param taste รสนิยมจากประวัติการฟัง (null = ไม่ใช้) เมื่อมี: (ก) ตัดเพลงที่เพิ่งฟังไป/ที่เคยข้ามจนไม่ชอบ
     *   รวมถึง "เพลงเดียวกันคนละคลิป" กับที่เพิ่งฟังข้ามเซสชัน (ข) จัดอันดับใหม่โดยรวมลำดับความเกี่ยวข้องของแหล่งเข้ากับ
     *   ความชอบศิลปิน (ค) ยังผ่านกฎกระจายศิลปินเหมือนเดิม จึงไม่วนอยู่แต่ศิลปินโปรดคนเดียว
     */
    /** [accepted] = เพลงที่รับ, [remainder] = เพลงที่ยังไม่ได้ตัดสินและควรเก็บไว้ใช้รอบหน้า (เพลงที่ถูกปฏิเสธไม่อยู่ในนี้) */
    class Selection(val accepted: List<Track>, val remainder: List<Track>)

    fun selectWithRemainder(
        candidates: List<Track>, alreadyQueued: List<Track>, limit: Int, taste: TasteProfile? = null
    ): Selection {
        if (limit <= 0 || candidates.isEmpty()) return Selection(emptyList(), candidates)

        val ordered = if (taste == null) candidates else rankByTaste(candidates, taste)

        val seenIds = HashSet<String>(alreadyQueued.size + candidates.size)
        val seenIdentities = ArrayList<TrackIdentity>(alreadyQueued.size + limit)
        alreadyQueued.forEach { track ->
            seenIds += track.id
            seenIdentities += identities.of(track)
        }
        // เพลงที่เพิ่งฟังข้ามเซสชันนับเป็น "เคยเล่นแล้ว" ด้วย แต่ไม่ได้อยู่ในคิวปัจจุบัน จึงไม่นับเข้าหน้าต่างศิลปิน
        taste?.recentTracks?.forEach { track ->
            seenIds += track.id
            seenIdentities += identities.of(track)
        }
        val window = ArrayDeque<TrackIdentity>()
        alreadyQueued.takeLast(config.artistWindow).forEach { window.addLast(identities.of(it)) }

        val accepted = ArrayList<Track>(limit)
        val deferred = ArrayList<Pair<Track, TrackIdentity>>()

        fun accept(track: Track, identity: TrackIdentity) {
            accepted += track
            seenIds += track.id
            seenIdentities += identity
            window.addLast(identity)
            while (window.size > config.artistWindow) window.removeFirst()
        }

        var examined = 0
        for (track in ordered) {
            if (accepted.size >= limit) break
            examined++
            if (track.id in seenIds || !isPlayable(track)) continue
            if (taste != null && (taste.isRecentlyPlayed(track.id) || taste.isDisliked(track.id))) continue
            val identity = identities.of(track)
            if (seenIdentities.any { it.isSameSong(identity, config) }) continue

            if (artistCountInWindow(window, identity) >= config.maxSameArtistInWindow) {
                deferred += track to identity
            } else {
                accept(track, identity)
            }
        }

        // เติมจากที่เลื่อนไว้ — ต้องเช็คซ้ำใหม่ เพราะระหว่างนั้นอาจรับเพลงเดียวกัน/id เดียวกันเข้าไปแล้ว
        val stillDeferred = ArrayList<Track>()
        for ((track, identity) in deferred) {
            if (accepted.size >= limit) { stillDeferred += track; continue }
            if (track.id in seenIds || seenIdentities.any { it.isSameSong(identity, config) }) continue
            accept(track, identity)
        }
        return Selection(accepted, stillDeferred + ordered.drop(examined))
    }

    /**
     * ลำดับจากแหล่ง (ตัวแรก = 1.0 ลดลงเป็นเส้นตรง) + ความชอบศิลปิน × น้ำหนัก — เรียงมากไปน้อยแบบ stable
     * (คะแนนเท่ากันคงลำดับเดิมของแหล่ง) ศิลปินที่ฟังบ่อยจึงถูกยกขึ้น แต่เพลงที่แหล่งจัดไว้ต้นๆ ยังมีโอกาสชนะ
     */
    private fun rankByTaste(candidates: List<Track>, taste: TasteProfile): List<Track> {
        val size = candidates.size
        return candidates.withIndex()
            .sortedByDescending { (index, track) ->
                (size - index).toDouble() / size + config.tasteAffinityWeight * taste.affinity(track)
            }
            .map { it.value }
    }

    /** เล่นเป็นเพลงทั่วไปได้ไหม: ต้องรู้ความยาว อยู่ในช่วงที่กำหนด และชื่อไม่เข้าข่ายอัลบั้มรวม/คาราโอเกะ ฯลฯ */
    fun isPlayable(track: Track): Boolean {
        val duration = track.durationSeconds ?: return false
        if (duration < config.minDurationSeconds || duration > config.maxDurationSeconds) return false

        val padded = " " + identities.titleTokens(track.title).joinToString(" ") + " "
        return rejectPhrases.none { phrase -> padded.contains(" $phrase ") }
    }

    private fun artistCountInWindow(window: Collection<TrackIdentity>, candidate: TrackIdentity): Int =
        window.count { it.isSameArtistAs(candidate) }
}
