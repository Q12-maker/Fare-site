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
 * Sonidos de sistema del launcher: notas de piano sintetizadas al abrir (no hay archivos de audio).
 * Tocar = Do, carrusel = Sol corto, cambiar de pantalla = Do grave, abrir paneles = arpegio Do-Mi-Sol,
 * escribir = una nota al azar de la escala pentatónica (siempre suena bonito).
 */
object Sfx {
    private const val RATE = 44100
    private var pool: SoundPool? = null
    private val ids = IntArray(5)
    private val keyIds = IntArray(6)
    var enabled = true

    const val TICK = 0
    const val TAP = 1
    const val KEY = 2
    const val SWIPE = 3
    const val OPEN = 4

    // Frecuencias de las notas (Hz)
    private const val C4 = 261.63
    private const val C5 = 523.25
    private const val D5 = 587.33
    private const val E5 = 659.26
    private const val G5 = 783.99
    private const val A5 = 880.0
    private const val C6 = 1046.5

    fun init(ctx: Context) {
        if (pool != null) return
        val p = SoundPool.Builder()
            .setMaxStreams(6)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            ).build()
        fun load(name: String, pcm: FloatArray): Int {
            val f = File(ctx.cacheDir, "piano_$name.wav")
            return runCatching { writeWav(f, pcm); p.load(f.path, 1) }.getOrDefault(0)
        }
        ids[TICK] = load("tick", piano(G5, .35, soft = .6))
        ids[TAP] = load("tap", piano(C5, .7))
        ids[SWIPE] = load("swipe", piano(C4, .9, soft = .7))
        ids[OPEN] = load("open", chord(listOf(C5, E5, G5), .07, 1.1))
        listOf(C5, D5, E5, G5, A5, C6).forEachIndexed { i, f -> keyIds[i] = load("key$i", piano(f, .4, soft = .55)) }
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
        val id = if (which == KEY) keyIds[Random.nextInt(keyIds.size)] else ids.getOrElse(which) { 0 }
        if (id != 0) pool?.play(id, volume, volume, 1, 0, 1f)
    }

    // --- Síntesis de piano: armónicos con leve desafinación (como cuerdas reales), cada uno se apaga
    // más rápido cuanto más agudo, y un golpecito de martillo al inicio ---

    private fun buf(seconds: Double) = FloatArray((RATE * seconds).toInt())

    private fun pianoSample(f: Double, t: Double, len: Double, soft: Double): Double {
        if (t < 0) return 0.0
        val attack = minOf(1.0, t / .003)
        var v = 0.0
        val amps = doubleArrayOf(1.0, .55, .28, .16, .09, .05, .03)
        for (h in amps.indices) {
            val n = h + 1
            val fn = f * n * kotlin.math.sqrt(1 + .0004 * n * n)
            val decay = exp(-t * (2.2 + n * 1.6) * (1.6 / len))
            v += amps[h] * sin(2 * PI * fn * t) * decay
        }
        // Dos cuerdas por nota, un poquito desafinadas: da el "brillo" del piano
        v += .35 * sin(2 * PI * f * 1.0015 * t) * exp(-t * 3.0 * (1.6 / len))
        val hammer = (Random.nextDouble() - .5) * exp(-t * 900) * .25
        // Al final se apaga suave para que no corte
        val tail = minOf(1.0, (len - t) / .06).coerceAtLeast(0.0)
        return (v * soft * .32 + hammer * soft) * attack * tail
    }

    private fun piano(f: Double, len: Double, soft: Double = 1.0) = buf(len).also { b ->
        for (i in b.indices) b[i] = pianoSample(f, i.toDouble() / RATE, len, soft).toFloat()
    }

    /** Arpegio: las notas entran una tras otra y siguen sonando juntas. */
    private fun chord(notes: List<Double>, gap: Double, len: Double) = buf(len).also { b ->
        for (i in b.indices) {
            val t = i.toDouble() / RATE
            var v = 0.0
            notes.forEachIndexed { k, f -> v += pianoSample(f, t - k * gap, len - k * gap, .4) }
            b[i] = v.toFloat()
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
