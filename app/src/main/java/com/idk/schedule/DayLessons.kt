package com.idk.schedule

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.view.get
import androidx.recyclerview.widget.RecyclerView
import java.lang.StringBuilder
import java.lang.reflect.Field
import java.util.*
import java.util.concurrent.TimeUnit
import androidx.core.math.MathUtils.clamp


data class DayElement(val lesson: Lesson?, val time: IntRange)
data class DayLessons(val date: Calendar, val dateOffsetFromNow: Int, val weekIndex: Boolean, val elements: Array<DayElement>, val activeIndex: Int)

class DayLessonsAdapter : RecyclerView.Adapter<DayLessonsAdapter.DayLessonsViewHolder>() {
    private var curDays: Array<DayLessons>? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayLessonsViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.lessons_fragment, parent, false)
        return DayLessonsViewHolder(view)
    }

    override fun onBindViewHolder(holder: DayLessonsViewHolder, position: Int) {
        curDays?.let{ holder.setFromDay(it[position]) }
    }

    override fun getItemCount(): Int = 3

    fun updateDays(info: Array<DayLessons>)  {
        curDays = info
        notifyDataSetChanged()
    }

    class DayLessonsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dateTV: TextView = itemView.findViewById(R.id.dateTV)
        private val weekIndexTV: TextView = itemView.findViewById(R.id.weekIndexTV)
        private val elements: LinearLayout = itemView.findViewById(R.id.elements)
        private val elementsSV: ScrollView = itemView.findViewById(R.id.elementsSV)

        fun setFromDay(lessons: DayLessons) {
            val c = elements.context
            elements.removeAllViews()
            val inflater = LayoutInflater.from(c)

            fun View.setElementElevation(elevation: Float) {
                this as CardView
                cardElevation = c.dipToPx(elevation)
                maxCardElevation = c.dipToPx(elevation)
            }
            val addEl = fun(id: Int): View {
                val el_l = inflater.inflate(R.layout.element_l, elements, false) as ViewGroup
                el_l.setElementElevation(3.0f)
                val container = el_l.findViewById<ViewGroup>(com.idk.schedule.R.id.container)
                elements.addView(el_l)
                val el = inflater.inflate(id, container, false)
                container.addView(el)
                return el_l
            }

            for((i, lessonInfo) in lessons.elements.withIndex()) {
                val (lesson, timespan) = lessonInfo

                val lessonEl = if(lesson != null) {
                    addEl(R.layout.element).also {
                        it.setElementText(lesson, timespan)
                    }
                }
                else {
                    addEl(R.layout.break_l).also {
                        it.setBreakText(
                            "${minuteOfDayToString(timespan.first)}-${
                            minuteOfDayToString(timespan.last)
                            }",
                            "Перемена ${timespan.last - timespan.first} мин."
                        )
                    }
                }

                if (lessons.activeIndex == i) {
                    lessonEl.setElementForeground(android.R.color.transparent)
                    lessonEl.setElementElevation(7.5f)
                }
                else {
                    lessonEl.setElementForeground(R.color.el_overlay)
                    lessonEl.scaleX = 0.9f
                    lessonEl.scaleY = 0.9f
                    //endOfDay = true
                }
            }

            run {
                val sv = if(lessons.activeIndex in 0 until elements.childCount) elements[lessons.activeIndex] else null
                elementsSV.post {
                    if(sv != null) elementsSV.scrollTo(0, (sv.top + sv.bottom) / 2 - elementsSV.height / 2)
                    if(lessons.activeIndex == elements.childCount) elementsSV.fullScroll(ScrollView.FOCUS_DOWN)
                    else elementsSV.fullScroll(ScrollView.FOCUS_UP)
                }
            }

            val offsets = arrayOf("раньше", "вчера", "сегодня", "завтра", "позже")

            val daysOfWeek = arrayOf("Воскресенье", "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота")
            dateTV.text = StringBuilder().run {
                append(daysOfWeek[lessons.date.get(Calendar.DAY_OF_WEEK)-1])
                append(" (")
                append(offsets[clamp(lessons.dateOffsetFromNow + 2, 0, offsets.lastIndex)])
                append(')')
                append(", ")
                append00(lessons.date.get(Calendar.DAY_OF_MONTH))
                append(".")
                append00(lessons.date.get(Calendar.MONTH))
                append(".")
                append(lessons.date.get(Calendar.YEAR))
                toString()
            }
            weekIndexTV.text = if(lessons.weekIndex) "Знаменатель" else "Числитель"
        }

        private companion object {
            fun StringBuilder.append00(it: Int): StringBuilder = if(it == 0) append("00") else if(it < 10) append('0').append(it) else append(it)

            private fun View.setBreakText(time: String, text: String) {
                findViewById<TextView>(R.id.timeTV).text = time
                findViewById<TextView>(R.id.textTV).text = text
            }

            private fun View.setElementText(element: Lesson, time: IntRange) = setElementText(
                    minuteOfDayToString(time.first) + "-" + minuteOfDayToString(time.last),
                    element.type,
                    element.loc,
                    element.name,
                    element.extra
            )

            private fun View.setElementText(
                    time: String, type: String, loc: String, name: String,
                    extra: String
            ) {
                findViewById<TextView>(R.id.timeTV).text = time
                findViewById<TextView>(R.id.typeTV).text = type
                findViewById<TextView>(R.id.locTV).text = loc
                findViewById<TextView>(R.id.nameTV).text = name
                findViewById<TextView>(R.id.extraTV).text = extra
            }

            private fun View.setElementTextEmpty(time: IntRange) = setElementText(
                    minuteOfDayToString(time.first) + "-" + minuteOfDayToString(time.last),
                    "",
                    "",
                    "Окно",
                    ""
            )

            private fun View.setElementForeground(foreground: Int) {
                findViewById<View>(R.id.foreground).setBackgroundResource(foreground)
            }
        }
    }
}