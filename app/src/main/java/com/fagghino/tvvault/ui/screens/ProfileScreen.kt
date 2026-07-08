package com.fagghino.tvvault.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import com.fagghino.tvvault.ui.viewmodel.MediaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: MediaViewModel,
    onSettingsClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    val tvStats by viewModel.tvShowStats.collectAsState()
    val movStats by viewModel.movieStats.collectAsState()
    val profileImageUri by viewModel.profileImageUri.collectAsState()
    val profileBannerUri by viewModel.profileBannerUri.collectAsState()

    var showAvatarPickerDialog by remember { mutableStateOf(false) }
    var showBannerPickerDialog by remember { mutableStateOf(false) }

    val avatarGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.saveProfileImageUri(it.toString()) } }

    val avatarCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, stream)
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "tvvault_avatar_${System.currentTimeMillis()}.jpg")
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            }
            val imgUri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            imgUri?.let { u ->
                context.contentResolver.openOutputStream(u)?.use { out -> out.write(stream.toByteArray()) }
                viewModel.saveProfileImageUri(u.toString())
            }
        }
    }

    val bannerGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { viewModel.saveProfileBannerUri(it.toString()) } }



    if (showAvatarPickerDialog) {
        AlertDialog(
            onDismissRequest = { showAvatarPickerDialog = false },
            title = { Text("Cambia foto profilo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        showAvatarPickerDialog = false
                        avatarCameraLauncher.launch(null)
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Scatta una foto")
                    }
                    TextButton(onClick = {
                        showAvatarPickerDialog = false
                        avatarGalleryLauncher.launch("image/*")
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Scegli dalla galleria")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAvatarPickerDialog = false }) { Text("Annulla") }
            }
        )
    }

    if (showBannerPickerDialog) {
        AlertDialog(
            onDismissRequest = { showBannerPickerDialog = false },
            title = { Text("Cambia banner profilo") },
            text = {
                TextButton(onClick = {
                    showBannerPickerDialog = false
                    bannerGalleryLauncher.launch("image/*")
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Scegli dalla galleria")
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showBannerPickerDialog = false }) { Text("Annulla") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Banner + Avatar
        Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clickable { showBannerPickerDialog = true }
            ) {
                if (profileBannerUri.isNotBlank()) {
                    AsyncImage(
                        model = Uri.parse(profileBannerUri),
                        contentDescription = "Banner profilo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.horizontalGradient(listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                            ))
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Aggiungi banner",
                            tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(32.dp))
                    }
                }
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopEnd
                ) {
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier
                            .padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Impostazioni",
                            tint = Color.White
                        )
                    }
                }
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Surface(
                        modifier = Modifier.padding(8.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.4f)
                    ) {
                        Text("Modifica banner", fontSize = 11.sp, color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp)
                    .size(80.dp)
                    .clip(CircleShape)
                    .border(3.dp, MaterialTheme.colorScheme.background, CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { showAvatarPickerDialog = true },
                contentAlignment = Alignment.Center
            ) {
                if (profileImageUri.isNotBlank()) {
                    AsyncImage(
                        model = Uri.parse(profileImageUri),
                        contentDescription = "Foto profilo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text("F", fontSize = 28.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }

        // Username + tagline
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text("Fagghino", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground)
            Text(stringResource(R.string.tagline), fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary)
        }

        // Stats
        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.stats_title), fontSize = 16.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground)

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Default.Star, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Serie TV", fontWeight = FontWeight.Bold, fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text("${tvStats.first} episodi visti", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Tempo totale", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(tvStats.second, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Film", fontWeight = FontWeight.Bold, fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text("${movStats.first} film visti", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Tempo totale", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(movStats.second, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }


        Spacer(modifier = Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileOptionItem(
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
