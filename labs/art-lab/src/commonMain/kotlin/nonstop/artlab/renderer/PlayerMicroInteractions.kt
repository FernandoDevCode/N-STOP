package nonstop.artlab.renderer

import androidx.compose.runtime.*
import nonstop.artlab.direction.ArtFamily
import nonstop.artlab.direction.ArtRecipe
import nonstop.artlab.features.mix
import kotlin.math.*

/** PLAYER_MICRO_INTERACTIONS_V1. Units follow the renderer's logical 432 dp viewport. */
object MicroParameters {
    const val TRANSITION_MS=180f
    const val IDLE_RADIUS_DP=6f
    const val DRAG_RADIUS_DP=10f
    const val IDLE_ALPHA=.18f
    const val DRAG_ALPHA=.34f
    const val THUMB_SCALE=1.08f
    const val EMISSION_MS=36f
    const val PARTICLES_PER_EMISSION=8
    const val MAX_PARTICLES=80
    const val ATTACK_MS=180f
    const val RELEASE_MS=360f
}

data class MicroProfile(val audioGain:Float,val trailGain:Float,val glowSpreadDp:Float) {
    companion object {
        fun from(recipe:ArtRecipe):MicroProfile {
            fun family(f:ArtFamily)=when(f) {
                ArtFamily.PRISMATIC -> MicroProfile(1f,1f,6f)
                ArtFamily.DENSE_CHROMATIC -> MicroProfile(.70f,.85f,5f)
                ArtFamily.EDITORIAL_NATURAL -> MicroProfile(.35f,.60f,3f)
                ArtFamily.DRAMATIC_CONTRAST -> MicroProfile(.70f,.75f,2.5f)
                ArtFamily.SELECTIVE_LUMINOUS -> MicroProfile(.60f,.75f,3.5f)
                ArtFamily.MINIMAL_MONOCHROME -> MicroProfile(.20f,.45f,2f)
                ArtFamily.NEUTRAL_FALLBACK -> MicroProfile(.25f,.5f,2.5f)
            }
            return recipe.familyWeights.entries.fold(MicroProfile(0f,0f,0f)) { p,(f,w) ->
                val q=family(f); MicroProfile(p.audioGain+q.audioGain*w,p.trailGain+q.trailGain*w,p.glowSpreadDp+q.glowSpreadDp*w)
            }
        }
    }
}

data class MicroOptions(val enabled:Boolean=true,val reducedMotion:Boolean=false,val active:Boolean=true,
    val audio:AudioEnergySource?=null,val automaticClock:Boolean=true)

/** PCM input port; no playback/Media3 dependency and no fabricated beat timer. */
interface AudioEnergySource {
    val durationMillis:Double
    fun energyAt(positionMillis:Double):Float
}

class PcmEnergyTimeline(private val levels:FloatArray,val stepMillis:Double,
    override val durationMillis:Double):AudioEnergySource {
    init { require(levels.isNotEmpty() && levels.all { it.isFinite() && it in 0f..1f })
        require(stepMillis.isFinite() && stepMillis>0 && durationMillis.isFinite() && durationMillis>0) }
    val size:Int get()=levels.size
    override fun energyAt(positionMillis:Double):Float {
        if(!positionMillis.isFinite() || positionMillis<0 || positionMillis>=durationMillis)return 0f
        val x=(positionMillis/stepMillis).coerceAtMost(levels.lastIndex.toDouble())
        val i=x.toInt(); val t=(x-i).toFloat()
        return levels[i]*(1-t)+levels[min(i+1,levels.lastIndex)]*t
    }
}

/** Streaming, per-channel AC RMS. Opposite stereo phases cannot cancel each other. */
class PcmRmsAccumulator(private val channels:Int,private val sampleRate:Double) {
    init { require(channels in 1..8 && sampleRate.isFinite() && sampleRate in 8000.0..192000.0) }
    private val windowFrames=(sampleRate*.020).roundToInt()
    private val sums=DoubleArray(channels)
    private val squares=DoubleArray(channels)
    private val rms=mutableListOf<Float>()
    private var frames=0
    private var totalFrames=0L
    fun addFrame(samples:FloatArray) {
        require(samples.size==channels)
        for(c in 0 until channels) {
            val v=if(samples[c].isFinite())samples[c].coerceIn(-1f,1f).toDouble() else 0.0
            sums[c]+=v; squares[c]+=v*v
        }
        frames++; totalFrames++
        if(frames==windowFrames)flush()
    }
    private fun flush() {
        if(frames==0)return
        var power=0.0
        for(c in 0 until channels) { power+=max(0.0,squares[c]/frames-(sums[c]/frames).pow(2)); sums[c]=0.0; squares[c]=0.0 }
        rms+=sqrt(power/channels).toFloat(); frames=0
    }
    fun finish():PcmEnergyTimeline {
        flush(); require(totalFrames>0)
        val sorted=rms.sorted()
        val reference=sorted[((sorted.size-1)*.95).toInt()].coerceIn(.08f,.70f)
        val normalized=FloatArray(rms.size) { ((rms[it]-.003f).coerceAtLeast(0f)/reference).coerceIn(0f,1f) }
        return PcmEnergyTimeline(normalized,windowFrames/sampleRate*1000,totalFrames/sampleRate*1000)
    }
}

fun progressColorAt(recipe:ArtRecipe,position:Float):Long {
    val p=position.coerceIn(0f,1f)
    val stops=recipe.progress.fill
    val i=stops.indexOfLast { it.position<=p }.coerceIn(0,stops.lastIndex-1)
    val a=stops[i]; val b=stops[i+1]
    return mix(a.color,b.color,((p-a.position)/(b.position-a.position)).toDouble())
}

class TrailPool {
    val x=FloatArray(MicroParameters.MAX_PARTICLES)
    val y=FloatArray(x.size)
    val size=FloatArray(x.size)
    val alpha=FloatArray(x.size)
    val age=FloatArray(x.size) { 1000f }
    val life=FloatArray(x.size)
    val drift=FloatArray(x.size)
    val color=LongArray(x.size)
    private var cursor=0
    private var random=0x56ac81L
    var count=0; private set
    private fun next():Float {
        random=(random*1664525+1013904223) and 0xffffffffL
        return ((random ushr 8)/16777216.0).toFloat()
    }
    fun clear() { age.fill(1000f); count=0 }
    fun emit(position:Float,direction:Float,tint:Long,gain:Float) {
        repeat(MicroParameters.PARTICLES_PER_EMISSION) {
            val i=cursor; cursor=(cursor+1)%x.size
            x[i]=position.coerceIn(0f,1f); y[i]=(next()-.5f)*3f
            size[i]=2f+next()*4f*gain; alpha[i]=.10f+next()*.08f*gain
            age[i]=0f; life[i]=220f+next()*60f
            drift[i]=-direction.coerceIn(-1f,1f)*(6f+next()*12f)
            color[i]=tint
        }
        count=age.indices.count { age[it]<life[it] }
    }
    fun advance(dtMs:Float) {
        count=0
        for(i in age.indices) if(age[i]<life[i]) { age[i]+=dtMs; if(age[i]<life[i])count++ }
    }
}

/** Only revision is observed by Canvas. Tick never invalidates layout/the full player. */
@Stable class PlayerMicroState {
    var revision by mutableIntStateOf(0); private set
    var wake by mutableIntStateOf(0); private set
    var engagement=0f; private set
    var energy=0f; private set
    var dragging=false; private set
    var audioPositionMillis=0.0; private set
    val trail=TrailPool()
    private var hovered=false; private var focused=false; private var pressed=false; private var keyPressed=false
    private var start=0f; private var target=0f; private var elapsed=MicroParameters.TRANSITION_MS
    private var emission=0f; private var position=.43f; private var direction=0f
    private var lastDrawEngagement=0f; private var lastDrawEnergy=0f
    private var tint=0xffffffffL; private var oldAudio:AudioEnergySource?=null; private var oldProgress=Float.NaN
    private fun retarget() {
        val next=when { dragging -> 1f; pressed || keyPressed -> .85f; focused -> .65f; hovered -> .55f; else -> 0f }
        if(next!=target) { start=engagement; target=next; elapsed=0f; wake++ }
    }
    fun hover(value:Boolean) { hovered=value; retarget() }
    fun focus(value:Boolean) { focused=value; if(!value)keyPressed=false; retarget() }
    fun press(value:Boolean) { pressed=value; retarget() }
    fun keyPress(value:Boolean) { keyPressed=value; retarget() }
    fun beginDrag(p:Float,color:Long) { dragging=true; position=p.coerceIn(0f,1f); tint=color; emission=0f; retarget(); wake++ }
    fun dragTo(p:Float,color:Long) { val clamped=p.coerceIn(0f,1f); direction=sign(clamped-position); position=clamped; tint=color }
    fun endDrag() { dragging=false; pressed=false; emission=0f; retarget(); wake++ }
    fun syncAudio(source:AudioEnergySource?,progress:Float) {
        if(source!==oldAudio || progress!=oldProgress) {
            audioPositionMillis=progress.coerceIn(0f,1f)*(source?.durationMillis ?: 0.0)
            oldAudio=source; oldProgress=progress
        }
    }
    fun needsFrame(options:MicroOptions,playing:Boolean):Boolean = options.enabled && options.active && !options.reducedMotion &&
        (elapsed<MicroParameters.TRANSITION_MS || dragging || trail.count>0 || energy>.0005f ||
            (playing && options.audio!=null && audioPositionMillis<options.audio.durationMillis))
    fun settle(options:MicroOptions) {
        if(!options.active || !options.enabled) {
            dragging=false; pressed=false; keyPressed=false; hovered=false; focused=false; target=0f; start=0f
        }
        trail.clear(); energy=0f
        engagement=if(options.active && options.enabled)target else 0f
        lastDrawEngagement=engagement; lastDrawEnergy=0f
        elapsed=MicroParameters.TRANSITION_MS; revision++
    }
    fun advance(dtMs:Float,rawEnergy:Float,profile:MicroProfile,options:MicroOptions=MicroOptions()) {
        if(!options.enabled || !options.active || options.reducedMotion) { settle(options); return }
        val dt=if(dtMs.isFinite())dtMs.coerceIn(0f,1000f) else 0f
        val hadTrail=trail.count>0
        val smoothDt=min(dt,48f)
        elapsed=(elapsed+smoothDt).coerceAtMost(MicroParameters.TRANSITION_MS)
        val t=elapsed/MicroParameters.TRANSITION_MS
        engagement=start+(target-start)*t*t*(3-2*t)
        val raw=if(rawEnergy.isFinite())rawEnergy.coerceIn(0f,1f) else 0f
        val tau=if(raw>energy)MicroParameters.ATTACK_MS else MicroParameters.RELEASE_MS
        energy+=(raw-energy)*(1-exp(-smoothDt/tau))
        if(energy<.0005f && raw==0f)energy=0f
        trail.advance(dt)
        if(dragging) {
            emission+=dt
            // Never catch up a backlog after a suspended/stalled frame.
            if(emission>=MicroParameters.EMISSION_MS) { emission%=MicroParameters.EMISSION_MS; trail.emit(position,direction,tint,profile.trailGain) }
        }
        if(hadTrail || trail.count>0 || abs(engagement-lastDrawEngagement)>.0001f || abs(energy-lastDrawEnergy)>.0001f) {
            lastDrawEngagement=engagement; lastDrawEnergy=energy; revision++
        }
    }
    fun tick(dtMs:Float,options:MicroOptions,playing:Boolean,profile:MicroProfile) {
        if(playing && options.enabled && options.active && !options.reducedMotion)audioPositionMillis+=dtMs
        advance(dtMs,if(playing)options.audio?.energyAt(audioPositionMillis) ?: 0f else 0f,profile,options)
    }
}

@Composable internal fun MicroAnimationDriver(micro:PlayerMicroState,options:MicroOptions,playing:Boolean,progress:Float,profile:MicroProfile) {
    SideEffect { micro.syncAudio(options.audio,progress) }
    LaunchedEffect(micro,micro.wake,options,playing,profile) {
        if(!options.automaticClock)return@LaunchedEffect
        if(!options.enabled || !options.active || options.reducedMotion) { micro.settle(options); return@LaunchedEffect }
        var previous=0L
        while(micro.needsFrame(options,playing)) withFrameNanos { now ->
            if(previous!=0L)micro.tick(((now-previous)/1_000_000.0).toFloat(),options,playing,profile)
            previous=now
        }
    }
}
