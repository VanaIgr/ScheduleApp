package com.idk.schedule

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.*
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {
    private var group = false

    private val emptyScheduleString = "0,0,0,0,0,0,0,01,09,2022,1,0"
    private var scheduleString: String = emptyScheduleString

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val sharedPref = getSharedPreferences("info", Context.MODE_PRIVATE)
        group = sharedPref.getBoolean("group", group)

        updateScheduleTimed()

        findViewById<Button>(R.id.accept).setOnClickListener {
            val source = findViewById<TextView>(R.id.getText).text.toString()
            val end = source.length

            val year_ = StringView(source, 0, end).parseNextValue()
            val month_ = StringView(source, year_.end + 1, end).parseNextValue()
            val day_ = StringView(source, month_.end + 1, end).parseNextValue()
            val hour_ = StringView(source, day_.end + 1, end).parseNextValue()
            val minute_ = StringView(source, hour_.end + 1, end).parseNextValue()

            val year = year_.get().trim().toInt()
            val month = month_.get().trim().toInt()
            val day = day_.get().trim().toInt()
            val hour = hour_.get().trim().toInt()
            val minute = minute_.get().trim().toInt()

            val c = Calendar.getInstance()
            c.clear()
            c.set(year, month - 1, day, hour, minute)

            updateSchedule(c)
        }

        findViewById<Switch>(R.id.groupSwitch).setOnCheckedChangeListener { _, isChecked ->
            group = isChecked
            val editor = sharedPref.edit()
            editor.putBoolean("group", group)
            editor.apply()
            updateSchedule(Calendar.getInstance())
        }
    }

    private fun updateScheduleTimed() {
        Log.d("Update", "Updated!")

        val next = Calendar.getInstance()
        next.add(Calendar.MINUTE, 1)

        val nextDate = Date(
                next.get(Calendar.YEAR) - 1900,
                next.get(Calendar.MONTH),
                next.get(Calendar.DAY_OF_MONTH),
                next.get(Calendar.HOUR_OF_DAY),
                next.get(Calendar.MINUTE)
                           )

        window.decorView.post{ updateSchedule(Calendar.getInstance()) }

        Timer().schedule(object : TimerTask() {
            override fun run() {
                updateScheduleTimed()
            }
        }, nextDate)
    }

    private fun updateSchedule(now: Calendar) {
        val elements = findViewById<ViewGroup>(R.id.elements).also { it.removeAllViews() }
        val inflater = LayoutInflater.from(this)

        val schedule = parseSchedule(scheduleString)

        val addEl = fun(id: Int): View {
            val el_l = inflater.inflate(R.layout.element_l, elements, false) as ViewGroup
            val container = el_l.findViewById<ViewGroup>(R.id.container)
            elements.addView(el_l)
            val el = inflater.inflate(id, container, false)
            container.addView(el)
            return el_l
        }

        var scrollTo: View? = null
        var endOfDay = false

        val curHour = now.get(Calendar.HOUR_OF_DAY)
        val curMinute = now.get(Calendar.MINUTE)
        val curDayOfWeek = floorMod(now.get(Calendar.DAY_OF_WEEK) - 1, 7) //week starts at monday not sunday !!
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
        val currentDay = schedule.week[curDayOfWeek - 1]

        findViewById<TextView>(R.id.weekIndex).text = if(weekIndex) "Знаменатель" else "Числитель"
        val curEndTV = findViewById<TextView>(R.id.currentEnd)
        val nextEndTV = findViewById<TextView>(R.id.nextEnd)

        for((i, lessonGroup) in currentDay.withIndex()) {
            val lesson = lessonGroup.getForGroupAndWeek(group, weekIndex)
            val lessonEl = addEl(R.layout.element)
            lessonEl.setElementText(lesson)

            when {
                lesson.endMinuteOfDay < curMinuteOfDay    -> {
                    lessonEl.setElementForeground(R.color.prev_el_overlay)
                    lessonEl.scaleX = 0.9f
                    lessonEl.scaleY = 0.9f
                    endOfDay = true
                }
                lesson.startMinuteOfDay >= curMinuteOfDay -> {
                    lessonEl.setElementForeground(R.color.next_el_overlay)
                    lessonEl.scaleX = 0.9f
                    lessonEl.scaleY = 0.9f
                }
                else                                      -> {
                    lessonEl.setElementForeground(android.R.color.transparent)
                    scrollTo = lessonEl

                    curEndTV.text = "До конца пары: ${lesson.endMinuteOfDay - curMinuteOfDay} мин."
                    if(i != currentDay.size-1) {
                        val nextLessonGroup = currentDay[i + 1]
                        val nextLesson = nextLessonGroup.getForGroupAndWeek(group, weekIndex)
                        nextEndTV.text = "До конца перемены: ${nextLesson.startMinuteOfDay - curMinuteOfDay} мин."
                    }
                    else nextEndTV.text = "Последняя пара"
                }
            }

            if(i != currentDay.size-1) {
                val breakEl = addEl(R.layout.break_l)
                val nextLessonGroup = currentDay[i + 1]
                val nextLesson = nextLessonGroup.getForGroupAndWeek(group, weekIndex)

                breakEl.setBreakText(
                        "${minuteOfDayToString(lesson.endMinuteOfDay)}-${
                            minuteOfDayToString(
                                    nextLesson.startMinuteOfDay
                                               )
                        }",
                        "Перемена ${nextLesson.startMinuteOfDay - lesson.endMinuteOfDay} мин."
                                    )

                when {
                    nextLesson.startMinuteOfDay <= curMinuteOfDay -> {
                        breakEl.setElementForeground(R.color.prev_el_overlay)
                        breakEl.scaleX = 0.9f
                        breakEl.scaleY = 0.9f
                    }
                    lesson.endMinuteOfDay > curMinuteOfDay -> {
                        breakEl.setElementForeground(R.color.next_el_overlay)
                        breakEl.scaleX = 0.9f
                        breakEl.scaleY = 0.9f
                    }
                    else -> {
                        breakEl.setElementForeground(android.R.color.transparent)
                        scrollTo = breakEl

                        curEndTV.text = "До конца перемены: ${nextLesson.startMinuteOfDay - curMinuteOfDay} мин."
                        nextEndTV.text = "До конца след. пары: ${nextLesson.endMinuteOfDay - curMinuteOfDay} мин."
                    }
                }

                (breakEl as CardView).cardElevation = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, 1.0f, resources.displayMetrics
                                                                               )
            }
        }

        run {
            val sv = scrollTo
            val scrollView = findViewById<ScrollView>(R.id.elementsSV)
            scrollView.post {
                if(sv != null) scrollView.scrollTo(0,
                                                   (sv.top + sv.bottom) / 2 - scrollView.height / 2
                                                  )
                else {
                    if(endOfDay) scrollView.fullScroll(scrollView.bottom)
                    else scrollView.fullScroll(0)
                }
            }

            if(sv == null) {
                if(currentDay.isEmpty()) {
                    curEndTV.text = "Выходной"
                    nextEndTV.text = ""
                }
                else {
                    if(endOfDay) {
                        //val lesson = currentDay[currentDay.size-1].getForGroupAndWeek(group, weekIndex)

                        curEndTV.text = "Конец учебного дня"
                        nextEndTV.text = ""
                    }
                    else {
                        val lesson = currentDay[0].getForGroupAndWeek(group, weekIndex)

                        curEndTV.text = "До начала учебного дня: ${lesson.startMinuteOfDay - curMinuteOfDay} мин."
                        nextEndTV.text = ""
                    }
                }
            }
        }

        //Log.d("AA", "${scrollTo?.let { (it.top + it.bottom) } ?: "none"}")
    }

    private fun View.setBreakText(time: String, text: String) {
        findViewById<TextView>(R.id.timeTV).text = time
        findViewById<TextView>(R.id.textTV).text = text
    }

    private fun View.setElementText(element: Lesson) = setElementText(
            minuteOfDayToString(element.startMinuteOfDay) + "-" + minuteOfDayToString(
                    element.endMinuteOfDay
                                                                                     ),
            element.type,
            element.loc,
            element.name,
            element.extra
                                                                     )

    private fun View.setElementText(time: String, type: String, loc: String, name: String,
                                    extra: String
                                   ) {
        findViewById<TextView>(R.id.timeTV).text = time
        findViewById<TextView>(R.id.typeTV).text = type
        findViewById<TextView>(R.id.locTV).text = loc
        findViewById<TextView>(R.id.nameTV).text = name
        findViewById<TextView>(R.id.extraTV).text = extra
    }

    private fun View.setElementForeground(foreground: Int) {
        findViewById<View>(R.id.foreground).setBackgroundResource(foreground)
    }

    //very nice API 24 Math. functions
    private fun floorDiv(x: Int, y: Int): Int {
        var r = x / y
        // if the signs are different and modulo not zero, round down
        if(x xor y < 0 && r * y != x) {
            r--
        }
        return r
    }

    private fun floorMod(x: Int, y: Int): Int {
        return x - floorDiv(x, y) * y
    }

    private fun floorDiv(x: Long, y: Long): Long {
        var r = x / y
        // if the signs are different and modulo not zero, round down
        if(x xor y < 0 && r * y != x) {
            r--
        }
        return r
    }

    private fun floorMod(x: Long, y: Long): Long {
        return x - floorDiv(x, y) * y
    }

    fun openFile(pickerInitialUri: Uri) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri)
        }
        startActivityForResult(intent, 1)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        //super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == 1 && resultCode == Activity.RESULT_OK) {
            resultData?.data?.also { uri ->
                readTextFromUri(uri)
            }
        }
    }

    private fun readTextFromUri(uri: Uri): String {
        val stringBuilder = StringBuilder()
        contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var line: String? = reader.readLine()
                while (line != null) {
                    stringBuilder.append(line)
                    line = reader.readLine()
                }
            }
        }
        return stringBuilder.toString()
    }
}