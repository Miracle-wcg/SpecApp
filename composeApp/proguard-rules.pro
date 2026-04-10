# ==========================================
# SpectraX ProGuard 保留规则 (防摇树误删)
# ==========================================

# 1. 保护主函数入口
-keep class com.wcg.app.specapp.MainKt { *; }

# 2. 保护 ONNX Runtime 及其底层 JNA/C++ 调用映射
-keep class ai.onnxruntime.** { *; }
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.Library { *; }

# 3. 保护 SLF4J / Logback 日志框架的反射调用
-keep class org.slf4j.** { *; }
-keep class ch.qos.logback.** { *; }
-keep class org.apache.commons.logging.** { *; }

# 4. 保护 Kotlin 协程和序列化底层机制
-keep class kotlinx.coroutines.** { *; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler { *; }

# 5. 保护 Compose 桌面端底层 Skia 引擎 (非常重要，否则 UI 画不出来)
-keep class org.jetbrains.skia.** { *; }
-keep class org.jetbrains.skiko.** { *; }

# 6. 保留所有的枚举类 (枚举在序列化时经常用到)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ==========================================
# 忽略第三方底层库的缺失警告 (解决 1038 个 unresolved references 报错)
# ==========================================
-dontwarn ch.qos.logback.**
-dontwarn org.slf4j.**
-dontwarn ai.onnxruntime.**
-dontwarn com.sun.jna.**
-dontwarn kotlinx.coroutines.**
-dontwarn org.jetbrains.skia.**
-dontwarn org.jetbrains.skiko.**
-dontwarn io.ktor.**
-dontwarn java.awt.**
-dontwarn sun.misc.**

# 🌟 终极保命符：忽略所有无法解析的警告，强行通过编译
-ignorewarnings