package nonstop.artlab

import nonstop.artlab.renderer.*
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.math.*

data class LabAudio(val name:String,val source:String,val envelope:PcmEnergyTimeline,val analysisMs:Double)

/** Stream-decodes local WAV to bounded RMS data. No audio device, network or playback service. */
object DesktopAudioEnergy {
    fun load(file:File):LabAudio {
        val start=System.nanoTime()
        require(file.extension.equals("wav",true)) { "Use WAV PCM local" }
        require(file.length() in 1..256L*1024*1024) { "WAV vazio ou maior que 256 MiB" }
        val result=AudioSystem.getAudioInputStream(file).use { source ->
            val format=source.format
            require(format.channels in 1..8 && format.sampleRate in 8000f..192000f) { "Formato WAV não suportado" }
            val pcm=AudioFormat(AudioFormat.Encoding.PCM_SIGNED,format.sampleRate,16,format.channels,format.channels*2,format.sampleRate,false)
            AudioSystem.getAudioInputStream(pcm,source).use { decoded ->
                val accumulator=PcmRmsAccumulator(pcm.channels,pcm.sampleRate.toDouble())
                val frame=FloatArray(pcm.channels)
                val buffer=ByteArray(pcm.frameSize*2048)
                var frames=0L
                while(true) {
                    val count=decoded.read(buffer); if(count<0)break
                    if(count==0)continue
                    require(count%pcm.frameSize==0) { "Frame PCM incompleto" }
                    for(offset in 0 until count step pcm.frameSize) {
                        for(channel in frame.indices) {
                            val i=offset+channel*2
                            val signed=((buffer[i].toInt() and 255) or (buffer[i+1].toInt() shl 8)).toShort()
                            frame[channel]=signed/32768f
                        }
                        accumulator.addFrame(frame); frames++
                        require(frames<=pcm.sampleRate*1800) { "Limite de 30 minutos para o envelope do laboratório" }
                    }
                }
                accumulator.finish()
            }
        }
        return LabAudio(file.name,"WAV local — prévia visual silenciosa, não associado ao álbum",result,(System.nanoTime()-start)/1e6)
    }

    /** Actual PCM amplitude fixture, not a UI sine pulse pretending to be album audio. */
    fun fixture():LabAudio {
        val start=System.nanoTime(); val rate=16000
        val accumulator=PcmRmsAccumulator(1,rate.toDouble()); val frame=FloatArray(1)
        repeat(rate*8) { i ->
            val t=i.toDouble()/rate
            val amplitude=when { t<1 -> 0.0; t<3 -> .12; t<5 -> .55; t<6 -> .035; else -> 0.0 }
            frame[0]=(amplitude*(.75*sin(2*PI*220*t)+.25*sin(2*PI*440*t))).toFloat()
            accumulator.addFrame(frame)
        }
        return LabAudio("PCM de teste — 8 s","Fixture sintetizada para testar RMS; sem áudio das músicas",accumulator.finish(),(System.nanoTime()-start)/1e6)
    }
}
