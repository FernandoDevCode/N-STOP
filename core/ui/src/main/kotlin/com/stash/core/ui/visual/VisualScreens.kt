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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Constraints
import coil3.compose.AsyncImage

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
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(
        top = if (state.page == VisualPage.Home) 16.dp else 0.dp, bottom = VisualDesign.Space.Section)) {
        if (state.page != VisualPage.Home) item("header") { VisualHeader(actions) }
        if (heading.isNotEmpty()) item("heading") {
            Text(heading, Modifier.padding(horizontal = VisualDesign.Gutter).padding(bottom = 12.dp),
                style = VisualDesign.PageTitle)
        }
        if (state.filters.isNotEmpty()) item("filters") {
            VisualFilters(state.filters, state.selectedFilter, actions.onFilter)
            Spacer(Modifier.height(when (state.page) { VisualPage.Home -> 18.dp; VisualPage.Library -> 8.dp; else -> 16.dp }))
        }
        if (state.page == VisualPage.Search) item("query") {
            VisualSearchField(state.query, actions.onQuery, actions.onSearch, Modifier.padding(horizontal = VisualDesign.Gutter))
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
                Modifier.padding(horizontal = VisualDesign.Gutter))
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
                val sections = state.sections.filter { it.cards.isNotEmpty() }
                val openingSections = if (state.page == VisualPage.Explore) sections.take(1) else emptyList()
                openingSections.forEach { section -> item("section:${section.id}") {
                    VisualSectionTitle(section.title, actions.onSection?.let { callback -> { callback(section.id) } })
                    VisualRail(section, actions.onCard, artwork)
                    Spacer(Modifier.height(VisualDesign.Space.Section))
                } }
                if (state.categories.isNotEmpty()) item("categories") {
                    VisualSectionTitle(if (state.page == VisualPage.Search) "Explorar tudo" else "Navegar por gênero")
                    VisualCategoryGrid(state.categories, if (state.page == VisualPage.Search) 1.48f else 1.75f, actions.onCard, artwork)
                    Spacer(Modifier.height(if (state.page == VisualPage.Search) 32.dp else 22.dp))
                }
                sections.drop(openingSections.size).forEach { section -> item("section:${section.id}") {
                    VisualSectionTitle(section.title, actions.onSection?.let { callback -> { callback(section.id) } })
                    VisualRail(section, actions.onCard, artwork)
                    Spacer(Modifier.height(VisualDesign.Space.Section))
                } }
                items(state.rows, key = { it.id }) { card -> VisualLibraryRow(card,
                    { actions.onCard(card.id) }, { actions.onMore(card.id) }, artwork) }
            }
        }
        extraContent()
    }
}

@Composable fun VisualHeader(actions: VisualActions, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth().testTag("visual-header")) {
        val density = LocalDensity.current
        val brandWidth = rememberTextMeasurer().measure("NØNSTOP", VisualDesign.Brand, softWrap = false).size.width
        val buttonCount = 1 + (if (actions.onCast != null) 1 else 0) + (if (actions.onNotifications != null) 1 else 0)
        val stacked = with(density) { brandWidth.toDp() + VisualDesign.Size.Control * buttonCount + 38.dp > maxWidth }
        val controls: @Composable () -> Unit = {
            actions.onCast?.let { VisualHeaderButton(VisualHeaderSymbol.Cast, "Transmitir", it) }
            actions.onNotifications?.let { VisualHeaderButton(VisualHeaderSymbol.Bell, "Notificações", it) }
            VisualHeaderButton(VisualHeaderSymbol.Settings, "Configurações", actions.onSettings)
        }
        if (stacked) Column(Modifier.fillMaxWidth().padding(start = VisualDesign.Gutter, end = 18.dp, top = 12.dp)) {
            Text("NØNSTOP", style = VisualDesign.Brand, color = VisualDesign.Ink, maxLines = 1)
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) { controls() }
        } else Row(Modifier.fillMaxWidth().heightIn(min = VisualDesign.Size.Chrome).padding(start = VisualDesign.Gutter, end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("NØNSTOP", style = VisualDesign.Brand, color = VisualDesign.Ink, maxLines = 1, modifier = Modifier.weight(1f))
            controls()
        }
    }
}

@Composable fun VisualFilters(filters: List<String>, selected: String, onSelect: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val unit = (maxWidth - 67.dp) / 4.32f
        val maximum = (maxWidth - 40.dp).coerceAtLeast(1.dp)
        LazyRow(contentPadding = PaddingValues(horizontal = VisualDesign.Gutter), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(filters.size) { index ->
                val label = filters[index]
                val active = selected == label
                val shape = RoundedCornerShape(VisualDesign.Radius.Filter)
                // Minimums retain the reference proportions without constraining enlarged text.
                val minimum = if (filters.size == 4 && maxWidth >= 360.dp)
                    unit * listOf(1f, 1.02f, 1.08f, 1.22f)[index] else 76.dp
                Box(Modifier.heightIn(min = 33.dp).widthIn(min = minimum.coerceAtMost(maximum), max = maximum).clip(shape)
                    .background(VisualDesign.filterSurface(active))
                    .border(if (active) .8.dp else .5.dp,
                        if (!LocalVisualDark.current) VisualDesign.Border else if (active) VisualDesign.FilterSelectedBorder else VisualDesign.FilterIdleBorder, shape)
                    .selectable(active, role = Role.Tab,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null) { onSelect(label) }
                    .padding(horizontal = 14.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
                    Text(label, textAlign = TextAlign.Center,
                        style = VisualDesign.Caption.copy(fontWeight = if (active) FontWeight.Medium else FontWeight.Normal),
                        color = if (active) VisualDesign.Ink else VisualDesign.Muted)
                }
            }
        }
    }
}

@Composable private fun VisualHeaderButton(symbol: VisualHeaderSymbol, label: String, onClick: () -> Unit) {
    IconButton(onClick, Modifier.size(VisualDesign.Size.Control).semantics { contentDescription = label }) {
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
        Spacer(Modifier.width(VisualDesign.Space.Medium))
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

@Composable fun VisualHeroCard(hero: VisualHero, onPlay: () -> Unit, onMore: () -> Unit, artwork: VisualArtwork = { url, size -> VisualCover(url, size) }) {
    BoxWithConstraints(Modifier.padding(horizontal = VisualDesign.Gutter).fillMaxWidth()) {
        val shape = RoundedCornerShape(VisualDesign.Radius.Hero)
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val textWidth = with(density) { (maxWidth - VisualDesign.Size.HeroPadding * 2).roundToPx().coerceAtLeast(1) }
        fun textHeight(text: String, style: TextStyle, lines: Int = Int.MAX_VALUE, width: Int = textWidth): Int =
            if (text.isBlank()) 0 else measurer.measure(text, style, maxLines = lines, constraints = Constraints(maxWidth = width)).size.height
        val required = with(density) {
            val playWidth = measurer.measure("Ouvir agora", VisualDesign.Caption.copy(fontWeight = FontWeight.SemiBold), softWrap = false).size.width.toDp() + 69.dp
            val actionsHeight = if (playWidth + 52.dp > maxWidth - VisualDesign.Size.HeroPadding * 2) 84.dp else 38.dp
            (textHeight(hero.eyebrow, VisualDesign.Caption) + textHeight(hero.card.title, VisualDesign.HeroTitle, 2) +
                textHeight(hero.card.subtitle, VisualDesign.HeroArtist, 2) +
                textHeight(hero.description, VisualDesign.Body, 2, minOf(textWidth, 220.dp.roundToPx()))).toDp() +
                VisualDesign.Size.HeroPadding * 2 + actionsHeight + 11.dp + (if (hero.description.isBlank()) 0.dp else 4.dp)
        }
        val below = required > VisualDesign.Size.Hero
        Column(Modifier.fillMaxWidth().clip(shape).background(VisualDesign.Surface).border(1.dp, VisualDesign.Border, shape)) {
            // The artwork viewport and existing scrim remain fixed, even when copy needs more space.
            Box(Modifier.fillMaxWidth().height(VisualDesign.Size.Hero).then(if (below) Modifier.clip(shape) else Modifier).testTag("visual-hero-artwork")) {
                artwork(hero.card.artwork, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent,
                    .35f to Color(0x18101116), 1f to Color(0xF5101116))))
                Box(Modifier.align(Alignment.TopEnd).padding(5.dp)) { VisualIcon(Icons.Outlined.MoreHoriz, "Opções do lançamento", onMore) }
                if (!below) VisualHeroMetadata(hero, onPlay, onMore, Modifier.align(Alignment.BottomStart))
            }
            if (below) VisualHeroMetadata(hero, onPlay, onMore, Modifier.fillMaxWidth(), overArtwork = false)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun VisualHeroMetadata(hero: VisualHero, onPlay: () -> Unit, onMore: () -> Unit, modifier: Modifier, overArtwork: Boolean = true) {
    val ink = if (overArtwork) Color.White else VisualDesign.Ink
    Column(modifier.padding(VisualDesign.Size.HeroPadding).testTag("visual-hero-metadata")) {
        if (hero.eyebrow.isNotBlank()) Text(hero.eyebrow, style = VisualDesign.Caption, color = ink)
        Text(hero.card.title, color = ink, style = VisualDesign.HeroTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (hero.card.subtitle.isNotBlank()) Text(hero.card.subtitle, color = ink, style = VisualDesign.HeroArtist, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (hero.description.isNotBlank()) Text(hero.description, Modifier.widthIn(max = 220.dp).padding(top = 4.dp),
            style = VisualDesign.Body, color = if (overArtwork) Color(0xFFD4CFDF) else VisualDesign.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        FlowRow(Modifier.padding(top = 11.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onPlay, shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = VisualDesign.Accent,
                contentColor = MaterialTheme.colorScheme.onPrimary), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                modifier = Modifier.height(38.dp)) {
                Icon(Icons.Filled.PlayArrow, null, Modifier.size(22.dp)); Spacer(Modifier.width(7.dp))
                Text("Ouvir agora", style = VisualDesign.Caption.copy(fontWeight = FontWeight.SemiBold))
            }
            Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xB33A394B))
                .border(1.dp, VisualDesign.Border, CircleShape).clickable(onClick = onMore), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Add, "Adicionar lançamento", Modifier.size(25.dp), tint = Color.White)
            }
        }
    }
}

@Composable fun VisualSectionTitle(title: String, onMore: (() -> Unit)? = null) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = VisualDesign.Gutter).padding(bottom = 10.dp)) {
        val density = LocalDensity.current
        val actionWidth = with(density) { rememberTextMeasurer().measure("Mostrar tudo", VisualDesign.Caption.copy(fontSize = 10.sp)).size.width.toDp() + 30.dp }
        val stacked = onMore != null && maxWidth - actionWidth < 160.dp * density.fontScale
        val action: @Composable () -> Unit = {
            onMore?.let { Row(Modifier.clickable(onClick = it).padding(start = 8.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Mostrar tudo", style = VisualDesign.Caption.copy(fontSize = 10.sp), color = VisualDesign.Muted)
                Icon(Icons.Outlined.ChevronRight, null, Modifier.size(17.dp), tint = VisualDesign.Muted)
            } }
        }
        if (stacked) Column(Modifier.fillMaxWidth()) {
            Text(title, style = VisualDesign.Heading, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Box(Modifier.align(Alignment.End)) { action() }
        } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = VisualDesign.Heading, maxLines = 2, overflow = TextOverflow.Ellipsis)
            action()
        }
    }
}

@Composable fun VisualRail(section: VisualSection, onCard: (String) -> Unit, artwork: VisualArtwork = { url, size -> VisualCover(url, size) }) {
    val cards = section.cards; val compact = section.compact; val plain = section.plain
    LazyRow(modifier = Modifier.testTag("visual-rail-${section.id}"), contentPadding = PaddingValues(horizontal = VisualDesign.Gutter), horizontalArrangement = Arrangement.spacedBy(VisualDesign.Space.Rail)) {
        items(cards, key = { it.id }) { card ->
            VisualRailCard(card, { onCard(card.id) }, compact = compact, plain = plain, artwork = artwork)
        }
    }
}

@Composable private fun VisualCategoryGrid(cards: List<VisualCard>, aspect: Float, onCard: (String) -> Unit, artwork: VisualArtwork = { url, size -> VisualCover(url, size) }) {
    val columns = 3
    Column(Modifier.padding(horizontal = VisualDesign.Gutter), verticalArrangement = Arrangement.spacedBy(VisualDesign.Space.Rail)) {
        cards.chunked(columns).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(VisualDesign.Space.Rail)) {
            row.forEach { card ->
                val shape = RoundedCornerShape(VisualDesign.Radius.Card)
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

@Composable fun VisualLibraryRow(card: VisualCard, onOpen: () -> Unit, onMore: () -> Unit, artwork: VisualArtwork = { url, size -> VisualCover(url, size) }) {
    Row(Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = onMore).padding(start = 20.dp, end = 10.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        val shape = if (card.round) CircleShape else RoundedCornerShape(6.dp)
        Box(Modifier.size(66.dp).clip(shape)) {
            if (card.liked) LikedArtwork(Modifier.fillMaxSize()) else artwork(card.artwork, Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(card.title, style = VisualDesign.Body.copy(fontWeight = FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (card.subtitle.isNotBlank()) Text(card.subtitle, style = VisualDesign.Caption, color = VisualDesign.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        VisualIcon(Icons.Outlined.MoreHoriz, "Opções de ${card.title}", onMore)
    }
}

@Composable fun VisualBottomBar(page: VisualPage, player: VisualPlayer?, actions: VisualActions,
    modifier: Modifier = Modifier, artwork: VisualArtwork = { url, size -> VisualCover(url, size) },
    backgroundVisible: Boolean = true) {
    Column(modifier.fillMaxWidth().then(if (backgroundVisible) Modifier.background(Brush.verticalGradient(listOf(if (LocalVisualDark.current) Color(0xE91A1A24) else VisualDesign.Surface, VisualDesign.Background))) else Modifier)) {
        player?.let { VisualMiniPlayer(it, actions, artwork) }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val density = LocalDensity.current
            val measurer = rememberTextMeasurer()
            val availableLabelWidth = with(density) { ((maxWidth - 16.dp) / 5).roundToPx() }
            Row(Modifier.fillMaxWidth().height(VisualDesign.Size.Chrome).clipToBounds()
                .then(if (backgroundVisible) Modifier.background(VisualDesign.navigationSurface()) else Modifier).padding(horizontal = VisualDesign.Space.Small)) {
                listOf(VisualPage.Home, VisualPage.Explore, VisualPage.Radio, VisualPage.Search, VisualPage.Library).forEachIndexed { index, destination ->
                    val selected = page == destination
                    val label = listOf("Início", "Explorar", "Rádio", "Buscar", "Biblioteca")[index]
                    val labelStyle = VisualDesign.NavigationLabel.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    val naturalWidth = measurer.measure(label, labelStyle, softWrap = false, maxLines = 1).size.width
                    val fit = if (naturalWidth <= availableLabelWidth) 1f
                        else ((availableLabelWidth - 1f) / naturalWidth).coerceIn(.1f, 1f)
                    Column(Modifier.weight(1f).fillMaxHeight().testTag("visual-tab-${destination.name}").selectable(selected, role = Role.Tab,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null) { actions.onPage(destination) }
                        .padding(top = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        VisualNavigationIcon(destination, selected, Modifier.size(VisualDesign.Size.NavigationIcon))
                        Text(label, maxLines = 1, softWrap = false,
                            style = labelStyle.copy(fontSize = 10.sp * fit),
                            color = if (selected) VisualDesign.Ink else VisualDesign.Muted)
                    }
                }
            }
        }
    }
}

@Composable fun VisualMiniPlayer(player: VisualPlayer, actions: VisualActions,
    artwork: VisualArtwork = { url, size -> VisualCover(url, size) }) {
    val shape = RoundedCornerShape(VisualDesign.Radius.MiniPlayer)
    Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(VisualDesign.Size.MiniPlayer).clip(shape).testTag("visual-miniplayer")
        .background(Brush.horizontalGradient(if (LocalVisualDark.current) listOf(Color(0xFF2C2B37), Color(0xFF20202C)) else listOf(VisualDesign.Surface, VisualDesign.Surface)))
        .border(1.dp, VisualDesign.Border, shape)) {
        Row(Modifier.fillMaxSize().clickable(onClick = actions.onPlayer).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            artwork(player.card.artwork, Modifier.size(37.dp).clip(RoundedCornerShape(5.dp)))
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(player.card.title, style = VisualDesign.Caption.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (player.card.subtitle.isNotBlank()) Text(player.card.subtitle, style = VisualDesign.Caption, color = VisualDesign.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    IconButton(onClick ?: {}, Modifier.size(VisualDesign.Size.Control), enabled = onClick != null) {
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

/** A single shared card for hosts with lazy paging and Android-only context menus. */
@Composable fun VisualRailCard(card: VisualCard, onClick: () -> Unit, onMore: (() -> Unit)? = null,
    compact: Boolean = false, plain: Boolean = false,
    artwork: VisualArtwork = { url, size -> VisualCover(url, size) }) {
            val width = if (compact) VisualDesign.Size.RecentWidth else if (plain) VisualDesign.Size.AlbumWidth else VisualDesign.Size.CardWidth
            val shape = RoundedCornerShape(VisualDesign.Radius.Card)
            Column(Modifier.width(width).then(if (!compact && !plain) Modifier.heightIn(min = VisualDesign.Size.CardMinimum * LocalDensity.current.fontScale.coerceAtLeast(1f)) else Modifier)
                .clip(shape).combinedClickable(onClick = onClick, onLongClick = onMore)
                .then(if (!compact && !plain) Modifier.background(VisualDesign.cardSurface()) else Modifier)
                .then(if (!compact && !plain) Modifier.border(1.dp, VisualDesign.Border, shape) else Modifier)) {
                Box(Modifier.fillMaxWidth().then(if (!compact && !plain) Modifier.height(VisualDesign.Size.CardArtwork) else Modifier.aspectRatio(1f)).clip(shape)) {
                    if (card.liked) LikedArtwork(Modifier.fillMaxSize()) else artwork(card.artwork, Modifier.fillMaxSize())
                }
                if (!compact) Column(Modifier.padding(horizontal = if (plain) 0.dp else 9.dp, vertical = if (plain) 7.dp else 8.dp), verticalArrangement = Arrangement.spacedBy(VisualDesign.Space.Tiny)) {
                    Text(card.title, style = VisualDesign.CardTitle.copy(fontSize = if (plain) 13.sp else 13.5.sp), maxLines = if (plain) 1 else 2, overflow = TextOverflow.Ellipsis)
                    if (card.subtitle.isNotBlank()) Text(card.subtitle, style = VisualDesign.CardCaption, color = VisualDesign.Muted, maxLines = if (plain) 1 else 2, overflow = TextOverflow.Ellipsis)
                }
            }
}
