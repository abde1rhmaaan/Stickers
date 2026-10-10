package com.volt.stickerstudio

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private val authority = "com.volt.stickerstudio.stickercontentprovider"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        webView.setBackgroundColor(android.graphics.Color.rgb(10, 12, 11))
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = WebViewClient()
        webView.addJavascriptInterface(StickerBridge(), "AndroidStickerBridge")
        setContentView(webView)
        webView.loadUrl("file:///android_asset/index.html")
    }

    inner class StickerBridge {
        @JavascriptInterface
        fun addStickerPack(packNameRaw: String, stickersJson: String) {
            runOnUiThread {
                try {
                    val stickers = JSONArray(stickersJson)
                    if (stickers.length() !in 3..30) {
                        Toast.makeText(this@MainActivity, "لازم المجموعة تحتوي على 3 إلى 30 ستيكر", Toast.LENGTH_LONG).show()
                        return@runOnUiThread
                    }
                    val packName = packNameRaw.trim().ifBlank { "My Stickers" }.take(80)
                    val packId = (slug(packName) + "_" + digest(packName + System.currentTimeMillis()).take(8)).take(64)
                    val root = File(filesDir, "sticker_packs/$packId")
                    val stickerDir = File(root, "stickers")
                    stickerDir.mkdirs()
                    val records = JSONArray()
                    var trayBitmap: Bitmap? = null
                    for (i in 0 until stickers.length()) {
                        val item = stickers.getJSONObject(i)
                        val dataUrl = item.getString("data")
                        val comma = dataUrl.indexOf(',')
                        if (comma < 0) throw IllegalArgumentException("صورة غير صالحة")
                        val raw = android.util.Base64.decode(dataUrl.substring(comma + 1), android.util.Base64.DEFAULT)
                        val source = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: throw IllegalArgumentException("تعذر قراءة صورة الستيكر")
                        val normalized = fitToSquare(source, 512)
                        val webp = encodeWebpUnderLimit(normalized, 100 * 1024)
                        if (webp == null) throw IllegalArgumentException("تعذر ضغط الستيكر ${i + 1} إلى أقل من 100KB")
                        val filename = "sticker_${String.format(Locale.US, "%03d", i + 1)}.webp"
                        FileOutputStream(File(stickerDir, filename)).use { it.write(webp) }
                        val record = JSONObject().put("file", filename).put("emoji", "😀")
                        records.put(record)
                        if (i == 0) trayBitmap = Bitmap.createBitmap(normalized)
                        if (source !== normalized) source.recycle()
                        normalized.recycle()
                    }
                    val tray = trayBitmap ?: throw IllegalArgumentException("المجموعة فاضية")
                    val traySquare = Bitmap.createScaledBitmap(tray, 96, 96, true)
                    val trayBytes = ByteArrayOutputStream().use { out -> traySquare.compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray() }
                    FileOutputStream(File(root, "tray.png")).use { it.write(trayBytes) }
                    tray.recycle(); traySquare.recycle()
                    val meta = JSONObject()
                        .put("identifier", packId)
                        .put("name", packName)
                        .put("publisher", "Sticker Studio")
                        .put("tray_image", "tray.png")
                        .put("stickers", records)
                        .put("image_data_version", "1")
                        .put("avoid_cache", false)
                        .put("animated_sticker_pack", false)
                    File(root, "metadata.json").writeText(meta.toString())
                    val prefs = getSharedPreferences("sticker_packs", MODE_PRIVATE)
                    val all = JSONArray(prefs.getString("packs", "[]"))
                    all.put(JSONObject().put("id", packId).put("name", packName))
                    prefs.edit().putString("packs", all.toString()).apply()
                    launchWhatsApp(packId, packName)
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "فشل تجهيز المجموعة: ${e.message ?: "خطأ غير معروف"}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun launchWhatsApp(packId: String, packName: String) {
        val intent = Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK")
            .putExtra("sticker_pack_id", packId)
            .putExtra("sticker_pack_authority", authority)
            .putExtra("sticker_pack_name", packName)
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            intent.setPackage("com.whatsapp.w4b")
            try {
                startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, "ثبّت WhatsApp أو WhatsApp Business أولاً", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun fitToSquare(source: Bitmap, size: Int): Bitmap {
        val scale = minOf(size.toFloat() / source.width, size.toFloat() / source.height)
        val w = (source.width * scale).toInt().coerceAtLeast(1)
        val h = (source.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, w, h, true)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(scaled, ((size - w) / 2f), ((size - h) / 2f), paint)
        if (scaled !== source) scaled.recycle()
        return output
    }

    private fun encodeWebpUnderLimit(bitmap: Bitmap, limit: Int): ByteArray? {
        for (quality in 90 downTo 25 step 5) {
            val out = ByteArrayOutputStream()
            @Suppress("DEPRECATION")
            bitmap.compress(Bitmap.CompressFormat.WEBP, quality, out)
            val bytes = out.toByteArray()
            if (bytes.size <= limit) return bytes
        }
        return null
    }

    private fun slug(value: String): String = value.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_").trim('_').ifBlank { "stickers" }
    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
