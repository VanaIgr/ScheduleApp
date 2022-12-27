package com.idk.schedule

import java.lang.IndexOutOfBoundsException
import java.lang.RuntimeException
import java.util.function.Supplier
import kotlin.collections.ArrayList
import kotlin.math.max
import kotlin.math.min
import kotlin.text.StringBuilder

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

    companion object {
        val emptyDay = Day(emptyArray(), emptyArray(), run {
            val value = IntArray(0)
            arrayOf(value, value, value, value)
        })
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
fun StringView.toInt(): Int {
    val string = get()
    try {
        return string.trim().toInt()
    }
    catch (e: NumberFormatException) {
        val expString = run {
            val begin2 = max(begin-3, 0)
            val end2   = min(end  +3, source.length)

            Triple(
                source.substring(begin2, begin),
                source.substring(begin, end),
                source.substring(end, end2),
            )
        }

        val (lineIndex, charIndex) = countPos(source, begin)

        throw RuntimeException("Error parsing int in [$begin;$end> at line $lineIndex, " +
                "position $charIndex: ...${expString.first}`${expString.second}`${expString.third}...", e)
    }
}

internal fun countPos(source: String, pos: Int): Pair<Int, Int> {
    var lineIndex = 1
    var charIndex = 0

    for(i in 0 until pos) {
        if(source[i] == '\n') {
            charIndex = 0
            lineIndex++
        }
        else charIndex++
    }

    return lineIndex to charIndex
}

internal fun StringView.parseRawString(): StringView {
    val lengthS = parseNextValue()
    val length = lengthS.toInt()
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

internal fun StringView.parseLessonIndices(count: Int): Pair<StringView, Array<IntArray>> {
    var curBegin = begin
    val array = Array(4) {
        IntArray(count) {
            val indexS = StringView(source, curBegin+1, end).parseNextValue()
            val index = indexS.toInt()
            curBegin = indexS.end
            index
        }
    }

    return StringView(source, begin, curBegin) to array
}

internal fun StringView.parseTime(): Pair<StringView, Array<IntRange>> {
    var curBegin = begin
    val timeCountS = StringView(source, curBegin+1, end).parseNextValue()
    val timeCount = timeCountS.toInt()
    curBegin = timeCountS.end
    val time = Array(timeCount) {
        val startHourS = StringView(source, curBegin+1, end).parseNextValue()
        val startHour = startHourS.toInt()
        curBegin = startHourS.end

        val startMinuteS = StringView(source, curBegin+1, end).parseNextValue()
        val startMinute = startMinuteS.toInt()
        curBegin = startMinuteS.end

        val endHourS = StringView(source, curBegin+1, end).parseNextValue()
        val endHour = endHourS.toInt()
        curBegin = endHourS.end

        val endMinuteS = StringView(source, curBegin+1, end).parseNextValue()
        val endMinute = endMinuteS.toInt()
        curBegin = endMinuteS.end

        IntRange(
                startHour * 60 + startMinute,
                endHour * 60 + endMinute,
        )
    }

    return StringView(source, begin, curBegin) to time
}

internal fun StringView.parseDay(): Pair<StringView, Day> {
    val lessonsCountS = StringView(source, begin, end).parseNextValue()
    var curBegin = lessonsCountS.end
    val lessonsCount = lessonsCountS.toInt()
    if(lessonsCount == 0) return Pair(lessonsCountS, Day.emptyDay)
    val lessonsUsed = Array(lessonsCount) {
        val pair = StringView(source, curBegin+1, end).parseLesson()
        curBegin = pair.first.end
        pair.second
    }

    val pair1 = StringView(source, curBegin, end).parseTime()
    curBegin = pair1.first.end
    val time = pair1.second

    val pair2 = StringView(source, curBegin, end).parseLessonIndices(time.size)
    curBegin = pair2.first.end
    val lessons = pair2.second

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
            day.toInt(),
            month.toInt(),
            year.toInt(),
            BooleanArray(string.end - string.begin) { string.source[string.begin + it] != '0' }
        )
    )
}

data class Schedule(val weeksDescription: Weeks, val week: Week)


class Sos(private val str: String) : CharSequence {
    override val length: Int = str.length

    override fun get(index: Int): Char {
        return str.get(index)
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        return str.subSequence(startIndex, endIndex)
    }

}

fun parseSchedule(input_: String): Schedule {
    val input = if(Character.isDigit(input_[0]) || Character.isSpaceChar(input_[0]))
        input_
    else run {
        val firstSpace = input_.indexOfFirst { Character.isSpaceChar(it) }
        if(firstSpace == -1) throw RuntimeException("No comment symbol defined")
        val comment = input_.substring(0, firstSpace)

        val commentSb = StringBuilder()
        val commentRepl = run {
            for(c in comment) {
                if(c == '\n') commentSb.append('\n')
                else commentSb.append(' ')
            }
            commentSb.toString()
        }

        var inComment = true
        var thisCommentEnd = firstSpace
        val sb = StringBuilder()

        sb.append(commentRepl)

        while (true) {
            val nextCommentStart = Sos(input_).indexOf(comment, thisCommentEnd)
            if (!inComment) {
                val end = if (nextCommentStart == -1) input_.length else nextCommentStart
                sb.append(input_.substring(thisCommentEnd, end))
                sb.append(commentRepl)
                if (nextCommentStart == -1) break
            }
            else {
                if (nextCommentStart == -1) {
                    val (line, char) = countPos(input_, thisCommentEnd)
                    throw RuntimeException(
                        "Comment block `$comment` at $line:$char must be closed before EOF"
                    )
                }

                commentSb.clear()
                for(c in input_.substring(thisCommentEnd, nextCommentStart)) {
                    if(c == '\n') commentSb.append('\n')
                    else commentSb.append(' ')
                }
                sb.append(commentSb.toString())
                sb.append(commentRepl)
            }
            thisCommentEnd = nextCommentStart + comment.length
            inComment = !inComment
        }
        sb.toString()
    }

    var begin = 0

    val versionS = StringView(input, begin, input.length).parseNextValue()
    begin = versionS.end + 1
    val version = versionS.toInt()

    if(version == 0) {
        val week = ArrayList<Day>()

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
    else if(version == 2) {
        val lessonsCountS = StringView(input, begin, input.length).parseNextValue()
        begin = lessonsCountS.end + 1
        val lessonsCount = lessonsCountS.toInt()
        val lessonsUsed = Array(lessonsCount) {
            val pair = StringView(input, begin, input.length).parseLesson()
            begin = pair.first.end + 1
            pair.second
        }

        val timeCountS = StringView(input, begin, input.length).parseNextValue()
        begin = timeCountS.end + 1
        val timeCount = timeCountS.toInt()
        val timeUsed = Array(timeCount) {
            val pair = StringView(input, begin, input.length).parseTime()
            begin = pair.first.end + 1
            pair.second
        }

        val dayCountS = StringView(input, begin, input.length).parseNextValue()
        begin = dayCountS.end + 1
        val dayCount = dayCountS.toInt()
        val dayUsed = Array(dayCount) {
            val timeIndexS = StringView(input, begin, input.length).parseNextValue()
            begin = timeIndexS.end + 1
            val timeIndex = timeIndexS.toInt()

            val time = timeUsed[timeIndex]

            val pair = StringView(input, begin, input.length).parseLessonIndices(time.size)
            begin = pair.first.end + 1
            Day(
                lessonsUsed,
                time,
                pair.second
            )
        }

        val days = IntArray(7) {
            val dayS = StringView(input, begin, input.length).parseNextValue()
            begin = dayS.end + 1
            val day = dayS.toInt()
            day
        }

        val sv = StringView(input, begin, input.length)
        val pair = sv.parseWeeks()
        begin = pair.first.end + 1
        val weeks = pair.second

        fun getDayUsed(index: Int) = if(index == -1) Day.emptyDay
        else dayUsed[index]

        return Schedule(
            weeks,
            Array(7) { getDayUsed(days[it]) }
        )
    }
    else throw RuntimeException("unknown version=$version")


}