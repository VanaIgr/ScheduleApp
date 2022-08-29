package com.idk.schedule

import android.util.Log
import java.lang.IllegalStateException
import java.lang.IndexOutOfBoundsException
import java.lang.RuntimeException
import java.lang.StringBuilder
import java.util.*
import kotlin.collections.ArrayList

fun Boolean.toInt(): Int = if(this) 1 else 0

fun minuteOfDayToString(mod: Int): String {
    return (mod/60).toString() + ":" + (mod%60).let { if(it < 10) "0$it" else it }
}

data class Lesson(
    val type: String,
    val loc: String,
    val name: String,
    val extra: String
)

//(group << 1) | числитель/знаменатель
data class Day(val lessonsUsed: Array<Lesson>, val time: Array<IntRange>, val lessons: Array<IntArray>) {
    fun getForGroupAndWeek(group: Boolean, week: Boolean): IntArray {
        return lessons[(week.toInt() shl 1) or group.toInt()]
    }
}

data class Weeks(val day: Int, val month: Int, val year: Int, val weeks: BooleanArray)

typealias Week = Array<Day>

data class StringView(val source: String, val begin: Int, val end: Int) {
    init {
        if(
           !isEmpty() && (begin !in source.indices
           || !(end >= 0 && end <= source.length)
           || end < begin)
        ) throw IndexOutOfBoundsException("begin=$begin end=$end source length=${source.length}")
    }

    fun get(): String = source.substring(begin, end)

    fun isEmpty() = begin == end
}

fun StringView.parseNextValue(): StringView {
    var i = begin
    while(this.source[i] != ',') i++
    return StringView(source, begin, i)
}
internal fun StringView.parseRawString(): StringView {
    val lengthS = parseNextValue()
    val length = source.substring(lengthS.run { IntRange(begin, end - 1) }).trim().toInt()
    return StringView(source, lengthS.end+1, lengthS.end+1 + length)
}
internal fun StringView.parseLesson(): Pair<StringView, Lesson> {
    val type = StringView(source, begin, end).parseRawString()
    val loc = StringView(source, type.end+1, end).parseRawString()
    val name = StringView(source, loc.end+1, end).parseRawString()
    val extra = StringView(source, name.end+1, end).parseRawString()

    return Pair(
        StringView(source, begin, extra.end),
        Lesson(
            type.get(),
            loc.get(),
            name.get(),
            extra.get()
        )
    )
}

internal fun StringView.parseDay(): Pair<StringView, Day> {
    val lessonsCountS = StringView(source, begin, end).parseNextValue()
    var curBegin = lessonsCountS.end
    val lessonsCount = lessonsCountS.get().trim().toInt()
    if(lessonsCount == 0) return Pair(lessonsCountS, Day(emptyArray(), emptyArray(), Array(4){intArrayOf()}))
    val lessonsUsed = Array(lessonsCount) {
        val pair = StringView(source, curBegin+1, end).parseLesson()
        curBegin = pair.first.end
        pair.second
    }

    val timeCountS = StringView(source, curBegin+1, end).parseNextValue()
    val timeCount = timeCountS.get().trim().toInt()
    curBegin = timeCountS.end
    val time = Array(timeCount) {
        val startHourS = StringView(source, curBegin+1, end).parseNextValue()
        val startHour = startHourS.get().trim().toInt()
        curBegin = startHourS.end

        val startMinuteS = StringView(source, curBegin+1, end).parseNextValue()
        val startMinute = startMinuteS.get().trim().toInt()
        curBegin = startMinuteS.end

        val endHourS = StringView(source, curBegin+1, end).parseNextValue()
        val endHour = endHourS.get().trim().toInt()
        curBegin = endHourS.end

        val endMinuteS = StringView(source, curBegin+1, end).parseNextValue()
        val endMinute = endMinuteS.get().trim().toInt()
        curBegin = endMinuteS.end

        IntRange(
            startHour * 60 + startMinute,
            endHour * 60 + endMinute,
        )
    }

    val lessons = Array(4) {
        IntArray(timeCount) {
            val indexS = StringView(source, curBegin+1, end).parseNextValue()
            val index = indexS.get().trim().toInt()
            curBegin = indexS.end
            index
        }
    }

    return Pair(
        StringView(source, begin, curBegin),
        Day(lessonsUsed, time, lessons)
    )
}

internal fun StringView.parseWeeks(): Pair<StringView, Weeks> {
    val day = parseNextValue()
    val month = StringView(source, day.end+1, end).parseNextValue()
    val year = StringView(source, month.end+1, end).parseNextValue()
    val string = StringView(source, year.end+1, end).parseRawString()

    return Pair(
        StringView(source, begin, string.end),
        Weeks(
            day.get().trim().toInt(),
            month.get().trim().toInt(),
            year.get().trim().toInt(),
            BooleanArray(string.end - string.begin) { string.source[string.begin + it] != '0' }
        )
    )
}

data class Schedule(val weeksDescription: Weeks, val week: Week)

fun parseSchedule(input: String): Schedule {
    val week = ArrayList<Day>()


    var begin = 0

    val versionS = StringView(input, begin, input.length).parseNextValue()
    begin = versionS.end + 1
    val version = versionS.get().trim().toInt()
    if(version != 0) throw RuntimeException("version $version is unknown")

    for (i in 0 until 7) {
        val pair = StringView(input, begin, input.length).parseDay()
        begin = pair.first.end + 1
        week.add(pair.second)
    }

    val sv = StringView(input, begin, input.length)
    val pair = sv.parseWeeks()
    begin = pair.first.end + 1
    val weeks = pair.second

    return Schedule(
        weeks,
        week.toTypedArray()
    )
}