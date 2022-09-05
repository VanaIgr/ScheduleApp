package com.idk.schedule

import android.content.Context
import android.content.Intent
import android.util.TypedValue

fun Context.dipToPx(dp: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics
)

fun Context.spToPx(sp: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics
)

//very nice API 24 Math. functions
fun floorDiv(x: Int, y: Int): Int {
    var r = x / y
    // if the signs are different and modulo not zero, round down
    if(x xor y < 0 && r * y != x) {
        r--
    }
    return r
}

fun floorMod(x: Int, y: Int): Int {
    return x - floorDiv(x, y) * y
}

fun floorDiv(x: Long, y: Long): Long {
    var r = x / y
    // if the signs are different and modulo not zero, round down
    if(x xor y < 0 && r * y != x) {
        r--
    }
    return r
}

fun floorMod(x: Long, y: Long): Long {
    return x - floorDiv(x, y) * y
}

fun IntArray.calculateNozeropaddingRange(): IntRange {
    var first = this.size
    var last = -1
    for((i, lessonIndex) in this.withIndex()) {
        if(lessonIndex != 0) {
            first = first.coerceAtMost(i)
            last  = last.coerceAtLeast(i)
        }
    }
    return IntRange(first, last)
}

infix fun Int.min(b: Int) = Math.min(this, b)
infix fun Int.max(b: Int) = Math.max(this, b)