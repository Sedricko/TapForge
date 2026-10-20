package com.tapforge.engine

import com.tapforge.model.GlobalKind
import com.tapforge.model.Region

/** Portas do motor: implementações Android reais ou falsas nos testes. */
interface GestureDispatcher {
    suspend fun tap(x: Int, y: Int, holdMs: Long): Boolean
    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): Boolean
    fun global(kind: GlobalKind): Boolean
}

data class Match(val x: Int, val y: Int, val score: Float)

interface Vision {
    /** Procura o template na tela atual; null se não achar acima do limiar. */
    suspend fun findImage(templateId: String, threshold: Float, region: Region?): Match?
    suspend fun readText(region: Region): String
}

sealed interface EngineEvent {
    data class Started(val macroId: String) : EngineEvent
    data class Iteration(val n: Int) : EngineEvent
    data class Finished(val reason: String) : EngineEvent
    data class Failed(val message: String) : EngineEvent
}
