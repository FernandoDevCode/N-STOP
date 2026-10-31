package com.stash.desktop

import com.stash.data.ytmusic.model.SearchResultSection
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicLong
import javax.sound.sampled.*

/** Read-only service probe; no playback device, credentials or signed URLs in output. */
internal fun probeCatalog() = runBlocking {
    val result = DesktopCatalog().search("Megadeth Holy Wars")
    println("Busca pública: ${result.sections.size} seções")
    val song = result.sections.filterIsInstance<SearchResultSection.Songs>().flatMap { it.tracks }.firstOrNull()
        ?: error("A busca respondeu sem uma seção de músicas verificável.")
    println("Parser original retornou faixa YouTube; resolvendo pelo backend real")
    val frames = AtomicLong()
    val silentLine = Proxy.newProxyInstance(DesktopAudio::class.java.classLoader, arrayOf(SourceDataLine::class.java)) { _, method, args ->
        when (method.name) {
            "write" -> { val size = args!![2] as Int; frames.addAndGet(size / 4L); Thread.sleep(5); size }
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
    val audio = DesktopAudio { silentLine }
    try {
        audio.setQueue(listOf(song.model()))
        withTimeout(90000) {
            while (frames.get() < 48000) {
                check(audio.error.value == null) { audio.error.value.orEmpty() }
                delay(20)
            }
        }
        check(audio.state.value.durationMs > 0) { "O resolvedor não recuperou a duração da faixa" }
        println("Backend Desktop: mais de 1s de PCM online, duração recuperada, sem saída sonora")
        audio.pause()
        check(!audio.state.value.playWhenReady)
        println("Pausa interrompeu o backend; nenhum endereço de streaming foi impresso")
    } finally { audio.close() }
}
