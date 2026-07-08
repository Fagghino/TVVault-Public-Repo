package com.fagghino.tvvault.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fagghino.tvvault.R
import com.fagghino.tvvault.data.local.entity.ImportJob
import com.fagghino.tvvault.ui.viewmodel.MediaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportJobsScreen(
    viewModel: MediaViewModel,
    onBackClick: () -> Unit,
    onReconcileClick: (Long) -> Unit
) {
    val importJobs by viewModel.importJobs.collectAsState()

    // SAF file picker contract
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importFollowedShows(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Main instructions & Picker button
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.import_desc),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            filePickerLauncher.launch(arrayOf("text/*", "application/*"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(stringResource(R.string.select_csv), color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }

            Text(
                text = stringResource(R.string.sessions_history),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            if (importJobs.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.no_sessions), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(importJobs) { job ->
                        ImportJobCard(job = job, onReconcileClick = { onReconcileClick(job.localId) })
                    }
                }
            }
        }
    }
}

@Composable
fun ImportJobCard(
    job: ImportJob,
    onReconcileClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = job.fileName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                val (statusText, statusColor) = when (job.status) {
                    "running" -> stringResource(R.string.status_running) to MaterialTheme.colorScheme.primary
                    "requires_reconciliation" -> stringResource(R.string.status_reconcile) to MaterialTheme.colorScheme.tertiary
                    "completed" -> stringResource(R.string.status_completed) to MaterialTheme.colorScheme.primary
                    else -> stringResource(R.string.status_failed) to MaterialTheme.colorScheme.error
                }
                
                Badge(
                    containerColor = statusColor.copy(alpha = 0.15f),
                    contentColor = statusColor
                ) {
                    Text(
                        text = statusText,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (job.status == "requires_reconciliation") {
                Text(
                    text = stringResource(R.string.reconcile_desc),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onReconcileClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Text(stringResource(R.string.status_reconcile), color = MaterialTheme.colorScheme.onTertiary)
                }
            }

            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

            // Stats grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatLabelValue(stringResource(R.string.total_rows), job.totalRows.toString())
                StatLabelValue(stringResource(R.string.matched_rows), job.matchedRows.toString())
                StatLabelValue(stringResource(R.string.ambiguous_rows), job.ambiguousRows.toString())
                StatLabelValue(stringResource(R.string.unmatched_rows), job.unmatchedRows.toString())
            }

            if (job.notes != null) {
                Text(
                    text = "Error: ${job.notes}",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Light
                )
            }
        }
    }
}

@Composable
fun StatLabelValue(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(text = label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
