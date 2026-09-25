package com.simonecompany.analizzatorespazio.viewmodel

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.simonecompany.analizzatorespazio.model.StorageCategory
import com.simonecompany.analizzatorespazio.model.StorageItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class CachedScan(
    val categories: List<StorageCategory>,
    val total: Long,
    val used: Long
)

object ScanCache {

    private const val FILE_NAME = "storage_scan_cache_v2.json"

    private val CATEGORY_COLORS_ARGB = mapOf(
        "apps" to 0xFF42A5F5.toInt(),
        "photos" to 0xFF66BB6A.toInt(),
        "videos" to 0xFFFFA726.toInt(),
        "audio" to 0xFFAB47BC.toInt(),
        "documents" to 0xFFEF5350.toInt(),
        "system" to 0xFF78909C.toInt(),
        "other" to 0xFFBDBDBD.toInt()
    )

    private val ITEM_PALETTE_ARGB = listOf(
        0xFFEF5350.toInt(), 0xFF42A5F5.toInt(), 0xFF66BB6A.toInt(),
        0xFFFFA726.toInt(), 0xFFAB47BC.toInt(), 0xFF26C6DA.toInt(),
        0xFFFDD835.toInt(), 0xFFEC407A.toInt(), 0xFF5C6BC0.toInt(),
        0xFF8D6E63.toInt(), 0xFF78909C.toInt(), 0xFFD4E157.toInt(),
        0xFF7E57C2.toInt(), 0xFF29B6F6.toInt(), 0xFFFF7043.toInt(),
        0xFF9CCC65.toInt(), 0xFF26A69A.toInt(), 0xFFFFCA28.toInt(),
        0xFF5D4037.toInt(), 0xFF009688.toInt(), 0xFFCDDC39.toInt(),
        0xFF607D8B.toInt(), 0xFFE91E63.toInt(), 0xFF3F51B5.toInt(),
        0xFF00BCD4.toInt(), 0xFFFF5722.toInt(), 0xFF4CAF50.toInt(),
        0xFFC2185B.toInt(), 0xFF1976D2.toInt(), 0xFF388E3C.toInt()
    )

    fun load(context: Context): CachedScan? = runCatching {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return null
        val root = JSONObject(file.readText())
        val total = root.optLong("total", 0L)
        val used = root.optLong("used", 0L)
        val arr = root.optJSONArray("categories") ?: return null
        val categories = (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            categoryFromJson(obj)
        }
        CachedScan(categories, total, used)
    }.getOrNull()

    fun save(context: Context, categories: List<StorageCategory>, total: Long, used: Long) {
        runCatching {
            val arr = JSONArray()
            categories.forEach { arr.put(categoryToJson(it)) }
            val root = JSONObject()
            root.put("total", total)
            root.put("used", used)
            root.put("categories", arr)
            File(context.filesDir, FILE_NAME).writeText(root.toString())
        }
    }

    private fun categoryToJson(category: StorageCategory): JSONObject {
        val arr = JSONArray()
        category.items.forEach { arr.put(itemToJson(it)) }
        return JSONObject()
            .put("key", category.key)
            .put("displayName", category.displayName)
            .put("icon", category.icon)
            .put("totalSizeBytes", category.totalSizeBytes)
            .put("isSystem", category.isSystem)
            .put("items", arr)
    }

    private fun itemToJson(item: StorageItem): JSONObject {
        val arr = JSONArray()
        item.children.forEach { arr.put(itemToJson(it)) }
        return JSONObject()
            .put("name", item.name)
            .put("description", item.description)
            .put("sizeBytes", item.sizeBytes)
            .put("packageName", item.packageName ?: JSONObject.NULL)
            .put("contentUri", item.contentUri ?: JSONObject.NULL)
            .put("mimeType", item.mimeType ?: JSONObject.NULL)
            .put("children", arr)
    }

    private fun categoryFromJson(obj: JSONObject): StorageCategory {
        val itemsArr = obj.optJSONArray("items") ?: JSONArray()
        val items = (0 until itemsArr.length()).mapNotNull { i ->
            val child = itemsArr.optJSONObject(i) ?: return@mapNotNull null
            itemFromJson(child, i)
        }
        val key = obj.optString("key", "")
        return StorageCategory(
            key = key,
            displayName = obj.optString("displayName", "Categoria"),
            icon = obj.optString("icon", "📁"),
            color = Color(CATEGORY_COLORS_ARGB[key] ?: 0xFF9E9E9E.toInt()),
            totalSizeBytes = obj.optLong("totalSizeBytes", 0L),
            items = items,
            isSystem = obj.optBoolean("isSystem", false)
        )
    }

    private fun itemFromJson(obj: JSONObject, colorIndex: Int): StorageItem {
        val childrenArr = obj.optJSONArray("children") ?: JSONArray()
        val children = (0 until childrenArr.length()).mapNotNull { ci ->
            val child = childrenArr.optJSONObject(ci) ?: return@mapNotNull null
            itemFromJson(child, colorIndex + ci + 1)
        }
        return StorageItem(
            name = obj.optString("name", "File"),
            description = obj.optString("description", ""),
            sizeBytes = obj.optLong("sizeBytes", 0L),
            color = Color(ITEM_PALETTE_ARGB[colorIndex % ITEM_PALETTE_ARGB.size]),
            children = children,
            packageName = obj.optStringOrNull("packageName"),
            contentUri = obj.optStringOrNull("contentUri"),
            mimeType = obj.optStringOrNull("mimeType")
        )
    }

    private fun JSONObject.optStringOrNull(name: String): String? {
        return if (isNull(name)) null else optString(name, null)
    }
}