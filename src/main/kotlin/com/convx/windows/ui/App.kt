package com.convx.windows.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.convx.windows.glass.GlassEffectConfig
import com.convx.windows.glass.LayerBackdrop
import com.convx.windows.glass.LocalAppBackdrop
import com.convx.windows.glass.LocalGlassEffectConfig
import com.convx.windows.glass.layerBackdrop
import com.convx.windows.glass.liquidGlass
import com.convx.windows.glass.rememberLayerBackdrop

/**
 * Shell for the Windows port: the scrollable library is the backdrop, and the
 * floating nav bar, the circular buttons and the mini player are glass surfaces
 * sampling it -- the same composition the Android app uses.
 */
@Composable
fun ConvxApp() {
    val backdrop = rememberLayerBackdrop()
    val config = remember { GlassEffectConfig() }

    MaterialTheme(colorScheme = darkColorScheme()) {
        CompositionLocalProvider(
            LocalAppBackdrop provides backdrop,
            LocalGlassEffectConfig provides config,
        ) {
            Box(Modifier.fillMaxSize().background(Color(0xFF07070A))) {
                Library(backdrop)

                GlassButton(Modifier.align(Alignment.TopStart).padding(24.dp), "‹")
                GlassButton(Modifier.align(Alignment.TopEnd).padding(24.dp), "⇪")

                Column(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    MiniPlayer()
                    NavBar()
                }
            }
        }
    }
}

@Composable
private fun Library(backdrop: LayerBackdrop) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF3B1E5A), Color(0xFF10213F), Color(0xFF0B0B10)),
                    start = Offset.Zero,
                    end = Offset(1400f, 1000f),
                ),
            )
            .layerBackdrop(backdrop),
    ) {
        Column(Modifier.fillMaxSize().padding(40.dp)) {
            Text("Listen Now", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            LazyRow(
                Modifier.padding(top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(List(6) { it }) { i -> Artwork(i, 190.dp) }
            }
            Text(
                "Your library",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 36.dp, bottom = 16.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(List(12) { it + 3 }) { i -> Artwork(i, 150.dp) }
            }
        }
    }
}

@Composable
private fun Artwork(index: Int, size: Dp) {
    val palette = listOf(
        Color(0xFFE05B7A) to Color(0xFF7B2BE0),
        Color(0xFF41C3F0) to Color(0xFF1B4BE0),
        Color(0xFFF5B944) to Color(0xFFE0542B),
        Color(0xFF63E08A) to Color(0xFF128A7B),
        Color(0xFFB98BFF) to Color(0xFF3B2BE0),
        Color(0xFFFF8FB1) to Color(0xFF8A1246),
    )
    val (a, b) = palette[index % palette.size]
    Box(
        Modifier.size(size)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(a, b))),
    )
}

@Composable
private fun GlassButton(modifier: Modifier, label: String) {
    Box(
        modifier
            .size(46.dp)
            .liquidGlass(shape = CircleShape, blurRadiusDp = 2f, backdropScale = 1f),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 20.sp)
    }
}

@Composable
private fun MiniPlayer() {
    Row(
        Modifier
            .fillMaxWidth(0.52f)
            .height(72.dp)
            .liquidGlass(shape = RoundedCornerShape(26.dp), blurRadiusDp = 3f)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Artwork(2, 48.dp)
        Column(Modifier.weight(1f)) {
            Text("Weightless", color = Color.White, fontWeight = FontWeight.SemiBold)
            Text("Marconi Union", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        }
        Text("⏮", color = Color.White, fontSize = 18.sp)
        Text("⏸", color = Color.White, fontSize = 22.sp)
        Text("⏭", color = Color.White, fontSize = 18.sp)
    }
}

@Composable
private fun NavBar() {
    var selected by remember { mutableStateOf(0) }
    val tabs = listOf("Home", "Explore", "Library", "Search")
    Row(
        Modifier
            .height(62.dp)
            .liquidGlass(
                shape = RoundedCornerShape(31.dp),
                blurRadiusDp = 2f,
                highlightAlpha = 0.35f,
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tabs.forEachIndexed { index, tab ->
            val isSelected = index == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (isSelected) Color.White.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable { selected = index }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(
                    tab,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.65f),
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}
