package nonstop.artlab.validation

import kotlin.test.*
import kotlin.math.*
import nonstop.artlab.renderer.*
import nonstop.artlab.direction.*

class PlayerMicroInteractionsTest {
    private val profile=MicroProfile(1f,1f,6f)
    @Test fun engagementHasFiniteDurationAndIdleDoesNotScheduleFrames() {
        val m=PlayerMicroState(); val o=MicroOptions()
        assertFalse(m.needsFrame(o,true))
        m.hover(true); assertTrue(m.needsFrame(o,true)); repeat(10) { m.advance(18f,0f,profile) }
        assertEquals(.55f,m.engagement,.0001f)
        m.focus(true); repeat(10) { m.advance(18f,0f,profile) }; assertEquals(.65f,m.engagement,.0001f)
        m.press(true); repeat(10) { m.advance(18f,0f,profile) }; assertEquals(.85f,m.engagement,.0001f)
        m.beginDrag(.4f,0xff88aaff); repeat(10) { m.advance(18f,0f,profile) }; assertEquals(1f,m.engagement,.0001f)
        m.endDrag(); m.hover(false); m.focus(false)
        repeat(20) { m.advance(18f,0f,profile) }
        assertEquals(0f,m.engagement); assertEquals(0,m.trail.count); assertFalse(m.needsFrame(o,true))
        m.focus(true); m.keyPress(true); repeat(10) { m.advance(18f,0f,profile) }
        assertEquals(.85f,m.engagement,.0001f)
        m.focus(false) // A missed KeyUp after losing focus must never leave a pressed glow stuck.
        repeat(10) { m.advance(18f,0f,profile) }; assertEquals(0f,m.engagement)
    }
    @Test fun particlesAreBoundedEmittedOnlyDuringDragAndExpirePromptly() {
        val m=PlayerMicroState()
        repeat(30) { m.advance(16f,0f,profile) }; assertEquals(0,m.trail.count)
        m.beginDrag(.5f,0xffaabbcc)
        repeat(100) { m.dragTo(it/100f,0xffaabbcc); m.advance(18f,0f,profile); assertTrue(m.trail.count<=80) }
        assertTrue(m.trail.count in 40..72)
        for(i in m.trail.age.indices)if(m.trail.age[i]<m.trail.life[i]) {
            assertTrue(m.trail.life[i] in 180f..320f); assertTrue(m.trail.size[i] in 2f..8f)
            assertTrue(m.trail.alpha[i] in .10f.. .22f); assertEquals(0xffaabbcc,m.trail.color[i])
            assertTrue(abs(m.trail.y[i])*1.4f+m.trail.size[i]/2<8f,"Particle must not reach timestamp text")
        }
        m.endDrag(); repeat(20) { m.advance(16f,0f,profile) }; assertEquals(0,m.trail.count)
        m.beginDrag(.5f,0xffffffff); m.advance(1000f,1f,profile)
        assertEquals(8,m.trail.count,"No emission catch-up burst after a stall")
        assertTrue(m.energy<.25f,"Stalled frames must not create an audio flash")
    }
    @Test fun pcmRmsRejectsSilenceDcAndKeepsOppositeStereoPhases() {
        val silence=PcmRmsAccumulator(1,16000.0); val dc=PcmRmsAccumulator(1,16000.0)
        val mono=PcmRmsAccumulator(1,16000.0); val stereo=PcmRmsAccumulator(2,16000.0)
        repeat(16000) { i ->
            val v=(sin(2*PI*400*i/16000)*.4).toFloat()
            silence.addFrame(floatArrayOf(Float.NaN)); dc.addFrame(floatArrayOf(.4f))
            mono.addFrame(floatArrayOf(v)); stereo.addFrame(floatArrayOf(v,-v))
        }
        assertEquals(0f,silence.finish().energyAt(100.0)); assertEquals(0f,dc.finish().energyAt(100.0))
        val a=mono.finish(); val b=stereo.finish()
        assertTrue(a.energyAt(100.0)>.9f); assertEquals(a.energyAt(100.0),b.energyAt(100.0),.001f)
        assertEquals(0f,a.energyAt(-1.0)); assertEquals(0f,a.energyAt(a.durationMillis)); assertEquals(0f,a.energyAt(Double.NaN))
    }
    @Test fun audioEnvelopeHasSpecifiedAttackReleaseAndSettlesWhenPaused() {
        val m=PlayerMicroState()
        repeat(10) { m.advance(18f,1f,profile) }
        assertEquals((1-exp(-1f)),m.energy,.0001f)
        val peak=m.energy
        repeat(20) { m.advance(18f,0f,profile) }
        assertEquals(peak*exp(-1f),m.energy,.0001f)
        repeat(200) { m.advance(18f,Float.NaN,profile) }
        assertEquals(0f,m.energy); assertFalse(m.needsFrame(MicroOptions(),false))
        m.beginDrag(.5f,0xffaabbcc); m.advance(20f,1f,profile,MicroOptions(reducedMotion=true))
        assertEquals(0,m.trail.count); assertEquals(0f,m.energy)
        m.advance(20f,1f,profile,MicroOptions(active=false)); assertEquals(0f,m.engagement)
        assertFalse(m.dragging)
        val revision=m.revision
        repeat(10) { m.advance(16f,0f,profile) }
        assertEquals(revision,m.revision,"Settled/silent frames must not invalidate drawing")
    }
    @Test fun everyFamilyIsSubtleAndTheTimelineUsesFixedGradientCoordinates() {
        for(f in ArtFamily.entries) {
            val r=baselineRecipe("micro-profile").copy(family=f,familyWeights=mapOf(f to 1f))
            val p=MicroProfile.from(r)
            assertTrue(p.audioGain in .2f..1f && p.glowSpreadDp in 0f..6f)
            assertTrue(1.5f*p.audioGain<=1.5f && .12f*p.audioGain<=.12f && 1+.02f*p.audioGain<=1.02f)
        }
        val r=baselineRecipe("gradient").copy(progress=baselineRecipe("gradient").progress.copy(fill=listOf(Stop(0f,0xffff0000),Stop(.5f,0xff00ff00),Stop(1f,0xff0000ff))))
        assertEquals(0xffff0000,progressColorAt(r,0f)); assertEquals(0xff00ff00,progressColorAt(r,.5f)); assertEquals(0xff0000ff,progressColorAt(r,1f))
        val mixed=MicroProfile.from(r.copy(familyWeights=mapOf(ArtFamily.PRISMATIC to .5f,ArtFamily.MINIMAL_MONOCHROME to .5f)))
        assertEquals(.6f,mixed.audioGain,.0001f)
    }
}
