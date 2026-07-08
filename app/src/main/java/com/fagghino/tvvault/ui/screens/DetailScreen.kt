package com.fagghino.tvvault.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
fun DetailScreen(
    viewModel: MediaViewModel,
    titleId: String,
    onBackClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    
    val mediaId = titleId.toLongOrNull() ?: 0L
    var mediaItem by remember { mutableStateOf<MediaItem?>(null) }
    
    LaunchedEffect(mediaId) {
        mediaItem = viewModel.getMediaItemById(mediaId)
    }

    val item = mediaItem
    if (item == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val userState by viewModel.observeMediaState(mediaId).collectAsState(initial = null)
    val seasons by viewModel.observeSeasons(mediaId).collectAsState(initial = emptyList())
    val watchedEpisodes by viewModel.observeWatchedEpisodes(mediaId).collectAsState(initial = emptyList())

    val state = userState
    var notesText by remember { mutableStateOf("") }
    var ratingValue by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(state) {
        state?.let {
            notesText = it.notes ?: ""
            ratingValue = it.rating ?: 0f
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item.title) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val isFav = state?.favorite ?: false
                    IconButton(
                        onClick = {
                            viewModel.toggleFavorite(mediaId, !isFav)
                            val text = if (!isFav) R.string.added_to_favorites else R.string.removed_from_favorites
                            Toast.makeText(context, context.getString(text), Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
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
                .verticalScroll(scrollState)
        ) {
            // Massive Edge-to-Edge Banner / Poster card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
            ) {
                // Show backdrop if available, fallback to gradient
                val backdropUrl = item.backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" }
                val posterUrl = item.posterPath?.let { "https://image.tmdb.org/t/p/w342$it" }
                if (backdropUrl != null) {
                    AsyncImage(
                        model = backdropUrl,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    // Gradient overlay for text readability
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .background(Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                            ))
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        if (posterUrl != null) {
                            AsyncImage(
                                model = posterUrl,
                                contentDescription = item.title,
                                modifier = Modifier.fillMaxHeight(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier.align(Alignment.BottomStart).padding(20.dp)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = if (backdropUrl != null) Color.White else MaterialTheme.colorScheme.primary
                    )
                    item.releaseDate?.let {
                        Text(
                            text = it.take(4), // Just year looks cleaner on headers
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (backdropUrl != null) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Content below header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Description overview
                Text(
                    text = item.overview.ifBlank { "Nessuna descrizione disponibile." },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

            // Dropdown Status Selector
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Stato personale",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                var expanded by remember { mutableStateOf(false) }
                val statusOptions = if (item.mediaType == "tv") {
                    listOf("watching", "completed", "watchlist", "paused", "dropped")
                } else {
                    listOf("watchlist", "completed")
                }

                val currentStatus = state?.personalStatus ?: "watchlist"

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = when (currentStatus) {
                                "watching" -> stringResource(R.string.watching_status)
                                "completed" -> stringResource(R.string.completed_status)
                                "watchlist" -> stringResource(R.string.watchlist_status)
                                "paused" -> stringResource(R.string.paused_status)
                                "dropped" -> stringResource(R.string.dropped_status)
                                else -> currentStatus
                            }
                        )
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        statusOptions.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when (option) {
                                            "watching" -> stringResource(R.string.watching_status)
                                            "completed" -> stringResource(R.string.completed_status)
                                            "watchlist" -> stringResource(R.string.watchlist_status)
                                            "paused" -> stringResource(R.string.paused_status)
                                            "dropped" -> stringResource(R.string.dropped_status)
                                            else -> option
                                        }
                                    )
                                },
                                onClick = {
                                    viewModel.updateStatus(mediaId, option)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Interactive Rating Rating Bar
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.personal_rating),
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        (1..5).forEach { index ->
                            val isSelected = ratingValue >= index
                            IconButton(onClick = { ratingValue = index.toFloat() }) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (ratingValue > 0f) "$ratingValue / 5.0" else "--",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            // Save rating button
            Button(
                onClick = {
                    viewModel.updateRatingAndNotes(
                        mediaId = mediaId,
                        rating = if (ratingValue > 0f) ratingValue else null,
                        notes = null
                    )
                    Toast.makeText(context, context.getString(R.string.info_saved), Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(stringResource(R.string.save_notes), color = MaterialTheme.colorScheme.onPrimary)
            }

            // TV Show Seasons and Episodes checklist
            if (item.mediaType == "tv" && seasons.isNotEmpty()) {
                Text(
                    text = "Stagioni ed Episodi",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )

                seasons.forEach { season ->
                    var isExpanded by remember { mutableStateOf(false) }
                    val episodesList by viewModel.observeEpisodes(mediaId, season.seasonNumber).collectAsState(initial = emptyList())
                    
                    val seasonEpIds = episodesList.map { it.localId }.toSet()
                    val watchedEpIds = watchedEpisodes.map { it.episodeLocalId }.toSet()
                    
                    val allWatched = seasonEpIds.isNotEmpty() && seasonEpIds.all { it in watchedEpIds }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column {
                            ListItem(
                                headlineContent = { Text(season.name, fontWeight = FontWeight.SemiBold) },
                                supportingContent = { Text("Episodi: ${season.episodeCount}") },
                                trailingContent = {
                                    Button(
                                        onClick = {
                                            viewModel.setSeasonWatched(mediaId, season.seasonNumber, !allWatched)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (allWatched) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary
                                        ),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text(
                                            text = if (allWatched) stringResource(R.string.mark_none_season) else stringResource(R.string.mark_all_season),
                                            fontSize = 10.sp,
                                            color = if (allWatched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                },
                                modifier = Modifier.clickable { isExpanded = !isExpanded },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )

                            if (isExpanded) {
                                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.05f))
                                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    episodesList.forEach { episode ->
                                        val isEpWatched = episode.localId in watchedEpIds
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    viewModel.setEpisodeWatched(episode.localId, !isEpWatched, mediaId)
                                                }
                                                .padding(vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Ep. ${episode.episodeNumber} - ${episode.name}",
                                                fontSize = 14.sp,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Checkbox(
                                                checked = isEpWatched,
                                                onCheckedChange = { checked ->
                                                    viewModel.setEpisodeWatched(episode.localId, checked, mediaId)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }
}
