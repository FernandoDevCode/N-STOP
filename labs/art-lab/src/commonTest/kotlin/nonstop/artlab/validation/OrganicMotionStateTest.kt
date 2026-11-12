package nonstop.artlab.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import nonstop.artlab.renderer.MotionInfluence
import nonstop.artlab.renderer.OrganicMotionState

class OrganicMotionStateTest {
    @Test fun blendsFamiliesWithoutHardSwitchOrEnergyDrivenPulse() {
        val state = OrganicMotionState()
        val starting = state.ethereal
        state.step(16f, MotionInfluence(.1f,.1f,.8f,.9f,.8f,true))
        assertTrue(state.ethereal < starting)
        assertTrue(state.ethereal > .1f, "No abrupt family switch")
        repeat(100) { state.step(16f, MotionInfluence(.1f,.1f,.8f,.1f,.1f,true)) }
        assertTrue(state.tense > state.elastic)
        assertTrue(state.tense > .08f)
        assertTrue(state.phase > 0f)
        val before = state.revision
        state.step(Float.NaN, MotionInfluence.CALM)
        assertEquals(before, state.revision)
    }

    @Test fun absentPcmRecoversToNeutralRatherThanFabricatingTransient() {
        val state = OrganicMotionState()
        repeat(120) { state.step(16f, MotionInfluence(.05f,.05f,.9f,1f,1f,true)) }
        val peak = state.agitation
        repeat(240) { state.step(16f, MotionInfluence.CALM) }
        assertTrue(state.agitation < peak)
        assertTrue(state.ethereal > state.tense)
    }
}
