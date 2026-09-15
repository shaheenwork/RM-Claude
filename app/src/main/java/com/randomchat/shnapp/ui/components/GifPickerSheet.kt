package com.randomchat.shnapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.randomchat.shnapp.integrations.Gif
import com.randomchat.shnapp.integrations.KlipyApi
import com.randomchat.shnapp.theme.AccentCyan
import com.randomchat.shnapp.theme.CardSurface
import com.randomchat.shnapp.theme.ElevatedCard
import com.randomchat.shnapp.theme.SubtleBorder
import com.randomchat.shnapp.theme.TextMuted
import com.randomchat.shnapp.theme.TextPrimary
import com.randomchat.shnapp.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * Bottom-sheet GIF picker powered by Tenor. Premium-only — call sites must gate.
 *
 * - Empty query → trending
 * - Debounced search (300ms)
 * - 3-column grid, GIF previews via Coil with bundled GifDecoder factory
 * - Tap GIF → onPick + auto-dismiss
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GifPickerSheet(
    onPick: (Gif) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Local ImageLoader with GIF decoders — animated previews + sent GIFs
    val gifLoader = remember {
        ImageLoader.Builder(context)
            .components {
                add(
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P)
                        ImageDecoderDecoder.Factory()
                    else
                        GifDecoder.Factory()
                )
            }
            .crossfade(120)
            .build()
    }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Gif>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // Initial load + search debounce
    LaunchedEffect(query) {
        if (query.isBlank()) {
            isLoading = true
            results = KlipyApi.trending()
            isLoading = false
        } else {
            delay(300)  // debounce
            isLoading = true
            results = KlipyApi.search(query)
            isLoading = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = ElevatedCard,
        tonalElevation   = 0.dp,
        dragHandle       = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 4.dp)
                    .size(width = 32.dp, height = 3.dp)
                    .background(SubtleBorder, CircleShape)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .heightIn(min = 360.dp, max = 520.dp)
        ) {
            // ── Search bar ────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(CardSurface)
                    .border(1.dp, SubtleBorder, RoundedCornerShape(22.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Search, null, tint = TextMuted, modifier = Modifier.size(18.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text("Search GIFs…", color = TextMuted, fontSize = 14.sp)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it.take(60) },
                        textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(AccentCyan),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, null, tint = TextMuted, modifier = Modifier.size(14.dp))
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Category chip row ─────────────────────────────────────────
            if (query.isEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Trending", "Reactions", "Hello", "LOL", "Love").forEach { tag ->
                        CategoryChip(
                            label = tag,
                            selected = (tag == "Trending"),
                            onClick = { if (tag != "Trending") query = tag.lowercase() }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            // ── Grid ──────────────────────────────────────────────────────
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(min = 280.dp)
            ) {
                when {
                    isLoading && results.isEmpty() -> {
                        Box(Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = AccentCyan, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                        }
                    }
                    results.isEmpty() -> {
                        Box(Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
                            Text(
                                if (query.isBlank()) "Couldn't load GIFs"
                                else "No results for \"$query\"",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(results, key = { it.id }) { gif ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(CardSurface)
                                        .clickable {
                                            onPick(gif)
                                            onDismiss()
                                        }
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(gif.previewUrl)
                                            .crossfade(true)
                                            .build(),
                                        imageLoader = gifLoader,
                                        contentDescription = gif.title.ifBlank { "GIF" },
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Text(
                "Powered by Klipy",
                color = TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (selected) AccentCyan.copy(alpha = 0.14f) else CardSurface)
            .border(
                1.dp,
                if (selected) AccentCyan.copy(alpha = 0.35f) else SubtleBorder,
                RoundedCornerShape(99.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            color = if (selected) AccentCyan else TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
