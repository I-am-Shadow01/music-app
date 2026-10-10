package com.cid.musicapp.radio

import com.cid.musicapp.data.repository.Track

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
    fun select(candidates: List<Track>, alreadyQueued: List<Track>, limit: Int): List<Track> =
        selectWithRemainder(candidates, alreadyQueued, limit).accepted

    /**
     * ผลคัดเลือก: [accepted] = เพลงที่รับ, [remainder] = เพลงที่ "ยังไม่ได้ตัดสิน" และควรเก็บไว้ใช้รอบหน้า
     * (ถูกเลื่อนเพราะโควตาศิลปิน หรือยังไม่ได้พิจารณาเพราะรับครบ [limit] ก่อน) — เพลงที่ถูกปฏิเสธเพราะซ้ำ/เล่นไม่ได้ไม่อยู่ในนี้
     */
    class Selection(val accepted: List<Track>, val remainder: List<Track>)

    fun selectWithRemainder(candidates: List<Track>, alreadyQueued: List<Track>, limit: Int): Selection {
        if (limit <= 0 || candidates.isEmpty()) return Selection(emptyList(), candidates)

        val seenIds = HashSet<String>(alreadyQueued.size + candidates.size)
        val seenIdentities = ArrayList<TrackIdentity>(alreadyQueued.size + limit)
        alreadyQueued.forEach { track ->
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
        for (track in candidates) {
            if (accepted.size >= limit) break
            examined++
            if (track.id in seenIds || !isPlayable(track)) continue
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
        return Selection(accepted, stillDeferred + candidates.drop(examined))
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
