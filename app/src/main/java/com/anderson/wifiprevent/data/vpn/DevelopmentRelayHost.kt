package com.anderson.wifiprevent.data.vpn

import android.os.Build
import com.anderson.wifiprevent.BuildConfig

fun developmentRelayHost(): String = if (
    Build.FINGERPRINT.startsWith("generic") ||
    Build.FINGERPRINT.contains("emulator") ||
    Build.MODEL.contains("Emulator") ||
    Build.MODEL.contains("Android SDK built for")
) {
    SocksRelayProbe.EMULATOR_HOST
} else {
    BuildConfig.LOCAL_BACKEND_HOST
}
