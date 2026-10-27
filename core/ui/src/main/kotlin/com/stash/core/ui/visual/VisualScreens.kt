package com.stash.core.ui.visual

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.stash.core.ui.theme.Inter

typealias VisualArtwork = @Composable (String?, Modifier) -> Unit

/** Shared Android/Desktop presentation. No services, platform windows or fixture data. */
@Composable fun VisualScreen(
    state: VisualScreenState, actions: VisualActions,
    modifier: Modifier = Modifier,
    artwork: VisualArtwork = { url, size -> VisualCover(url, size) },
    extraContent: LazyListScope.() -> Unit = {},
) {
    val heading = when (state.page) {
        VisualPage.Home -> ""; VisualPage.Explore -> "Explorar"; VisualPage.Search -> "Buscar"
        VisualPage.Library -> "Sua Biblioteca"; VisualPage.Radio -> "Rádio"
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item("header") { VisualHeader(actions) }
        if (heading.isNotEmpty()) item("heading") {
            Text(heading, Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp),
                style = VisualDesign.Heading.copy(fontSize = 20.sp))
        }
        if (state.filters.isNotEmpty()) item("filters") {
            VisualFilters(state.filters, state.selectedFilter, actions.onFilter)
            Spacer(Modifier.height(when (state.page) { VisualPage.Home -> 18.dp; VisualPage.Library -> 8.dp; else -> 16.dp }))
        }
        if (state.page == VisualPage.Search) item("query") {
            VisualSearchField(state.query, actions.onQuery, actions.onSearch, Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(22.dp))
        }
        if (state.page == VisualPage.Library) item("library-tools") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                VisualIcon(Icons.Outlined.SwapVert, "Ordenar biblioteca", actions.onSort)
                Text("Recentes", style = VisualDesign.Caption, color = VisualDesign.Muted)
                Spacer(Modifier.weight(1f))
                VisualIcon(Icons.Outlined.Search, "Buscar na biblioteca", { actions.onSearch() })
                VisualIcon(Icons.Outlined.Add, "Nova playlist", actions.onCreate)
                actions.onImport?.let { VisualIcon(Icons.Outlined.FileDownload, "Adicionar áudio", it) }
            }
            if (state.showSearch || state.query.isNotEmpty()) VisualSearchField(state.query, actions.onQuery, actions.onSearch,
                Modifier.padding(horizontal = 20.dp))
        }
        when (state.status) {
            VisualStatus.Loading -> item("loading") { VisualFeedback("Carregando…", loading = true) }
            VisualStatus.Error -> item("error") { VisualFeedback(state.message.ifBlank { "Não foi possível carregar." }, "Tentar novamente", actions.onRetry) }
            VisualStatus.Empty -> item("empty") { VisualFeedback(state.message.ifBlank { "Sua biblioteca ainda está vazia." }, state.emptyAction, actions.onEmpty) }
            VisualStatus.Content -> {
                state.hero?.let { hero -> item("hero") {
                    VisualHeroCard(hero, { actions.onCard(hero.card.id) }, { actions.onMore(hero.card.id) }, artwork)
                    Spacer(Modifier.height(20.dp))
                } }
                val openingSections = if (state.page == VisualPage.Explore) state.sections.take(1) else emptyList()
                openingSections.forEach { section -> item("section:${section.id}") {
                    VisualSectionTitle(section.title, { actions.onSection(section.id) })
                    VisualRail(section.cards, section.compact, section.plain, actions.onCard, artwork)
                    Spacer(Modifier.height(22.dp))
                } }
                if (state.categories.isNotEmpty()) item("categories") {
                    VisualSectionTitle(if (state.page == VisualPage.Search) "Explorar tudo" else "Navegar por gênero")
                    VisualCategoryGrid(state.categories, if (state.page == VisualPage.Search) 1.48f else 1.75f, actions.onCard, artwork)
                    Spacer(Modifier.height(if (state.page == VisualPage.Search) 32.dp else 22.dp))
                }
                state.sections.drop(openingSections.size).forEach { section -> item("section:${section.id}") {
                    VisualSectionTitle(section.title, { actions.onSection(section.id) })
                    VisualRail(section.cards, section.compact, section.plain, actions.onCard, artwork)
                    Spacer(Modifier.height(22.dp))
                } }
                items(state.rows, key = { it.id }) { card -> VisualLibraryRow(card,
                    { actions.onCard(card.id) }, { actions.onMore(card.id) }, artwork) }
            }
        }
        extraContent()
    }
}

@Composable fun VisualHeader(actions: VisualActions, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(62.dp).padding(start = 20.dp, end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("NØNSTOP", style = TextStyle(fontFamily = Inter, fontSize = 21.sp, letterSpacing = 3.2.sp,
            fontWeight = FontWeight.Normal), color = VisualDesign.Ink, modifier = Modifier.weight(1f))
        actions.onCast?.let { VisualHeaderButton(VisualHeaderSymbol.Cast, "Transmitir", it) }
        actions.onNotifications?.let { VisualHeaderButton(VisualHeaderSymbol.Bell, "Notificações", it) }
        VisualHeaderButton(VisualHeaderSymbol.Settings, "Configurações", actions.onSettings)
    }
}

@Composable fun VisualFilters(filters: List<String>, selected: String, onSelect: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val unit = (maxWidth - 61.dp) / 4.32f
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        items(filters.size) { index ->
            val label = filters[index]
            val active = selected == label
            val shape = RoundedCornerShape(12.dp)
            val width = if (filters.size == 4 && maxWidth >= 360.dp)
                Modifier.width(unit * listOf(1f, 1.02f, 1.08f, 1.22f)[index]) else Modifier.widthIn(min = 76.dp)
            Box(Modifier.heightIn(min = 34.dp).then(width).clip(shape)
                .background(Brush.verticalGradient(if (!LocalVisualDark.current) listOf(VisualDesign.Surface, VisualDesign.Surface)
                    else if (active) listOf(Color(0xFF68617F), Color(0xFF48435D)) else listOf(Color(0xFF2E2D3F), Color(0xFF252432))))
                .border(if (active) 1.dp else .65.dp,
                    if (!LocalVisualDark.current) VisualDesign.Border else if (active) Color(0xFF89809F) else Color(0xFF3B394D), shape)
                .selectable(active, role = Role.Tab) { onSelect(label) }
                .padding(horizontal = 14.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
                Text(label, style = VisualDesign.Caption.copy(fontWeight = if (active) FontWeight.Medium else FontWeight.Normal),
                    color = if (active) VisualDesign.Ink else VisualDesign.Muted)
            }
        }
    }
    }
}

@Composable private fun VisualHeaderButton(symbol: VisualHeaderSymbol, label: String, onClick: () -> Unit) {
    IconButton(onClick, Modifier.size(40.dp).semantics { contentDescription = label }) {
        VisualHeaderIcon(symbol, Modifier.size(when (symbol) {
            VisualHeaderSymbol.Cast -> 24.dp; VisualHeaderSymbol.Bell -> 23.dp; VisualHeaderSymbol.Settings -> 22.dp
        }))
    }
}

@Composable fun VisualSearchField(query: String, onQuery: (String) -> Unit, onSearch: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(25.dp)
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp).clip(shape)
        .background(Brush.verticalGradient(if (LocalVisualDark.current) listOf(Color(0xFF535266), Color(0xFF353547)) else listOf(VisualDesign.Surface, VisualDesign.Surface)))
        .border(1.dp, VisualDesign.Muted.copy(alpha = .6f), shape).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Search, null, Modifier.size(23.dp), tint = VisualDesign.Muted)
        Spacer(Modifier.width(12.dp))
        BasicTextField(query, onQuery, Modifier.weight(1f).padding(vertical = 12.dp)
            .semantics { contentDescription = "O que você quer ouvir?" },
            textStyle = VisualDesign.Body.copy(color = VisualDesign.Ink), singleLine = true,
            cursorBrush = androidx.compose.ui.graphics.SolidColor(VisualDesign.Accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            decorationBox = { inner -> Box { if (query.isEmpty()) Text("O que você quer ouvir?", style = VisualDesign.Caption, color = VisualDesign.Muted); inner() } })
        if (query.isNotEmpty()) VisualIcon(Icons.Outlined.Close, "Limpar busca", { onQuery("") })
        if (query.isNotEmpty()) VisualIcon(Icons.Outlined.ArrowForward, "Buscar", onSearch)
    }
}

@Composable private fun VisualHeroCard(hero: VisualHero, onPlay: () -> Unit, onMore: () -> Unit, artwork: VisualArtwork) {
    val shape = RoundedCornerShape(11.dp)
    Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(304.dp).clip(shape)
        .background(VisualDesign.Surface).border(1.dp, VisualDesign.Border, shape)) {
        artwork(hero.card.artwork, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent,
            .35f to Color(0x18101116), 1f to Color(0xF5101116))))
        Box(Modifier.align(Alignment.TopEnd).padding(5.dp)) { VisualIcon(Icons.Outlined.MoreHoriz, "Opções do lançamento", onMore) }
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
            Text(hero.eyebrow, style = VisualDesign.Caption, color = Color.White)
            Text(hero.card.title, color = Color.White, style = VisualDesign.Heading.copy(fontSize = 27.sp, lineHeight = 32.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(hero.card.subtitle, color = Color.White, style = VisualDesign.Heading.copy(fontSize = 16.sp))
            if (hero.description.isNotBlank()) Text(hero.description, Modifier.widthIn(max = 220.dp).padding(top = 4.dp),
                style = VisualDesign.Body, color = Color(0xFFD4CFDF), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onPlay, shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = VisualDesign.Accent,
                    contentColor = MaterialTheme.colorScheme.onPrimary), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                    modifier = Modifier.height(38.dp)) {
                    Icon(Icons.Filled.PlayArrow, null, Modifier.size(22.dp)); Spacer(Modifier.width(7.dp))
                    Text("Ouvir agora", style = VisualDesign.Caption.copy(fontWeight = FontWeight.SemiBold))
                }
                Spacer(Modifier.width(14.dp))
                Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xB33A394B))
                    .border(1.dp, VisualDesign.Border, CircleShape).clickable(onClick = onMore), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Add, "Adicionar lançamento", Modifier.size(25.dp), tint = Color.White)
                }
            }
        }
    }
}

@Composable private fun VisualSectionTitle(title: String, onMore: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = VisualDesign.Heading, maxLines = 2, overflow = TextOverflow.Ellipsis)
        onMore?.let { Row(Modifier.clickable(onClick = it).padding(start = 8.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Mostrar tudo", style = VisualDesign.Caption.copy(fontSize = 10.sp), color = VisualDesign.Muted)
            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(17.dp), tint = VisualDesign.Muted)
        } }
    }
}

@Composable private fun VisualRail(cards: List<VisualCard>, compact: Boolean, plain: Boolean, onCard: (String) -> Unit, artwork: VisualArtwork) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(cards, key = { it.id }) { card ->
            val width = if (compact) 80.dp else if (plain) 104.dp else 110.dp
            val shape = RoundedCornerShape(8.dp)
            Column(Modifier.width(width).then(if (!compact && !plain) Modifier.height((146 * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp) else Modifier)
                .clip(shape).clickable { onCard(card.id) }
                .then(if (!compact && !plain) Modifier.background(Brush.verticalGradient(if (LocalVisualDark.current) listOf(Color(0xFF252532), Color(0xFF181922)) else listOf(VisualDesign.Surface, VisualDesign.Surface))) else Modifier)
                .then(if (!compact && !plain) Modifier.border(1.dp, VisualDesign.Border, shape) else Modifier)) {
                Box(Modifier.fillMaxWidth().then(if (!compact && !plain) Modifier.height(82.dp) else Modifier.aspectRatio(1f)).clip(shape)) {
                    if (card.liked) LikedArtwork(Modifier.fillMaxSize()) else artwork(card.artwork, Modifier.fillMaxSize())
                }
                if (!compact) Column(Modifier.padding(horizontal = if (plain) 0.dp else 9.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(card.title, style = VisualDesign.Body.copy(fontWeight = FontWeight.SemiBold), maxLines = if (plain) 1 else 2, overflow = TextOverflow.Ellipsis)
                    if (card.subtitle.isNotEmpty()) Text(card.subtitle, style = VisualDesign.Caption.copy(fontSize = 10.sp), color = VisualDesign.Muted, maxLines = if (plain || card.title.length > 15) 1 else 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable private fun VisualCategoryGrid(cards: List<VisualCard>, aspect: Float, onCard: (String) -> Unit, artwork: VisualArtwork) {
    val columns = 3
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cards.chunked(columns).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { card ->
                val shape = RoundedCornerShape(8.dp)
                Box(Modifier.weight(1f).aspectRatio(aspect).clip(shape).background(VisualDesign.Surface)
                    .border(1.dp, Color(0xFF393847), shape).clickable { onCard(card.id) }) {
                    artwork(card.artwork, Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x3A292536), Color(0xB0101119)))))
                    Text(card.title, Modifier.align(Alignment.BottomStart).padding(8.dp), color = Color.White, style = VisualDesign.Caption.copy(fontWeight = FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        } }
    }
}

@Composable private fun VisualLibraryRow(card: VisualCard, onOpen: () -> Unit, onMore: () -> Unit, artwork: VisualArtwork) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 20.dp, end = 10.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        val shape = if (card.round) CircleShape else RoundedCornerShape(6.dp)
        Box(Modifier.size(66.dp).clip(shape)) {
            if (card.liked) LikedArtwork(Modifier.fillMaxSize()) else artwork(card.artwork, Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(card.title, style = VisualDesign.Body.copy(fontWeight = FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(card.subtitle, style = VisualDesign.Caption, color = VisualDesign.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        VisualIcon(Icons.Outlined.MoreHoriz, "Opções de ${card.title}", onMore)
    }
}

@Composable fun VisualBottomBar(page: VisualPage, player: VisualPlayer?, actions: VisualActions,
    modifier: Modifier = Modifier, artwork: VisualArtwork = { url, size -> VisualCover(url, size) }) {
    Column(modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(if (LocalVisualDark.current) Color(0xE91A1A24) else VisualDesign.Surface, VisualDesign.Background)))) {
        player?.let { VisualMiniPlayer(it, actions, artwork) }
        Row(Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 8.dp)) {
            listOf(VisualPage.Home, VisualPage.Explore, VisualPage.Radio, VisualPage.Search, VisualPage.Library).forEachIndexed { index, destination ->
                val selected = page == destination
                Column(Modifier.weight(1f).fillMaxHeight().testTag("visual-tab-${destination.name}").selectable(selected, role = Role.Tab) { actions.onPage(destination) }
                    .padding(top = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    VisualNavigationIcon(destination, selected, Modifier.size(22.dp))
                    Text(listOf("Início", "Explorar", "Rádio", "Buscar", "Biblioteca")[index],
                        style = VisualDesign.Caption.copy(fontSize = 10.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            shadow = if (selected && LocalVisualDark.current) Shadow(Color(0x66BDB0ED), Offset.Zero, 5f) else null),
                        color = if (selected) VisualDesign.Ink else VisualDesign.Muted)
                }
            }
        }
    }
}

@Composable fun VisualMiniPlayer(player: VisualPlayer, actions: VisualActions,
    artwork: VisualArtwork = { url, size -> VisualCover(url, size) }) {
    val shape = RoundedCornerShape(13.dp)
    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(52.dp).clip(shape)
        .background(Brush.horizontalGradient(if (LocalVisualDark.current) listOf(Color(0xFF2C2B37), Color(0xFF20202C)) else listOf(VisualDesign.Surface, VisualDesign.Surface)))
        .border(1.dp, VisualDesign.Border, shape)) {
        Row(Modifier.fillMaxSize().clickable(onClick = actions.onPlayer).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            artwork(player.card.artwork, Modifier.size(37.dp).clip(RoundedCornerShape(5.dp)))
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(player.card.title, style = VisualDesign.Caption.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(player.card.subtitle, style = VisualDesign.Caption, color = VisualDesign.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            VisualIcon(if (player.card.liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, "Curtir música", actions.onLike)
            IconButton(actions.onToggle, Modifier.size(38.dp).background(Color(0xFF67637C), CircleShape)) {
                if (player.buffering) CircularProgressIndicator(Modifier.size(22.dp), color = VisualDesign.Ink, strokeWidth = 2.dp)
                else Icon(if (player.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (player.playing) "Pausar" else "Reproduzir", Modifier.size(23.dp))
            }
        }
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(player.progress.coerceIn(0f, 1f)).height(2.dp).background(VisualDesign.Accent))
    }
}

@Composable fun VisualCover(url: String?, modifier: Modifier = Modifier) {
    Box(modifier.background(Brush.linearGradient(if (LocalVisualDark.current) listOf(Color(0xFF3A354C), Color(0xFF20212D)) else listOf(VisualDesign.Surface, Color(0xFFD6CDDF))))) {
        if (url != null && !url.startsWith("icon:")) AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else {
            val symbol = when (url?.removePrefix("icon:")) {
                "Podcasts" -> Icons.Outlined.Podcasts
                "Audiolivros" -> Icons.Outlined.MenuBook
                "Playlists" -> Icons.Outlined.QueueMusic
                "Artistas" -> Icons.Outlined.Person
                "Álbuns" -> Icons.Outlined.Album
                "Shows" -> Icons.Outlined.Mic
                "Para relaxar" -> Icons.Outlined.Spa
                "Para focar" -> Icons.Outlined.Headphones
                "Para treinar" -> Icons.Outlined.FitnessCenter
                "Para dirigir" -> Icons.Outlined.DirectionsCar
                "Festas" -> Icons.Outlined.Celebration
                else -> Icons.Outlined.MusicNote
            }
            Icon(symbol, null, Modifier.align(Alignment.Center).size(32.dp), tint = VisualDesign.Muted.copy(alpha = .45f))
        }
    }
}
@Composable private fun LikedArtwork(modifier: Modifier) {
    Box(modifier.background(Brush.linearGradient(listOf(Color(0xFF4123F5), Color(0xFF794DFF), Color(0xFF3830D4))))) {
        Icon(Icons.Filled.Favorite, null, Modifier.align(Alignment.Center).size(31.dp), tint = Color.White)
    }
}
@Composable private fun VisualIcon(icon: ImageVector, label: String, onClick: (() -> Unit)?) {
    IconButton(onClick ?: {}, Modifier.size(40.dp), enabled = onClick != null) {
        Icon(icon, label, Modifier.size(22.dp), tint = if (onClick != null) VisualDesign.Ink else VisualDesign.Muted.copy(alpha = .4f))
    }
}
@Composable private fun VisualFeedback(message: String, action: String? = null, onAction: () -> Unit = {}, loading: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = VisualDesign.Accent)
        Text(message, style = VisualDesign.Body, color = VisualDesign.Muted)
        action?.let { Button(onAction, colors = ButtonDefaults.buttonColors(containerColor = VisualDesign.Accent)) { Text(it) } }
    }
}
