package com.stash.desktop

import com.stash.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.TimeUnit
import javax.sound.sampled.*

/** Real PCM playback. FFmpeg decodes; Windows' audio device clocks the transport. */
class DesktopAudio(private val openLine: () -> SourceDataLine = {
    AudioSystem.getSourceDataLine(AudioFormat(48000f, 16, 2, true, false))
}) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutable = MutableStateFlow(PlayerState())
    val state = mutable.asStateFlow()
    val error = MutableStateFlow<String?>(null)
    var resolveRemoteTrack: suspend (Track) -> Track = { it }
    @Volatile var volume: Float = 1f
        set(value) { field = value.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f }
    private var job: Job? = null
    private var decoder: Process? = null
    private var line: SourceDataLine? = null
    private var generation = 0L
    private val resolved = mutableMapOf<String, Triple<Long, String, Long>>()

    @Synchronized fun setQueue(tracks: List<Track>, index: Int = 0, startPlaying: Boolean = true) {
        require(index in tracks.indices)
        stopWorker()
        mutable.value = mutable.value.copy(queue = tracks, currentIndex = index,
            currentTrack = tracks[index], durationMs = tracks[index].durationMs, positionMs = 0,
            isPlaying = false, isBuffering = false, isSeekable = true)
        if (startPlaying) play()
    }
    @Synchronized fun play() {
        val snapshot = mutable.value
        val track = snapshot.currentTrack ?: return
        stopWorker()
        val ticket = generation
        error.value = null
        mutable.value = snapshot.copy(isBuffering = true, playWhenReady = true)
        job = scope.launch {
            var localDecoder: Process? = null
            var localLine: SourceDataLine? = null
            try {
                val localFile = track.filePath?.takeIf { File(it).isFile }
                val playable = if (localFile == null && track.youtubeId == null) resolveRemoteTrack(track) else track
                synchronized(this@DesktopAudio) {
                    if (ticket != generation) throw CancellationException()
                    mutable.value = mutable.value.copy(currentTrack = playable,
                        queue = mutable.value.queue.mapIndexed { index, item -> if (index == snapshot.currentIndex) playable else item })
                }
                val input = localFile ?: resolve(playable, ticket)
                ensureActive()
                val args = listOf("ffmpeg", "-nostdin", "-loglevel", "error", "-ss",
                    (snapshot.positionMs / 1000.0).toString(), "-i", input,
                    "-vn", "-f", "s16le", "-acodec", "pcm_s16le", "-ar", "48000", "-ac", "2", "pipe:1")
                localDecoder = ProcessBuilder(args).redirectError(ProcessBuilder.Redirect.DISCARD).start()
                localLine = openLine()
                localLine.open(AudioFormat(48000f, 16, 2, true, false), 19200)
                synchronized(this@DesktopAudio) {
                    if (ticket != generation) throw CancellationException()
                    decoder = localDecoder; line = localLine
                    mutable.value = mutable.value.copy(isPlaying = true, isBuffering = false, playWhenReady = true)
                }
                localLine.start()
                val data = ByteArray(9600)
                var frames = 0L
                while (isActive) {
                    val size = localDecoder.inputStream.readNBytes(data, 0, data.size)
                    if (size == 0) break
                    val gain = volume
                    if (gain < 1f) {
                        for (i in 0 until size - 1 step 2) {
                            val sample = ((data[i].toInt() and 255) or (data[i + 1].toInt() shl 8)).toShort()
                            val adjusted = (sample * gain).toInt()
                            data[i] = adjusted.toByte(); data[i + 1] = (adjusted shr 8).toByte()
                        }
                    }
                    var offset = 0
                    while (offset < size && isActive) offset += localLine.write(data, offset, size - offset)
                    frames += size / 4
                    synchronized(this@DesktopAudio) {
                        if (ticket != generation) throw CancellationException()
                        // Device frame position excludes buffered audio that has not reached the speaker yet.
                        val position = snapshot.positionMs + localLine.longFramePosition * 1000 / 48000
                        mutable.value = mutable.value.copy(positionMs = position)
                    }
                }
                ensureActive()
                check(frames > 0) { "FFmpeg não conseguiu decodificar o áudio. Verifique o arquivo ou a disponibilidade da faixa." }
                localLine.drain()
                synchronized(this@DesktopAudio) {
                    if (ticket == generation) {
                        mutable.value = mutable.value.copy(isPlaying = false, playWhenReady = false, isBuffering = false)
                        advanceAtEnd()
                    }
                }
            } catch (_: CancellationException) {
            } catch (failure: Exception) {
                synchronized(this@DesktopAudio) {
                    if (ticket == generation) {
                        error.value = failure.message ?: "Não foi possível reproduzir esta faixa."
                        mutable.value = mutable.value.copy(isPlaying = false, isBuffering = false, playWhenReady = false)
                    }
                }
            } finally {
                localDecoder?.destroyForcibly()
                localLine?.runCatching { stop(); flush(); close() }
            }
        }
    }
    private fun resolve(track: Track, ticket: Long): String {
        val id = track.youtubeId ?: error("O arquivo do celular não está neste PC e esta faixa não tem uma origem YouTube para resolver.")
        require(Regex("[A-Za-z0-9_-]{11}").matches(id)) { "Identificador YouTube inválido." }
        synchronized(this) {
            resolved[id]?.takeIf { System.currentTimeMillis() - it.first < 300000 }?.let {
                if (ticket != generation) throw CancellationException()
                if (mutable.value.durationMs <= 0 && it.third > 0) mutable.value = mutable.value.copy(durationMs = it.third,
                    currentTrack = mutable.value.currentTrack?.copy(durationMs = it.third))
                return it.second
            }
        }
        val process = ProcessBuilder("yt-dlp", "--no-playlist", "--no-warnings", "--socket-timeout", "15",
            "-f", "bestaudio/best", "--print", "%(url)s", "--print", "%(duration)s", "https://www.youtube.com/watch?v=$id")
            .redirectError(ProcessBuilder.Redirect.DISCARD).start()
        synchronized(this) { if (ticket != generation) { process.destroyForcibly(); throw CancellationException() }; decoder = process }
        check(process.waitFor(60, TimeUnit.SECONDS)) { process.destroyForcibly(); "A resolução da faixa excedeu 60 segundos." }
        val output = process.inputStream.bufferedReader().readLines()
        val url = output.firstOrNull { it.startsWith("https://") }
        val duration = ((output.firstNotNullOfOrNull { it.toDoubleOrNull() } ?: 0.0) * 1000).toLong()
        check(process.exitValue() == 0 && url != null) { "Não foi possível resolver esta faixa no YouTube. Atualize yt-dlp ou tente outra faixa." }
        synchronized(this) {
            if (ticket != generation) throw CancellationException()
            resolved[id] = Triple(System.currentTimeMillis(), url, duration)
            if (mutable.value.durationMs <= 0 && duration > 0) mutable.value = mutable.value.copy(durationMs = duration,
                currentTrack = mutable.value.currentTrack?.copy(durationMs = duration))
        }
        return url
    }
    @Synchronized fun pause() {
        stopWorker()
        mutable.value = mutable.value.copy(isPlaying = false, isBuffering = false, playWhenReady = false)
    }
    @Synchronized fun toggle() { if (mutable.value.playWhenReady) pause() else play() }
    @Synchronized fun seek(ms: Long) {
        val resume = mutable.value.playWhenReady
        stopWorker()
        mutable.value = mutable.value.copy(positionMs = ms.coerceIn(0, mutable.value.durationMs.coerceAtLeast(0)),
            isPlaying = false, isBuffering = false, playWhenReady = false)
        if (resume) play()
    }
    @Synchronized fun next() {
        val snapshot = mutable.value
        if (snapshot.queue.isEmpty()) return
        if (!snapshot.isShuffleEnabled && snapshot.currentIndex == snapshot.queue.lastIndex && snapshot.repeatMode != RepeatMode.ALL) return
        val index = if (snapshot.isShuffleEnabled && snapshot.queue.size > 1)
            snapshot.queue.indices.filter { it != snapshot.currentIndex }.random()
        else (snapshot.currentIndex + 1) % snapshot.queue.size
        setQueue(snapshot.queue, index)
    }
    @Synchronized fun previous() {
        if (mutable.value.positionMs > 3000) { seek(0); return }
        val snapshot = mutable.value
        if (snapshot.queue.isNotEmpty()) setQueue(snapshot.queue, (snapshot.currentIndex - 1).coerceAtLeast(0))
    }
    @Synchronized fun shuffle() { mutable.value = mutable.value.copy(isShuffleEnabled = !mutable.value.isShuffleEnabled) }
    @Synchronized fun repeat() {
        mutable.value = mutable.value.copy(repeatMode = RepeatMode.entries[(mutable.value.repeatMode.ordinal + 1) % RepeatMode.entries.size])
    }
    private fun advanceAtEnd() {
        val snapshot = mutable.value
        when {
            snapshot.repeatMode == RepeatMode.ONE -> { mutable.value = snapshot.copy(positionMs = 0); play() }
            snapshot.currentIndex < snapshot.queue.lastIndex || snapshot.repeatMode == RepeatMode.ALL || snapshot.isShuffleEnabled -> next()
        }
    }
    private fun stopWorker() {
        generation++
        job?.cancel(); decoder?.destroyForcibly()
        line?.runCatching { stop(); flush(); close() }
        decoder = null; line = null
    }
    @Synchronized override fun close() { stopWorker(); scope.cancel() }
}
