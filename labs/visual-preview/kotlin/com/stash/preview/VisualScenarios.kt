package com.stash.preview

import com.stash.core.ui.visual.*

/** Development data only. Reuses existing artwork keys; never opens a library or a service. */
enum class VisualScenario {
    Approved, Dynamic, Many, NoPlayer, Loading, Empty, Error;

    fun screen(page: VisualPage): VisualScreenState {
        val approved = VisualFixture.screen(page)
        return when (this) {
            Approved, NoPlayer -> approved
            Loading -> approved.copy(status = VisualStatus.Loading)
            Empty -> approved.copy(status = VisualStatus.Empty, message = "Ainda não há conteúdo nesta seção.")
            Error -> approved.copy(status = VisualStatus.Error, message = "Não foi possível carregar o conteúdo. Tente novamente.")
            Dynamic, Many -> {
                val long = "Uma seleção musical com um título extremamente longo, artistas convidados e diferentes versões ao vivo"
                val art = listOf("wall", "library5", "library1", null)
                val cards = List(if (this == Many) 60 else 8) { i ->
                    VisualCard("dev-$i", if (i % 2 == 0) "$long $i" else "Faixa $i",
                        if (i % 3 == 0) "" else "Artista e colaboração com um subtítulo bastante longo", art[i % art.size])
                }
                approved.copy(hero = VisualHero(cards.first().copy(artwork = "hero"),
                    description = "Uma descrição longa para validar o espaço destinado aos textos sem alterar a imagem do hero."),
                    sections = listOf(VisualSection("dev-empty", "Seção vazia", emptyList()),
                        VisualSection("dev-covers", "Conteúdo dinâmico com um título de seção muito longo", cards),
                        VisualSection("dev-recent", "Mais músicas", cards.reversed(), compact = true)),
                    rows = if (page == VisualPage.Library) cards else emptyList(), categories = emptyList())
            }
        }
    }

    fun player(): VisualPlayer? = when (this) {
        NoPlayer, Empty -> null
        Dynamic, Many -> VisualFixture.player.copy(card = VisualFixture.player.card.copy(
            title = "Uma música com um nome muito longo e participações especiais", subtitle = "Artista com nome extenso"))
        else -> VisualFixture.player
    }

    companion object {
        fun parse(value: String?) = entries.firstOrNull { it.name.equals(value, true) } ?: Approved
    }
}
