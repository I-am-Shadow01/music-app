# เพิ่ม rule เฉพาะโปรเจกต์ตรงนี้เมื่อเปิด minifyEnabled

## Rules for NewPipeExtractor (Rhino JS engine ใช้ deobfuscate signature ของ YouTube)
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
