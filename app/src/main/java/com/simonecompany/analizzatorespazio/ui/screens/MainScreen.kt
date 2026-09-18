package com.simonecompany.analizzatorespazio.ui.screens

import android.app.Activity
import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.simonecompany.analizzatorespazio.model.formatBytes
import com.simonecompany.analizzatorespazio.ui.components.CategoryLegendList
import com.simonecompany.analizzatorespazio.ui.components.DonutChart
import com.simonecompany.analizzatorespazio.viewmodel.StorageViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onCategoryClick: (String) -> Unit,
    viewModel: StorageViewModel
) {
    val state by viewModel.mainState.collectAsState()
    val context = LocalContext.current

    var permissionRequested by remember { mutableStateOf(false) }

    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionRequested = true
        viewModel.onMediaPermissionRequested()
        if (result.values.any { it }) {
            viewModel.refreshStorageData()
        }
    }

    val usageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshStorageData()
    }

    val fullAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshStorageData()
    }

    LaunchedEffect(Unit) {
        viewModel.resetDetailState()
        viewModel.loadStorageData()
    }

    LaunchedEffect(Unit) {
        if (!permissionRequested && !viewModel.mediaPermissionRequested) {
            permissionRequested = true
            viewModel.onMediaPermissionRequested()
            permissionLauncher.launch(permissions)
        }
    }

    var lastBackTime by remember { mutableStateOf(0L) }

    val activity = (context as? Activity)
        ?: (context as? ContextWrapper)?.baseContext as? Activity

    BackHandler {
        val now = System.currentTimeMillis()
        if (now - lastBackTime < 2000L) {
            activity?.finishAffinity()
        } else {
            lastBackTime = now
            Toast.makeText(context, "Premi di nuovo per uscire", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Analizzatore Spazio",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshStorageData() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Aggiorna"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(
                        text = "Analisi della memoria in corso...",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }
        } else {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                val landscape = maxWidth > maxHeight
                val usedPct = if (state.totalStorageBytes > 0) {
                    state.usedStorageBytes.toFloat() / state.totalStorageBytes * 100f
                } else 0f

                val activeCategories = state.categories.filter { it.totalSizeBytes > 0L }
                val activeTotal = activeCategories.sumOf { it.totalSizeBytes.toDouble() }.toFloat()

                val chart: @Composable () -> Unit = {
                    val percentages = if (activeTotal > 0f) {
                        activeCategories.map { category ->
                            category.totalSizeBytes.toFloat() / activeTotal * 100f
                        }
                    } else {
                        emptyList()
                    }
                    DonutChart(
                        percentages = percentages,
                        colors = activeCategories.map { it.color },
                        modifier = if (landscape) {
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                        },
                        centerContent = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = formatBytes(state.usedStorageBytes),
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "di ${formatBytes(state.totalStorageBytes)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format("%.1f%% in uso", usedPct),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    )
                }

                val usageCard: @Composable () -> Unit = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !state.usageAccessGranted) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Permesso mancante",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Senza accesso ai dati di utilizzo, le dimensioni delle app non sono disponibili.\nPremi qui sotto per concedere il permesso.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(
                                    onClick = {
                                        runCatching {
                                            usageLauncher.launch(
                                                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                            )
                                        }
                                    }
                                ) {
                                    Text("Apri Impostazioni")
                                }
                            }
                        }
                    }
                }

                val fullAccessCard: @Composable () -> Unit = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !state.fullStorageAccessGranted) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "File completi",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Per vedere documenti, APK e altri file serve l'accesso completo ai file.\nPremi qui sotto per concedere il permesso.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(
                                    onClick = {
                                        runCatching {
                                            fullAccessLauncher.launch(
                                                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                            )
                                        }
                                    }
                                ) {
                                    Text("Apri Impostazioni")
                                }
                            }
                        }
                    }
                }

                if (landscape) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            chart()
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            usageCard()
                            fullAccessCard()
                            CategoryLegendList(
                                categories = state.categories,
                                totalSizeBytes = state.totalStorageBytes,
                                onCategoryClick = onCategoryClick,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        chart()
                        usageCard()
                        fullAccessCard()
                        CategoryLegendList(
                            categories = state.categories,
                            totalSizeBytes = state.totalStorageBytes,
                            onCategoryClick = onCategoryClick,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}