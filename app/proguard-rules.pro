# Nrfr release 混淆规则
# 本应用通过 Shizuku 直连系统 binder 服务,依赖 AIDL 桩类与隐藏 API,
# R8 默认裁剪/重命名会直接导致运行时 ClassNotFound 或 binder 调用失败。

# --- AIDL binder 桩:Parcel 反序列化按类名反射构造,禁止重命名/裁剪 ---
-keep class com.android.internal.telephony.** { *; }
-keep class android.telephony.TelephonyFrameworkInitializer { *; }
-keep class android.app.IActivityManager* { *; }
-keep class android.app.UiAutomationConnection { *; }

# --- Shizuku ---
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-dontwarn rikka.**
-dontwarn moe.shizuku.**

# --- HiddenApiBypass(内部大量反射/Unsafe) ---
-keep class org.lsposed.hiddenapibypass.** { *; }
-dontwarn org.lsposed.hiddenapibypass.**

# --- Compose ---
-dontwarn androidx.compose.**

# 保留行号便于崩溃定位
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
