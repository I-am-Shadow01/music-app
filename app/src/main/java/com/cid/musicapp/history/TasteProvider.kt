package com.cid.musicapp.history

import com.cid.musicapp.config.AppSettings
import com.cid.musicapp.radio.TrackIdentityFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn

/**
 * จุดเดียวที่ส่วนอื่นของแอปขอ [TasteProfile] — ประกอบจากประวัติการฟัง + เพลงโปรด
 * ผลเป็น null เมื่อผู้ใช้ปิด "จดจำประวัติการฟัง" ไว้ หรือยังไม่มีข้อมูลพอจะแนะนำอะไร (ผู้เรียกใช้พฤติกรรมปกติแทนได้เลย)
 */
class TasteProvider(
    private val history: ListeningHistory,
    private val appSettings: AppSettings,
    identities: TrackIdentityFactory,
    config: TasteConfig = TasteConfig(),
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) {
    private val builder = TasteProfileBuilder(config, identities)

    /**
     * โปรไฟล์ล่าสุดเสมอ — คำนวณใหม่ทุกครั้งที่ประวัติ/เพลงโปรด/การตั้งค่าเปลี่ยน
     * คำนวณบน Default dispatcher เพราะต้องไล่ทั้งประวัติ (ไม่กี่พันรายการ) ไม่ควรกินเวลา Main
     */
    fun profileFlow(): Flow<TasteProfile?> =
        combine(
            history.entries,
            appSettings.historyEnabledFlow,
            appSettings.favoriteTracksFlow
        ) { entries, enabled, favorites ->
            if (!enabled) null
            else builder.build(entries, favorites, nowMillis()).takeIf { it.hasData }
        }.flowOn(Dispatchers.Default)

    /** โปรไฟล์ ณ ตอนนี้ครั้งเดียว (ใช้ตอน Radio เติมคิว) */
    suspend fun current(): TasteProfile? = profileFlow().first()
}
