package com.cid.musicapp.radio

import com.cid.musicapp.data.repository.Track
import java.util.Locale

/**
 * "ตัวตนของเพลง" ที่ไม่สนว่าใครอัป/ตั้งชื่อคลิปยังไง — ใช้เทียบว่าสอง track เป็นเพลงเดียวกันไหม
 * (เช่น "Artist - Song (Official MV)" กับ "Song [Lyrics]" คนละช่อง ต้องถือว่าซ้ำกัน)
 * ตัดเหลือแค่ [songKey] (ชื่อเพลงที่ normalize แล้ว) กับ [artistTokens] (คำที่ระบุตัวศิลปิน/ช่อง)
 */
data class TrackIdentity(val songKey: String, val artistTokens: Set<String>) {

    /** ศิลปิน/ช่องใช้คำร่วมกันอย่างน้อยหนึ่งคำ — ถ้าฝั่งใดไม่มีข้อมูลศิลปินเลย ให้ถือว่าเข้ากันได้ (ไม่ตัดสินจากความไม่รู้) */
    fun sharesArtist(other: TrackIdentity): Boolean =
        artistTokens.isEmpty() || other.artistTokens.isEmpty() ||
            artistTokens.any { it in other.artistTokens }

    /** ใช้นับว่า "ศิลปินคนนี้โผล่ในหน้าต่างล่าสุดกี่ครั้ง" — ต่างจาก [sharesArtist] ตรงที่ไม่มีข้อมูลศิลปิน = ไม่ใช่ศิลปินเดียวกัน */
    fun isSameArtistAs(other: TrackIdentity): Boolean =
        artistTokens.isNotEmpty() && other.artistTokens.isNotEmpty() &&
            artistTokens.any { it in other.artistTokens }

    fun isSameSong(other: TrackIdentity, config: RadioConfig): Boolean {
        if (songKey.length < config.minSongKeyLength || songKey != other.songKey) return false
        // ชื่อสั้น/ทั่วไป (เช่น "Home") ชนกันได้ง่ายคนละเพลง → ต้องศิลปินตรงกันด้วย; ชื่อยาวพอถือว่าเฉพาะตัว
        return songKey.length >= config.artistFreeSongKeyLength || sharesArtist(other)
    }
}

/**
 * คีย์ศิลปินที่ normalize แล้ว (ตัวพิมพ์/คำประกอบอย่าง "Topic", "VEVO" ไม่มีผล)
 * @property displayName ชื่อที่เอาไปโชว์/ค้นหาได้ (ใช้ชื่อดิบที่เห็นจริง ไม่ใช่คีย์)
 * @property weight 1.0 = ชื่อช่องที่อัป; ต่ำกว่า = เดามาจากชื่อคลิป ("ศิลปิน - เพลง") เชื่อถือน้อยกว่า
 */
data class ArtistKey(val key: String, val displayName: String, val weight: Double)

/** สร้าง [TrackIdentity] จาก [Track] ตาม [RadioConfig] — ไม่มี state ใช้ซ้ำได้ทุก thread */
class TrackIdentityFactory(private val config: RadioConfig) {

    private val noiseWords = config.noiseWords.map { normalizeWord(it) }.toSet()
    private val versionWords = config.versionWords.map { normalizeWord(it) }.toSet()
    private val artistNoiseWords = config.artistNoiseWords.map { normalizeWord(it) }.toSet()

    /** ผลแยกชื่อคลิปเป็น "ส่วนเพลง" กับ "ส่วนศิลปินจากชื่อคลิป" (ถ้ามีรูปแบบ A - B) */
    private class Parsed(val songPart: String, val titleArtistPart: String?, val uploaderTokens: Set<String>)

    private fun parse(track: Track): Parsed {
        val cleanedTitle = stripDecorations(firstSegment(track.title))
        val parts = ARTIST_SONG_SPLITTER.split(cleanedTitle, limit = 2)
        val uploaderTokens = artistTokens(track.artist)
        if (parts.size != 2) return Parsed(cleanedTitle, null, uploaderTokens)

        val left = artistTokens(parts[0])
        val right = artistTokens(parts[1])
        // ปกติรูปแบบคือ "ศิลปิน - เพลง"; สลับเป็น "เพลง - ศิลปิน" เมื่อชื่อช่องไปตรงกับฝั่งขวาแต่ไม่ตรงฝั่งซ้าย
        val reversed = uploaderTokens.any { it in right } && uploaderTokens.none { it in left }
        return if (reversed) Parsed(parts[0], parts[1], uploaderTokens) else Parsed(parts[1], parts[0], uploaderTokens)
    }

    fun of(track: Track): TrackIdentity {
        val parsed = parse(track)
        val titleArtistTokens = parsed.titleArtistPart?.let { artistTokens(it) }.orEmpty()
        return TrackIdentity(
            songKey = songTokens(parsed.songPart).joinToString(" "),
            artistTokens = parsed.uploaderTokens + titleArtistTokens
        )
    }

    /**
     * ศิลปินของเพลงนี้ (ว่างได้ถ้าไม่รู้เลย): ชื่อช่องที่อัปมาก่อน (น้ำหนัก 1.0) แล้วตามด้วยศิลปินที่เดาจากชื่อคลิป
     * (น้ำหนักต่ำกว่า) เฉพาะเมื่อสั้นพอจะเป็นชื่อคน/วง และไม่ซ้ำกับชื่อช่อง — ช่องเนื้อเพลง/ช่องรวมเพลงจึงยังนับให้ศิลปินจริงได้
     */
    fun artistKeys(track: Track): List<ArtistKey> {
        val parsed = parse(track)
        val result = ArrayList<ArtistKey>(2)

        val uploaderKey = keyOf(parsed.uploaderTokens)
        if (uploaderKey.isNotEmpty()) result += ArtistKey(uploaderKey, track.artist.trim(), 1.0)

        val titlePart = parsed.titleArtistPart
        if (titlePart != null) {
            val tokens = artistTokens(titlePart)
            val titleKey = keyOf(tokens)
            if (titleKey.isNotEmpty() && titleKey != uploaderKey && tokens.size <= config.maxArtistTokens) {
                result += ArtistKey(titleKey, titlePart.trim(), config.titleArtistWeight)
            }
        }
        return result
    }

    /** คำ (token) ทั้งหมดของชื่อคลิป หลัง normalize — ใช้เทียบวลีต้องห้ามใน [RadioSelector] ด้วย */
    fun titleTokens(title: String): List<String> = tokenize(title)

    /** เรียงคำก่อนต่อกัน ให้ "Band The" กับ "The Band" ได้คีย์เดียวกัน */
    private fun keyOf(tokens: Set<String>): String = tokens.sorted().joinToString(" ")

    private fun songTokens(raw: String): List<String> {
        val withoutFeat = FEAT_SUFFIX.replace(raw.lowercase(Locale.ROOT), " ")
        return tokenize(withoutFeat).filterNot { it in noiseWords }
    }

    private fun artistTokens(raw: String): Set<String> =
        tokenize(raw).filterNot { it in artistNoiseWords }.toSet()

    /** ตัดวงเล็บที่ "มีคำประกอบ/คำบอกเวอร์ชัน" ทิ้งทั้งก้อน — วงเล็บที่เป็นส่วนของชื่อจริง เช่น "(G)I-DLE" ต้องเก็บไว้ */
    private fun stripDecorations(title: String): String =
        BRACKET_GROUP.replace(title) { match ->
            val inner = tokenize(match.value)
            if (inner.any { it in noiseWords || it in versionWords }) " " else match.value
        }

    private fun tokenize(raw: String): List<String> =
        raw.lowercase(Locale.ROOT)
            .replace(MV_SLASH, "mv")
            .replace(NON_WORD, " ")
            .trim()
            .split(' ')
            .filter { it.isNotEmpty() }

    private fun normalizeWord(word: String): String = word.lowercase(Locale.ROOT).trim()

    /** ชื่อคลิปมักต่อท้ายด้วย "| ช่อง/คำโปรโมต" หรือ "// ..." — เอาเฉพาะส่วนแรกที่มีตัวอักษร */
    private fun firstSegment(title: String): String =
        SEGMENT_SPLITTER.split(title).firstOrNull { it.any(Char::isLetterOrDigit) } ?: title

    private companion object {
        // ไม่ใช้ \p{L} อย่างเดียว: สระ/วรรณยุกต์ไทย (เช่น ี ่) เป็นหมวด Mn (\p{M}) ถ้าไม่เก็บไว้ คำไทยจะถูกหั่นเป็นเศษ
        val NON_WORD = Regex("[^\\p{L}\\p{M}\\p{N}]+")
        val MV_SLASH = Regex("\\bm\\s*/\\s*v\\b")
        val BRACKET_GROUP = Regex("[(\\[{（【「『《][^)\\]}）】」』》]*[)\\]}）】」』》]")
        val ARTIST_SONG_SPLITTER = Regex("\\s[-–—]\\s")
        val SEGMENT_SPLITTER = Regex("\\||//")
        val FEAT_SUFFIX = Regex("\\b(feat|ft|featuring)\\b.*$")
    }
}
