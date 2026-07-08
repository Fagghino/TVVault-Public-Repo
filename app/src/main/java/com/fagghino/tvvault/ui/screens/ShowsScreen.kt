package com.fagghino.tvvault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fagghino.tvvault.R
import com.fagghino.tvvault.data.local.entity.MediaItem
import com.fagghino.tvvault.data.local.entity.Episode
import com.fagghino.tvvault.ui.components.MediaCard
import com.fagghino.tvvault.ui.viewmodel.MediaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowsScreen(
    viewModel: MediaViewModel,
    onShowClick: (String) -> Unit
) {
    val showsWithState by viewModel.showsWithState.collectAsState()
    val groupOrder by viewModel.groupOrder.collectAsState()
    var selectedFilter by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Filter chips row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedFilter == null,
                onClick = { selectedFilter = null },
                label = { Text(stringResource(R.string.all_filter)) }
            )
            FilterChip(
                selected = selectedFilter == "watching",
                onClick = { selectedFilter = if (selectedFilter == "watching") null else "watching" },
                label = { Text(stringResource(R.string.watching_filter)) }
            )
            FilterChip(
                selected = selectedFilter == "completed",
                onClick = { selectedFilter = if (selectedFilter == "completed") null else "completed" },
                label = { Text(stringResource(R.string.completed_filter)) }
            )
            FilterChip(
                selected = selectedFilter == "dropped",
                onClick = { selectedFilter = if (selectedFilter == "dropped") null else "dropped" },
                label = { Text(stringResource(R.string.dropped_filter)) }
            )
            FilterChip(
                selected = selectedFilter == "watchlist",
                onClick = { selectedFilter = if (selectedFilter == "watchlist") null else "watchlist" },
                label = { Text(stringResource(R.string.not_started_filter)) }
            )
        }

        val groupOrderList = groupOrder.split(",").filter { it.isNotBlank() }
        val statusLabels = mapOf(
            "watching" to stringResource(R.string.watching_filter),
            "watchlist" to stringResource(R.string.not_started_filter),
            "dropped" to stringResource(R.string.dropped_filter),
            "completed" to stringResource(R.string.completed_filter)
        )

        val grouped = groupOrderList.mapNotNull { status ->
            val group = showsWithState.filter { (_, state) ->
                (state?.personalStatus ?: "watchlist") == status
            }.filter { (_, state) ->
                selectedFilter == null || (state?.personalStatus ?: "watchlist") == selectedFilter
            }
            if (group.isEmpty()) null else status to group
        }

        if (showsWithState.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.empty_shows_library), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                grouped.forEach { (status, items) ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = statusLabels[status] ?: status,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }
                    items(items) { (show, state) ->
                        ShowLibraryCard(
                            show = show,
                            personalStatus = state?.personalStatus,
                            viewModel = viewModel,
                            onClick = { onShowClick(show.localId.toString()) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ShowLibraryCard(
    show: MediaItem,
    personalStatus: String?,
    viewModel: MediaViewModel,
    onClick: () -> Unit
) {
    var nextEpisode by remember { mutableStateOf<Episode?>(null) }
    LaunchedEffect(show.localId) {
        nextEpisode = viewModel.getNextEpisodeToWatch(show.localId)
    }

    val label = when {
        personalStatus == "completed" -> "Completata"
        personalStatus == "dropped" -> "Abbandonata"
        nextEpisode != null -> "S${nextEpisode!!.seasonNumber}E${nextEpisode!!.episodeNumber}"
        personalStatus == "watching" -> "In Corso"
        personalStatus == "watchlist" -> "Da Vedere"
        else -> null
    }

    MediaCard(
        title = show.title,
        posterPath = show.posterPath,
        label = label,
        onClick = onClick
    )
}
