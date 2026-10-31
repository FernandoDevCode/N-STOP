package com.stash.desktop

import com.stash.core.model.Track
import kotlinx.coroutines.*
import org.junit.*
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicLong
import javax.sound.sampled.*
import kotlin.math.sin

class DesktopAudioTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun ffmpegDecodesWavToPcmAndTransportAdvancesWithoutUsingSpeakers() = runBlocking {
        val file = folder.newFile("tone.wav")
        val pcm = ByteArray(48000 * 4 / 4)
        for (i in 0 until pcm.size / 4) {
            val sample = (sin(i * 2 * Math.PI * 440 / 48000) * 10000).toInt()
            for (channel in 0..1) { pcm[i * 4 + channel * 2] = sample.toByte(); pcm[i * 4 + channel * 2 + 1] = (sample shr 8).toByte() }
        }
        AudioInputStream(ByteArrayInputStream(pcm), AudioFormat(48000f, 16, 2, true, false), (pcm.size / 4).toLong()).use {
            AudioSystem.write(it, AudioFileFormat.Type.WAVE, file)
        }
        val count = AtomicLong()
        val frames = AtomicLong()
        val sink = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SourceDataLine::class.java)) { _, method, args ->
            when (method.name) {
                "write" -> { val size = args!![2] as Int; count.addAndGet(size.toLong()); frames.addAndGet(size / 4L); size }
                "getLongFramePosition" -> frames.get()
                "getFramePosition" -> frames.get().toInt()
                "isOpen", "isActive", "isRunning" -> true
                "getFormat" -> AudioFormat(48000f, 16, 2, true, false)
                "getControls" -> emptyArray<Control>()
                "available", "getBufferSize" -> 19200
                "getMicrosecondPosition" -> frames.get() * 1000000 / 48000
                "getLevel" -> 0f
                else -> null
            }
        } as SourceDataLine
        val audio = DesktopAudio { sink }
        try {
            audio.setQueue(listOf(Track(id = 1, title = "Teste", artist = "Fixture PCM", filePath = file.absolutePath, durationMs = 250)))
            withTimeout(15000) { while (audio.state.value.playWhenReady) delay(20) }
            Assert.assertNull(audio.error.value)
            Assert.assertEquals(pcm.size.toLong(), count.get())
            Assert.assertEquals(250L, audio.state.value.positionMs)
            Assert.assertFalse(audio.state.value.isPlaying)
            audio.seek(100)
            Assert.assertEquals(100L, audio.state.value.positionMs)
            Assert.assertFalse(audio.state.value.playWhenReady)
        } finally { audio.close() }
    }
}
