package com.ateliersourcedeck.toolbox

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Link(val label: String, val url: String, val primary: Boolean, val linkType: String)

data class Tool(
    val id: String,
    val cat: String,
    val name: String,
    val author: String,
    val version: String,
    val date: String,
    val desc: String,
    val warn: String,
    val pkg: String,
    val isNew: Boolean,
    val links: List<Link>
)

data class Contact(val threads: String, val email: String)

data class Toolkit(
    val memo: String,
    val updated: String,
    val tools: List<Tool>,
    val contact: Contact,
    val source: String,
    val rootNote: String = ""
)

object Repo {
    const val SITE_HOST = "ateliersourcedeck.com"
    const val JSON_URL = "https://ateliersourcedeck.com/toolkit.json"
    const val GUIDES_URL = "https://ateliersourcedeck.com/toolkit.html?view=guides"
    private const val CACHE_NAME = "toolkit-cache.json"

    fun parse(text: String, source: String): Toolkit {
        val root = JSONObject(text)
        val tools = ArrayList<Tool>()
        val arr = root.optJSONArray("updates")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val links = ArrayList<Link>()
                val la = o.optJSONArray("links")
                if (la != null) {
                    for (j in 0 until la.length()) {
                        val l = la.getJSONObject(j)
                        links.add(
                            Link(
                                l.optString("label"),
                                l.optString("url"),
                                l.optBoolean("primary", false),
                                l.optString("linkType", "page")
                            )
                        )
                    }
                }
                tools.add(
                    Tool(
                        id = o.optString("id"),
                        cat = o.optString("cat"),
                        name = o.optString("name"),
                        author = o.optString("author"),
                        version = o.optString("version"),
                        date = o.optString("date"),
                        desc = o.optString("desc"),
                        warn = o.optString("warn"),
                        pkg = o.optString("pkg"),
                        isNew = o.optBoolean("isNew", false),
                        links = links
                    )
                )
            }
        }
        val c = root.optJSONObject("contact")
        val contact = Contact(
            c?.optString("threads") ?: "",
            c?.optString("email") ?: ""
        )
        return Toolkit(root.optString("memo"), root.optString("updated"), tools, contact, source, root.optString("rootNote"))
    }

    /** 阻塞呼叫，請在背景執行緒使用。順序：線上 → 上次快取 → App 內建備援。 */
    fun load(ctx: Context): Toolkit {
        val cache = File(ctx.filesDir, CACHE_NAME)
        try {
            val conn = URL(JSON_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Cache-Control", "no-cache")
            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val tk = parse(text, "線上")
                cache.writeText(text)
                return tk
            }
        } catch (e: Exception) {
            // 忽略，改用快取或備援
        }
        try {
            if (cache.exists()) return parse(cache.readText(), "快取")
        } catch (e: Exception) {
        }
        val text = ctx.assets.open("toolkit.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        return parse(text, "內建備援")
    }
}
