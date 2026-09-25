package com.simonecompany.analizzatorespazio.viewmodel

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.simonecompany.analizzatorespazio.model.StorageCategory
import com.simonecompany.analizzatorespazio.model.StorageItem
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MainUiState(
    val categories: List<StorageCategory> = emptyList(),
    val totalStorageBytes: Long = 0,
    val usedStorageBytes: Long = 0,
    val isLoading: Boolean = true,
    val usageAccessGranted: Boolean = true,
    val fullStorageAccessGranted: Boolean = false
)

data class DetailUiState(
    val category: StorageCategory? = null,
    val path: List<Int> = emptyList(),
    val currentEntries: List<StorageItem> = emptyList(),
    val searchQuery: String = "",
    val highlightedIndex: Int = -1,
    val filteredEntries: List<StorageItem> = emptyList(),
    val isLoading: Boolean = true
)

class StorageViewModel(private val appContext: Context) : ViewModel() {

    private val _mainState = MutableStateFlow(MainUiState())
    val mainState: StateFlow<MainUiState> = _mainState.asStateFlow()

    private val _detailState = MutableStateFlow(DetailUiState())
    val detailState: StateFlow<DetailUiState> = _detailState.asStateFlow()

    private var cachedCategories: List<StorageCategory> = emptyList()
    private var cachedTotalBytes: Long = 0
    private var cachedUsedBytes: Long = 0
    private var scanInProgress = false
    var mediaPermissionRequested: Boolean = false
        private set

    fun loadStorageData() {
        viewModelScope.launch {
            if (cachedCategories.isNotEmpty()) {
                _mainState.value = _mainState.value.copy(
                    usageAccessGranted = hasUsageAccess(),
                    fullStorageAccessGranted = hasFullStorageAccess()
                )
                return@launch
            }

            val cached = withContext(Dispatchers.IO) { ScanCache.load(appContext) }
            if (cached != null) {
                cachedCategories = cached.categories
                cachedTotalBytes = cached.total
                cachedUsedBytes = cached.used
                _mainState.value = MainUiState(
                    categories = cachedCategories,
                    totalStorageBytes = cachedTotalBytes,
                    usedStorageBytes = cachedUsedBytes,
                    isLoading = false,
                    usageAccessGranted = hasUsageAccess(),
                    fullStorageAccessGranted = hasFullStorageAccess()
                )
            } else {
                _mainState.value = MainUiState(isLoading = true)
            }

            refreshInBackground()
        }
    }

    fun onMediaPermissionRequested() {
        mediaPermissionRequested = true
    }

    fun refreshStorageData() {
        viewModelScope.launch {
            _mainState.value = _mainState.value.copy(isLoading = true)
            val result = withContext(Dispatchers.IO) { scanAllStorage() }
            applyScanResult(result)
        }
    }

    private fun refreshInBackground() {
        if (scanInProgress) return
        scanInProgress = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { scanAllStorage() }
            applyScanResult(result)
            scanInProgress = false
        }
    }

    private fun applyScanResult(result: ScanResult) {
        val newCategories = if (cachedCategories.isEmpty()) {
            result.categories
        } else {
            result.categories.map { next ->
                val prev = cachedCategories.find { it.key == next.key }
                if (prev != null &&
                    prev.totalSizeBytes == next.totalSizeBytes &&
                    prev.items.size == next.items.size
                ) {
                    prev
                } else {
                    next
                }
            }
        }

        cachedCategories = newCategories
        cachedTotalBytes = result.total
        cachedUsedBytes = result.used

        runCatching {
            ScanCache.save(appContext, newCategories, result.total, result.used)
        }

        _mainState.value = MainUiState(
            categories = cachedCategories,
            totalStorageBytes = cachedTotalBytes,
            usedStorageBytes = cachedUsedBytes,
            isLoading = false,
            usageAccessGranted = hasUsageAccess(),
            fullStorageAccessGranted = hasFullStorageAccess()
        )
    }

    fun hasUsageAccess(): Boolean {
        return runCatching {
            val appOps = appContext.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                appContext.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(true)
    }

    fun selectCategory(categoryKey: String) {
        val category = cachedCategories.find { it.key == categoryKey } ?: return
        _detailState.value = DetailUiState(
            category = category,
            path = emptyList(),
            currentEntries = category.items,
            isLoading = false
        )
        applySearch("")
    }

    fun navigateToPath(categoryKey: String, path: List<Int>) {
        val category = cachedCategories.find { it.key == categoryKey } ?: return
        val entries = resolveEntries(category, path)
        _detailState.value = DetailUiState(
            category = category,
            path = path,
            currentEntries = entries,
            isLoading = false
        )
        applySearch(_detailState.value.searchQuery)
    }

    fun drillInto(index: Int) {
        val state = _detailState.value ?: return
        val category = state.category ?: return
        if (index !in state.currentEntries.indices) return
        val newPath = state.path + index
        val entries = resolveEntries(category, newPath)
        _detailState.value = state.copy(
            path = newPath,
            currentEntries = entries,
            searchQuery = "",
            highlightedIndex = -1
        )
        applySearch("")
    }

    fun goBackInDetail() {
        val state = _detailState.value ?: return
        val category = state.category ?: return
        if (state.path.isEmpty()) return
        val newPath = state.path.dropLast(1)
        val entries = resolveEntries(category, newPath)
        _detailState.value = state.copy(
            path = newPath,
            currentEntries = entries,
            searchQuery = "",
            highlightedIndex = -1
        )
        applySearch("")
    }

    fun onSearchQueryChanged(query: String) {
        applySearch(query)
    }

    private fun applySearch(query: String) {
        val state = _detailState.value
        val entries = state.currentEntries
        val trimmed = query.trim()
        val filtered = if (trimmed.isNotEmpty()) {
            entries.filter {
                it.name.contains(trimmed, ignoreCase = true) ||
                    it.description.contains(trimmed, ignoreCase = true)
            }.sortedByDescending { it.sizeBytes }
        } else {
            entries
        }
        val highlightedIndex = if (filtered.isNotEmpty()) 0 else -1
        _detailState.value = state.copy(
            searchQuery = query,
            filteredEntries = filtered,
            highlightedIndex = highlightedIndex
        )
    }

    fun resetDetailState() {
        _detailState.value = DetailUiState()
    }

    private fun resolveEntries(category: StorageCategory, path: List<Int>): List<StorageItem> {
        var entries = category.items
        for (idx in path) {
            if (idx in entries.indices) {
                entries = entries[idx].children
            } else {
                return emptyList()
            }
        }
        return entries
    }

    // ---------- Storage scanning ----------

    private data class ScanResult(
        val categories: List<StorageCategory>,
        val total: Long,
        val used: Long
    )

    private fun resolveStorageUuid(): UUID {
        return runCatching {
            val sm = appContext.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val vol = sm.primaryStorageVolume
            if (vol != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    vol.storageUuid
                } else {
                    vol.uuid?.let(UUID::fromString)
                }
            } else {
                null
            }
        }.getOrNull() ?: StorageManager.UUID_DEFAULT
    }

    private fun getTotalStorageBytes(): Long {
        return runCatching {
            val ssm = appContext.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
            ssm.getTotalBytes(resolveStorageUuid())
        }.getOrElse {
            runCatching { StatFs(Environment.getDataDirectory().path).totalBytes }.getOrDefault(0L)
        }
    }

    private fun getFreeStorageBytes(): Long {
        return runCatching {
            val ssm = appContext.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
            ssm.getFreeBytes(resolveStorageUuid())
        }.getOrElse {
            runCatching { StatFs(Environment.getDataDirectory().path).availableBytes }.getOrDefault(0L)
        }
    }

    fun hasFullStorageAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            appContext.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun scanAllStorage(): ScanResult {
        val total = getTotalStorageBytes()
        val free = getFreeStorageBytes()
        val used = (total - free).coerceAtLeast(0)

        val usageAccess = hasUsageAccess()
        val apps = loadApps(usageAccess)
        val photos = loadMediaGrouped(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "Foto")
        val videos = loadMediaGrouped(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "Video")
        val audio = loadAudio()

        val fullAccess = hasFullStorageAccess()
        val documents = if (fullAccess) {
            loadDocumentsFromWalk(walkSharedStorage())
        } else {
            loadDocuments()
        }
        val other = if (fullAccess) loadOtherFromWalk(walkSharedStorage()) else emptyList()
        val totalDocs = documents.sumOf { it.sizeBytes }
        val totalOther = other.sumOf { it.sizeBytes }

        val mediaTotal = photos.sumOf { it.sizeBytes } +
            videos.sumOf { it.sizeBytes } +
            audio.sumOf { it.sizeBytes }

        val totalApps = if (usageAccess) apps.sumOf { it.sizeBytes } else 0L
        val accounted = totalApps + mediaTotal + totalDocs + totalOther
        val systemSize = (used - accounted).coerceAtLeast(0)

        val categories = mutableListOf(
            StorageCategory("apps", "Applicazioni", "\uD83D\uDCF1", Color(0xFF42A5F5), totalApps, apps),
            StorageCategory("photos", "Foto", "\uD83D\uDCF7", Color(0xFF66BB6A), photos.sumOf { it.sizeBytes }, photos),
            StorageCategory("videos", "Video", "\uD83C\uDFAC", Color(0xFFFFA726), videos.sumOf { it.sizeBytes }, videos),
            StorageCategory("audio", "Audio", "\uD83C\uDFB5", Color(0xFFAB47BC), audio.sumOf { it.sizeBytes }, audio),
            StorageCategory("documents", "Documenti", "\uD83D\uDCC4", Color(0xFFEF5350), totalDocs, documents)
        )

        if (fullAccess) {
            categories += StorageCategory(
                "other", "Altro", "\uD83D\uDCDA", Color(0xFFBDBDBD), totalOther, other
            )
        }

        categories += StorageCategory(
            "system", "Sistema", "\u2699\uFE0F", Color(0xFF78909C),
            systemSize, emptyList(), isSystem = true
        )

        return ScanResult(categories, total, used)
    }

    private fun loadApps(usageAccess: Boolean = hasUsageAccess()): List<StorageItem> {
        val pm = appContext.packageManager
        val items = mutableListOf<StorageItem>()
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val storageUuid = resolveStorageUuid()
        val ssm = if (usageAccess) {
            appContext.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        } else null

        for (appInfo in installed) {
            try {
                if (appInfo.uid <= 0) continue
                var size = 0L

                if (ssm != null) {
                    try {
                        val stats = ssm.queryStatsForUid(storageUuid, appInfo.uid)
                        size = stats.appBytes + stats.dataBytes - stats.cacheBytes
                    } catch (_: Exception) { }
                }

                if (size <= 0L) {
                    val apk = appInfo.sourceDir
                    if (apk != null) {
                        val f = java.io.File(apk)
                        if (f.exists()) size = f.length()
                    }
                }

                if (size > 0L) {
                    val label = pm.getApplicationLabel(appInfo).toString()
                    val note = if (!usageAccess) "dimensione APK (stima)" else appInfo.packageName
                    items.add(
                        StorageItem(
                            name = label,
                            description = note,
                            sizeBytes = size,
                            color = Color.Unspecified,
                            packageName = appInfo.packageName
                        )
                    )
                }
            } catch (_: Exception) { }
        }

        val base = items
            .sortedByDescending { it.sizeBytes }
            .take(60)
            .mapIndexed { index, item ->
                item.copy(color = ITEM_PALETTE[index % ITEM_PALETTE.size])
            }

        return base.ifEmpty {
            val total = getTotalStorageBytes()
            val emulated = total / 100 * 18
            listOf(
                StorageItem(
                    name = "App installate",
                    description = "Concedi l'accesso all'utilizzo per dimensioni complete",
                    sizeBytes = emulated,
                    color = ITEM_PALETTE[0]
                )
            )
        }
    }

    private data class MediaFile(
        val group: String,
        val name: String,
        val size: Long,
        val contentUri: String? = null,
        val mimeType: String? = null
    )

    private fun queryMediaFiles(uri: Uri, fallbackMime: String): List<MediaFile> {
        val files = mutableListOf<MediaFile>()
        val projection = arrayOf(
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.MIME_TYPE
        )
        try {
            appContext.contentResolver.query(
                uri, projection,
                "${MediaStore.MediaColumns.SIZE} > ?",
                arrayOf("0"),
                "${MediaStore.MediaColumns.SIZE} DESC"
            )?.use { c ->
                val gi = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val ni = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val si = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val ii = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val mi = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                while (c.moveToNext()) {
                    val group = c.getString(gi) ?: "Sconosciuto"
                    val name = c.getString(ni) ?: group
                    val mime = c.getString(mi) ?: fallbackMime
                    val contentUri = android.content.ContentUris.withAppendedId(uri, c.getLong(ii)).toString()
                    files += MediaFile(group, name, c.getLong(si), contentUri, mime)
                }
            }
        } catch (_: Exception) {
            // permission not granted
        }
        return files
    }

    private fun loadMediaGrouped(uri: Uri, categoryType: String): List<StorageItem> {
        val fallbackMime = if (uri == MediaStore.Images.Media.EXTERNAL_CONTENT_URI) "image/*" else "video/*"
        val files = queryMediaFiles(uri, fallbackMime)
        val groups = files.groupBy { it.group }

        return groups.entries
            .map { (group, entries) ->
                val total = entries.sumOf { it.size }
                Total(group, categoryType, total, entries)
            }
            .sortedByDescending { it.size }
            .take(25)
            .mapIndexed { index, g ->
                val color = ITEM_PALETTE[index % ITEM_PALETTE.size]
                val children = g.files
                    .take(20)
                    .mapIndexed { fi, f ->
                        StorageItem(
                            name = f.name,
                            description = "$categoryType - ${g.name}",
                            sizeBytes = f.size,
                            color = ITEM_PALETTE[(index + fi + 1) % ITEM_PALETTE.size],
                            contentUri = f.contentUri,
                            mimeType = f.mimeType
                        )
                    }
                StorageItem(
                    name = g.name,
                    description = "$categoryType - album",
                    sizeBytes = g.size,
                    color = color,
                    children = children
                )
            }
    }

    private data class Total(
        val name: String,
        val categoryType: String,
        val size: Long,
        val files: List<MediaFile>
    )

    private fun loadAudio(): List<StorageItem> {
        data class AudioEntry(val type: String, val name: String, val size: Long, val uri: String, val mime: String)

        val audioFiles = mutableListOf<AudioEntry>()
        val projection = arrayOf(
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.IS_MUSIC,
            MediaStore.Audio.Media.IS_PODCAST,
            MediaStore.Audio.Media.IS_RINGTONE
        )
        try {
            appContext.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection,
                "${MediaStore.Audio.Media.SIZE} > ?",
                arrayOf("0"),
                "${MediaStore.Audio.Media.SIZE} DESC"
            )?.use { c ->
                val ni = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val si = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val ii = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val mi = c.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_MUSIC)
                val pi = c.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_PODCAST)
                val ri = c.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_RINGTONE)
                while (c.moveToNext()) {
                    val isMusic = c.getInt(mi) == 1
                    val isPodcast = c.getInt(pi) == 1
                    val isRingtone = c.getInt(ri) == 1
                    val type = when {
                        isMusic -> "Musica"
                        isPodcast -> "Podcast"
                        isRingtone -> "Suonerie"
                        else -> "Altro Audio"
                    }
                    val uri = android.content.ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, c.getLong(ii)
                    ).toString()
                    audioFiles += AudioEntry(
                        type = type,
                        name = c.getString(ni) ?: "Audio",
                        size = c.getLong(si),
                        uri = uri,
                        mime = if (isMusic) "audio/mpeg" else "audio/*"
                    )
                }
            }
        } catch (_: Exception) {
        }

        val groups = audioFiles.groupBy { it.type }
        return groups.entries
            .map { (type, entries) ->
                Triple(type, entries.sumOf { it.size }, entries)
            }
            .sortedByDescending { it.second }
            .mapIndexed { index, (type, total, entries) ->
                val color = ITEM_PALETTE[index % ITEM_PALETTE.size]
                val children = entries
                    .take(20)
                    .mapIndexed { fi, entry ->
                        StorageItem(
                            name = entry.name,
                            description = "Audio - $type",
                            sizeBytes = entry.size,
                            color = ITEM_PALETTE[(index + fi + 1) % ITEM_PALETTE.size],
                            contentUri = entry.uri,
                            mimeType = entry.mime
                        )
                    }
                StorageItem(
                    name = type,
                    description = "Audio - categoria",
                    sizeBytes = total,
                    color = color,
                    children = children
                )
            }
    }

    private fun loadDocuments(): List<StorageItem> {
        data class DocFile(val type: String, val name: String, val size: Long, val uri: String, val mime: String)

        val files = mutableListOf<DocFile>()
        val docMimeTypes = listOf(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
            "application/zip",
            "application/x-rar-compressed",
            "application/vnd.android.package-archive"
        )
        val projection = arrayOf(
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns._ID
        )
        val placeholders = docMimeTypes.joinToString(",") { "?" }
        val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} IN ($placeholders) AND ${MediaStore.Files.FileColumns.SIZE} > ?"
        val args = docMimeTypes.toTypedArray() + "0"
        val docUri = MediaStore.Files.getContentUri("external")

        try {
            appContext.contentResolver.query(
                docUri, projection,
                selection, args,
                "${MediaStore.Files.FileColumns.SIZE} DESC"
            )?.use { c ->
                val mi = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val ni = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val si = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val ii = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                while (c.moveToNext()) {
                    val mime = c.getString(mi) ?: "Altro"
                    val uri = android.content.ContentUris.withAppendedId(docUri, c.getLong(ii)).toString()
                    files += DocFile(
                        type = categorizeDocument(mime),
                        name = c.getString(ni) ?: "Documento",
                        size = c.getLong(si),
                        uri = uri,
                        mime = mime
                    )
                }
            }
        } catch (_: Exception) {
        }

        val groups = files.groupBy { it.type }
        return groups.entries
            .map { (type, entries) ->
                Triple(type, entries.sumOf { it.size }, entries)
            }
            .sortedByDescending { it.second }
            .mapIndexed { index, (type, total, entries) ->
                val color = ITEM_PALETTE[index % ITEM_PALETTE.size]
                val children = entries
                    .take(20)
                    .mapIndexed { fi, doc ->
                        StorageItem(
                            name = doc.name,
                            description = "Documenti - $type",
                            sizeBytes = doc.size,
                            color = ITEM_PALETTE[(index + fi + 1) % ITEM_PALETTE.size],
                            contentUri = doc.uri,
                            mimeType = doc.mime
                        )
                    }
                StorageItem(
                    name = type,
                    description = "Documenti - categoria",
                    sizeBytes = total,
                    color = color,
                    children = children
                )
            }
    }

    private data class ScannedFile(
        val file: File,
        val name: String,
        val ext: String,
        val size: Long
    )

    private val DOCUMENT_EXTS = setOf(
        "pdf", "doc", "docx", "odt", "xls", "xlsx", "ods", "csv",
        "ppt", "pptx", "odp", "txt", "rtf", "md", "zip", "rar", "7z",
        "tar", "gz", "bz2"
    )

    private val OTHER_EXTS = setOf(
        "apk", "iso", "img", "bin", "cab", "msi", "deb", "rpm", "dmg",
        "exe", "com", "bat", "cmd", "sh", "run", "appimage",
        "dat", "bak", "tmp", "log", "cfg", "ini", "conf", "config",
        "db", "sqlite", "sqlite3", "realm",
        "dll", "so", "dylib",
        "jar", "war", "ear",
        "py", "pyc", "pyo",
        "class", "dex", "o", "a",
        "ttf", "otf", "woff", "woff2",
        "patch", "diff",
        "iso", "nrg", "cue", "ccd", "sub",
        "torrent", "magnet"
    )

    private fun walkSharedStorage(maxFiles: Int = 1500): List<ScannedFile> {
        val out = mutableListOf<ScannedFile>()
        val root = Environment.getExternalStorageDirectory()
        val excluded = setOf("Android", ".thumbnails", ".cache")
        fun walk(dir: File, depth: Int) {
            if (depth > 5 || out.size >= maxFiles) return
            val children = runCatching { dir.listFiles() }.getOrNull() ?: return
            for (f in children) {
                if (out.size >= maxFiles) return
                if (f.isDirectory) {
                    if (f.name in excluded) continue
                    walk(f, depth + 1)
                } else {
                    val size = runCatching { f.length() }.getOrDefault(0L)
                    if (size > 0L) {
                        out += ScannedFile(f, f.name, f.extension.lowercase(), size)
                    }
                }
            }
        }
        walk(root, 0)
        return out
    }

    private fun mimeForExt(ext: String): String {
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: "application/octet-stream"
    }

    private fun loadDocumentsFromWalk(files: List<ScannedFile>): List<StorageItem> {
        val docs = files.filter { it.ext in DOCUMENT_EXTS }
        return docs.groupBy { categorizeDocumentExt(it.ext) }
            .mapNotNull { (type, entries) ->
                val total = entries.sumOf { it.size }
                if (total <= 0L) null
                else Triple(type, total, entries.sortedByDescending { it.size })
            }
            .sortedByDescending { it.second }
            .take(25)
            .mapIndexed { index, (type, total, entries) ->
                val color = ITEM_PALETTE[index % ITEM_PALETTE.size]
                val children = entries.take(20).mapIndexed { fi, f ->
                    StorageItem(
                        name = f.name,
                        description = "Documenti - $type",
                        sizeBytes = f.size,
                        color = ITEM_PALETTE[(index + fi + 1) % ITEM_PALETTE.size],
                        contentUri = Uri.fromFile(f.file).toString(),
                        mimeType = mimeForExt(f.ext)
                    )
                }
                StorageItem(
                    name = type,
                    description = "Documenti - categoria",
                    sizeBytes = total,
                    color = color,
                    children = children
                )
            }
    }

    private fun loadOtherFromWalk(files: List<ScannedFile>): List<StorageItem> {
        val others = files.filter { it.ext in OTHER_EXTS }
        return others.groupBy { it.file.parentFile?.name ?: "Spazio" }
            .mapNotNull { (folder, entries) ->
                val total = entries.sumOf { it.size }
                if (total <= 0L) null
                else Triple(folder, total, entries.sortedByDescending { it.size })
            }
            .sortedByDescending { it.second }
            .take(25)
            .mapIndexed { index, (folder, total, entries) ->
                val color = ITEM_PALETTE[index % ITEM_PALETTE.size]
                val children = entries.take(20).mapIndexed { fi, f ->
                    StorageItem(
                        name = f.name,
                        description = "Altro - $folder",
                        sizeBytes = f.size,
                        color = ITEM_PALETTE[(index + fi + 1) % ITEM_PALETTE.size],
                        contentUri = Uri.fromFile(f.file).toString(),
                        mimeType = mimeForExt(f.ext)
                    )
                }
                StorageItem(
                    name = folder,
                    description = "Altro - cartella",
                    sizeBytes = total,
                    color = color,
                    children = children
                )
            }
    }

    private fun categorizeDocumentExt(ext: String): String = when (ext) {
        "pdf" -> "PDF"
        "doc", "docx", "odt" -> "Word"
        "xls", "xlsx", "ods", "csv" -> "Excel"
        "ppt", "pptx", "odp" -> "PowerPoint"
        "txt", "rtf", "md" -> "Testo"
        "zip", "rar", "7z", "tar", "gz", "bz2" -> "Archivi"
        else -> "Altro"
    }

    private fun categorizeDocument(mimeType: String): String = when {
        mimeType.contains("pdf") -> "PDF"
        mimeType.contains("word") || mimeType.contains("document") -> "Word"
        mimeType.contains("excel") || mimeType.contains("sheet") -> "Excel"
        mimeType.contains("powerpoint") || mimeType.contains("presentation") -> "PowerPoint"
        mimeType.startsWith("text/") -> "Testo"
        mimeType.contains("zip") || mimeType.contains("rar") -> "Archivi"
        mimeType.contains("package-archive") -> "APK"
        else -> "Altro"
    }

    companion object {
        val ITEM_PALETTE = listOf(
            Color(0xFFEF5350), Color(0xFF42A5F5), Color(0xFF66BB6A),
            Color(0xFFFFA726), Color(0xFFAB47BC), Color(0xFF26C6DA),
            Color(0xFFFDD835), Color(0xFFEC407A), Color(0xFF5C6BC0),
            Color(0xFF8D6E63), Color(0xFF78909C), Color(0xFFD4E157),
            Color(0xFF7E57C2), Color(0xFF29B6F6), Color(0xFFFF7043),
            Color(0xFF9CCC65), Color(0xFF26A69A), Color(0xFFFFCA28),
            Color(0xFF5D4037), Color(0xFF009688), Color(0xFFCDDC39),
            Color(0xFF607D8B), Color(0xFFE91E63), Color(0xFF3F51B5),
            Color(0xFF00BCD4), Color(0xFFFF5722), Color(0xFF4CAF50),
            Color(0xFFC2185B), Color(0xFF1976D2), Color(0xFF388E3C)
        )
    }
}

class StorageViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StorageViewModel::class.java)) {
            return StorageViewModel(context.applicationContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}