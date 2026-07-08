package com.fagghino.tvvault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fagghino.tvvault.data.local.entity.MediaItem
import com.fagghino.tvvault.ui.viewmodel.MediaViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class UpcomingEpisodeItem(
    val show: MediaItem,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val episodeName: String,
    val airDate: LocalDate,
    val posterPath: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpcomingScreen(
    viewModel: MediaViewModel,
    onShowClick: (String) -> Unit
) {
    val shows by viewModel.shows.collectAsState()
    val upcomingItems by viewModel.upcomingEpisodes.collectAsState()
    val isLoading by viewModel.isLoadingUpcoming.collectAsState()

    LaunchedEffect(shows) {
        viewModel.loadUpcomingEpisodes()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Column {
                Text(
                    "Prossime Uscite",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Episodi in arrivo per le serie che segui",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("Caricamento uscite...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                }
            }
        } else if (upcomingItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("📅", fontSize = 48.sp)
                    Text(
                        "Nessuna uscita imminente trovata",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        "Aggiungi serie TV alla tua libreria per vedere le prossime uscite",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else {
            // Group by date
            val today = LocalDate.now()
            val grouped = upcomingItems.groupBy { it.airDate }

            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                grouped.toSortedMap().forEach { (date, episodes) ->
                    item {
                        // Date header
                        val dayLabel = when (date) {
                            today -> "Oggi"
                            today.plusDays(1) -> "Domani"
                            else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ITALIAN).replaceFirstChar { it.uppercase() } +
                                    " ${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL, Locale.ITALIAN)}"
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            if (date == today) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                        )
                                )
                                Text(
                                    text = dayLabel,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (date == today) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${episodes.size} ep.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    items(episodes) { item ->
                        UpcomingEpisodeCard(
                            item = item,
                            onClick = { onShowClick(item.show.localId.toString()) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UpcomingEpisodeCard(
    item: UpcomingEpisodeItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Poster
            Box(
                modifier = Modifier
                    .size(width = 60.dp, height = 88.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                if (!item.posterPath.isNullOrBlank()) {
                    AsyncImage(
                        model = "https://image.tmdb.org/t/p/w185${item.posterPath}",
                        contentDescription = item.show.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        item.show.title.take(2).uppercase(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Info
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = item.show.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Stagione ${item.seasonNumber} · Episodio ${item.episodeNumber}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                if (item.episodeName.isNotBlank() && item.episodeName != "Episodio ${item.episodeNumber}") {
                    Text(
                        text = item.episodeName,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Air date badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Text(
                    text = "${item.airDate.dayOfMonth}/${item.airDate.monthValue}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
