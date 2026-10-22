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
 * gota de marimba al mover el carrusel, tecla al escribir y soplo al cambiar de pantalla.
 * Los toques y la apertura de paneles usan el bip de vidrio de res/raw/ui_glass.ogg.
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
        // Sonido de interfaz elegido por Juan (bip de vidrio): toques y apertura de paneles
        runCatching {
            val glass = p.load(ctx, R.raw.ui_glass, 1)
            ids[TAP] = glass; ids[OPEN] = glass
        }
        pool = p
    }

    /** Tecla al escribir en cualquier cuadro de texto del launcher. */
    fun typed(old: String, new: String) {
        if (old != new) play(KEY, .35f)
    }

    fun play(which: Int, volume: Float = .5f) {
        if (enabled) raw(which, volume)
    }

    /** La tecla de escribir solo suena al escribir; el resto son sonidos suaves propios. */
    fun raw(which: Int, volume: Float = .5f) {
        val id = ids.getOrElse(which) { 0 }
        if (id != 0) pool?.play(id, volume, volume, 1, 0, 1f)
    }

    // --- Síntesis (sonidos suaves: ataque lento para que no "piquen" y caída natural) ---

    private fun buf(seconds: Double) = FloatArray((RATE * seconds).toInt())

    /** Ataque corto y suave (sin chasquido). */
    private fun attack(t: Double, ms: Double) = minOf(1.0, t / (ms / 1000))

    /** Tick de carrusel: gota de marimba muy corta y grave-media. */
    private fun tick() = buf(.09).also { b ->
        for (i in b.indices) {
            val t = i.toDouble() / RATE
            val env = attack(t, 2.0) * exp(-t * 55)
            b[i] = ((sin(2 * PI * 1046.5 * t) * .7 + sin(2 * PI * 2093 * t) * .12 + sin(2 * PI * 4 * 1046.5 * t) * .03 * exp(-t * 200)) * env * .55).toFloat()
        }
    }

    /** Toque: burbuja suave, un tono redondo que baja un poco. */
    private fun tap() = buf(.12).also { b ->
        var ph = 0.0
        for (i in b.indices) {
            val t = i.toDouble() / RATE
            val f = 520 + 260 * exp(-t * 40)
            ph += 2 * PI * f / RATE
            val env = attack(t, 3.0) * exp(-t * 32)
            b[i] = ((sin(ph) * .8 + sin(ph * 2) * .08) * env * .6).toFloat()
        }
    }

    /** Tecla de escribir: chasquido corto y seco (se conserva). */
    private fun key() = buf(.02).also { b ->
        for (i in b.indices) {
            val t = i.toDouble() / RATE
            b[i] = ((Random.nextDouble() - .5) * exp(-t * 600) * .8 + sin(2 * PI * 1800 * t) * exp(-t * 400) * .3).toFloat()
        }
    }

    /** Cambio de pantalla: soplo de aire muy suave y grave. */
    private fun swipe() = buf(.3).also { b ->
        var lp = 0.0
        var lp2 = 0.0
        for (i in b.indices) {
            val t = i.toDouble() / RATE
            val env = sin(PI * t / .3).let { it * it }
            val a = .02 + .08 * (t / .3)
            lp += a * ((Random.nextDouble() - .5) - lp)
            lp2 += a * (lp - lp2)
            b[i] = (lp2 * env * 5.5).toFloat()
        }
    }

    /** Apertura de panel: campanita de dos notas (Do y Mi), suave y luminosa. */
    private fun open() = buf(.6).also { b ->
        for (i in b.indices) {
            val t = i.toDouble() / RATE
            val e1 = attack(t, 4.0) * exp(-t * 7)
            val t2 = t - .07
            val e2 = if (t2 > 0) attack(t2, 4.0) * exp(-t2 * 7) else 0.0
            val n1 = sin(2 * PI * 784 * t) + sin(2 * PI * 1568 * t) * .15
            val n2 = if (t2 > 0) sin(2 * PI * 987.8 * t2) + sin(2 * PI * 1975.5 * t2) * .15 else 0.0
            b[i] = ((n1 * e1 + n2 * e2) * .3).toFloat()
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
