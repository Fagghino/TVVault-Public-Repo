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
import androidx.compose.material.icons.filled.MoreVert
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
import com.fagghino.tvvault.data.local.entity.Episode
import com.fagghino.tvvault.data.local.entity.Season
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
    val allEpisodes by viewModel.observeAllEpisodes(mediaId).collectAsState(initial = emptyList())

    var showPreviousEpisodesDialog by remember { mutableStateOf<Episode?>(null) }
    var showPreviousSeasonsDialog by remember { mutableStateOf<Season?>(null) }

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

                    var menuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Sposta in 'In Corso'") },
                                onClick = {
                                    viewModel.updateStatus(mediaId, "watching")
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Sposta in 'Da Vedere'") },
                                onClick = {
                                    viewModel.updateStatus(mediaId, "watchlist")
                                    menuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Sposta in 'Abbandonato'") },
                                onClick = {
                                    viewModel.updateStatus(mediaId, "dropped")
                                    menuExpanded = false
                                }
                            )
                            Divider()
                            DropdownMenuItem(
                                text = { Text("Rimuovi dalla libreria", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    viewModel.removeMediaItem(mediaId)
                                    menuExpanded = false
                                    onBackClick()
                                }
                            )
                        }
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
                                            if (!allWatched) {
                                                // Marking season as watched, check previous seasons
                                                val sortedSeasons = seasons.sortedBy { it.seasonNumber }
                                                val clickedSeasonIndex = sortedSeasons.indexOfFirst { it.seasonNumber == season.seasonNumber }
                                                val previousUnwatchedSeasons = if (clickedSeasonIndex > 0) {
                                                    sortedSeasons.subList(0, clickedSeasonIndex).filter { prevSeason ->
                                                        val prevSeasonEpIds = allEpisodes.filter { it.seasonNumber == prevSeason.seasonNumber }.map { it.localId }
                                                        prevSeasonEpIds.isNotEmpty() && !prevSeasonEpIds.all { it in watchedEpIds }
                                                    }
                                                } else emptyList()

                                                if (previousUnwatchedSeasons.isNotEmpty()) {
                                                    showPreviousSeasonsDialog = season
                                                } else {
                                                    viewModel.setSeasonWatched(mediaId, season.seasonNumber, true)
                                                }
                                            } else {
                                                // Unmarking season
                                                viewModel.setSeasonWatched(mediaId, season.seasonNumber, false)
                                            }
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
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    episodesList.forEach { episode ->
                                        val isEpWatched = episode.localId in watchedEpIds
                                        
                                        val dismissState = rememberSwipeToDismissBoxState(
                                            confirmValueChange = { value ->
                                                if (value == SwipeToDismissBoxValue.StartToEnd) {
                                                    // Swipe right -> mark watched
                                                    if (!isEpWatched) {
                                                        val sortedEps = allEpisodes.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))
                                                        val clickedIndex = sortedEps.indexOfFirst { it.localId == episode.localId }
                                                        val previousUnwatched = if (clickedIndex > 0) {
                                                            sortedEps.subList(0, clickedIndex).filter { it.localId !in watchedEpIds }
                                                        } else emptyList()

                                                        if (previousUnwatched.isNotEmpty()) {
                                                            showPreviousEpisodesDialog = episode
                                                        } else {
                                                            viewModel.setEpisodeWatched(episode.localId, true, mediaId)
                                                        }
                                                    }
                                                    false
                                                } else if (value == SwipeToDismissBoxValue.EndToStart) {
                                                    // Swipe left -> mark unwatched
                                                    if (isEpWatched) {
                                                        viewModel.setEpisodeWatched(episode.localId, false, mediaId)
                                                    }
                                                    false
                                                } else {
                                                    false
                                                }
                                            }
                                        )

                                        SwipeToDismissBox(
                                            state = dismissState,
                                            backgroundContent = {
                                                val color = when (dismissState.dismissDirection) {
                                                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                                                    else -> Color.Transparent
                                                }
                                                val align = when (dismissState.dismissDirection) {
                                                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                                                    SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                                                    else -> Alignment.Center
                                                }
                                                val labelText = when (dismissState.dismissDirection) {
                                                    SwipeToDismissBoxValue.StartToEnd -> "Visto"
                                                    SwipeToDismissBoxValue.EndToStart -> "Non Visto"
                                                    else -> ""
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(color)
                                                        .padding(horizontal = 16.dp),
                                                    contentAlignment = align
                                                ) {
                                                    Text(
                                                        text = labelText,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            },
                                            content = {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(MaterialTheme.colorScheme.surface)
                                                        .clickable {
                                                            if (!isEpWatched) {
                                                                val sortedEps = allEpisodes.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))
                                                                val clickedIndex = sortedEps.indexOfFirst { it.localId == episode.localId }
                                                                val previousUnwatched = if (clickedIndex > 0) {
                                                                    sortedEps.subList(0, clickedIndex).filter { it.localId !in watchedEpIds }
                                                                } else emptyList()

                                                                if (previousUnwatched.isNotEmpty()) {
                                                                    showPreviousEpisodesDialog = episode
                                                                } else {
                                                                    viewModel.setEpisodeWatched(episode.localId, true, mediaId)
                                                                }
                                                            } else {
                                                                viewModel.setEpisodeWatched(episode.localId, false, mediaId)
                                                            }
                                                        }
                                                        .padding(horizontal = 16.dp, vertical = 8.dp),
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
                                                            if (checked) {
                                                                val sortedEps = allEpisodes.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))
                                                                val clickedIndex = sortedEps.indexOfFirst { it.localId == episode.localId }
                                                                val previousUnwatched = if (clickedIndex > 0) {
                                                                    sortedEps.subList(0, clickedIndex).filter { it.localId !in watchedEpIds }
                                                                } else emptyList()

                                                                if (previousUnwatched.isNotEmpty()) {
                                                                    showPreviousEpisodesDialog = episode
                                                                } else {
                                                                    viewModel.setEpisodeWatched(episode.localId, true, mediaId)
                                                                }
                                                            } else {
                                                                viewModel.setEpisodeWatched(episode.localId, false, mediaId)
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Confirmation Dialogs
            if (showPreviousEpisodesDialog != null) {
                val ep = showPreviousEpisodesDialog!!
                val sortedEps = allEpisodes.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))
                val clickedIndex = sortedEps.indexOfFirst { it.localId == ep.localId }
                val watchedEpIds = watchedEpisodes.map { it.episodeLocalId }.toSet()
                val previousUnwatched = if (clickedIndex > 0) {
                    sortedEps.subList(0, clickedIndex).filter { it.localId !in watchedEpIds }
                } else emptyList()

                AlertDialog(
                    onDismissRequest = { showPreviousEpisodesDialog = null },
                    title = { Text("Episodi precedenti non visti") },
                    text = { Text("Vuoi segnare come visti anche i precedenti ${previousUnwatched.size} episodi di questa serie?") },
                    confirmButton = {
                        Button(
                            onClick = {
                                val ids = previousUnwatched.map { it.localId } + ep.localId
                                viewModel.setEpisodesWatched(ids, true, mediaId)
                                showPreviousEpisodesDialog = null
                            }
                        ) {
                            Text("Sì, tutti")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                viewModel.setEpisodeWatched(ep.localId, true, mediaId)
                                showPreviousEpisodesDialog = null
                            }
                        ) {
                            Text("Solo questo")
                        }
                    }
                )
            }

            if (showPreviousSeasonsDialog != null) {
                val s = showPreviousSeasonsDialog!!
                val watchedEpIds = watchedEpisodes.map { it.episodeLocalId }.toSet()
                val unwatchedEps = allEpisodes
                    .filter { it.seasonNumber <= s.seasonNumber && it.localId !in watchedEpIds }
                    .map { it.localId }

                AlertDialog(
                    onDismissRequest = { showPreviousSeasonsDialog = null },
                    title = { Text("Stagioni precedenti non completate") },
                    text = { Text("Vuoi segnare come visti tutti gli episodi delle stagioni precedenti?") },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.setEpisodesWatched(unwatchedEps, true, mediaId)
                                showPreviousSeasonsDialog = null
                            }
                        ) {
                            Text("Sì, segna tutti")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                viewModel.setSeasonWatched(mediaId, s.seasonNumber, true)
                                showPreviousSeasonsDialog = null
                            }
                        ) {
                            Text("Solo questa stagione")
                        }
                    }
                )
            }
            }
        }
    }
}
