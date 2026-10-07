package com.inspyresoftworks.ti84evo.smartpad

/**
 * Stable identity for a physical TI-84 Evo key.
 *
 * HID usages and calculator scancodes are deliberately kept outside the enum
 * identity: they are independent transports and are not numerically related.
 */
enum class EvoKey(val displayLabel: String) {
    Y_EQUALS("Y="), WINDOW("WINDOW"), ZOOM("ZOOM"), TRACE("TRACE"), GRAPH("GRAPH"),
    SECOND("2nd"), MODE("MODE"), DELETE("DEL"), UP("UP"),
    ALPHA("ALPHA"), X_T_THETA_N("X,T,θ,n"), STAT("STAT"), LEFT("LEFT"),
    RIGHT("RIGHT"), DOWN("DOWN"), MATH("MATH"), FRACTION_TEMPLATE("FRAC"),
    PROGRAM("PRGM"), VARS("VARS"), CLEAR("CLEAR"), RECIPROCAL("x⁻¹"),
    SIN("SIN"), COS("COS"), TAN("TAN"), DIVIDE("÷"), SQUARE("x²"),
    COMMA(","), LEFT_PAREN("("), RIGHT_PAREN(")"), MULTIPLY("×"), LOG("LOG"),
    SEVEN("7"), EIGHT("8"), NINE("9"), MINUS("−"), LN("LN"), FOUR("4"),
    FIVE("5"), SIX("6"), PLUS("+"), STORE("STO→"), ONE("1"), TWO("2"),
    THREE("3"), FRACTION_DECIMAL_TOGGLE("►Frac/►Dec"), ON("ON"), ZERO("0"),
    DECIMAL("."), NEGATE("(-)"), ENTER("ENTER"),
}

enum class EvoScancodeEvidence { CONFIRMED, INFERRED }

data class EvoScancodeMapping(
    val key: EvoKey,
    val scancode: Int,
    val evidence: EvoScancodeEvidence,
) {
    init {
        require(scancode in 0..0xFF) { "scancode must fit in one byte" }
    }
}

object EvoScancodeMap {
    /** Hardware-confirmed mappings already exercised by the List Editor reset. */
    val confirmed: Map<EvoKey, Int> = mapOf(
        EvoKey.SECOND to 0x36,
        EvoKey.MODE to 0x37,
        EvoKey.STAT to 0x20,
        EvoKey.FIVE to 0x1B,
        EvoKey.ENTER to 0x09,
    )

    /** Intentionally empty until a hardware probe records evidence. */
    val inferred: Map<EvoKey, Int> = emptyMap()

    fun mappingFor(key: EvoKey): EvoScancodeMapping? =
        confirmed[key]?.let { EvoScancodeMapping(key, it, EvoScancodeEvidence.CONFIRMED) }
            ?: inferred[key]?.let { EvoScancodeMapping(key, it, EvoScancodeEvidence.INFERRED) }
}
