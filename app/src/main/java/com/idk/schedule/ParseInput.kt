package com.idk.schedule

import java.lang.IllegalStateException
import java.lang.IndexOutOfBoundsException
import java.lang.RuntimeException

fun Boolean.toInt(): Int = if(this) 1 else 0

fun minuteOfDayToString(mod: Int): String {
    return (mod/60).toString() + ":" + (mod%60).let { if(it < 10) "0$it" else it }
}

data class Lesson(
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,

    val type: String,
    val loc: String,
    val name: String,
    val extra: String
) {
    init {
        if(startMinuteOfDay in 0 until (24*60)
           && endMinuteOfDay in 0 until (24*60));
        else throw IllegalStateException(
            "time is incorrect ${minuteOfDayToString(startMinuteOfDay)}, ${minuteOfDayToString(endMinuteOfDay)}"
        )
    }
}

//(group << 1) | числитель/знаменатель
data class LessonGroup(val lessons: Array<Lesson>) {
    fun getForGroupAndWeek(group: Boolean, week: Boolean): Lesson {
        return lessons[(group.toInt() shl 1) or week.toInt()]
    }
}

data class Weeks(val day: Int, val month: Int, val year: Int, val weeks: BooleanArray)

typealias Day = Array<LessonGroup>

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
    val sHour = parseNextValue()
    val sMinute = StringView(source, sHour.end+1, end).parseNextValue()
    val eHour = StringView(source, sMinute.end+1, end).parseNextValue()
    val eMinute = StringView(source, eHour.end+1, end).parseNextValue()

    val type = StringView(source, eMinute.end+1, end).parseRawString()
    val loc = StringView(source, type.end+1, end).parseRawString()
    val name = StringView(source, loc.end+1, end).parseRawString()
    val extra = StringView(source, name.end+1, end).parseRawString()

    return Pair(
            StringView(source, begin, extra.end),
            Lesson(
            sHour.get().trim().toInt() * 60 + sMinute.get().trim().toInt(),
            eHour.get().trim().toInt() * 60 + eMinute.get().trim().toInt(),
            type.get(),
            loc.get(),
            name.get(),
            extra.get()
                  )
    );
}
internal fun StringView.parseLessonGroup(): Pair<StringView, LessonGroup> {
    val groupsS = parseNextValue()
    val groups = groupsS.get().trim() != "0"

    val lessonsArray = arrayOfNulls<Lesson>(4)

    var curBegin = groupsS.end

    fun parseWeeks(group: Int) {
        val weeksS = StringView(source, curBegin+1, end).parseNextValue()
        curBegin = weeksS.end
        val weeks = weeksS.get().trim() != "0"

        if(weeks) {
            val pair1 = StringView(source, curBegin+1, end).parseLesson()
            curBegin = pair1.first.end

            val pair2 = StringView(source, curBegin+1, end).parseLesson()
            curBegin = pair2.first.end

            lessonsArray[(group shl 1) or 0] = pair1.second
            lessonsArray[(group shl 1) or 1] = pair2.second
        }
        else {
            val pair = StringView(source, curBegin+1, end).parseLesson()
            curBegin = pair.first.end

            lessonsArray[(group shl 1) or 0] = pair.second
            lessonsArray[(group shl 1) or 1] = pair.second
        }
    }

    if(groups) {
        parseWeeks(0)
        parseWeeks(1)
    }
    else {
        parseWeeks(0)

        lessonsArray[(1 shl 1) or 0] = lessonsArray[(0 shl 1) or 0]
        lessonsArray[(1 shl 1) or 1] = lessonsArray[(0 shl 1) or 1]
    }

    return Pair(
        StringView(source, begin, curBegin),
        LessonGroup(lessonsArray as Array<Lesson>)
    )
}

internal fun StringView.parseDay(): Pair<StringView, Day> {
    val lessonsCountS = parseNextValue()
    val lessonsCount = source.substring(lessonsCountS.run { IntRange(begin, end - 1) }).trim().toInt()

    val lessons = ArrayList<LessonGroup>()
    var curBegin = lessonsCountS.end
    for(i in 0 until lessonsCount) {
        val pair = StringView(source, curBegin+1, end).parseLessonGroup()
        curBegin = pair.first.end
        lessons.add(pair.second)
    }

    return Pair(StringView(source, begin, curBegin), lessons.toTypedArray())
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