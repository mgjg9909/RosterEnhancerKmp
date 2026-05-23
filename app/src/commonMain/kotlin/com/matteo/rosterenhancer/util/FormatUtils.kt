package com.matteo.rosterenhancer.util

import kotlin.math.roundToInt
import kotlin.math.abs

import kotlin.math.pow

fun Double.formatDecimal(decimals: Int = 2): String {
    val factor = 10.0.pow(decimals)
    val rounded = (this * factor).roundToInt() / factor
    val stringVal = rounded.toString()
    if (decimals == 0) return rounded.toInt().toString()
    
    val parts = stringVal.split(".")
    val integerPart = parts[0]
    val decimalPart = if (parts.size > 1) parts[1] else "0"
    
    return "${integerPart}.${decimalPart.padEnd(decimals, '0')}"
}

fun Float.formatDecimal(decimals: Int = 2): String = this.toDouble().formatDecimal(decimals)

fun Double.formatCurrency(): String {
    val formatted = this.formatDecimal(2)
    // Basic comma separation for thousands could be added here, but simple format is ok for now
    return formatted
}

fun Double.formatCurrencyInt(): String {
    return this.toInt().toString()
}
