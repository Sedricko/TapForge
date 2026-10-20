package com.tapforge.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Region(val x: Int, val y: Int, val w: Int, val h: Int)

@Serializable
data class Point(val x: Int, val y: Int)

/** Um dedo: caminho percorrido, quando começa (ms) e quanto dura. */
@Serializable
data class Stroke(val points: List<Point>, val startMs: Long = 0, val durationMs: Long = 50)

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
    /** Tela onde o macro foi criado; se diferente da atual, as coordenadas são ajustadas. 0 = não ajustar. */
    val screenW: Int = 0,
    val screenH: Int = 0,
    val folder: String = "",
    val favorite: Boolean = false,
    val trigger: Trigger? = null,
)

/** Início automático. */
@Serializable
sealed interface Trigger {
    @Serializable @SerialName("app_opened") data class AppOpened(val packageName: String) : Trigger
    @Serializable @SerialName("charging") data object Charging : Trigger
    /** Todo dia em "HH:mm" (horário aproximado: o Android pode atrasar alguns minutos). */
    @Serializable @SerialName("schedule") data class Schedule(val time: String) : Trigger
}

@Serializable
sealed interface Action {
    /** Toque em (x, y) ou, se [area] existir, num ponto aleatório dentro dela. */
    @Serializable @SerialName("tap")
    data class Tap(val x: Int, val y: Int, val holdMs: Long = 50, val jitter: Jitter = Jitter(), val area: Region? = null) : Action

    @Serializable @SerialName("swipe")
    data class Swipe(val x1: Int, val y1: Int, val x2: Int, val y2: Int, val durationMs: Long = 300, val jitter: Jitter = Jitter()) : Action

    /** Gesto livre: caminhos com curvas, vários dedos ao mesmo tempo, pinça. */
    @Serializable @SerialName("gesture")
    data class Gesture(val strokes: List<Stroke>, val jitter: Jitter = Jitter()) : Action

    @Serializable @SerialName("wait")
    data class Wait(val ms: Long, val jitterMs: Long = 0) : Action

    /** Repete [body] até [count] vezes / [untilMs] ms; para antes se [whileCond] ficar falsa ou [untilCond] ficar verdadeira. */
    @Serializable @SerialName("loop")
    data class Loop(
        val count: Int = 0, val untilMs: Long = 0,
        @SerialName("while") val whileCond: Condition? = null,
        @SerialName("until") val untilCond: Condition? = null,
        val body: List<Action>,
    ) : Action

    @Serializable @SerialName("if")
    data class If(val cond: Condition, val then: List<Action>, val otherwise: List<Action> = emptyList()) : Action

    /**
     * Procura um template (ou alternativas) na tela; grava "<into>.x" e "<into>.y" se achar.
     * Com [all], executa [onFound] para cada ocorrência e grava "<into>.count".
     */
    @Serializable @SerialName("find_image")
    data class FindImage(
        val templateId: String, val threshold: Float = 0.9f, val region: Region? = null,
        val timeoutMs: Long = 0, val into: String = "img",
        val onFound: List<Action> = emptyList(), val onNotFound: List<Action> = emptyList(),
        val alternatives: List<String> = emptyList(),
        val all: Boolean = false, val maxResults: Int = 20,
        val scaleTolerant: Boolean = false,
    ) : Action

    /** OCR; com [numbersOnly] guarda só o número ("Ouro: 1.250" -> "1250"). */
    @Serializable @SerialName("read_text")
    data class ReadText(val region: Region, val into: String, val numbersOnly: Boolean = false) : Action

    /** [value] aceita "${variavel}". */
    @Serializable @SerialName("set_var")
    data class SetVar(val name: String, val value: String) : Action

    @Serializable @SerialName("math")
    data class Calc(val name: String, val op: MathOp, val value: String) : Action

    @Serializable @SerialName("tap_found")
    data class TapFound(val from: String = "img", val holdMs: Long = 50, val jitter: Jitter = Jitter()) : Action

    @Serializable @SerialName("global")
    data class Global(val kind: GlobalKind) : Action

    /** Escreve no campo de texto em foco. */
    @Serializable @SerialName("type_text")
    data class TypeText(val text: String, val append: Boolean = false) : Action

    @Serializable @SerialName("clipboard")
    data class Clipboard(val text: String) : Action

    /** Abre um app; com [waitMs] espera ele ficar em primeiro plano. */
    @Serializable @SerialName("open_app")
    data class OpenApp(val packageName: String, val waitMs: Long = 0) : Action

    /** Espera a tela (ou região) ficar parada por [stableMs]. */
    @Serializable @SerialName("wait_stable")
    data class WaitStable(val region: Region? = null, val stableMs: Long = 500, val timeoutMs: Long = 5000) : Action

    @Serializable @SerialName("alert")
    data class Alert(val message: String = "", val vibrateMs: Long = 0, val sound: Boolean = false) : Action

    @Serializable @SerialName("log")
    data class Log(val message: String) : Action

    /** Executa outro macro (sub-macro). */
    @Serializable @SerialName("call")
    data class Call(val macroId: String) : Action

    /** Executa [body] até [until] ficar verdadeira, no máximo [times] vezes; se não conseguir, executa [onFail]. */
    @Serializable @SerialName("retry")
    data class Retry(
        val until: Condition, val body: List<Action>, val times: Int = 3,
        val delayMs: Long = 500, val onFail: List<Action> = emptyList(),
    ) : Action

    @Serializable @SerialName("stop")
    data object Stop : Action
}

enum class GlobalKind { BACK, HOME, RECENTS, LOCK_SCREEN, NOTIFICATIONS, QUICK_SETTINGS }

enum class MathOp { SET, ADD, SUB, MUL, DIV, MOD, MIN, MAX }

@Serializable
sealed interface Condition {
    @Serializable @SerialName("always") data object Always : Condition
    @Serializable @SerialName("random") data class Random(val probability: Double) : Condition
    @Serializable @SerialName("var") data class Var(val name: String, val op: Op, val value: String) : Condition
    @Serializable @SerialName("text_contains") data class TextContains(val region: Region, val text: String, val ignoreCase: Boolean = true) : Condition
    @Serializable @SerialName("image_visible") data class ImageVisible(val templateId: String, val threshold: Float = 0.9f, val region: Region? = null) : Condition
    @Serializable @SerialName("not") data class Not(val inner: Condition) : Condition
    @Serializable @SerialName("all") data class AllOf(val conds: List<Condition>) : Condition
    @Serializable @SerialName("any") data class AnyOf(val conds: List<Condition>) : Condition
    /** Cor do pixel ("#RRGGBB") com tolerância por canal. */
    @Serializable @SerialName("pixel") data class Pixel(val x: Int, val y: Int, val color: String, val tolerance: Int = 16) : Condition
    /** Verdadeira quando a região mudou desde a última vez que esta condição ([key]) foi avaliada. */
    @Serializable @SerialName("changed") data class Changed(val key: String = "tela", val region: Region? = null) : Condition
    @Serializable @SerialName("app_open") data class AppOpen(val packageName: String) : Condition
}

enum class Op { EQ, NE, LT, LE, GT, GE, CONTAINS }
