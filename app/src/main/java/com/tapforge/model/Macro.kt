package com.tapforge.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Region(val x: Int, val y: Int, val w: Int, val h: Int)

/** Humanização opcional: variação aleatória de posição (px) e tempo (ms). */
@Serializable
data class Jitter(val posPx: Int = 0, val timeMs: Long = 0)

@Serializable
data class Macro(
    val id: String,
    val name: String,
    val version: Int = 1,
    val actions: List<Action>,
    /** Parada global: 0 = sem limite. */
    val maxRuntimeMs: Long = 0,
    val maxIterations: Int = 0,
)

@Serializable
sealed interface Action {
    @Serializable @SerialName("tap")
    data class Tap(val x: Int, val y: Int, val holdMs: Long = 50, val jitter: Jitter = Jitter()) : Action

    @Serializable @SerialName("swipe")
    data class Swipe(val x1: Int, val y1: Int, val x2: Int, val y2: Int, val durationMs: Long = 300, val jitter: Jitter = Jitter()) : Action

    @Serializable @SerialName("wait")
    data class Wait(val ms: Long, val jitterMs: Long = 0) : Action

    @Serializable @SerialName("loop")
    data class Loop(val count: Int = 0, val untilMs: Long = 0, val body: List<Action>) : Action

    @Serializable @SerialName("if")
    data class If(val cond: Condition, val then: List<Action>, val otherwise: List<Action> = emptyList()) : Action

    /** Procura um template na tela; grava "<into>.x" e "<into>.y" se achar. */
    @Serializable @SerialName("find_image")
    data class FindImage(
        val templateId: String, val threshold: Float = 0.9f, val region: Region? = null,
        val timeoutMs: Long = 0, val into: String = "img",
        val onFound: List<Action> = emptyList(), val onNotFound: List<Action> = emptyList(),
    ) : Action

    @Serializable @SerialName("read_text")
    data class ReadText(val region: Region, val into: String) : Action

    @Serializable @SerialName("set_var")
    data class SetVar(val name: String, val value: String) : Action

    @Serializable @SerialName("tap_found")
    data class TapFound(val from: String = "img", val holdMs: Long = 50, val jitter: Jitter = Jitter()) : Action

    @Serializable @SerialName("global")
    data class Global(val kind: GlobalKind) : Action

    @Serializable @SerialName("stop")
    data object Stop : Action
}

enum class GlobalKind { BACK, HOME, RECENTS, LOCK_SCREEN }

@Serializable
sealed interface Condition {
    @Serializable @SerialName("always") data object Always : Condition
    @Serializable @SerialName("random") data class Random(val probability: Double) : Condition
    @Serializable @SerialName("var") data class Var(val name: String, val op: Op, val value: String) : Condition
    @Serializable @SerialName("text_contains") data class TextContains(val region: Region, val text: String, val ignoreCase: Boolean = true) : Condition
    @Serializable @SerialName("image_visible") data class ImageVisible(val templateId: String, val threshold: Float = 0.9f, val region: Region? = null) : Condition
    @Serializable @SerialName("not") data class Not(val inner: Condition) : Condition
}

enum class Op { EQ, NE, LT, LE, GT, GE, CONTAINS }
