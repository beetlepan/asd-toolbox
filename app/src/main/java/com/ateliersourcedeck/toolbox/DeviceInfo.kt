package com.ateliersourcedeck.toolbox

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

object DeviceInfo {
    /** 第一版只偵測套件是否安裝；Magisk 可能被隱藏成隨機套件名稱，偵測不到屬正常。 */
    val ROOT_MANAGER_PKGS = listOf(
        "me.weishu.kernelsu" to "KernelSU",
        "com.sukisu.ultra" to "SukiSU Ultra",
        "com.rifsxd.ksunext" to "KernelSU Next",
        "com.topjohnwu.magisk" to "Magisk",
        "io.github.vvb2060.magisk" to "Magisk Alpha"
    )

    /** 正式版建置後，填入簽署憑證的 SHA-256（小寫十六進位，無冒號）。空字串＝不檢查。 */
    const val OFFICIAL_CERT_SHA256 = ""

    fun sysProp(key: String): String = try {
        val c = Class.forName("android.os.SystemProperties")
        val m = c.getMethod("get", String::class.java, String::class.java)
        (m.invoke(null, key, "") as? String) ?: ""
    } catch (e: Throwable) {
        ""
    }

    fun brandKey(): String {
        val m = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        return when {
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") -> "xiaomi"
            m.contains("samsung") -> "samsung"
            else -> "other"
        }
    }

    fun rom(): String {
        val miui = sysProp("ro.miui.ui.version.name")
        val hyper = sysProp("ro.mi.os.version.name")
        val oneui = sysProp("ro.build.version.oneui")
        return when {
            hyper.isNotEmpty() -> "HyperOS ($hyper)"
            miui.isNotEmpty() -> "MIUI ($miui)"
            oneui.isNotEmpty() -> {
                val n = oneui.toIntOrNull()
                if (n != null) "One UI ${n / 10000}.${(n % 10000) / 100}" else "One UI ($oneui)"
            }
            else -> Build.DISPLAY ?: "unknown"
        }
    }

    fun installedVersion(ctx: Context, pkg: String): String? = try {
        ctx.packageManager.getPackageInfo(pkg, 0).versionName ?: ""
    } catch (e: PackageManager.NameNotFoundException) {
        null
    } catch (e: Exception) {
        null
    }

    fun detectedRootManagers(ctx: Context): List<String> =
        ROOT_MANAGER_PKGS.mapNotNull { (pkg, name) ->
            val v = installedVersion(ctx, pkg)
            if (v == null) null else "$name $v"
        }

    fun signatureStatus(ctx: Context): String {
        if (OFFICIAL_CERT_SHA256.isEmpty()) return "未檢查"
        return try {
            val pm = ctx.packageManager
            val sigs = if (Build.VERSION.SDK_INT >= 28) {
                pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                    .signingInfo.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNATURES).signatures
            }
            val ok = sigs.any { s ->
                val d = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
                d.joinToString("") { "%02x".format(it) } == OFFICIAL_CERT_SHA256
            }
            if (ok) "正常" else "不符"
        } catch (e: Exception) {
            "無法檢查"
        }
    }
}
