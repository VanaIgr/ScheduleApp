package com.idk.schedule

import android.R
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CalendarView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import java.util.*
import java.util.concurrent.TimeUnit

class DayLessonsAdapter : RecyclerView.Adapter<DayLessonsAdapter.DayLessonsViewHolder>() {
    private lateinit var curDays: DaysInfo

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayLessonsViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.lessons_fragment, parent, false)
        return DayLessonsViewHolder(view)
    }

    override fun onBindViewHolder(holder: DayLessonsViewHolder, position: Int) {
        holder.setFromDay(curDays, position - 1)
    }

    override fun getItemCount(): Int = 3

    fun updateDays(info: DaysInfo)  {
        curDays = info
        notifyDataSetChanged()
    }

    data class DaysInfo(val now: Calendar, val schedule: Schedule, val baseOffset: Int, val group: Boolean)

    class DayLessonsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val weekIndexTV: TextView = itemView.findViewById(R.id.weekIndex)
        private val currentEndTV: TextView = itemView.findViewById(R.id.currentEnd)
        private val nextEndTV: TextView = itemView.findViewById(R.id.nextEnd)
        private val elements: LinearLayout = itemView.findViewById(R.id.elements)
        private val elementsSV: ScrollView = itemView.findViewById(R.id.elementsSV)

        fun setFromDay(info: DaysInfo, curOffset: Int) {
            val (baseNow, schedule, baseOffset, group) = info
            val offset = baseOffset + curOffset

            val c = elements.context
            elements.removeAllViews()
            val inflater = LayoutInflater.from(c)

            fun View.setElementElevation(elevation: Float) {
                this as CardView
                cardElevation = c.dipToPx(elevation)
                maxCardElevation = c.dipToPx(elevation)
            }
            val addEl = fun(id: Int): View {
                val el_l = inflater.inflate(com.idk.schedule.R.layout.element_l, elements, false) as ViewGroup
                el_l.setElementElevation(3.0f)
                val container = el_l.findViewById<ViewGroup>(com.idk.schedule.R.id.container)
                elements.addView(el_l)
                val el = inflater.inflate(id, container, false)
                container.addView(el)
                return el_l
            }

            var scrollTo: View? = null
            var endOfDay = false

            val now = (baseNow.clone() as Calendar).also { it.add(Calendar.DAY_OF_YEAR, offset) }
            val curHour = now.get(Calendar.HOUR_OF_DAY)
            val curMinute = now.get(Calendar.MINUTE)
            val curDayOfWeek = floorMod(now.get(Calendar.DAY_OF_WEEK) - 2, 7) //Monday is 2 but should be 0, Sunday is 0 but should be 6
            val curMinuteOfDay = curHour * 60 + curMinute

            //very robust
            val yearStart = run {
                val calendar = Calendar.getInstance()
                calendar.clear()
                calendar.set(
                        schedule.weeksDescription.year,
                        schedule.weeksDescription.month - 1,
                        schedule.weeksDescription.day
                )
                val weekOfYear = calendar.get(Calendar.WEEK_OF_YEAR)
                calendar.clear()
                calendar.set(Calendar.YEAR, schedule.weeksDescription.year)
                calendar.set(Calendar.WEEK_OF_YEAR, weekOfYear)

                Date(
                        calendar.get(Calendar.YEAR) - 1900,
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH),
                ).time
            }
            val curDay = Date(
                    now.get(Calendar.YEAR) - 1900,
                    now.get(Calendar.MONTH),
                    now.get(Calendar.DAY_OF_MONTH)
            ).time
            val weekDiff = floorDiv(
                    TimeUnit.DAYS.convert(curDay - yearStart, TimeUnit.MILLISECONDS),
                    7L
            ).toInt()
            val weekIndex = schedule.weeksDescription.weeks[floorMod(
                    weekDiff,
                    schedule.weeksDescription.weeks.size
            )]
            val currentDay = schedule.week[curDayOfWeek]

            weekIndexTV.text = if(weekIndex) "Знаменатель" else "Числитель"

            val currentLessonIndices = currentDay.getForGroupAndWeek(group, weekIndex)
            val lessonIndicesRange = currentLessonIndices.calculateNozeropaddingRange()

            for(i in lessonIndicesRange) {
                val lessonIndex = currentLessonIndices[i]
                val lessonEl = addEl(com.idk.schedule.R.layout.element)

                val lessonMinutes = currentDay.time[i]
                val nextLessonI = run {
                    var nextI: Int = lessonIndicesRange.last+1
                    for(nextI_ in i+1..lessonIndicesRange.last) {
                        if(currentLessonIndices[nextI_] != 0) {
                            nextI = nextI_
                            break
                        }
                    }
                    nextI
                }
                val nextLessonMinutes = if(nextLessonI in lessonIndicesRange) currentDay.time[nextLessonI] else null

                val lesson = if(lessonIndex == 0) {
                    lessonEl.setElementTextEmpty(lessonMinutes)
                    null
                }
                else {
                    val lesson = currentDay.lessonsUsed[lessonIndex - 1]
                    lessonEl.setElementText(lesson, lessonMinutes)
                    lesson
                }

                when {
                    lessonMinutes.last < curMinuteOfDay    -> {
                        lessonEl.setElementForeground(com.idk.schedule.R.color.prev_el_overlay)
                        lessonEl.scaleX = 0.9f
                        lessonEl.scaleY = 0.9f
                        endOfDay = true
                    }
                    lessonMinutes.first > curMinuteOfDay -> {
                        lessonEl.setElementForeground(com.idk.schedule.R.color.next_el_overlay)
                        lessonEl.scaleX = 0.9f
                        lessonEl.scaleY = 0.9f
                    }
                    else -> {
                        lessonEl.setElementForeground(android.R.color.transparent)

                        if(lesson == null) {
                            scrollTo = lessonEl
                            if(nextLessonMinutes != null) {
                                currentEndTV.text = "До конца перемены: ${nextLessonMinutes.first - curMinuteOfDay} мин."
                                nextEndTV.text = "До конца след. пары: ${nextLessonMinutes.last - curMinuteOfDay} мин."
                            }
                        }
                        else {
                            scrollTo = lessonEl

                            currentEndTV.text = "До конца пары: ${lessonMinutes.last - curMinuteOfDay} мин."
                            if(nextLessonMinutes != null) {
                                nextEndTV.text = "До конца перемены: ${nextLessonMinutes.first - curMinuteOfDay} мин."
                            }
                            else nextEndTV.text = "Последняя пара"
                        }

                        lessonEl.setElementElevation(7.5f)
                    }
                }

                if(nextLessonMinutes != null) {
                    val breakEl = addEl(com.idk.schedule.R.layout.break_l)

                    breakEl.setBreakText(
                            "${minuteOfDayToString(lessonMinutes.last)}-${
                                minuteOfDayToString(
                                        nextLessonMinutes.first
                                )
                            }",
                            "Перемена ${nextLessonMinutes.first - lessonMinutes.last} мин."
                    )

                    breakEl.setElementElevation(1.0f)

                    when {
                        nextLessonMinutes.first <= curMinuteOfDay -> {
                            breakEl.setElementForeground(com.idk.schedule.R.color.prev_el_overlay)
                            breakEl.scaleX = 0.9f
                            breakEl.scaleY = 0.9f
                        }
                        lessonMinutes.last >= curMinuteOfDay -> {
                            breakEl.setElementForeground(com.idk.schedule.R.color.next_el_overlay)
                            breakEl.scaleX = 0.9f
                            breakEl.scaleY = 0.9f
                        }
                        else -> {
                            breakEl.setElementForeground(android.R.color.transparent)
                            scrollTo = breakEl
                            currentEndTV.text = "До конца перемены: ${nextLessonMinutes.first - curMinuteOfDay} мин."
                            nextEndTV.text = "До конца след. пары: ${nextLessonMinutes.last - curMinuteOfDay} мин."
                            breakEl.setElementElevation(7.5f)
                        }
                    }
                }
            }

            run {
                val sv = scrollTo
                elementsSV.post {
                    if(sv != null) elementsSV.scrollTo(
                            0,
                            (sv.top + sv.bottom) / 2 - elementsSV.height / 2
                    )
                    if(endOfDay) elementsSV.fullScroll(elementsSV.bottom)
                    else elementsSV.fullScroll(0)
                }

                if(sv == null) {
                    when {
                        lessonIndicesRange.isEmpty() -> {
                            currentEndTV.text = "Выходной"
                        }
                        endOfDay                     -> {
                            currentEndTV.text = "Конец учебного дня"
                        }
                        else                         -> {
                            currentEndTV.text = "До начала учебного дня: ${currentDay.time[lessonIndicesRange.first].first - curMinuteOfDay} мин."
                        }
                    }
                    nextEndTV.text = ""
                }
            }
        }

        private companion object {
            private fun View.setBreakText(time: String, text: String) {
                findViewById<TextView>(com.idk.schedule.R.id.timeTV).text = time
                findViewById<TextView>(com.idk.schedule.R.id.textTV).text = text
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
                findViewById<TextView>(com.idk.schedule.R.id.timeTV).text = time
                findViewById<TextView>(com.idk.schedule.R.id.typeTV).text = type
                findViewById<TextView>(com.idk.schedule.R.id.locTV).text = loc
                findViewById<TextView>(com.idk.schedule.R.id.nameTV).text = name
                findViewById<TextView>(com.idk.schedule.R.id.extraTV).text = extra
            }

            private fun View.setElementTextEmpty(time: IntRange) = setElementText(
                    minuteOfDayToString(time.first) + "-" + minuteOfDayToString(time.last),
                    "",
                    "",
                    "Окно",
                    ""
            )

            private fun View.setElementForeground(foreground: Int) {
                findViewById<View>(com.idk.schedule.R.id.foreground).setBackgroundResource(foreground)
            }
        }
    }
}

/*class DayLessonsAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 100

    override fun createFragment(position: Int): Fragment {
        // Return a NEW fragment instance in createFragment(int)
        val fragment = DemoObjectFragment()
        fragment.arguments = Bundle().apply {
            // Our object is just an integer :-P
            putInt(ARG_OBJECT, position + 1)
        }
        return fragment
    }
}

private const val ARG_OBJECT = "object"

// Instances of this class are fragments representing a single
// object in our collection.
class DemoObjectFragment : Fragment() {

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_collection_object, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        arguments?.takeIf { it.containsKey(ARG_OBJECT) }?.apply {
            val textView: TextView = view.findViewById(android.R.id.text1)
            textView.text = getInt(ARG_OBJECT).toString()
        }
    }
}*/