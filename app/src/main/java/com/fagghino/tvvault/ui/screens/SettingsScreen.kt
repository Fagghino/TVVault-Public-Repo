package com.fagghino.tvvault.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fagghino.tvvault.R
import com.fagghino.tvvault.ui.viewmodel.MediaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MediaViewModel,
    onBackClick: () -> Unit,
    onManageImportClick: () -> Unit
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val groupOrder by viewModel.groupOrder.collectAsState()

    val groupOrderList = groupOrder.split(",").filter { it.isNotBlank() }

    val statusLabels = mapOf(
        "watching" to "In Visione",
        "watchlist" to "Non iniziate",
        "dropped" to "Interrotte",
        "completed" to "Terminate"
    )

    val context = LocalContext.current
    
    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            viewModel.exportBackup(it) { success ->
                if (success) Toast.makeText(context, "Backup JSON salvato!", Toast.LENGTH_SHORT).show()
                else Toast.makeText(context, "Errore salvataggio JSON", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val exportZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let {
            viewModel.exportBackupToZip(it) { success ->
                if (success) Toast.makeText(context, "Backup ZIP salvato!", Toast.LENGTH_SHORT).show()
                else Toast.makeText(context, "Errore salvataggio ZIP", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.importBackup(it) { success ->
                if (success) Toast.makeText(context, "Ripristino completato!", Toast.LENGTH_SHORT).show()
                else Toast.makeText(context, "Errore ripristino", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Impostazioni", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Indietro")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ─── Account & Sincronizzazione ──────────────────────────────────
            val userEmail by viewModel.userEmail.collectAsState()
            val isSyncing by viewModel.isSyncing.collectAsState()

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Account",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column {
                        if (userEmail == null) {
                            SettingsOptionItem(
                                title = "Accedi con Google",
                                subtitle = "Attiva il backup e la sincronizzazione in tempo reale",
                                icon = Icons.Default.AccountCircle,
                                onClick = {
                                    viewModel.login { success, msg ->
                                        if (success) {
                                            Toast.makeText(context, "Accesso completato!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Errore: $msg", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            )
                        } else {
                            SettingsOptionItem(
                                title = userEmail ?: "Utente",
                                subtitle = "Sincronizzazione in tempo reale attiva",
                                icon = Icons.Default.Check,
                                onClick = {}
                            )
                            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                            SettingsOptionItem(
                                title = "Esci",
                                subtitle = "Disconnetti l'account",
                                icon = Icons.Default.ExitToApp,
                                onClick = { viewModel.logout() }
                            )
                        }
                    }
                }
            }

            // ─── Tema ────────────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Aspetto",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column {
                        ThemeOptionRow(
                            label = "Segui sistema",
                            selected = themeMode == "system",
                            onClick = { viewModel.saveThemeMode("system") }
                        )
                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                        ThemeOptionRow(
                            label = "Chiaro",
                            selected = themeMode == "light",
                            onClick = { viewModel.saveThemeMode("light") }
                        )
                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                        ThemeOptionRow(
                            label = "Scuro",
                            selected = themeMode == "dark",
                            onClick = { viewModel.saveThemeMode("dark") }
                        )
                    }
                }
            }

            // ─── Ordine gruppi serie ──────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Ordine gruppi serie TV",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Tocca un gruppo per spostarlo in cima. L'ordine determina come vengono raggruppate le serie nella libreria.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column {
                        groupOrderList.forEachIndexed { index, status ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (index > 0) {
                                            // Move this item one position up
                                            val mutable = groupOrderList.toMutableList()
                                            mutable.removeAt(index)
                                            mutable.add(index - 1, status)
                                            viewModel.saveGroupOrder(mutable.joinToString(","))
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.width(24.dp)
                                    )
                                    Text(
                                        text = statusLabels[status] ?: status,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (index > 0) {
                                    Text(
                                        "▲ Sposta su",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (index < groupOrderList.lastIndex) {
                                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                            }
                        }
                    }
                }
            }

            // ─── Import / Export ──────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Dati e Backup",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column {
                        SettingsOptionItem(
                            title = stringResource(R.string.action_import),
                            subtitle = stringResource(R.string.action_import_desc),
                            icon = Icons.Default.Refresh,
                            onClick = onManageImportClick
                        )
                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                        SettingsOptionItem(
                            title = stringResource(R.string.action_export_json),
                            subtitle = stringResource(R.string.action_export_json_desc),
                            icon = Icons.Default.Share,
                            onClick = { exportJsonLauncher.launch("tvvault_backup_${System.currentTimeMillis()}.json") }
                        )
                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                        SettingsOptionItem(
                            title = stringResource(R.string.action_export_zip),
                            subtitle = stringResource(R.string.action_export_zip_desc),
                            icon = Icons.Default.Share,
                            onClick = { exportZipLauncher.launch("tvvault_backup_${System.currentTimeMillis()}.zip") }
                        )
                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                        SettingsOptionItem(
                            title = stringResource(R.string.action_restore),
                            subtitle = stringResource(R.string.action_restore_desc),
                            icon = Icons.Default.Settings,
                            onClick = { importJsonLauncher.launch(arrayOf("application/json", "application/zip")) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsOptionItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) },
        supportingContent = { Text(subtitle, fontSize = 12.sp) },
        leadingContent = {
            Icon(icon, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        },
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun ThemeOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
