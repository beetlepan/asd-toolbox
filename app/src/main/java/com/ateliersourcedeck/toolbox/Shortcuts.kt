package com.ateliersourcedeck.toolbox

import android.provider.Settings

/** 設定捷徑與各品牌手動路徑（路徑為整理初稿，需在實機確認）。 */
data class Shortcut(
    val key: String,
    val title: String,
    val action: String,
    val xiaomi: String,
    val samsung: String,
    val other: String
) {
    fun path(brand: String): String = when (brand) {
        "xiaomi" -> xiaomi
        "samsung" -> samsung
        else -> other
    }
}

object Shortcuts {
    val all = listOf(
        Shortcut(
            "dev", "開發者選項", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
            "設定 → 我的裝置 → 全部規格 → 連點「OS 版本」7 次 → 返回設定 → 更多設定 → 開發者選項",
            "設定 → 關於手機 → 軟體資訊 → 連點「版本號碼」7 次 → 返回設定，最底部會出現「開發人員選項」",
            "設定 → 關於手機 → 連點「版本號碼」7 次 → 系統（或設定最底部）→ 開發人員選項"
        ),
        Shortcut(
            "acc", "無障礙設定", Settings.ACTION_ACCESSIBILITY_SETTINGS,
            "設定 → 更多設定 → 無障礙",
            "設定 → 無障礙",
            "設定 → 無障礙"
        ),
        Shortcut(
            "loc", "定位設定", Settings.ACTION_LOCATION_SOURCE_SETTINGS,
            "設定 → 密碼與安全性 → 位置資訊",
            "設定 → 位置資訊",
            "設定 → 位置資訊（或 安全性與位置資訊）"
        ),
        Shortcut(
            "defaults", "預設程式 / 開啟連結設定", Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
            "設定 → 應用程式設定 → 預設應用程式 → 開啟連結",
            "設定 → 應用程式 → 選擇預設應用程式；或進入單一 App → 預設開啟",
            "設定 → 應用程式 → 預設應用程式 → 開啟連結"
        )
    )
}
