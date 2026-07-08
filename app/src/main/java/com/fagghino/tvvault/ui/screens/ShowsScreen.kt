package com.fagghino.tvvault.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import com.fagghino.tvvault.ui.components.MediaCard
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fagghino.tvvault.R
import com.fagghino.tvvault.data.local.entity.MediaItem
import com.fagghino.tvvault.ui.viewmodel.MediaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowsScreen(
    viewModel: MediaViewModel,
    onShowClick: (String) -> Unit
) {
    val showsList by viewModel.shows.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val groupOrder by viewModel.groupOrder.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf<String?>(null) }

    val localProviderIds = showsList.map { it.providerId }.toSet()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Upper search bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { query ->
                    searchQuery = query
                    viewModel.searchOnline(query, "tv")
                },
                placeholder = { Text(stringResource(R.string.search_placeholder_shows)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            viewModel.clearSearch()
                        }) {
                            Text(stringResource(R.string.cancel), fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
        }

        if (searchQuery.isNotEmpty()) {
            // Search Results Mode
            Text(
                text = stringResource(R.string.online_results, searchQuery),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (isSearching) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (searchResults.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_online_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(110.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(searchResults) { result ->
                        val isAdded = result.providerId in localProviderIds
                        MediaCard(
                            title = result.title,
                            posterPath = result.posterPath,
                            label = if (isAdded) "✓" else "+",
                            onClick = {
                                val matchedLocal = showsList.firstOrNull { it.providerId == result.providerId }
                                if (matchedLocal != null) {
                                    onShowClick(matchedLocal.localId.toString())
                                } else {
                                    viewModel.addMediaToLibrary(result)
                                }
                            }
                        )
                    }
                }
            }
        } else {
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

            val showsWithState by viewModel.showsWithState.collectAsState()
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
                            val label = when (state?.personalStatus) {
                                "watching" -> "In Corso"
                                "completed" -> "Completato"
                                "dropped" -> "Abbandonato"
                                "watchlist" -> "Da Vedere"
                                else -> null
                            }
                            MediaCard(
                                title = show.title,
                                posterPath = show.posterPath,
                                label = label,
                                onClick = { onShowClick(show.localId.toString()) }
                            )
                        }
                    }
                }
            }
        }
    }
}


