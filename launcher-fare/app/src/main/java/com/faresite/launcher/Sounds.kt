package com.faresite.launcher

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Sonidos de sistema del launcher, sintetizados al abrir (no hay archivos de audio):
 * tick suave al mover el carrusel, clic de vidrio al tocar, tecla al buscar y un barrido al cambiar de pantalla.
 */
object Sfx {
    private const val RATE = 44100
    private var pool: SoundPool? = null
    private val ids = IntArray(5)
    var enabled = true

    const val TICK = 0
    const val TAP = 1
    const val KEY = 2
    const val SWIPE = 3
    const val OPEN = 4

    fun init(ctx: Context) {
        if (pool != null) return
        val p = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            ).build()
        val sounds = listOf(tick(), tap(), key(), swipe(), open())
        sounds.forEachIndexed { i, pcm ->
            val f = File(ctx.cacheDir, "sfx$i.wav")
            runCatching { writeWav(f, pcm); ids[i] = p.load(f.path, 1) }
        }
        pool = p
    }

    fun play(which: Int, volume: Float = .5f) {
        if (!enabled) return
        val id = ids.getOrNull(which) ?: return
        if (id != 0) pool?.play(id, volume, volume, 1, 0, 1f)
    }

    // --- Síntesis ---

    private fun buf(seconds: Double) = FloatArray((RATE * seconds).toInt())

    /** Tick de cristal: dos parciales agudos que se apagan en 25 ms. */
    private fun tick() = buf(.035).also { b ->
        for (i in b.indices) {
            val t = i.toDouble() / RATE
            b[i] = ((sin(2 * PI * 2600 * t) * .6 + sin(2 * PI * 4100 * t) * .3) * exp(-t * 190)).toFloat()
        }
    }

    /** Clic de vidrio: golpe corto con un tono que baja. */
    private fun tap() = buf(.09).also { b ->
        var ph = 0.0
        for (i in b.indices) {
            val t = i.toDouble() / RATE
            val f = 1400 - 700 * (t / .09)
            ph += 2 * PI * f / RATE
            b[i] = ((sin(ph) * .7 + sin(ph * 2.01) * .2) * exp(-t * 55) + (Random.nextDouble() - .5) * .25 * exp(-t * 900)).toFloat()
        }
    }

    /** Tecla: chasquido muy corto y seco. */
    private fun key() = buf(.02).also { b ->
        for (i in b.indices) {
            val t = i.toDouble() / RATE
            b[i] = ((Random.nextDouble() - .5) * exp(-t * 600) * .8 + sin(2 * PI * 1800 * t) * exp(-t * 400) * .3).toFloat()
        }
    }

    /** Barrido de aire suave al cambiar de pantalla. */
    private fun swipe() = buf(.22).also { b ->
        var lp = 0.0
        for (i in b.indices) {
            val t = i.toDouble() / RATE
            val env = sin(PI * t / .22)
            val a = .04 + .25 * (t / .22)
            lp += a * ((Random.nextDouble() - .5) - lp)
            b[i] = (lp * env * 2.2).toFloat()
        }
    }

    /** Apertura de panel: acorde corto que sube. */
    private fun open() = buf(.25).also { b ->
        for (i in b.indices) {
            val t = i.toDouble() / RATE
            val env = (1 - exp(-t * 80)) * exp(-t * 14)
            b[i] = ((sin(2 * PI * 660 * t) + sin(2 * PI * 990 * t) * .6 + sin(2 * PI * 1320 * t * (1 + t * .3)) * .3) * env * .45).toFloat()
        }
    }

    private fun writeWav(f: File, pcm: FloatArray) {
        val data = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        pcm.forEach { data.putShort((it.coerceIn(-1f, 1f) * 32767 * .8f).toInt().toShort()) }
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()).putInt(36 + pcm.size * 2).put("WAVE".toByteArray())
        h.put("fmt ".toByteArray()).putInt(16).putShort(1.toShort()).putShort(1.toShort()).putInt(RATE).putInt(RATE * 2)
            .putShort(2.toShort()).putShort(16.toShort())
        h.put("data".toByteArray()).putInt(pcm.size * 2)
        FileOutputStream(f).use { it.write(h.array()); it.write(data.array()) }
    }
}
