package com.idk.schedule

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.text.LineBreaker
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Layout
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.widget.*
import androidx.annotation.ColorRes
import androidx.annotation.RequiresApi
import androidx.gridlayout.widget.GridLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import androidx.cardview.widget.CardView
import androidx.core.view.MenuCompat
import androidx.core.view.setMargins
import androidx.core.view.setPadding
import androidx.core.widget.TextViewCompat
import androidx.viewpager2.widget.ViewPager2
import com.google.android.flexbox.FlexboxLayout
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.math.*


class MainActivity : AppCompatActivity() {
    private var group = false

    lateinit var schedule: Schedule

    private var timer: Timer? = null
    private var lastUpdate: Date? = null

    private lateinit var dayLessonAdapter: DayLessonsAdapter
    private var daysOffset = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val sharedPref = getSharedPreferences("info", Context.MODE_PRIVATE)
        group = sharedPref.getBoolean("group", group)

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

            updateScheduleDisplay(c)
        }

        with(findViewById<Switch>(R.id.groupSwitch)) {
            isChecked = group

            setOnCheckedChangeListener { _, isChecked ->
                group = isChecked
                val editor = sharedPref.edit()
                editor.putBoolean("group", group)
                editor.apply()
                updateScheduleDisplay(Calendar.getInstance())
            }
        }

        val settingsB = findViewById<View>(R.id.settings)
        val settingsPopup = PopupMenu(this, settingsB)
        settingsPopup.menuInflater.inflate(R.menu.parameters, settingsPopup.menu)
        settingsPopup.menu.add(1, Menu.FIRST, Menu.NONE, "Выбрать файл...")
        MenuCompat.setGroupDividerEnabled(settingsPopup.menu, true)
        settingsB.setOnClickListener { settingsPopup.show() }
        settingsPopup.setOnMenuItemClickListener { return@setOnMenuItemClickListener when(it.itemId) {
            Menu.FIRST -> {
                openFile(); true
            }
            else -> false
        } }

        val lessonsView = findViewById<ViewGroup>(R.id.lessonsView)
        val calendarView = findViewById<ViewGroup>(R.id.calendarView)

        run {
            val toLessonsView = findViewById<ImageView>(R.id.selectDayView)
            val toCalendarView = findViewById<ImageView>(R.id.selectWeekView)

            toLessonsView.setOnClickListener {
                toLessonsView.setColorFilter(
                    resources.getColor(R.color.purple_500),
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
                toCalendarView.clearColorFilter()
                lessonsView.visibility = View.VISIBLE
                calendarView.visibility = View.GONE
            }
            toCalendarView.setOnClickListener {
                toCalendarView.setColorFilter(
                    resources.getColor(R.color.purple_500),
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
                toLessonsView.clearColorFilter()
                calendarView.visibility = View.VISIBLE
                lessonsView.visibility = View.GONE
            }

            toLessonsView.callOnClick()
        }

        val scheduleString = try {
            readSchedule()
        }
        catch (e: Throwable) {
            val toast = Toast.makeText(
                applicationContext, "Error opening schedule file: $e",
                Toast.LENGTH_LONG
            )
            Log.e("ERROR", "", e)
            toast.show()
            "0,  0,0,0,0,0,0,0,  01,09,2022, 1,0,"
        }

        schedule = parseSchedule(scheduleString)

        val pager = findViewById<ViewPager2>(R.id.dayLessonsPager)
        dayLessonAdapter = DayLessonsAdapter()
        pager.adapter = dayLessonAdapter

        pager.currentItem = 1
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                super.onPageScrollStateChanged(state)

                if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    if(pager.currentItem != 1) {
                        daysOffset += pager.currentItem - 1
                        pager.currentItem = 1
                    }
                }
            }
        })

        updateScheduleWeekDisplay()
    }

    private fun updateScheduleWeekDisplay() {
        val week = findViewById<LinearLayout>(R.id.week)
        week.removeAllViews()

        fun TableRow.addElement(view: View, params: TableRow.LayoutParams, colorRes: Int = R.color.white) {
            this.addView(
                view.apply {
                    setPadding(dipToPx(5.0f).toInt())
                    setBackgroundColor(resources.getColor(colorRes))
                },
                params.apply {
                    bottomMargin = dipToPx(2.0f).toInt()
                    marginEnd = dipToPx(2.0f).toInt()
                }
            )
        }

        val dayNames = arrayOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")

        for(dayIndex in 0 until 7) {
            val day = TableLayout(this)
            day.setBackgroundColor(resources.getColor(R.color.light_gray))
            day.setPaddingRelative(dipToPx(2.0f).toInt(), dipToPx(2.0f).toInt(), 0, 0)

            week.addView(
                CardView(this).apply {
                    radius = 0.0f
                    useCompatPadding = true
                    maxCardElevation = dipToPx(3.0f)
                    cardElevation = dipToPx(3.0f)
                    addView(day, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = dipToPx(5.0f).toInt()
                    marginEnd = dipToPx(5.0f).toInt()
                    topMargin = dipToPx(20.0f).toInt()
                }
            )

            day.addView(TableRow(this).apply {
                addElement(
                    TextView(this@MainActivity).apply {
                        text = dayNames[dayIndex]
                        textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                        setTextColor(resources.getColor(R.color.black))
                        textSize = spToPx(12.0f)
                    },
                    TableRow.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        span = 3
                        weight = 1.0f
                    }
                )
            })

            val curDay = schedule.week[dayIndex]

            val lessonIndicesRange = run {
                val range0 = curDay.lessons[0].calculateNozeropaddingRange()
                val range1 = curDay.lessons[1].calculateNozeropaddingRange()
                val range2 = curDay.lessons[2].calculateNozeropaddingRange()
                val range3 = curDay.lessons[3].calculateNozeropaddingRange()

                return@run IntRange(
                    range0.first min range1.first min range2.first min range3.first,
                    range0.last  max range1.last  max range2.last  max range3.last ,
                )
            }

            //for((i, time) in curDay.time.withIndex()) {
            for(i in lessonIndicesRange) {
                val time = curDay.time[i]
                fun lessonAt(group: Boolean, week: Boolean) =
                    curDay.getForGroupAndWeek(group, week).let {
                        if (i in it.indices) it[i] else 0
                    }

                val row = TableRow(this)
                day.addView(row)

                row.addElement(
                    FrameLayout(this).apply {
                        addView(
                            TextView(this@MainActivity).apply {
                                text = minuteOfDayToString(time.first) + "\n-\n" + minuteOfDayToString(time.last)
                                textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                                setTextColor(resources.getColor(R.color.black))
                                textSize = spToPx(10.0f)
                                gravity = Gravity.CENTER_VERTICAL
                            },
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        )
                    },
                    TableRow.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )

                
                fun TableRow.addWrapLesson(lessonIndex: Int, colorRes: Int = R.color.white) {
                    val view = if(lessonIndex == 0)
                        FrameLayout(this@MainActivity).apply { addView(View(this@MainActivity)) }
                    else {
                        val lesson = curDay.lessonsUsed[lessonIndex-1]
                        FrameLayout(this@MainActivity).apply {
                            addView(
                                AppCompatTextView(this@MainActivity).apply {
                                    val noBreakSpace = '\u00A0'
                                    val textSB = StringBuilder()
                                    textSB.append(lesson.name)
                                    fun addOther(text: String) {
                                        if(text.isEmpty()) return
                                        textSB.append(' ')
                                        val newText = if(text.length < 20) text.replace(' ', noBreakSpace)
                                        else text
                                        textSB.append(newText)
                                    }
                                    addOther(lesson.type)
                                    addOther(lesson.loc)
                                    addOther(lesson.extra)
    
                                    this.text = textSB.toString()
                                    setTextColor(resources.getColor(R.color.black))
                                    textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                                    isSingleLine = false
                                    gravity = Gravity.CENTER
                                    textSize = spToPx(10.0f)
                                    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        @SuppressLint("WrongConstant") // it's declared as the corresponding constant in LineBreaker (API level 29)
                                        breakStrategy = Layout.BREAK_STRATEGY_BALANCED
                                    }
                                },
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                ).apply {
                                    gravity = Gravity.CENTER
                                }
                            )
                        }
                    }
                    addElement(
                        view,
                        TableRow.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        ).apply {
                            weight = 1.0f
                            gravity = Gravity.FILL_VERTICAL
                        },
                        colorRes
                    )
                }

                val groupHorizontal = lessonAt(group = false, false) == lessonAt(group = true, false) &&
                                      lessonAt(group = false, true ) == lessonAt(group = true, true )
                val groupVertical = lessonAt(false, week = false) == lessonAt(false, week = true) &&
                                     lessonAt(true , week = false) == lessonAt(true , week = true)

                row.addView(
                    TableLayout(this).apply {
                        isStretchAllColumns = true
                        when {
                            groupHorizontal && groupVertical -> {
                                addView(
                                    TableRow(this@MainActivity).apply {
                                        addWrapLesson(lessonAt(false, false))
                                    },
                                    TableLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                                        weight = 1.0f
                                    }
                                )
                            }
                            !groupHorizontal && !groupVertical -> {
                                addView(
                                    TableRow(this@MainActivity).apply {
                                        addWrapLesson(lessonAt(group = false, week = false))
                                        addWrapLesson(lessonAt(group = true, week = false))
                                    },
                                    TableLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                                        weight = 1.0f
                                    }
                                )
                                addView(
                                    TableRow(this@MainActivity).apply {
                                        addWrapLesson(lessonAt(group = false, week = true), R.color.yellow)
                                        addWrapLesson(lessonAt(group = true, week = true), R.color.yellow)
                                    },
                                    TableLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                                        weight = 1.0f
                                    }
                                )
                            }
                            groupHorizontal -> {
                                addView(
                                    TableRow(this@MainActivity).apply {
                                        addWrapLesson(lessonAt(false, week = false))
                                    },
                                    TableLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                                        weight = 1.0f
                                    }
                                )
                                addView(
                                    TableRow(this@MainActivity).apply {
                                        addWrapLesson(lessonAt(false, week = true), R.color.yellow)
                                    },
                                    TableLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                                        weight = 1.0f
                                    }
                                )
                            }
                            groupVertical -> {
                                addView(
                                    TableRow(this@MainActivity).apply {
                                        addWrapLesson(lessonAt(group = false, false))
                                        addWrapLesson(lessonAt(group = true, false))
                                    },
                                    TableLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                                        weight = 1.0f
                                    }
                                )
                            }
                        }
                    },
                    TableRow.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    ).apply {
                        weight = 1.0f
                        setMargins(0, 0, 0, 0)
                    }
                )
            }
        }
    }

    private fun updateScheduleDisplayOnce() {
        window.decorView.post{
            val calendar = Calendar.getInstance()
            val curDate = calendar.time
            if(lastUpdate == null || curDate.after(lastUpdate)) {
                calendar.add(Calendar.MINUTE, 1)
                val nextDate = Date(
                        calendar.get(Calendar.YEAR) - 1900,
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH),
                        calendar.get(Calendar.HOUR_OF_DAY),
                        calendar.get(Calendar.MINUTE)
                )
                lastUpdate = nextDate
                updateScheduleDisplay(Calendar.getInstance())
                Log.d("Update", "Updated!")
            }
        }
    }

    private fun updateScheduleDisplayTimed() {
        val next = Calendar.getInstance()
        next.add(Calendar.MINUTE, 1)

        val nextDate = Date(
            next.get(Calendar.YEAR) - 1900,
            next.get(Calendar.MONTH),
            next.get(Calendar.DAY_OF_MONTH),
            next.get(Calendar.HOUR_OF_DAY),
            next.get(Calendar.MINUTE)
        )

        updateScheduleDisplayOnce()

        try { timer?.cancel() } catch(e: Throwable) {}
        val newTimer = Timer()
        timer = newTimer
        newTimer.schedule(object : TimerTask() {
            override fun run() {
                updateScheduleDisplayTimed()
            }
        }, nextDate)
    }

    override fun onStop() {
        super.onStop()
        try { timer?.cancel() } catch(e: Throwable) {}
    }

    override fun onResume() {
        super.onResume()
        updateScheduleDisplayTimed()
    }

    private fun updateScheduleDisplay(now: Calendar) {
        dayLessonAdapter.updateDays(DayLessonsAdapter.DaysInfo(
            now, schedule, daysOffset, group
        ))

        //Log.d("AA", "${scrollTo?.let { (it.top + it.bottom) } ?: "none"}")
    }

    private fun writeSchedule(text: String) {
        val file = openFileOutput("schedule_0.scd", Context.MODE_PRIVATE)
        file.use { stream -> stream.write(text.toByteArray(Charsets.UTF_8)) }
    }

    private fun readSchedule(): String {
        val file = openFileInput("schedule_0.scd")
        return file.use { stream -> String(stream.readBytes(), Charsets.UTF_8) }
    }

    private fun openFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/*"
        }
        startActivityForResult(intent, 1)
    }

    @SuppressLint("MissingSuperCall")
    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        if (requestCode == 1 && resultCode == Activity.RESULT_OK) {
            resultData?.data?.also { uri ->
                val text = readTextFromUri(uri)
                try {
                    schedule = parseSchedule(text)
                    writeSchedule(text)
                    updateScheduleDisplay(Calendar.getInstance())
                    updateScheduleWeekDisplay()
                    updateScheduleWeekDisplay()
                } catch (e: Exception) {
                    val toast = Toast.makeText(
                        applicationContext,
                        "Error parsing schedule from file",
                        Toast.LENGTH_LONG
                    )
                    Log.e("ERROR", "", e)
                    toast.show()
                }
            }
        }
    }

    private fun readTextFromUri(uri: Uri): String {
        return contentResolver.openInputStream(uri)!!.use { inputStream ->
            String(inputStream.readBytes(), Charsets.UTF_8)
        }
    }
}