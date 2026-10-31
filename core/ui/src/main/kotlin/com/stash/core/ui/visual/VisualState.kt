package com.stash.core.ui.visual

import androidx.compose.runtime.Immutable

/** Presentation-only contracts. IDs are opaque to the renderer; hosts own all side effects. */
enum class VisualPage { Home, Explore, Radio, Search, Library }
enum class VisualStatus { Content, Loading, Empty, Error }
@Immutable data class VisualCard(
    val id: String, val title: String, val subtitle: String = "", val artwork: String? = null,
    val liked: Boolean = false, val round: Boolean = false,
)
@Immutable data class VisualSection(val id: String, val title: String, val cards: List<VisualCard>, val compact: Boolean = false, val plain: Boolean = false)
@Immutable data class VisualHero(val card: VisualCard, val eyebrow: String = "Lançamento", val description: String = "")
@Immutable data class VisualScreenState(
    val page: VisualPage = VisualPage.Home,
    val filters: List<String> = emptyList(), val selectedFilter: String = "",
    val hero: VisualHero? = null, val sections: List<VisualSection> = emptyList(),
    val categories: List<VisualCard> = emptyList(), val rows: List<VisualCard> = emptyList(),
    val query: String = "", val status: VisualStatus = VisualStatus.Content, val showSearch: Boolean = false,
    val message: String = "", val emptyAction: String = "Buscar músicas",
)
@Immutable data class VisualPlayer(val card: VisualCard, val playing: Boolean, val progress: Float = 0f, val buffering: Boolean = false)

data class VisualActions(
    val onPage: (VisualPage) -> Unit = {}, val onFilter: (String) -> Unit = {},
    val onCard: (String) -> Unit = {}, val onMore: (String) -> Unit = {},
    val onSection: ((String) -> Unit)? = null, val onQuery: (String) -> Unit = {},
    val onSearch: () -> Unit = {}, val onSettings: () -> Unit = {},
    val onCast: (() -> Unit)? = null, val onNotifications: (() -> Unit)? = null,
    val onCreate: (() -> Unit)? = null, val onImport: (() -> Unit)? = null,
    val onSort: (() -> Unit)? = null, val onRetry: () -> Unit = {},
    val onEmpty: () -> Unit = {}, val onPlayer: () -> Unit = {},
    val onToggle: () -> Unit = {}, val onLike: () -> Unit = {},
)
