package com.ateliersourcedeck.toolbox

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val tabNames = listOf("首頁", "教學", "寶可夢", "Root", "設定")
    private val pages = arrayOfNulls<View>(5)
    private val tabViews = ArrayList<TextView>()
    private var current = 0
    private var toolkit: Toolkit? = null
    private val shortcutLog = LinkedHashMap<String, String>()

    private lateinit var content: FrameLayout
    private lateinit var guidesPage: FrameLayout
    private lateinit var web: WebView
    private lateinit var webError: LinearLayout
    private var webLoaded = false

    private val navHeightDp = 64
    private val navMarginDp = 10

    private enum class St { LATEST, OUTDATED, NOT_INSTALLED, INSTALLED_ONLY, UNDETECTABLE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = C.BG
        window.navigationBarColor = C.BG

        val root = FrameLayout(this)
        root.setBackgroundColor(C.BG)
        content = FrameLayout(this)
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        buildGuidesPage()
        root.addView(buildNav())
        setContentView(root)

        showLoading()
        select(0)
        reload()
    }

    // ---------- 資料載入 ----------

    private fun reload() {
        Thread {
            val tk = Repo.load(applicationContext)
            runOnUiThread {
                toolkit = tk
                buildPages()
                select(current)
            }
        }.start()
    }

    private fun showLoading() {
        val t = label("載入中…", 14f, C.MUTED)
        t.gravity = Gravity.CENTER
        pages[0] = t
        content.removeAllViews()
        content.addView(t, FrameLayout.LayoutParams(-1, -1))
    }

    private fun buildPages() {
        val tk = toolkit ?: return
        pages[0] = buildHome(tk)
        pages[1] = guidesPage
        pages[2] = buildTools(tk, "game")
        pages[3] = buildTools(tk, "root")
        pages[4] = buildSettings()
        content.removeAllViews()
        pages.forEach { p ->
            if (p != null) {
                if (p.parent != null) (p.parent as ViewGroup).removeView(p)
                content.addView(p, FrameLayout.LayoutParams(-1, -1))
            }
        }
    }

    // ---------- 導覽 ----------

    private fun buildNav(): View {
        val nav = LinearLayout(this)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.gravity = Gravity.CENTER_VERTICAL
        nav.setPadding(dp(6), dp(6), dp(6), dp(6))
        nav.background = shape(C.GLASS, C.GLASS_EDGE, 24)
        nav.elevation = dp(8).toFloat()
        tabNames.forEachIndexed { i, name ->
            val t = label(name, 13f, C.MUTED)
            t.gravity = Gravity.CENTER
            t.isClickable = true
            t.setOnClickListener { select(i) }
            tabViews.add(t)
            nav.addView(t, LinearLayout.LayoutParams(0, -1, 1f))
        }
        val lp = FrameLayout.LayoutParams(-1, dp(navHeightDp))
        lp.gravity = Gravity.BOTTOM
        lp.setMargins(dp(navMarginDp), 0, dp(navMarginDp), dp(navMarginDp))
        nav.layoutParams = lp
        return nav
    }

    private fun select(i: Int) {
        current = i
        pages.forEachIndexed { idx, p -> p?.visibility = if (idx == i) View.VISIBLE else View.GONE }
        tabViews.forEachIndexed { idx, t ->
            val on = idx == i
            t.setTextColor(if (on) C.GREEN else C.MUTED)
            t.background = if (on) shape(C.GREEN_DIM, 0, 18) else null
        }
        if (i == 1 && !webLoaded) {
            webLoaded = true
            web.loadUrl(Repo.GUIDES_URL)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (current == 1 && web.canGoBack()) {
            web.goBack()
        } else if (current != 0) {
            select(0)
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    private val navSpace get() = dp(navHeightDp + navMarginDp * 2)

    private fun page(build: LinearLayout.() -> Unit): ScrollView {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(16), dp(16), dp(16) + navSpace)
        col.build()
        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        sv.addView(col, ViewGroup.LayoutParams(-1, -2))
        return sv
    }

    // ---------- 版本狀態 ----------

    private fun statusOf(t: Tool): Pair<St, String?> {
        if (t.pkg.isEmpty()) return Pair(St.UNDETECTABLE, null)
        val inst = DeviceInfo.installedVersion(this, t.pkg) ?: return Pair(St.NOT_INSTALLED, null)
        if (!Versions.looksLikeVersion(t.version)) return Pair(St.INSTALLED_ONLY, inst)
        return if (Versions.compare(inst, t.version) >= 0) Pair(St.LATEST, inst) else Pair(St.OUTDATED, inst)
    }

    private fun statusChip(s: St): TextView {
        val (text, color) = when (s) {
            St.LATEST -> "最新" to C.GREEN
            St.OUTDATED -> "可更新" to C.AMBER
            St.NOT_INSTALLED -> "未安裝" to C.MUTED
            St.INSTALLED_ONLY -> "已安裝" to C.GREEN
            St.UNDETECTABLE -> "僅顯示最新版" to C.DIM
        }
        val c = label(text, 11f, color, bold = true)
        c.setPadding(dp(8), dp(3), dp(8), dp(3))
        c.background = shape(Color.TRANSPARENT, color, 99)
        return c
    }

    // ---------- 首頁 ----------

    private fun buildHome(tk: Toolkit): View = page {
        val ctx = this@MainActivity

        // 裝置資訊（終端機風格）
        val console = card()
        console.background = shape(C.CONSOLE, C.BORDER, 10)
        val roots = DeviceInfo.detectedRootManagers(ctx)
        val sig = DeviceInfo.signatureStatus(ctx)
        val lines = listOf(
            "> ASD TOOLBOX  v" + versionName(),
            "MODEL    " + Build.MANUFACTURER + " " + Build.MODEL,
            "ANDROID  " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")",
            "PATCH    " + (Build.VERSION.SECURITY_PATCH ?: "unknown"),
            "ROM      " + DeviceInfo.rom(),
            "ROOT MGR " + (if (roots.isEmpty()) "未偵測到" else roots.joinToString(", ")),
            "DATA     " + tk.source + (if (tk.updated.isNotEmpty()) " · " + tk.updated else ""),
            "SIGN     $sig"
        )
        lines.forEachIndexed { i, s ->
            val tv = label(s, 12f, if (i == 0) C.GREEN else C.GREEN, mono = true)
            tv.setPadding(0, if (i == 0) 0 else dp(3), 0, 0)
            console.addView(tv)
        }
        addSpaced(console)

        if (sig == "不符") {
            val warn = card()
            warn.addView(label("此版本的簽章與官方不符，請改從官網下載。", 13f, C.RED, bold = true))
            addSpaced(warn)
        }

        // 公告
        if (tk.memo.isNotEmpty()) {
            val memo = card()
            memo.addView(label(tk.memo, 13f, C.INK))
            addSpaced(memo)
        }

        // 更新摘要
        var outdated = 0
        var notInstalled = 0
        tk.tools.forEach {
            when (statusOf(it).first) {
                St.OUTDATED -> outdated++
                St.NOT_INSTALLED -> notInstalled++
                else -> {}
            }
        }
        val sum = card()
        sum.addView(label("$outdated 項軟體可更新", 18f, if (outdated > 0) C.AMBER else C.GREEN, bold = true))
        sum.addView(label("另有 $notInstalled 項尚未安裝（只計算可偵測的軟體）", 12f, C.MUTED))
        val sumRow = ctx.row(
            button("看寶可夢", false) { select(2) },
            button("看 Root", false) { select(3) },
            button("重新整理", false) { reload(); Toast.makeText(ctx, "重新載入中", Toast.LENGTH_SHORT).show() }
        )
        val rl = LinearLayout.LayoutParams(-1, -2)
        rl.topMargin = dp(10)
        sum.addView(sumRow, rl)
        addSpaced(sum)

        // 四個頁面入口
        addSpaced(ctx.row(button("教學", true) { select(1) }, button("寶可夢", true) { select(2) }), 8)
        addSpaced(ctx.row(button("Root", true) { select(3) }, button("設定", true) { select(4) }), 18)

        // 聯絡與回報
        val contact = card()
        contact.addView(label("聯絡與回報", 14f, C.INK, bold = true))
        val cr = LinearLayout(ctx)
        cr.orientation = LinearLayout.VERTICAL
        val c = tk.contact
        if (c.form.isNotEmpty()) cr.addSpaced(button("預約 / 回報表單") { openUrl(c.form) }, 8)
        if (c.instagram.isNotEmpty()) cr.addSpaced(button("Instagram") { openUrl(c.instagram) }, 8)
        if (c.email.isNotEmpty()) cr.addSpaced(button("Email") {
            startSafe(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + c.email)))
        }, 8)
        cr.addSpaced(button("複製診斷資訊（回報用）", true) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("ASD diag", buildDiagnostics(tk)))
            Toast.makeText(ctx, "已複製，貼給我們即可", Toast.LENGTH_SHORT).show()
        }, 0)
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(10)
        contact.addView(cr, lp)
        addSpaced(contact)

        val ver = label("ASD 工具箱 v" + versionName(), 11f, C.DIM, mono = true)
        ver.gravity = Gravity.CENTER
        addView(ver)
    }

    private fun versionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }

    // ---------- 寶可夢 / Root 清單 ----------

    private fun buildTools(tk: Toolkit, cat: String): View = page {
        val ctx = this@MainActivity
        val title = if (cat == "game") "寶可夢相關軟體" else "Root 相關軟體"
        addSpaced(label(title, 20f, C.INK, bold = true), 12)
        tk.tools.filter { it.cat == cat }.forEach { t -> addSpaced(toolCard(t)) }
        if (tk.tools.none { it.cat == cat }) addView(label("目前沒有項目", 13f, C.MUTED))
    }

    private fun toolCard(t: Tool): View {
        val ctx = this
        val (st, inst) = statusOf(t)
        val c = card()

        val head = LinearLayout(ctx)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        val name = label(t.name + (if (t.isNew) "  【新增】" else ""), 15f, C.INK, bold = true)
        head.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(statusChip(st))
        c.addView(head)

        val ver = StringBuilder()
        if (t.version.isNotEmpty()) ver.append("最新 ").append(t.version)
        if (t.date.isNotEmpty()) ver.append(" · ").append(t.date)
        if (inst != null) ver.append("\n已安裝 ").append(if (inst.isEmpty()) "(未知版本)" else inst)
        if (ver.isNotEmpty()) {
            val v = label(ver.toString(), 12f, C.GREEN, mono = true)
            v.setPadding(0, dp(6), 0, 0)
            c.addView(v)
        }
        if (t.desc.isNotEmpty()) {
            val d = label(t.desc, 13f, C.MUTED)
            d.setPadding(0, dp(6), 0, 0)
            c.addView(d)
        }
        if (t.warn.isNotEmpty()) {
            val w = label("注意：" + t.warn, 12f, C.AMBER)
            w.setPadding(0, dp(6), 0, 0)
            c.addView(w)
        }
        if (t.links.isNotEmpty()) {
            val btns = t.links.map { l -> button(l.label, l.primary) { openLink(l) } }
            val rows = LinearLayout(ctx)
            rows.orientation = LinearLayout.VERTICAL
            btns.chunked(2).forEachIndexed { i, chunk ->
                val r = if (chunk.size == 2) ctx.row(chunk[0], chunk[1]) else ctx.row(chunk[0])
                rows.addSpaced(r, if (i == btns.chunked(2).size - 1) 0 else 8)
            }
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.topMargin = dp(10)
            c.addView(rows, lp)
        }
        return c
    }

    // ---------- 連結處理 ----------

    private fun openLink(l: Link) {
        when (l.linkType) {
            "apk-direct" -> confirm(
                "下載 APK",
                "會用瀏覽器下載 APK。下載完成後點通知安裝；若被擋住，請依畫面提示允許瀏覽器「安裝未知來源應用程式」。",
                "下載"
            ) { openUrl(l.url) }
            "module-zip" -> confirm(
                "下載 Root 模組",
                "這是 Root 模組（zip），不能直接安裝。下載後請打開 Root 管理器（KernelSU / Magisk）→ 模組 → 從儲存空間安裝，選擇剛下載的 zip，完成後重新開機。",
                "前往下載"
            ) { openUrl(l.url) }
            "store" -> {
                val id = Uri.parse(l.url).getQueryParameter("id")
                var done = false
                if (id != null) {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id")))
                        done = true
                    } catch (e: ActivityNotFoundException) {
                    }
                }
                if (!done) openUrl(l.url)
            }
            else -> openUrl(l.url)
        }
    }

    private fun openUrl(url: String) = startSafe(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    private fun startSafe(i: Intent) {
        try {
            startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(this, "無法開啟，請稍後再試", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirm(title: String, msg: String, ok: String, onOk: () -> Unit) {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(title).setMessage(msg)
            .setPositiveButton(ok) { _, _ -> onOk() }
            .setNegativeButton("取消", null)
            .show()
    }

    // ---------- 設定捷徑 ----------

    private fun buildSettings(): View = page {
        val ctx = this@MainActivity
        val brand = DeviceInfo.brandKey()
        addSpaced(label("手機設定捷徑", 20f, C.INK, bold = true), 4)
        addSpaced(label("點擊直接開啟；若這支手機打不開，會顯示手動路徑。", 12f, C.MUTED), 12)
        Shortcuts.all.forEach { s ->
            val c = card()
            c.addView(label(s.title, 15f, C.INK, bold = true))
            val p = label("路徑：" + s.path(brand), 12f, C.MUTED)
            p.setPadding(0, dp(6), 0, dp(10))
            c.addView(p)
            c.addView(button("開啟", true) { openShortcut(s, brand) })
            addSpaced(c)
        }
    }

    private fun openShortcut(s: Shortcut, brand: String) {
        if (s.key == "dev") {
            val on = try {
                Settings.Global.getInt(contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
            } catch (e: Exception) {
                true
            }
            if (!on) {
                shortcutLog[s.key] = "dev-not-enabled"
                confirm(
                    "尚未開啟開發者選項",
                    "請先依以下路徑開啟：\n" + s.path(brand),
                    "仍嘗試開啟"
                ) { launchSetting(s, brand) }
                return
            }
        }
        launchSetting(s, brand)
    }

    private fun launchSetting(s: Shortcut, brand: String) {
        try {
            startActivity(Intent(s.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            shortcutLog[s.key] = "opened"
        } catch (e: Exception) {
            shortcutLog[s.key] = "failed:" + e.javaClass.simpleName
            confirm(
                "這支手機無法直接開啟",
                "請手動前往：\n" + s.path(brand) + "\n\n（品牌：$brand）",
                "開啟設定首頁"
            ) { startSafe(Intent(Settings.ACTION_SETTINGS)) }
        }
    }

    // ---------- 教學（WebView，只允許自己的網域）----------

    private fun buildGuidesPage() {
        guidesPage = FrameLayout(this)
        web = WebView(this)
        web.setBackgroundColor(C.BG)
        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                val host = u.host ?: ""
                val mine = u.scheme == "https" && (host == Repo.SITE_HOST || host == "www." + Repo.SITE_HOST)
                if (mine) return false
                startSafe(Intent(Intent.ACTION_VIEW, u))
                return true
            }

            override fun onPageFinished(view: WebView, url: String?) {
                webError.visibility = View.GONE
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) webError.visibility = View.VISIBLE
            }
        }
        val lp = FrameLayout.LayoutParams(-1, -1)
        lp.bottomMargin = navSpace
        guidesPage.addView(web, lp)

        webError = LinearLayout(this)
        webError.orientation = LinearLayout.VERTICAL
        webError.gravity = Gravity.CENTER
        webError.setBackgroundColor(C.BG)
        webError.setPadding(dp(24), dp(24), dp(24), dp(24))
        webError.addView(label("教學頁面載入失敗，請確認網路。", 14f, C.MUTED))
        val retry = button("重試", true) {
            webError.visibility = View.GONE
            web.loadUrl(Repo.GUIDES_URL)
        }
        val rlp = LinearLayout.LayoutParams(-2, -2)
        rlp.topMargin = dp(12)
        webError.addView(retry, rlp)
        webError.visibility = View.GONE
        val elp = FrameLayout.LayoutParams(-1, -1)
        elp.bottomMargin = navSpace
        guidesPage.addView(webError, elp)
    }

    // ---------- 診斷 ----------

    private fun buildDiagnostics(tk: Toolkit): String {
        val sb = StringBuilder()
        sb.append("ASD Toolbox ").append(versionName()).append('\n')
        sb.append("Device: ").append(Build.MANUFACTURER).append(" / ").append(Build.BRAND)
            .append(" / ").append(Build.MODEL).append('\n')
        sb.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT)
            .append("), patch ").append(Build.VERSION.SECURITY_PATCH).append('\n')
        sb.append("ROM: ").append(DeviceInfo.rom()).append('\n')
        sb.append("Data: ").append(tk.source).append(" ").append(tk.updated).append('\n')
        sb.append("Signature: ").append(DeviceInfo.signatureStatus(this)).append('\n')
        sb.append("Root managers: ").append(DeviceInfo.detectedRootManagers(this).joinToString(", ")).append('\n')
        sb.append("Tools:\n")
        tk.tools.forEach { t ->
            val (st, inst) = statusOf(t)
            sb.append(" - ").append(t.id)
            if (t.pkg.isNotEmpty()) sb.append(" [").append(t.pkg).append("]")
            sb.append(" installed=").append(inst ?: "-")
                .append(" latest=").append(t.version).append(" -> ").append(st.name).append('\n')
        }
        sb.append("Shortcuts:\n")
        Shortcuts.all.forEach { s ->
            sb.append(" - ").append(s.key).append(": ").append(shortcutLog[s.key] ?: "not tried").append('\n')
        }
        return sb.toString()
    }
}
