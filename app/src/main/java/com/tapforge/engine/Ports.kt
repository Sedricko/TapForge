package com.tapforge.engine

import com.tapforge.model.Action
import com.tapforge.model.GlobalKind
import com.tapforge.model.Region
import com.tapforge.model.Stroke

/** Portas do motor: implementações Android reais ou falsas nos testes. */
interface GestureDispatcher {
    /** Até 10 dedos simultâneos; um toque é um traço de um ponto só. */
    suspend fun gesture(strokes: List<Stroke>): Boolean
    fun global(kind: GlobalKind): Boolean
}

data class Match(val x: Int, val y: Int, val score: Float, val w: Int = 0, val h: Int = 0)

interface Vision {
    /** false quando a captura de tela está desligada. */
    val available: Boolean get() = true
    /** Ocorrências do template (centro), melhores primeiro; vazia se nenhuma passar do limiar. */
    suspend fun find(templateId: String, threshold: Float, region: Region?, max: Int = 1, scaleTolerant: Boolean = false): List<Match>
    suspend fun readText(region: Region): String
    /** Cor ARGB do pixel; null sem captura. */
    suspend fun pixel(x: Int, y: Int): Int? = null
    /** Miniatura em cinza da região, para detectar mudança; null sem captura. */
    suspend fun snapshot(region: Region?): IntArray? = null
}

/** Recursos do aparelho fora de gestos e visão. */
interface Device {
    fun screenSize(): Pair<Int, Int> = 0 to 0
    fun foregroundPackage(): String? = null
    fun openApp(packageName: String): Boolean = false
    fun typeText(text: String, append: Boolean): Boolean = false
    fun setClipboard(text: String) {}
    fun alert(message: String, vibrateMs: Long, sound: Boolean) {}
}

object NoDevice : Device

sealed interface EngineEvent {
    data class Started(val macroId: String) : EngineEvent
    data class Iteration(val n: Int) : EngineEvent
    data class Finished(val reason: String) : EngineEvent
    data class Failed(val message: String) : EngineEvent
    data class Step(val action: Action) : EngineEvent
    data class Log(val message: String) : EngineEvent
    data class Touched(val x: Int, val y: Int) : EngineEvent
    data class Found(val match: Match) : EngineEvent
}
