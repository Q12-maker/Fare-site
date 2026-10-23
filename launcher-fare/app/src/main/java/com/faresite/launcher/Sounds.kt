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
 * Sonidos de sistema del launcher, estilo futurista ciberpunk, sintetizados al abrir (no hay archivos de audio):
 * tocar = blip digital, carrusel = clic de datos, cambiar de pantalla = barrido de filtro,
 * abrir paneles = trino de datos que sube con eco. Escribir conserva su chasquido de tecla.
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
            .setMaxStreams(6)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            ).build()
        fun load(name: String, pcm: FloatArray): Int {
            val f = File(ctx.cacheDir, "cyber_$name.wav")
            return runCatching { writeWav(f, pcm); p.load(f.path, 1) }.getOrDefault(0)
        }
        ids[TICK] = load("tick", tick())
        ids[TAP] = load("tap", tap())
        ids[KEY] = load("key", key())
        ids[SWIPE] = load("swipe", swipe())
        ids[OPEN] = load("open", open())
        pool = p
    }

    /** Tecla al escribir en cualquier cuadro de texto del launcher. */
    fun typed(old: String, new: String) {
        if (old != new) play(KEY, .35f)
    }

    fun play(which: Int, volume: Float = .5f) {
        if (enabled) raw(which, volume)
    }

    fun raw(which: Int, volume: Float = .5f) {
        val id = ids.getOrElse(which) { 0 }
        if (id != 0) pool?.play(id, volume, volume, 1, 0, 1f)
    }

    // --- Síntesis ciberpunk: ondas cuadradas suavizadas, FM, reducción de bits y barridos de filtro ---

    private fun buf(seconds: Double) = FloatArray((RATE * seconds).toInt())

    /** Onda cuadrada con bordes suaves (menos áspera que una cuadrada pura). */
    private fun sq(ph: Double) = kotlin.math.tanh(sin(ph) * 4) * .8

    /** Reduce la resolución como un chip digital viejo. */
    private fun crush(v: Double, levels: Double = 24.0) = kotlin.math.round(v * levels) / levels

    private fun fadeIn(t: Double, ms: Double) = minOf(1.0, t / (ms / 1000))

    /** Tecla de escribir: chasquido corto y seco (se conserva). */
    private fun key() = buf(.02).also { b ->
        for (i in b.indices) {
            val t = i.toDouble() / RATE
            b[i] = ((Random.nextDouble() - .5) * exp(-t * 600) * .8 + sin(2 * PI * 1800 * t) * exp(-t * 400) * .3).toFloat()
        }
    }

    /** Carrusel: clic de datos muy corto y agudo. */
    private fun tick() = buf(.035).also { b ->
        var ph = 0.0
        for (i in b.indices) {
            val t = i.toDouble() / RATE
            ph += 2 * PI * (2600 - 900 * t / .035) / RATE
            b[i] = (crush(sq(ph) * exp(-t * 110) * fadeIn(t, 1.0), 12.0) * .32).toFloat()
        }
    }

    /** Toque: blip FM que baja rápido, con un brillo metálico. */
    private fun tap() = buf(.09).also { b ->
        var ph = 0.0
        var mod = 0.0
        for (i in b.indices) {
            val t = i.toDouble() / RATE
            val f = 1400 * exp(-t * 18) + 700
            mod += 2 * PI * f * 2.01 / RATE
            ph += 2 * PI * f / RATE + sin(mod) * 1.4 * exp(-t * 40) * 2 * PI * f / RATE
            val env = fadeIn(t, 1.5) * exp(-t * 38)
            b[i] = (crush(sin(ph) * .7 + sq(ph * .5) * .25, 20.0) * env * .55).toFloat()
        }
    }

    /** Cambio de pantalla: ruido con filtro resonante que barre hacia arriba, más un zumbido de sierra. */
    private fun swipe() = buf(.26).also { b ->
        var low = 0.0
        var band = 0.0
        var ph = 0.0
        for (i in b.indices) {
            val t = i.toDouble() / RATE
            val x = t / .26
            val fc = 300 + 3200 * x * x
            val fq = 2 * sin(PI * fc / RATE)
            val n = Random.nextDouble() - .5
            low += fq * band
            band += fq * (n - low - .15 * band)
            ph += 2 * PI * (90 + 260 * x) / RATE
            val saw = ((ph / (2 * PI)) % 1.0) * 2 - 1
            val env = sin(PI * x).let { it * it }
            b[i] = ((band * 1.6 + saw * .08) * env * .5).toFloat()
        }
    }

    /** Abrir paneles: trino de datos de tres notas que sube, con eco digital. */
    private fun open() = buf(.42).also { b ->
        val notes = doubleArrayOf(880.0, 1318.5, 1760.0)
        val dry = DoubleArray(b.size)
        for (i in b.indices) {
            val t = i.toDouble() / RATE
            var v = 0.0
            notes.forEachIndexed { k, f ->
                val tk = t - k * .045
                if (tk >= 0) v += sq(2 * PI * f * tk) * fadeIn(tk, 1.5) * exp(-tk * 30) * .3
            }
            dry[i] = crush(v, 16.0)
        }
        val d = (RATE * .09).toInt()
        for (i in b.indices) {
            val e1 = if (i >= d) dry[i - d] * .35 else 0.0
            val e2 = if (i >= 2 * d) dry[i - 2 * d] * .14 else 0.0
            b[i] = ((dry[i] + e1 + e2) * .7).toFloat()
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
