package com.volt.stickerstudio

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException

class StickerContentProvider : ContentProvider() {
    companion object {
        const val AUTHORITY = "com.volt.stickerstudio.stickercontentprovider"
        private const val METADATA = 1
        private const val ONE_METADATA = 2
        private const val STICKERS = 3
        private const val ASSET = 4
        private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "metadata", METADATA)
            addURI(AUTHORITY, "metadata/*", ONE_METADATA)
            addURI(AUTHORITY, "stickers/*", STICKERS)
            addURI(AUTHORITY, "stickers_asset/*/*", ASSET)
        }
    }

    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val ctx = context ?: throw IllegalStateException("Provider context unavailable")
        return when (matcher.match(uri)) {
            METADATA -> {
                val cursor = MatrixCursor(arrayOf("identifier", "name", "publisher", "tray_image", "android_play_store_link", "ios_app_store_link", "publisher_email", "publisher_website", "privacy_policy_website", "license_agreement_website", "image_data_version", "avoid_cache", "animated_sticker_pack"))
                packs().forEach { p -> cursor.addRow(arrayOf(p.optString("id"), p.optString("name"), "Sticker Studio", "tray.png", "", "", "", "", "", "", "1", 0, 0)) }
                cursor
            }
            ONE_METADATA -> {
                val p = findPack(uri.lastPathSegment ?: "") ?: return MatrixCursor(arrayOf("identifier", "name", "publisher", "tray_image", "android_play_store_link", "ios_app_store_link", "publisher_email", "publisher_website", "privacy_policy_website", "license_agreement_website", "image_data_version", "avoid_cache", "animated_sticker_pack"))
                MatrixCursor(arrayOf("identifier", "name", "publisher", "tray_image", "android_play_store_link", "ios_app_store_link", "publisher_email", "publisher_website", "privacy_policy_website", "license_agreement_website", "image_data_version", "avoid_cache", "animated_sticker_pack")).apply {
                    addRow(arrayOf(p.optString("id"), p.optString("name"), "Sticker Studio", "tray.png", "", "", "", "", "", "", "1", 0, 0))
                }
            }
            STICKERS -> {
                val id = uri.lastPathSegment ?: ""
                val metadata = metadata(id) ?: return MatrixCursor(arrayOf("sticker_file_name", "sticker_emoji"))
                val arr = metadata.optJSONArray("stickers") ?: JSONArray()
                MatrixCursor(arrayOf("sticker_file_name", "sticker_emoji")).apply {
                    for (i in 0 until arr.length()) {
                        val s = arr.getJSONObject(i)
                        addRow(arrayOf(s.optString("file"), s.optString("emoji", "😀")))
                    }
                }
            }
            else -> MatrixCursor(projection ?: emptyArray())
        }
    }

    override fun getType(uri: Uri): String = when (matcher.match(uri)) {
        METADATA, ONE_METADATA -> "vnd.android.cursor.dir/vnd.$AUTHORITY.metadata"
        STICKERS -> "vnd.android.cursor.dir/vnd.$AUTHORITY.stickers"
        ASSET -> if (uri.lastPathSegment == "tray.png") "image/png" else "image/webp"
        else -> "vnd.android.cursor.item/vnd.$AUTHORITY"
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (matcher.match(uri) != ASSET) throw FileNotFoundException("Unsupported URI: $uri")
        val segments = uri.pathSegments
        if (segments.size < 3) throw FileNotFoundException("Malformed sticker URI")
        val packId = segments[1]
        val filename = File(segments[2]).name
        val root = File(getProviderContext().filesDir, "sticker_packs/$packId")
        val file = if (filename == "tray.png") File(root, "tray.png") else File(root, "stickers/$filename")
        if (!file.exists()) throw FileNotFoundException(file.path)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private fun getProviderContext() = context ?: throw IllegalStateException("Provider context unavailable")
    private fun packs(): List<JSONObject> {
        val raw = getProviderContext().getSharedPreferences("sticker_packs", 0).getString("packs", "[]") ?: "[]"
        val array = JSONArray(raw)
        return (0 until array.length()).map { array.getJSONObject(it) }
    }
    private fun findPack(id: String): JSONObject? = packs().firstOrNull { it.optString("id") == id }
    private fun metadata(id: String): JSONObject? {
        val pack = findPack(id) ?: return null
        val file = File(getProviderContext().filesDir, "sticker_packs/${pack.optString("id")}/metadata.json")
        return if (file.exists()) JSONObject(file.readText()) else null
    }
}
