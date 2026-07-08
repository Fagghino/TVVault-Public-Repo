package com.fagghino.tvvault.ui.screens

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
import com.fagghino.tvvault.data.local.entity.ImportMatchCandidate
import com.fagghino.tvvault.ui.viewmodel.MediaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReconciliationScreen(
    viewModel: MediaViewModel,
    jobId: Long,
    onBackClick: () -> Unit
) {
    val candidates by viewModel.observeCandidates(jobId).collectAsState(initial = emptyList())
    
    // Group active pending candidates by their raw title
    val pendingGrouped = candidates
        .filter { !it.accepted && !it.rejected }
        .groupBy { it.rawTitle }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reconcile_title)) },
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
            Text(
                text = stringResource(R.string.reconcile_subtitle),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (pendingGrouped.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.all_resolved), fontWeight = FontWeight.Bold)
                        Button(onClick = onBackClick) {
                            Text(stringResource(R.string.go_to_history))
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    items(pendingGrouped.keys.toList()) { rawTitle ->
                        val options = pendingGrouped[rawTitle] ?: emptyList()
                        ReconciliationGroupCard(
                            rawTitle = rawTitle,
                            options = options,
                            onAcceptOption = { candidateId ->
                                viewModel.reconcileCandidate(candidateId, true)
                            },
                            onRejectAll = {
                                options.forEach { option ->
                                    viewModel.reconcileCandidate(option.localId, false)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReconciliationGroupCard(
    rawTitle: String,
    options: List<ImportMatchCandidate>,
    onAcceptOption: (Long) -> Unit,
    onRejectAll: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.tv_time_export, rawTitle),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.primary
            )
            
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
            
            Text(
                text = stringResource(R.string.select_tmdb_match),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            options.forEach { option ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = option.candidateTitle,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            option.rawYear?.let { year ->
                                Text(
                                    text = stringResource(R.string.year_label, year),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        Button(
                            onClick = { onAcceptOption(option.localId) },
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(stringResource(R.string.accept_btn), fontSize = 11.sp)
                        }
                    }
                }
            }

            TextButton(
                onClick = onRejectAll,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(R.string.discard_title), fontSize = 12.sp)
            }
        }
    }
}
