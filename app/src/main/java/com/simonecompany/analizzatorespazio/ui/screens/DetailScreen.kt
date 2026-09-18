package com.simonecompany.analizzatorespazio.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
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
                        .onFailure {
                            Toast.makeText(context, "Impossibile aprire l'app", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Toast.makeText(context, "Nessuna app disponibile", Toast.LENGTH_SHORT).show()
                }
            }
            item.contentUri != null -> {
                val raw = item.contentUri
                val uri = if (raw.startsWith("file://")) {
                    runCatching {
                        FileProvider.getUriForFile(
                            context,
                            context.packageName + ".fileprovider",
                            File(raw.removePrefix("file://"))
                        )
                    }.getOrNull()
                } else {
                    android.net.Uri.parse(raw)
                }
                if (uri != null) {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, item.mimeType ?: "*/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { context.startActivity(intent) }
                        .onFailure {
                            Toast.makeText(context, "Nessuna app per aprire questo file", Toast.LENGTH_SHORT).show()
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