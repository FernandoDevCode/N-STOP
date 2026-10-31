package nonstop.artlab

import java.io.File
import javax.sound.sampled.*
import java.io.ByteArrayInputStream
import kotlin.math.*
import kotlin.test.*

class DesktopAudioEnergyTest {
    @Test fun localWavFeedsMeasuredEnergyAndRejectsInvalidInput() {
        val folder=File("build/test-audio").apply { mkdirs() }
        val bytes=ByteArray(16000*2)
        repeat(16000) { i -> val value=(sin(2*PI*220*i/16000)*(if(i<8000)0 else 16000)).toInt()
            bytes[i*2]=value.toByte(); bytes[i*2+1]=(value shr 8).toByte() }
        val file=File(folder,"energy.wav")
        val format=AudioFormat(16000f,16,1,true,false)
        AudioInputStream(ByteArrayInputStream(bytes),format,16000).use { AudioSystem.write(it,AudioFileFormat.Type.WAVE,file) }
        val audio=DesktopAudioEnergy.load(file)
        assertEquals(0f,audio.envelope.energyAt(200.0))
        assertTrue(audio.envelope.energyAt(750.0)>.9f)
        assertTrue(audio.analysisMs>=0 && audio.envelope.size==50)
        val broken=File(folder,"broken.wav").apply { writeText("invalid") }
        assertFails { DesktopAudioEnergy.load(broken) }
        assertFails { DesktopAudioEnergy.load(File(folder,"not-a-wave.mp3")) }
    }
}
