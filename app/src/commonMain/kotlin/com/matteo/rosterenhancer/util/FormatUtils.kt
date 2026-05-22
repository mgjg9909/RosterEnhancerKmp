package com.matteo.rosterenhancer.util

import kotlin.math.roundToInt
import kotlin.math.abs

fun Double.formatDecimal(): String {
    val integerPart = this.toInt()
    val decimalPart = abs(((this - integerPart) * 100).roundToInt())
    return "${integerPart}.${decimalPart.toString().padStart(2, '0')}"
}

fun Float.formatDecimal(): String {
    val integerPart = this.toInt()
    val decimalPart = abs(((this - integerPart) * 100).roundToInt())
    return "${integerPart}.${decimalPart.toString().padStart(2, '0')}"
}
