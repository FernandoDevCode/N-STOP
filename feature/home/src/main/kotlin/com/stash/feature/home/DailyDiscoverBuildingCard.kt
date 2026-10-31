package com.stash.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stash.core.ui.visual.VisualDesign

/** Only composed while the existing Daily Discover pipeline reports actual pending work. */
@Composable
internal fun DailyDiscoverBuildingCard(modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth().semantics(mergeDescendants = true) {
        liveRegion = LiveRegionMode.Polite
    }, shape = RoundedCornerShape(VisualDesign.Radius.Hero),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(.5.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.nostop_home_updated_daily), style = VisualDesign.Caption, color = VisualDesign.Muted)
            Text(stringResource(R.string.sonora_home_building), style = VisualDesign.Heading)
            Text(stringResource(R.string.sonora_home_building_description), style = VisualDesign.Body, color = VisualDesign.Muted)
        }
    }
}
