package nonstop.artlab

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nonstop.artlab.catalog.Album
import nonstop.artlab.direction.ArtRecipe
import nonstop.artlab.renderer.*

/** Simulation content belongs to Desktop only and is never packaged in the Android APK. */
@Composable
fun LabV8Renderer(album: Album, cover: ImageBitmap?, atmosphere: AtmosphereBitmaps?, recipe: ArtRecipe,
    state: PlayerState, onState: (PlayerState) -> Unit, previous: () -> Unit, next: () -> Unit,
    onClose: () -> Unit, onMenu: () -> Unit, font: FontFamily, debug: DebugView = DebugView.FINAL,
    fontScale: Float = 1f, modifier: Modifier = Modifier,
    microOptions: MicroOptions = MicroOptions(), microState: PlayerMicroState? = null) {
    V8Renderer(album, cover, atmosphere, recipe, state, onState, previous, next, onClose, onMenu,
        font, debug, fontScale, modifier, microOptions, microState,
        lyricsContent = { placement, u ->
            val text = Color(0xfffafbff); val muted = Color(0xff9296a6)
            Column(placement, verticalArrangement = Arrangement.spacedBy((20*u).dp)) {
                Text(album.track ?: album.album, color=text, fontSize=(22*u).sp, lineHeight=(28*u).sp,
                    fontFamily=font, fontWeight=FontWeight.SemiBold, maxLines=2, overflow=TextOverflow.Ellipsis)
                Text("LETRAS · DEMONSTRAÇÃO\nSem letras reais ou versos atribuídos à faixa", color=muted,
                    fontSize=(13*u).sp, lineHeight=(18*u).sp, fontFamily=font)
                repeat(5) { i -> Text("Linha de teste ${i+1}", color=(if(i==1) Color(recipe.lyrics.active) else Color(recipe.lyrics.future))
                    .copy(alpha=recipe.lyrics.alphaProgression[i.coerceAtMost(recipe.lyrics.alphaProgression.lastIndex)]),
                    fontSize=(26*u).sp, lineHeight=(33*u).sp, fontFamily=font, fontWeight=FontWeight.SemiBold) }
                Text("Voltar ao player", color=text, fontSize=(16*u).sp, fontFamily=font,
                    modifier=Modifier.heightIn(min=(48*u).dp).clickable { onState(state.copy(lyrics=false)) }.padding((12*u).dp))
            }
        })
}
