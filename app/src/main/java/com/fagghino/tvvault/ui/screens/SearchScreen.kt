package com.fagghino.tvvault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fagghino.tvvault.ui.components.MediaCard
import com.fagghino.tvvault.ui.viewmodel.MediaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: MediaViewModel,
    onShowClick: (String) -> Unit,
    onMovieClick: (String) -> Unit
) {
    val showsList by viewModel.shows.collectAsState()
    val moviesList by viewModel.movies.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = TV Shows, 1 = Movies

    val localShowsMap = showsList.associateBy { it.providerId }
    val localMoviesMap = moviesList.associateBy { it.providerId }

    // Clear search query when tab switches
    LaunchedEffect(selectedTab) {
        searchQuery = ""
        viewModel.clearSearch()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Search Bar
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
                    val type = if (selectedTab == 0) "tv" else "movie"
                    if (query.isNotBlank()) {
                        viewModel.searchOnline(query, type)
                    } else {
                        viewModel.clearSearch()
                    }
                },
                placeholder = {
                    Text(if (selectedTab == 0) "Cerca serie TV online..." else "Cerca film online...")
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            viewModel.clearSearch()
                        }) {
                            Text("Cancella", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
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

        // Tab Row
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Serie TV", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Film", fontWeight = FontWeight.Bold) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Content Area
        Box(modifier = Modifier.weight(1f)) {
            if (searchQuery.isBlank()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (selectedTab == 0) "Cerca serie TV da aggiungere alla tua watchlist" else "Cerca film da aggiungere alla tua watchlist",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 32.dp),
                        fontSize = 14.sp
                    )
                }
            } else if (isSearching) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (searchResults.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nessun risultato online trovato.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(110.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(searchResults) { result ->
                        val matchedLocalShow = localShowsMap[result.providerId]
                        val matchedLocalMovie = localMoviesMap[result.providerId]
                        val isAdded = matchedLocalShow != null || matchedLocalMovie != null

                        MediaCard(
                            title = result.title,
                            posterPath = result.posterPath,
                            label = if (isAdded) "✓" else "+",
                            onClick = {
                                if (selectedTab == 0) {
                                    if (matchedLocalShow != null) {
                                        onShowClick(matchedLocalShow.localId.toString())
                                    } else {
                                        viewModel.addMediaToLibrary(result)
                                    }
                                } else {
                                    if (matchedLocalMovie != null) {
                                        onMovieClick(matchedLocalMovie.localId.toString())
                                    } else {
                                        viewModel.addMediaToLibrary(result)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
