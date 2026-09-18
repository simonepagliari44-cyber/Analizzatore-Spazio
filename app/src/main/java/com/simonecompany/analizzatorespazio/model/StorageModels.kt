package com.simonecompany.analizzatorespazio.model

import androidx.compose.ui.graphics.Color

data class StorageItem(
    val name: String,
    val description: String,
    val sizeBytes: Long,
    val color: Color,
    val children: List<StorageItem> = emptyList(),
    val packageName: String? = null,
    val contentUri: String? = null,
    val mimeType: String? = null
) {
    val hasChildren: Boolean get() = children.isNotEmpty()

    val isLaunchable: Boolean
        get() = packageName != null || (contentUri != null && mimeType != null)

    fun sizeFormatted(): String {
        val gb = sizeBytes / (1024.0 * 1024.0 * 1024.0)
        val mb = sizeBytes / (1024.0 * 1024.0)
        val kb = sizeBytes / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.1f GB", gb)
            mb >= 1.0 -> String.format("%.1f MB", mb)
            kb >= 1.0 -> String.format("%.0f KB", kb)
            else -> "$sizeBytes B"
        }
    }
}

data class StorageCategory(
    val key: String,
    val displayName: String,
    val icon: String,
    val color: Color,
    val totalSizeBytes: Long,
    val items: List<StorageItem>,
    val isSystem: Boolean = false
) {
    fun totalSizeFormatted(): String {
        val gb = totalSizeBytes / (1024.0 * 1024.0 * 1024.0)
        val mb = totalSizeBytes / (1024.0 * 1024.0)
        return when {
            gb >= 1.0 -> String.format("%.1f GB", gb)
            mb >= 1.0 -> String.format("%.1f MB", mb)
            else -> String.format("%.0f KB", totalSizeBytes / 1024.0)
        }
    }
}

fun formatBytes(bytes: Long): String {
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    val mb = bytes / (1024.0 * 1024.0)
    val kb = bytes / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.1f GB", gb)
        mb >= 1.0 -> String.format("%.1f MB", mb)
        kb >= 1.0 -> String.format("%.0f KB", kb)
        else -> "$bytes B"
    }
}