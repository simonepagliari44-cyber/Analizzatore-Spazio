package com.simonecompany.analizzatorespazio.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.simonecompany.analizzatorespazio.model.StorageItem
import com.simonecompany.analizzatorespazio.ui.components.DrilledList
import com.simonecompany.analizzatorespazio.viewmodel.StorageViewModel
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    categoryKey: String,
    indices: List<Int>,
    onBack: () -> Unit,
    viewModel: StorageViewModel
) {
    val state by viewModel.detailState.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val handleBack: () -> Unit = {
        if (state.path.isNotEmpty()) {
            viewModel.goBackInDetail()
            scope.launch {
                listState.scrollToItem(0)
            }
        } else {
            onBack()
        }
    }

    BackHandler(onBack = handleBack)

    val openItem: (StorageItem) -> Unit = { item ->
        when {
            item.packageName != null -> {
                val intent = context.packageManager.getLaunchIntentForPackage(item.packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                        .onFailure { e ->
                            Toast.makeText(context, "Apri app impossibile (${e::class.simpleName})", Toast.LENGTH_LONG).show()
                        }
                } else {
                    Toast.makeText(context, "Nessun avvio per ${item.packageName}", Toast.LENGTH_SHORT).show()
                }
            }
            item.contentUri != null -> {
                val raw = item.contentUri
                var uri: android.net.Uri? = null
                var fileNameForMime: String? = null

                when {
                    raw.startsWith("file://") -> {
                        val file = File(raw.removePrefix("file://"))
                        uri = runCatching {
                            FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                        }.getOrNull()
                        fileNameForMime = file.name
                    }
                    raw.startsWith("content://") -> {
                        val parsed = Uri.parse(raw)
                        if (parsed.authority?.startsWith("media") == true) {
                            uri = parsed
                            fileNameForMime = item.name
                        } else {
                            val realPath = resolveRealPath(context, raw)
                            val file = realPath?.let { File(it) }
                            if (file != null && file.exists()) {
                                uri = runCatching {
                                    FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                                }.getOrNull()
                                fileNameForMime = file.name
                            } else {
                                uri = parsed
                                fileNameForMime = item.name
                            }
                        }
                    }
                    else -> {
                        uri = android.net.Uri.parse(raw)
                        fileNameForMime = raw.substringAfterLast('/')
                    }
                }

                if (uri != null) {
                    val type = when {
                        !item.mimeType.isNullOrBlank() -> item.mimeType!!
                        !fileNameForMime.isNullOrBlank() -> inferMime(fileNameForMime!!)
                        else -> runCatching { context.contentResolver.getType(uri) }
                            .getOrNull() ?: "application/octet-stream"
                    }
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, type)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching { context.startActivity(intent) }
                    .onFailure { e ->
                        runCatching { context.startActivity(Intent.createChooser(intent, "Apri con")) }
                            .onFailure { e2 ->
                                Toast.makeText(
                                    context,
                                    "Apri impossibile (${e2::class.simpleName})",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                    }
                } else {
                    Toast.makeText(context, "File non accessibile", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(categoryKey, indices) {
        viewModel.navigateToPath(categoryKey, indices)
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            matches?.firstOrNull()?.let { spokenText ->
                viewModel.onSearchQueryChanged(spokenText)
            }
        }
    }

    val category = state.category

    val breadcrumb = remember(category, state.path, state.currentEntries) {
        if (category == null) {
            ""
        } else {
            val names = mutableListOf(category.displayName)
            var entries = category.items
            for (idx in state.path) {
                if (idx in entries.indices) {
                    names += entries[idx].name
                    entries = entries[idx].children
                }
            }
            names.joinToString(" › ")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = breadcrumb.ifEmpty { "Dettaglio" },
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Indietro"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = category?.color?.copy(alpha = 0.18f)
                        ?: MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        if (state.isLoading || category == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
                // Header summary
                val shownTotal = state.currentEntries.sumOf { it.sizeBytes }
                val headerSize = category.totalSizeFormatted()
                Text(
                    text = "${category.icon}  ${category.displayName}  •  $headerSize",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (state.path.isNotEmpty()) {
                    Text(
                        text = "${shownTotal.toFormatted()} totali in questa cartella",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Cerca e filtra...") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Cerca")
                    },
                    trailingIcon = {
                        OutlinedIconButton(
                            onClick = {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(
                                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                    )
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
                                    putExtra(
                                        RecognizerIntent.EXTRA_PROMPT,
                                        "Pronuncia il nome da cercare"
                                    )
                                }
                                runCatching { speechLauncher.launch(intent) }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Ricerca vocale",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    singleLine = true
                )

                val isFiltering = state.searchQuery.isNotBlank()
                val displayList = if (isFiltering) state.filteredEntries else state.currentEntries

                if (displayList.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isFiltering)
                                "Nessun risultato per \"${state.searchQuery}\""
                            else
                                "Nessun elemento in questa categoria",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                val totalForBars =
                    if (isFiltering) state.filteredEntries.sumOf { it.sizeBytes }
                    else state.currentEntries.sumOf { it.sizeBytes }

                DrilledList(
                    items = displayList,
                    totalSizeBytes = totalForBars,
                    highlightedIndex = state.highlightedIndex,
                    onItemClick = { lstIndex ->
                        val item = displayList.getOrNull(lstIndex) ?: return@DrilledList
                        if (item.hasChildren) {
                            val realIndex = state.currentEntries.indexOf(item)
                            if (realIndex >= 0) {
                                viewModel.drillInto(realIndex)
                                scope.launch {
                                    listState.scrollToItem(0)
                                }
                            }
                        } else if (item.isLaunchable) {
                            openItem(item)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}

private fun Long.toFormatted(): String {
    val gb = this / (1024.0 * 1024.0 * 1024.0)
    val mb = this / (1024.0 * 1024.0)
    return when {
        gb >= 1.0 -> String.format("%.1f GB", gb)
        mb >= 1.0 -> String.format("%.1f MB", mb)
        else -> String.format("%.0f KB", this / 1024.0)
    }
}

private val EXTENSION_MIME = mapOf(
    "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png",
    "gif" to "image/gif", "webp" to "image/webp", "heic" to "image/heic",
    "heif" to "image/heif", "bmp" to "image/bmp", "svg" to "image/svg+xml",
    "mp4" to "video/mp4", "mkv" to "video/x-matroska", "mov" to "video/quicktime",
    "avi" to "video/x-msvideo", "webm" to "video/webm", "3gp" to "video/3gpp",
    "mp3" to "audio/mpeg", "wav" to "audio/x-wav", "flac" to "audio/flac",
    "aac" to "audio/aac", "ogg" to "audio/ogg", "m4a" to "audio/mp4",
    "pdf" to "application/pdf",
    "doc" to "application/msword",
    "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "xls" to "application/vnd.ms-excel",
    "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "ppt" to "application/vnd.ms-powerpoint",
    "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "txt" to "text/plain", "csv" to "text/csv",
    "rtf" to "application/rtf", "odt" to "application/vnd.oasis.opendocument.text",
    "zip" to "application/zip", "rar" to "application/vnd.rar",
    "7z" to "application/x-7z-compressed",
    "apk" to "application/vnd.android.package-archive",
    "iso" to "application/x-iso9660-image", "bin" to "application/octet-stream",
    "ttf" to "font/ttf", "html" to "text/html", "htm" to "text/html"
)

private fun inferMime(fileName: String): String {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        ?: EXTENSION_MIME[ext]
        ?: "application/octet-stream"
}

private fun resolveRealPath(context: Context, contentUriRaw: String): String? {
    return runCatching {
        val uri = android.net.Uri.parse(contentUriRaw)
        var path: String? = null
        context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DATA),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                path = cursor.getString(0)
            }
        }
        path?.takeIf { it.isNotBlank() && File(it).exists() }
    }.getOrNull()
}