package com.stash.preview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.stash.core.ui.visual.*
import kotlin.math.max
import kotlin.math.roundToInt

/** Included only by the Desktop development lab and isolated Android preview application. */
object VisualFixture {
    val filters = listOf("Todas", "Músicas", "Podcasts", "Audiolivros")
    val mixes = listOf(
        VisualCard("mix1", "Mix Diário 1", "Músicas que você gosta", "mix1"),
        VisualCard("mix2", "Mix Diário 2", "Descobertas para você", "mix2"),
        VisualCard("radar", "Radar de Lançamentos", "Novidades da semana", "radar"))
    val releases = listOf(
        VisualCard("eternal", "eternal return", "Mili", "release1"),
        VisualCard("cutouts", "Cutouts", "The Smile", "release2"),
        VisualCard("romance", "Romance", "Fontaines D.C.", "release3"),
        VisualCard("hit", "HIT", "Novidades", "release2"))
    val genres = listOf("Indie", "Rock", "Eletrônica", "Hip Hop", "Pop", "Jazz", "Música Brasileira", "Clássica", "Lo-fi")
        .mapIndexed { i, title -> VisualCard("genre$i", title, artwork = "genre$i") }
    val categories = listOf("Podcasts", "Audiolivros", "Playlists", "Artistas", "Álbuns", "Músicas", "Shows", "Para relaxar", "Para focar", "Para treinar", "Para dirigir", "Festas")
        .mapIndexed { i, title -> VisualCard("category$i", title, artwork = "category$i") }
    val podcasts = listOf("Naruhodo", "Xadrez Verbal", "Braincast", "Melhores")
        .mapIndexed { i, title -> VisualCard("podcast$i", title, artwork = "podcast${i.coerceAtMost(2)}") }
    val charts = listOf(VisualCard("chart0", "Top Brasil", "Mais ouvidas no Brasil", "chart0"),
        VisualCard("chart1", "Top Global", "Mais ouvidas no mundo", "chart1"),
        VisualCard("chart2", "Viral 50", "As que estão em alta agora", "chart2"))
    val library = listOf(
        VisualCard("liked", "Músicas Curtidas", "Playlist · 1.024 músicas", liked = true),
        VisualCard("rainbows", "In Rainbows", "Álbum · Radiohead", "library1"),
        VisualCard("blonde", "blonde", "Álbum · Frank Ocean", "library2"),
        VisualCard("twin", "Twin Peaks", "Playlist · NØNSTOP", "library3"),
        VisualCard("japanese", "Japonês", "Playlist · 342 músicas", "library4"),
        VisualCard("instrumental", "Instrumental", "Playlist · 278 músicas", "library5"),
        VisualCard("deftones", "Deftones", "Artista", "library6", round = true),
        VisualCard("podcasts", "Podcasts", "8 episódios não ouvidos", "category0"))
    val player = VisualPlayer(VisualCard("nights", "Nights", "Frank Ocean", "mini"), true, .47f)

    fun screen(page: VisualPage): VisualScreenState = when (page) {
        VisualPage.Home -> VisualScreenState(page, filters, "Todas",
            VisualHero(releases.first().copy(artwork = "hero"), description = "Um mergulho em camadas orquestrais e eletrônicas."),
            listOf(VisualSection("made", "Feito para você", mixes),
                VisualSection("recent", "Tocados recentemente", listOf(library[0], library[1], releases[0], VisualCard("wall", "The Wall", artwork = "wall")), compact = true)))
        VisualPage.Explore -> VisualScreenState(page, listOf("Tudo", "Novidades", "Charts", "Moods", "Gêneros"), "Tudo",
            sections = listOf(VisualSection("releases", "Lançamentos para você", releases, plain = true), VisualSection("podcasts", "Podcasts em destaque", podcasts, plain = true)), categories = genres)
        VisualPage.Search -> VisualScreenState(page, sections = listOf(VisualSection("charts", "Charts", charts)), categories = categories)
        VisualPage.Library -> VisualScreenState(page, listOf("Playlists", "Artistas", "Álbuns", "Podcasts", "Baixados"), "Playlists", rows = library)
        VisualPage.Radio -> VisualScreenState(page, sections = listOf(VisualSection("stations", "Suas estações", mixes)))
    }
}

private data class ArtRegion(val file: String, val x: Int, val y: Int, val width: Int, val height: Int)
private fun region(key: String): ArtRegion? = when {
    key == "hero" -> ArtRegion("reference_home", 17, 118, 272, 114)
    key == "mix1" -> ArtRegion("reference_home", 18, 399, 85, 66)
    key == "mix2" -> ArtRegion("reference_home", 115, 399, 84, 66)
    key == "radar" -> ArtRegion("reference_home", 212, 399, 83, 66)
    key == "wall" -> ArtRegion("reference_home", 240, 555, 60, 60)
    key == "mini" -> ArtRegion("reference_home", 18, 624, 28, 28)
    key.startsWith("release") -> ArtRegion("reference_explore", listOf(20, 107, 194)[key.last().digitToInt() - 1], 170, 78, 78)
    key.startsWith("genre") -> key.removePrefix("genre").toInt().let { i -> ArtRegion("reference_explore", 21 + (i % 3) * 96, 326 + (i / 3) * 58, 84, 29) }
    key.startsWith("podcast") -> ArtRegion("reference_explore", 20 + key.last().digitToInt() * 96, 538, 84, 58)
    key.startsWith("category") -> key.removePrefix("category").toInt().let { i -> ArtRegion("reference_search", 16 + (i % 3) * 94, 196 + (i / 3) * 66, 81, 29) }
    key.startsWith("chart") -> ArtRegion("reference_search", 17 + key.last().digitToInt() * 94, 502, 81, 64)
    key.startsWith("library") -> key.last().digitToInt().let { i -> ArtRegion("reference_library", 20, 246 + (i - 1) * 60, 48, 48) }
    else -> null
}

/** Draws only artwork regions. Text, controls and containers are always native Compose. */
@Composable fun ReferenceArtwork(key: String?, modifier: Modifier, load: (String) -> ImageBitmap) {
    val bounds = key?.let(::region)
    if (bounds == null) { VisualCover(null, modifier); return }
    val bitmap = remember(bounds.file) { load(bounds.file) }
    Canvas(modifier.clipToBounds()) {
        if (key == "hero") {
            // The screenshot contains no clean full-resolution hero asset. Reconstruct only
            // the photographic regions; never reuse its title, buttons or overflow control.
            drawImage(bitmap, IntOffset(17, 118), IntSize(244, 105), IntOffset.Zero,
                IntSize(size.width.roundToInt(), size.height.roundToInt()))
            val left = size.width * .57f
            val top = size.height * .24f
            drawIntoCanvas { canvas ->
                canvas.saveLayer(Rect(left, top, size.width, size.height), Paint())
                drawImage(bitmap, IntOffset(192, 174), IntSize(95, 178),
                    IntOffset(left.roundToInt(), top.roundToInt()),
                    IntSize((size.width - left).roundToInt(), (size.height - top).roundToInt()))
                drawRect(Brush.horizontalGradient(0f to Color.Transparent, .35f to Color.White, 1f to Color.White,
                    startX = left, endX = size.width), blendMode = BlendMode.DstIn)
                drawRect(Brush.verticalGradient(0f to Color.Transparent, .2f to Color.White, 1f to Color.White,
                    startY = top, endY = size.height), blendMode = BlendMode.DstIn)
                canvas.restore()
            }
            return@Canvas
        }
        val scale = max(size.width / bounds.width, size.height / bounds.height)
        val destination = IntSize((bounds.width * scale).roundToInt(), (bounds.height * scale).roundToInt())
        clipRect { drawImage(bitmap, IntOffset(bounds.x, bounds.y), IntSize(bounds.width, bounds.height),
            IntOffset(((size.width - destination.width) / 2).roundToInt(), ((size.height - destination.height) / 2).roundToInt()), destination) }
    }
}

@Composable fun VisualPreview(initialPage: VisualPage = VisualPage.Home,
    initialStatus: VisualStatus = VisualStatus.Content, dark: Boolean = true, amoled: Boolean = false, artwork: VisualArtwork) {
    var page by rememberSaveable { mutableStateOf(initialPage) }
    var filter by rememberSaveable(page) { mutableStateOf(VisualFixture.screen(page).selectedFilter) }
    var query by rememberSaveable { mutableStateOf("") }
    var playing by rememberSaveable { mutableStateOf(true) }
    var liked by rememberSaveable { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf(initialStatus) }
    val source = VisualFixture.screen(page)
    val state = source.copy(selectedFilter = filter, query = query, status = status,
        rows = source.rows.filter { it.title.contains(query, true) },
        message = if (status == VisualStatus.Error) "Não foi possível carregar. Tente novamente." else "Sua biblioteca ainda está vazia.")
    val actions = VisualActions(onPage = { page = it; query = "" }, onFilter = { filter = it },
        onCard = { dialog = "Prévia de ${it}" }, onMore = { dialog = "Opções de ${it}" }, onSection = { dialog = "Seção ${it}" },
        onQuery = { query = it }, onSearch = { if (page != VisualPage.Search) page = VisualPage.Search else dialog = "Busca de demonstração: $query" },
        onSettings = { dialog = "Configurações" }, onCast = { dialog = "Transmitir" }, onNotifications = { dialog = "Notificações" },
        onCreate = { dialog = "Nova playlist" }, onSort = { dialog = "Ordenar biblioteca" },
        onToggle = { playing = !playing }, onLike = { liked = !liked }, onPlayer = { dialog = "Player em tela cheia preservado" },
        onRetry = { status = VisualStatus.Content }, onEmpty = { page = VisualPage.Search; status = VisualStatus.Content })
    VisualTheme(dark, amoled) {
        VisualBackground(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) { key(page) { VisualScreen(state, actions, artwork = artwork) } }
                VisualBottomBar(page, VisualFixture.player.copy(playing = playing, card = VisualFixture.player.card.copy(liked = liked)), actions, artwork = artwork)
            }
        }
        dialog?.let { text -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text(text) },
            text = { Text("Prévia visual · os dados do aplicativo não são alterados.") },
            confirmButton = { TextButton({ dialog = null }) { Text("Fechar") } }) }
    }
}
