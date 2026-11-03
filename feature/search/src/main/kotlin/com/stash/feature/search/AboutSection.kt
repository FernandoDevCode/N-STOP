package com.stash.feature.search

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.stash.core.common.ArtUrlUpgrader
import com.stash.data.ytmusic.model.ArtistAbout

@DrawableRes
fun socialIconFor(kind: String): Int? = when (kind.lowercase()) {
    "instagram" -> R.drawable.ic_social_instagram
    "x" -> R.drawable.ic_social_x
    "tiktok" -> R.drawable.ic_social_tiktok
    "youtube" -> R.drawable.ic_social_youtube
    "facebook" -> R.drawable.ic_social_facebook
    "soundcloud" -> R.drawable.ic_social_soundcloud
    "bandcamp" -> R.drawable.ic_social_bandcamp
    else -> null
}

/** User-visible labels preserve discoverability beyond brand icons. */
internal fun artistSocialDisplayName(kind: String): String = when (kind.lowercase()) {
    "instagram" -> "Instagram"
    "x" -> "X"
    "tiktok" -> "TikTok"
    "youtube" -> "YouTube"
    "facebook" -> "Facebook"
    "soundcloud" -> "SoundCloud"
    "bandcamp" -> "Bandcamp"
    else -> "Site"
}

/**
 * Compact artist context; no invented biographies, duplicated hero photographs
 * or full-width decorative cards in place of missing content.
 */
@Composable
fun AboutSection(
    about: ArtistAbout,
    avatarUrl: String?,
    wordmarkSource: String? = null,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val bio = about.bio?.takeIf { it.isNotBlank() }
    // Reset expansion and overflow when navigating between artists.
    var expanded by remember(bio) { mutableStateOf(false) }
    var bioOverflows by remember(bio) { mutableStateOf(false) }
    val alternatePhoto = about.photoUrl?.takeIf { it != avatarUrl }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        if (bio == null) {
            Text(
                text = "Biografia não disponível no momento.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = if (about.socials.isEmpty()) 0.dp else 18.dp),
            )
        }
        if (bio != null) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .22f))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                if (alternatePhoto != null) {
                    AsyncImage(
                        model = ArtUrlUpgrader.upgrade(alternatePhoto),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.width(106.dp).height(120.dp)
                            .clip(RoundedCornerShape(10.dp)),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = bio,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 5,
                        overflow = TextOverflow.Ellipsis,
                        onTextLayout = { if (!expanded) bioOverflows = it.hasVisualOverflow },
                        modifier = Modifier.animateContentSize().clickable { expanded = !expanded },
                    )
                    if (bioOverflows || expanded) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (expanded) "Ler menos" else "Ler mais",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.clickable { expanded = !expanded },
                        )
                    }
                }
            }
        }

        if (about.socials.isNotEmpty()) {
            if (bio != null) Spacer(Modifier.height(22.dp))
            Text(
                text = "Redes e links",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                about.socials.forEach { social ->
                    val label = artistSocialDisplayName(social.kind)
                    Column(
                        modifier = Modifier.width(70.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        IconButton(
                            onClick = { runCatching { uriHandler.openUri(social.url) } },
                            modifier = Modifier.size(48.dp),
                        ) {
                            val brand = socialIconFor(social.kind)
                            if (brand != null) {
                                Icon(
                                    painter = painterResource(brand),
                                    contentDescription = label,
                                    modifier = Modifier.size(26.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = label,
                                    modifier = Modifier.size(26.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }

        if (wordmarkSource != null || bio != null) {
            Spacer(Modifier.height(26.dp))
            Text(
                text = listOfNotNull(
                    wordmarkSource?.let { "Imagem de perfil: " + it },
                    if (bio != null) "Biografia: Last.fm" else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .62f),
            )
        }
    }
}
