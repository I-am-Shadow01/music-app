plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.cid.musicapp"
    compileSdk = 34

    // ใช้ keystore คงที่ตัวเดียวกันทุก build (เก็บไว้ที่ keystore/debug.keystore ใน repo)
    // กันปัญหา "แพ็กเกจขัดแย้งกับแพ็กเกจที่มีอยู่" ตอนอัปเดต — เกิดจาก AGP สุ่มสร้าง debug key
    // ใหม่ทุกครั้งที่ CI รันบนเครื่องใหม่ (fresh VM ไม่มี ~/.android/debug.keystore เดิม)
    // ทำให้แต่ละ build เซ็นด้วย key คนละตัว Android เลยไม่ยอมอัปเดตทับของเดิม
    signingConfigs {
        getByName("debug") {
            storeFile = file("${rootDir}/keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "com.cid.musicapp"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // เลข build จาก GitHub Actions (GITHUB_RUN_NUMBER) — ใช้เทียบว่ามีเวอร์ชันใหม่กว่าไหม
        // รันในเครื่อง local จะได้ 0 เสมอ (ไม่มี env ตัวนี้) ไม่กระทบการ build ปกติ
        buildConfigField(
            "int",
            "BUILD_NUMBER",
            System.getenv("GITHUB_RUN_NUMBER") ?: "0"
        )
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // จำเป็นสำหรับ NewPipeExtractor บน minSdk ต่ำกว่า 33
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.navigation:navigation-compose:2.7.7")

    // เล่นเสียง + background playback
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")

    // ค้นหา + ดึงลิงก์เสียงจาก YouTube โดยตรงในแอป (ไม่ง้อ backend แยก)
    // NewValve = ตัวห่อ NewPipeExtractor ให้พร้อมใช้กับ OkHttp ทันที
    // หมายเหตุ: exclude NewPipeExtractor เวอร์ชันเก่าที่ NewValve พ่วงมาด้วยออกไปก่อน
    // เพราะมันมาคนละ group/artifact กับตัวที่เราดึงเองด้านล่าง (บรรทัดถัดไป)
    // resolutionStrategy.force() ใช้ไม่ได้ผลในกรณีนี้ เนื่องจาก force() จะทำงาน
    // เฉพาะตอน group:module ตรงกันแต่ version ต่างกันเท่านั้น ไม่ใช่กรณีนี้ที่ artifact คนละชื่อกัน
    implementation("com.github.shalva97:NewValve:1.5") {
        exclude(group = "com.github.TeamNewPipe.NewPipeExtractor", module = "extractor")
        exclude(group = "com.github.TeamNewPipe.NewPipeExtractor", module = "timeago-parser")
    }

    // โหลดรูป thumbnail
    implementation("io.coil-kt:coil-compose:2.6.0")

    // เก็บค่าตั้งค่าแอป (ธีม, คุณภาพเสียง) แบบ persist
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // เช็ค/โหลดอัปเดตแอปจาก GitHub Releases
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // ใช้สำหรับ callback ของ MediaController.buildAsync()
    implementation("com.google.guava:guava:33.2.1-android")

    // NewValve พ่วง NewPipeExtractor เวอร์ชันเก่ามาด้วย (ตัดออกไปแล้วด้านบน) — ดึงเวอร์ชันจริงจาก
    // gradle.properties แทน ("newpipeExtractorVersion") เพื่อให้ workflow update-newpipe.yml
    // เช็ค+อัปเดตเลขนี้ให้เองอัตโนมัติเมื่อ NewPipeExtractor ออกเวอร์ชันใหม่
    implementation("com.github.teamnewpipe:NewPipeExtractor:${project.property("newpipeExtractorVersion")}")

    // จำเป็นสำหรับ NewPipeExtractor บน minSdk < 33
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.0.4")

    debugImplementation("androidx.compose.ui:ui-tooling")
}

configurations.all {
    resolutionStrategy {
        // บังคับให้ทุก dependency ที่พ่วง NewPipeExtractor มา (เช่นจาก NewValve) ใช้เวอร์ชันเดียวกับด้านบนเสมอ
        force("com.github.teamnewpipe:NewPipeExtractor:${project.property("newpipeExtractorVersion")}")
    }
}
