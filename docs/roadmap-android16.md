# Roadmap: Optimize + QoL สำหรับ Android 16

เอกสารนี้เขียนจากการอ่านโค้ดจริงของ `main` (commit `b3949be`) และอ่านเอกสารทางการของ Android/Media3 ณ วันที่ 29 ต.ค. 2026
เป้าหมาย: ทำให้แอปลื่น/เบา/เชื่อถือได้ขึ้น และเพิ่มฟังก์ชันที่ใช้งานจริงบ่อย โดยใช้ความสามารถของ Android 16 ได้เต็มที่
(ใช้เองบนเครื่อง Android 16 เครื่องเดียว ไม่ต้องรองรับ Android 12–15)

> **ข้อจำกัดที่ต้องรู้ก่อนอ่านแผน:** ทั้งเครื่องผู้ช่วยและเครื่องคุณไม่มี Android SDK — การ compile/ทดสอบทำผ่าน GitHub Actions เท่านั้น
> และ **ยังไม่มีใครรันแอปบนเครื่องจริงหรือวัดด้วย profiler** ตัวเลข/ผลที่เขียนว่า "น่าจะดีขึ้น" ด้านล่างคือข้อสรุปจากการอ่านโค้ด ไม่ใช่ผลวัด
> ทุกเฟสจึงมีรายการ "ต้องทดสอบบนเครื่อง" แยกไว้ และจะไม่ถูกเขียนว่าผ่านจนกว่าจะมีคนลองจริง

---

## 1. สิ่งที่พบจากโค้ดปัจจุบัน

| # | สิ่งที่พบ | หลักฐาน | ผลกระทบ |
|---|---|---|---|
| F1 | APK ที่แจกให้เครื่องคือ **debug build** (`isMinifyEnabled=false`, release เซ็นด้วย debug key, CI สั่งแค่ `assembleDebug`) | `app/build.gradle.kts`, `build.yml` | เอกสาร Compose ระบุว่า debug mode มี performance cost; ไม่มี R8 → APK ใหญ่ (มี `material-icons-extended` ทั้งก้อน), โค้ดไม่ถูก optimize |
| F2 | `compileSdk/targetSdk = 34`, `minSdk = 26` + core library desugaring | `app/build.gradle.kts` | ยังไม่ได้ใช้ behavior/ความสามารถของ Android 15–16; มี `Build.VERSION` branch และ desugaring ที่ไม่จำเป็นถ้า minSdk 36 |
| F3 | Media3 **1.4.1** (ล่าสุดที่ stable คือ **1.11.1**), Gradle 8.7, AGP 8.5.2, Kotlin 2.0.20, Compose BOM 2024.09 | `app/build.gradle.kts`, `build.gradle.kts`, wrapper | พลาดการแก้บั๊ก gapless/offload/wake lock/stuck-player (1.9–1.11), ต้องอัปเพื่อใช้ API ใหม่ |
| F4 | ตัวนับตำแหน่งเพลงเขียน `StateFlow<PlaybackUiState>` ทั้งก้อนทุก 500 ms ขณะเล่น (แม้จอดับ) และ `AppNavHost` collect state ทั้งก้อน | `PlayerController.startPositionTicker`, `AppNavHost` | Scaffold + แถบนำทาง + mini player recompose ทุก 0.5 วิ; CPU wake ตลอดตอนจอดับ |
| F5 | ExoPlayer ถือ **เพลงเดียว** (`setMediaItem`) คิวจริงอยู่ใน `PlayerController`; ปุ่ม next/prev จากแจ้งเตือน/บลูทูธต้องผ่าน `PlaybackBridge` + `ForwardingPlayer` บังคับ command | `PlayerController`, `PlaybackService`, `PlaybackBridge` | ทุกการต่อเพลงมี gap (resolve + บัฟเฟอร์ใหม่); ไม่มี preload/gapless จริง; โค้ดซับซ้อน (837 บรรทัด); เอกสารเดิมมี TODO(debt) เรื่องปุ่มแจ้งเตือนอยู่แล้ว |
| F6 | คิว/ตำแหน่งเล่นไม่ถูกเก็บ (process ตาย = หาย), ไม่มี `onPlaybackResumption` | `restructure-review.md` (ข้อ user-decision), `PlaybackService` | กดเล่นจากหูฟังบลูทูธหลังแอปถูกฆ่าไม่ได้ผล |
| F7 | เพลงโปรด/คำค้นล่าสุดเก็บเป็น JSON string ใน DataStore; `favoriteTracksFlow` **decode JSON ทั้งก้อนทุกครั้งที่ DataStore เปลี่ยน** (รวมถึงตอนเปลี่ยนธีม) และแต่ละ collector decode แยกกัน | `AppSettings.kt` | เสียงานซ้ำเมื่อ favorites เยอะ (สูงสุด 500); ไม่มีโครงสำหรับ playlist |
| F8 | Waveform ใช้ `android.media.audiofx.Visualizer` ซึ่ง**ต้องขอสิทธิ์ `RECORD_AUDIO`**; เอกสารเดิมระบุว่ายังไม่เคยยืนยันบนเครื่อง | `WaveformVisualizer.kt`, manifest, `restructure-review.md` | แอปเพลงขอสิทธิ์ไมค์ = น่าสงสัย; พึ่ง audioSessionId ผ่าน MediaController |
| F9 | `NewValve:1.5` (JitPack) ห่อ NewPipeExtractor + OkHttp client ของตัวเอง แยกจาก `AppContainer.okHttpClient`; ยกเลิกคำขอกลางทางไม่ได้ (TODO(debt) เดิม) | `build.gradle.kts`, `MusicRepository` | connection pool สองชุด; ยกเลิกค้นหา/resolve แล้ว network ยังวิ่งต่อ; ตั้ง locale/region ของ YouTube ไม่ได้ |
| F10 | `PlayerScreen` เป็น Column เดียวไม่เลื่อน มี `LazyColumn(weight(1f))` ซ้อนใน Column ที่ `weight(1f)` + ไม่จัดการ insets (Scaffold ไม่มี topBar) | `PlayerScreen.kt`, `AppNavHost.kt` | พอบังคับ edge-to-edge (target 36) หัวจอจะไปอยู่ใต้ status bar; จอเตี้ย/แนวนอนคิวจะหาย |
| F11 | ไอคอนแอปไม่มี `<monochrome>` layer | `mipmap-anydpi-v26/ic_launcher.xml` | ไม่มี themed icon |
| F12 | ผลค้นหา/Radio ใช้ search แบบไม่มี content filter และ `Track.id` = URL เต็มของวิดีโอ | `MusicRepository.search`, `StreamItemMapper` | ได้คลิปปนเพลง; ต้องระวังตอนทำ Media ID/ลิงก์/ฐานข้อมูล |
| F13 | อัปเดตแอป: error เครือข่ายถูกแปลงเป็น "ไม่มีอัปเดต" (TODO(debt)), ไม่มี progress, ไม่ตรวจ hash | `AppUpdateChecker`, `ApkInstaller` | ผู้ใช้ไม่รู้ว่าเช็กไม่สำเร็จ |

| F14 | **Android developer verification**: ตามหน้าช่วยเหลือของ Google ตั้งแต่ **30 ก.ย. 2026** ในไทย/อินโดนีเซีย/สิงคโปร์ แอปจากนักพัฒนาที่ยังไม่ยืนยันจะ "ติดตั้งใหม่ไม่ได้" บนเครื่องที่ผ่านการรับรอง (ขยายทั่วโลกปี 2027); มีทางเลือกบัญชี *limited distribution* (ไม่มีค่าธรรมเนียม ไม่ต้องใช้บัตรประชาชน แจกได้ถึง 20 เครื่อง) และ "advanced flow" สำหรับผู้ใช้ขั้นสูง; ADB ยังลงแอปที่ไม่ยืนยันบนเครื่องตัวเองได้ | หน้าช่วยเหลือ Google (Understanding Android developer verification) | แอปนี้ติดตั้งเองด้วย APK และอัปเดตผ่านตัวติดตั้งในแอป → **เสี่ยงลงใหม่/อัปเดตไม่ได้**. หน้าเดียวกัน**ไม่ได้ระบุ**ว่าแอปที่ติดตั้งอยู่แล้ว/การอัปเดตโดนหรือไม่ และไม่ได้อธิบายว่าผูกกับ package name+signing key อย่างไร → ยังไม่รู้ |

เรื่องที่ **ไม่ใช่ปัญหา** (ตรวจแล้ว): การค้นหา/หน้าถัดไป/cancel race, cache URL แบบ LRU+TTL, ประวัติการฟังเขียนไฟล์แบบ atomic+debounce, ตัวคัดเพลง Radio — เอกสาร `restructure-review.md`, `radio-design.md`, `taste-design.md` ครอบคลุมแล้วและมี unit test (9 ไฟล์) แผนนี้ **ไม่รื้อ** ส่วนเหล่านี้

---

## 2. การตัดสินใจหลัก (แก้ได้ทั้งหมด — บอกถ้าไม่เห็นด้วย)

| เรื่อง | ตัดสินใจ | เหตุผล | ถอยกลับยังไง |
|---|---|---|---|
| ระดับ SDK | `compileSdk=36`, `targetSdk=36`, **`minSdk=36`** | คุณบอกว่าใช้ Android 16 เท่านั้น → ตัด desugaring + `SDK_INT` branch, ใช้ API ใหม่ตรงๆ | แก้บรรทัดเดียวใน `build.gradle.kts` |
| Build tool | Gradle **8.13** + AGP **8.13.x** + Kotlin **2.2.x** (ไม่ขึ้น AGP 9) | เอกสาร AGP: API 36 ต้องการ ≥ 8.9.1, 36.1 ต้อง 8.13; Media3 1.11 ถูก build ด้วย Kotlin 2.2.0; AGP 9 เปลี่ยน DSL/Kotlin plugin มาก ไม่มีประโยชน์ที่ผู้ใช้เห็น | ถ้า Compose BOM ล่าสุดบังคับ compileSdk 37 ให้ถอย BOM ลง (AGP จะฟ้อง error ชัดเจนใน CI) |
| Media3 | **1.11.1** | stable ล่าสุด; มี wake lock default, stuck-player detection, แก้ gapless/offload | pin เวอร์ชันใน catalog |
| ที่เก็บข้อมูล | **ไม่ใช้ Room** — ทำ `LibraryStore` (JSON ไฟล์เดียว + in-memory `StateFlow` + atomic write + debounce) ตามแพทเทิร์นเดียวกับ `FileHistoryStore` | ข้อมูลระดับส่วนตัว (≤ พันรายการ) ไม่คุ้ม KSP/codegen ที่ตรวจในเครื่องไม่ได้; ไม่เพิ่ม dependency | เปิด TODO(debt) เดิม "ย้ายไป Room" ไว้ใน `DEBT.md` |
| NewPipe | เขียน `Downloader` เอง (OkHttp client ตัวเดียวกับทั้งแอป) แทน NewValve | ยกเลิกคำขอได้จริง, connection pool เดียว, ตั้ง Localization/ContentCountry ได้ | revert เป็น NewValve ได้ |
| ลายเซ็น/versionCode | **ไม่เปลี่ยน keystore, ไม่เปลี่ยน `versionCode=1`** | เปลี่ยน key = ต้องถอนแอป+เสียข้อมูล; versionCode คงที่ทำให้ลง APK ตัวเก่าทับกลับ (rollback) ได้ | — |
| APK ที่แจก | CI ทำ **release APK (R8 แบบระวัง)** เป็นตัวหลักใน GitHub Release + เก็บ debug APK เป็น artifact สำรอง | แก้ F1 โดยไม่เสี่ยงจนใช้ไม่ได้ | ปิด `isMinifyEnabled` ได้ |
| การ merge | ผู้ช่วยเปิด PR + รอ CI เขียว, **คุณเป็นคน merge** | push เข้า `main` = ปล่อยอัปเดตเข้าเครื่องคุณอัตโนมัติ (`build.yml` publish release) | — |
| Android Auto / Cast / offline download | **ไม่ทำ** | ใช้เองบนมือถือ; download เพลงออฟไลน์ต้องตัดสินเรื่อง storage+นโยบาย (ค้างเป็น user-decision เดิม) — มี **cache เสียงแบบจำกัดขนาด** แทน | ถ้าต้องการบอกได้ |

---

## 3. โครงสร้างเป้าหมาย

```
UI (Compose, edge-to-edge, predictive back)
   │  StateFlow แยกส่วน: PlaybackState (เปลี่ยนน้อย) / PositionState (เปลี่ยนถี่ — ใช้เฉพาะ leaf composable)
   ▼
PlayerController  ← บางลง: เป็น MediaController client + แปลงเป็น StateFlow (API ฝั่ง UI คงเดิมเท่าที่ทำได้)
   │  MediaController (in-process)
   ▼
PlaybackService (MediaSessionService)  ← เจ้าของคิวจริง
 ├─ ExoPlayer: playlist จริง + preload + shuffle/repeat ของ player เอง
 │    └─ DataSource: ResolvingDataSource → StreamResolver (NewPipe) → CacheDataSource(SimpleCache LRU)
 ├─ QueueCoordinator: เติม Radio, แทรก "เล่นต่อจากนี้", บันทึก/กู้คิว (ไฟล์ atomic), sleep timer
 ├─ SessionCallback: onAddMediaItems, onConnectAsync (media button prefs: ❤ / shuffle / repeat), onPlaybackResumption
 ├─ ListenRecorder → ListeningHistory (ของเดิม)
 └─ AudioBufferSink → WaveformBus (ไม่ต้อง RECORD_AUDIO)

Data: AppSettings(DataStore: ค่าตั้งค่าเท่านั้น) · LibraryStore(favorites+playlists) · ListeningHistory · queue.json · SimpleCache
Net:  OkHttpClient ตัวเดียว → NewPipe Downloader + OkHttpDataSource + อัปเดตแอป
```

ค่าตัวเลข/ชื่อ/ขนาดใหม่ทั้งหมดไปอยู่ใน `AppConstants`/resources/version catalog (ตามกฎ no-hardcode); เวอร์ชันไลบรารีย้ายเข้า `gradle/libs.versions.toml`

---

## 4. เฟส (หนึ่งเฟส = หนึ่ง PR, ไล่ตามลำดับ; ทุก PR ต้อง CI เขียว: `testDebugUnitTest` + build)

### เฟส 0 — Toolchain + พื้นฐาน Android 16 (พฤติกรรมเดิม)
- **งานของคุณ (ทำก่อนเฟสอื่น ผู้ช่วยทำแทนไม่ได้เพราะเป็นบัญชี Google ของคุณ):** ลงทะเบียน `com.cid.musicapp` + signing key (`keystore/debug.keystore`) ใน Android Developer Console แบบ *limited distribution* แล้วเช็กว่าเครื่องคุณลง/อัปเดต APK ของ build ปัจจุบันได้ปกติ — ผู้ช่วยยังไม่ได้อ่านขั้นตอนลงทะเบียนจริง (เปิดหน้าช่วยเหลือแล้วไม่มีรายละเอียดส่วนนี้) จึงไม่รับรองขั้นตอน; ถ้าผูกกับ signing key นี่คือเหตุผลอีกข้อที่ **ห้ามเปลี่ยน keystore**
- ปรับ in-app updater ให้ตรวจ error ตอนติดตั้งและบอกผู้ใช้ชัดๆ (ไม่ใช่เงียบ) เมื่อระบบปฏิเสธการติดตั้ง
- Gradle 8.13, AGP 8.13.x, Kotlin 2.2.x, version catalog, `compileSdk/targetSdk/minSdk = 36`, ตัด desugaring และ `SDK_INT` branch (`MainActivity`, `Theme`, ที่ขอ POST_NOTIFICATIONS)
- Media3 → 1.11.1, Compose BOM (ไล่ลงจาก 2026.09.00 ถ้าติด compileSdk 37), lifecycle/activity/navigation/datastore/coil อัปเป็นล่าสุดที่ compile ผ่าน
- **Edge-to-edge**: `enableEdgeToEdge()` + จัดการ insets ทุกหน้า (โดยเฉพาะ `PlayerScreen`); **Predictive back** (`enableOnBackInvokedCallback`, ย้ายการพับ player ไป `BackHandler`)
- ไอคอน `<monochrome>`; `DEBT.md` รวม TODO(debt) ทั้งหมด; `dependabot.yml`; CI: cache Gradle + `concurrency` ยกเลิก run ซ้ำ + พิมพ์ขนาด APK
- ตรวจ 16 KB page size: สคริปต์ใน CI เช็กว่า APK ไม่มีไฟล์ `.so` (ถ้ามีให้ตรวจ alignment)
- เอกสารที่ต้องเปิดก่อนแก้: AGP release notes 8.13, Media3 1.5→1.11 breaking changes (`onConnect` default, threading ของ session, `MediaNotification.Provider`), Navigation predictive back
- ✅ เกณฑ์ผ่าน: CI เขียว, unit test เดิมผ่านครบ, APK ใหม่ลงทับของเดิมได้
- 📱 ต้องลองบนเครื่อง: เปิดแอป/ค้นหา/เล่น/next/prev, แจ้งเตือน, หน้า Player ไม่ชน status bar, ปัดย้อนกลับแล้วเห็นแอนิเมชัน

### เฟส 1 — Performance + ท่อ release
- **R8 แบบระวัง**: `isMinifyEnabled` + `isShrinkResources`, `-dontobfuscate`, keep ทั้งก้อน `org.schabi.newpipe.extractor.**`, Rhino, jsoup, nanojson, protobuf (ต่อยอดกฎเดิมใน `proguard-rules.pro`) → ให้ R8 ตัดเฉพาะส่วนอื่น (ใหญ่สุดคาดว่าคือ icons-extended); CI ทำ release APK เซ็นด้วย key เดิม; updater เลือก asset ตามชื่อไฟล์ (ไม่ใช่ `.apk` ตัวแรก)
- แยก **PositionState** ออกจาก `PlaybackUiState`; ticker ทำงานเฉพาะเมื่อมีคนดู (UI STARTED) และอ่านค่าใน leaf composable (slider, progress ของ mini player) → ไม่ recompose ทั้งหน้า
- `Downloader` ของเราเอง + OkHttp ตัวเดียว + ยกเลิกคำขอได้ + Localization/ContentCountry ตั้งได้ (ค่าเริ่มต้นตามที่เลือกในตั้งค่า); `OkHttpDataSource` สำหรับ ExoPlayer
- Coil: `ImageRequest` ที่ `remember` ไว้ใน list, กำหนด size, ตั้ง cache ของ loader ผ่าน config
- หน้า **Diagnostics** (ในโหมดนักพัฒนา): เวลาเปิดแอปล่าสุดจาก `ApplicationStartInfo`, ขนาด cache, เวอร์ชัน NewPipe/Media3, crash log ล่าสุด (เขียนลงไฟล์ในแอป + ปุ่มแชร์) — ไว้ให้คุณเก็บหลักฐานได้โดยไม่ต้องมี Android Studio
- ตรวจ: มี `androidx.profileinstaller` ใน dependency tree หรือยัง (ยังไม่รู้) — ถ้าไม่มีให้เพิ่ม เพราะแอปที่ลงเอง (sideload) ไม่ได้ baseline profile จาก Play
- เอกสารที่ต้องเปิด: shrink-code/R8 keep rules, ApplicationStartInfo, NewPipeExtractor `Downloader` API
- 📱 ต้องลองบนเครื่อง (สำคัญสุดของเฟสนี้): **ค้นหา → เล่น → Radio เติมคิว → รูปปกขึ้น** บน release APK (ถ้า R8 ตัดอะไรที่ extractor ต้องใช้ จะเห็นเฉพาะตอนรันจริง) — ถ้าพัง ใช้ debug APK สำรองจาก Actions artifact ได้ทันที

### เฟส 2 — แกนเครื่องเล่น (คิวย้ายเข้า service) ← ใหญ่และเสี่ยงสุด
- `QueueModel` (pure Kotlin, มี unit test): สร้างจาก logic เดิมของ `PlayerController` (order/shuffle/radio-suggested/remove/move) เพื่อพิสูจน์พฤติกรรมเดิมด้วยเทสต์ก่อนย้าย
- `StreamResolver` + `ResolvingDataSource`: MediaItem ใช้ URI กลาง (`Track.id`), resolve เป็นลิงก์จริงตอนโหลด; เจอ HTTP 403/หมดอายุ → ล้าง cache ของเพลงนั้นแล้ว resolve ใหม่ 1 ครั้ง
- ExoPlayer playlist จริง + `PreloadConfiguration` → ต่อเพลงแทบไม่มี gap; shuffle/repeat ใช้ของ player; เก็บ `RepeatMode`/`PlaybackMode` เดิม
- `SimpleCache` (LRU, ขนาดตั้งได้ในตั้งค่า, cache key = id+itag ไม่ใช่ URL เพราะ URL หมดอายุ) → เล่นซ้ำเร็ว/ออฟไลน์ได้เฉพาะที่เคยฟัง
- บันทึก/กู้คิว+ตำแหน่ง (ไฟล์ atomic, debounce) + `onPlaybackResumption` + `MediaButtonReceiver` → กดเล่นจากหูฟังบลูทูธหลังแอปถูกฆ่าแล้วเล่นต่อได้
- `setMediaButtonPreferences`: ปุ่ม ❤ / shuffle / repeat ในแจ้งเตือน; ลบ `PlaybackBridge` + `ForwardingPlayer` hack (ปิด TODO(debt) เดิม)
- sleep timer ย้ายเข้า service (ไม่หายเมื่อ UI ตาย) + ตัวเลือก "จบเพลงนี้แล้วหยุด"
- Waveform ใหม่ด้วย `AudioBufferSink` ใน audio pipeline → **ถอด `RECORD_AUDIO`**
- Error ที่แยกประเภท (ออฟไลน์ / ติดภูมิภาค / จำกัดอายุ / ถูกบล็อก) แทนข้อความ exception ดิบ
- `PlayerController` เหลือเป็น client บางๆ; ฝั่ง UI แก้น้อยที่สุด (เก็บ signature เดิม)
- เอกสารที่ต้องเปิด: `onAddMediaItems`/`onConnectAsync`/`onPlaybackResumption`, `ResolvingDataSource` (เธรดที่รัน — เอกสารหน้า customization ไม่ระบุ ต้องอ่าน API reference), `PreloadConfiguration`, `CacheDataSource`, `AudioBufferSink`/`TeeAudioProcessor`
- ✅ เกณฑ์ผ่าน: เทสต์ `QueueModel` ครอบคลุมเคสในรายการ regression ของ `restructure-review.md` ข้อ 3–5 (คิวซ้ำ id, ลบ/สลับ/shuffle/repeat) + CI เขียว
- 📱 ต้องลองบนเครื่อง: ต่อเพลงแล้วเงียบกี่วินาที (เทียบก่อน/หลัง), next/prev จากแจ้งเตือน, หูฟังหลุด, ฆ่าแอปแล้วกดเล่นจากบลูทูธ, ปิดเน็ตกลางเพลง/กลางคิว Radio, sleep timer ตอนปิดจอ, ❤ จากแจ้งเตือน
- **แนวทางลดความเสี่ยง:** PR นี้ไม่ถูก merge จนกว่าคุณจะลง APK ของ PR (ดาวน์โหลดจาก Actions artifact — PR ไม่ปล่อย release) ลองแล้วบอกผล

### เฟส 3 — คลังเพลง + การค้นหา
- `LibraryStore`: favorites + **playlists** (สร้าง/เปลี่ยนชื่อ/ลบ/เรียงลำดับ), migrate favorites จาก DataStore แบบครั้งเดียว (ไม่ลบของเก่าจนกว่าจะยืนยันว่าอ่านใหม่ได้), `favoriteIds` เป็น `StateFlow<Set>` ที่คำนวณครั้งเดียว
- แท็บ **คลัง** (เพลงโปรด / เพลย์ลิสต์ / ประวัติการฟังล่าสุด) + เมนู "เพิ่มเข้าเพลย์ลิสต์" ใช้ร่วมทุกหน้า (ค้นหา/คิว/คลัง/Player) ผ่าน component เดียว
- **สำรอง/กู้คืนข้อมูล** เป็นไฟล์ JSON ผ่าน Storage Access Framework (favorites, playlists, ค่าตั้งค่า, ประวัติ) — ใช้ตอนต้องถอนแอป
- **เปิดลิงก์ YouTube / แชร์เข้าแอป**: รับ `ACTION_SEND` (เมนูแชร์) เป็นทางหลัก + `ACTION_VIEW` สำหรับ `youtube.com/watch`, `youtu.be`, `music.youtube.com`, ลิงก์ playlist → เลือก เล่นเลย / เล่นต่อจากนี้ / เข้าคิว / บันทึกลงเพลย์ลิสต์ (ลิงก์ youtube.com มักถูกผูกกับแอป YouTube จึงน่าจะโผล่แค่ในตัวเลือก "เปิดด้วย" — **ยังไม่ยืนยันพฤติกรรมบน Android 16**)
- ค้นหา: คำแนะนำระหว่างพิมพ์ (suggestion extractor), ชิปกรอง "เพลง / วิดีโอ" (content filter ของ extractor — ต้องตรวจชื่อ filter จากเอกสารเวอร์ชันที่ pin), ค้นในเพลงโปรด
- เอกสารที่ต้องเปิด: NewPipeExtractor `SuggestionExtractor` + `YoutubeSearchQueryHandlerFactory` filters (เวอร์ชันที่ pin), SAF `CreateDocument/OpenDocument`, พฤติกรรม intent filter/เมนูแชร์ของ Android 16

### เฟส 4 — UI/UX
- Now Playing ใหม่: mini player ขยายเป็นหน้าเต็ม (ปัดขึ้น/ลง + predictive back), **คิวเป็น bottom sheet** ลากสลับได้เสถียร (ใช้ไลบรารี reorder ถ้า resolve ผ่านบน CI; ไม่งั้นเขียนเอง), layout ปรับตามขนาดจอ/แนวนอน (WindowSizeClass)
- สีพื้นหลังจากปกอัลบั้ม (Palette) — เพิ่ม dependency เล็ก 1 ตัว ตามที่โค้ดเดิมเคยเลี่ยงไว้เพราะ "ไม่อยากเพิ่ม dependency"
- Material 3 Expressive เฉพาะส่วนที่ stable ใน material3 ที่ใช้ (เช่น progress/loading) — **ยังไม่ยืนยันว่า API ไหนต้อง opt-in**; ตรวจจากเอกสารตอนทำ
- **Widget หน้าจอหลัก** (Glance): ปก/ชื่อ/ปุ่ม play-pause-next; **App shortcuts** (เล่นเพลงที่ชอบ / เพลงโปรดแบบสุ่ม / ค้นหา)
- Accessibility: contentDescription ครบ, ขนาดปุ่มสัมผัส, ไม่ใช้สีอย่างเดียวสื่อสถานะ, ลบการพึ่ง `announceForAccessibility` (ถูก deprecate ใน Android 16)

### เฟส 5 — ตั้งค่า/QoL เพิ่มเติม
- **โหมดประหยัดเน็ต**: คุณภาพเสียงแยกตาม Wi-Fi / เน็ตมือถือ (ตรวจ `NET_CAPABILITY_NOT_METERED`) + หยุด prefetch บนเน็ตมือถือ (ตั้งได้)
- Skip silence, ปุ่มเปิด Equalizer ของระบบ (ถ้าเครื่องมี), ความเร็วเล่นแบบรักษา pitch (ตรวจพฤติกรรมของ `PlaybackParameters`)
- ภูมิภาค/ภาษาเนื้อหาของ YouTube ตั้งได้ (ต่อจากเฟส 1)
- ระบบอัปเดต: ผลแบบ typed (ไม่ใช่ "ไม่มีอัปเดต" เมื่อเน็ตพัง), progress การโหลด, แสดง release notes, ตรวจ hash ถ้า API ให้มา — **อัปเดตแบบไม่ต้องกดยืนยัน** ผ่าน `PackageInstaller` (ต้องอ่านเงื่อนไข installer-of-record ของ Android 14–16 ก่อน; ยังไม่รู้ว่าเครื่องคุณเข้าเงื่อนไขไหม)
- (เสริม ทำถ้าเวลาเหลือ) เนื้อเพลงแบบ sync จาก LRCLIB — ต้องตรวจ API/เงื่อนไขการใช้งานจากเอกสารก่อน และเป็นการเรียกบริการภายนอกเพิ่ม

### เฟส 6 — ปิดงาน
- ไล่ regression checklist ทั้งหมด, อัปเดต README + `docs/*.md`, เคลียร์/อัปเดต `DEBT.md`, รอบ review โครงสร้าง (refactor checkpoint) ก่อนจบ

---

## 5. ความเสี่ยงและสิ่งที่ "ยังไม่รู้"

| เรื่อง | สถานะ |
|---|---|
| แอปทำงานจริงบน Android 16 หลังอัปเกรด | **ยังไม่รู้** — ไม่มีใครรันได้จนกว่าคุณลง APK |
| R8 ตัดโค้ดที่ NewPipeExtractor ต้องใช้ตอนรัน | **ยังไม่รู้** — compile ผ่านไม่ได้พิสูจน์; ใช้ keep-all ของ extractor + debug APK สำรองลดความเสี่ยง |
| YouTube ตอบ 403 / ต้องใช้ poToken กับ stream URL | เป็นปัญหาฝั่ง NewPipeExtractor (เจอ issue เรื่อง 403 ใน repo ของ NewPipe จากการค้นหา แต่ยังไม่ได้ตรวจว่าสถานะตอนนี้เป็นอย่างไร) — แก้ที่ต้นเหตุไม่ได้ แอปทำได้แค่ re-resolve 1 ครั้ง + แจ้งข้อความชัด + workflow bump เวอร์ชันรายวันที่มีอยู่แล้ว |
| Compose BOM ล่าสุดต้องการ compileSdk 37 หรือไม่ | **ยังไม่รู้** — เอกสารขัดกันเอง (ดูใน material3 release notes); AGP จะฟ้องตรงๆ ใน CI แล้วถอยเวอร์ชัน |
| Developer verification กระทบการอัปเดตแอปที่ลงอยู่แล้วหรือไม่ / ขั้นตอนลงทะเบียน limited distribution จริงๆ | **ยังไม่รู้** — หน้า Google ไม่ระบุ; ต้องอ่านเอกสารหน้า Developer Console ตอนทำเฟส 0 |
| `androidx.profileinstaller` อยู่ใน dependency tree หรือไม่ | **ยังไม่รู้** — ต้องดู `dependencies` ใน CI |
| อัปเดตเงียบผ่าน `PackageInstaller` ใช้ได้กับเครื่องคุณหรือไม่ | **ยังไม่รู้** — ต้องอ่านเงื่อนไขก่อนสัญญา |
| ตัวเลขประสิทธิภาพ (เวลาเปิด, gap ต่อเพลง, แบตเตอรี่) | **ยังไม่มีข้อมูล** — ทุกข้อใน "ผลกระทบ" เป็นข้อสรุปจากการอ่านโค้ด; หน้า Diagnostics (เฟส 1) ไว้เก็บเวลาเปิดแอปก่อน/หลัง |
| `ResolvingDataSource.resolveDataSpec` อนุญาตให้ block ได้หรือไม่ / รันเธรดไหน | หน้าเอกสาร customization ไม่ระบุ — ต้องอ่าน API reference ก่อนเขียนเฟส 2 |

## 6. สิ่งที่ตั้งใจไม่ทำ
Android Auto/Cast, ดาวน์โหลดเพลงออฟไลน์ถาวร (ใช้ cache จำกัดขนาดแทน), เปลี่ยน keystore, ย้ายไป Room/AGP 9/Coil 3 (ไม่มีประโยชน์ที่ผู้ใช้เห็นเทียบกับความเสี่ยงที่ตรวจในเครื่องไม่ได้), รื้อ Radio/Taste/ระบบค้นหาที่มีเทสต์แล้ว

## 7. วิธีทำงาน
- branch ต่อเฟส (`feat/android16-phase-N`) → PR → CI (`build.yml`: unit test + build) → ผู้ช่วยแนบผล CI จริงใน PR ห้ามเขียนว่า "ผ่าน" ถ้ายังไม่เขียว
- ทุกการแก้ไขตามกฎเดิมของโปรเจกต์: อ่านเอกสารทางการก่อนใช้ API, ไม่ hardcode ค่า, ตรวจ side-effect ของ call site, shortcut ที่จำเป็นต้องมี `TODO(debt)` + ลงใน `DEBT.md`
- refactor checkpoint หลังเฟส 2 และเฟส 4 (เช็กว่าแพทเทิร์นยังรับ feature ใหม่ได้ก่อนไปต่อ)
