package com.idk.schedule

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Layout
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.view.View.MeasureSpec
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.SwitchCompat
import androidx.cardview.widget.CardView
import androidx.core.view.*
import androidx.viewpager2.widget.ViewPager2
import com.github.rongi.rotate_layout.layout.RotateLayout
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.math.*


class MainActivity : AppCompatActivity() {
    private var group = false

    lateinit var schedule: Schedule

    private var timer: Timer? = null
    private var lastUpdate: Calendar? = null

    private lateinit var dayLessonAdapter: DayLessonsAdapter
    private var daysOffset = 0

    private lateinit var currentEndTV: TextView
    private lateinit var nextEndTV: TextView

    class TimeChangeBroadcastReceiver(private val callback: () -> Unit) : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            callback()
        }
    }

    private val timeChangedReceiver: BroadcastReceiver = TimeChangeBroadcastReceiver { updateScheduleDisplayOnce() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val sharedPref = getSharedPreferences("info", Context.MODE_PRIVATE)
        group = sharedPref.getBoolean("group", group)

        val settingsB = findViewById<View>(R.id.settings)

        val settingsPopup = PopupWindow(this)
        var dismissTimer: Timer? = null
        fun setDismiss() {
            try{ dismissTimer?.cancel() } catch (e: Throwable) {}
            dismissTimer = Timer().apply {
                schedule(
                    object : TimerTask() {
                        override fun run() {
                            settingsPopup.contentView.post { settingsPopup.dismiss() }
                        }
                    },
                    200L
                )
            }
        }
        settingsPopup.apply menu@{
            contentView = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL

                    val itemParams = fun() = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        0
                    ).apply {
                        weight = 1.0f
                    }

                    val a = TypedValue()
                    assert(theme.resolveAttribute(
                        android.R.attr.colorControlHighlight,
                        a, true
                    ))
                    val pressedColor = a.data

                    addView(
                        FrameLayout(this@MainActivity).apply {
                            val switch = SwitchCompat(this@MainActivity).apply {
                                textSize = spToPx(10.0f)
                                setTextColor(resources.getColor(R.color.black))
                                isChecked = group
                                isClickable = false
                                isFocusable = false
                            }

                            fun updateSwitch(isChecked: Boolean) {
                                switch.isChecked = isChecked
                                switch.text = "Номер группы: " + if (isChecked) 2 else 1
                            }

                            foreground = RippleDrawable(
                                ColorStateList.valueOf(pressedColor),
                                null, null
                            )
                            setOnClickListener {
                                group = !group
                                updateSwitch(group)
                                val editor = sharedPref.edit()
                                editor.putBoolean("group", group)
                                editor.apply()
                                updateScheduleDisplay(Calendar.getInstance())
                                setDismiss()
                            }
                            isClickable = true
                            isFocusable = false

                            updateSwitch(group)

                            addView(
                                switch,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                ).apply {
                                    gravity = Gravity.FILL_HORIZONTAL or Gravity.CENTER_VERTICAL
                                    setPadding(dipToPx(10.0f).toInt())
                                }
                            )
                        },
                        itemParams()
                    )

                    addView(
                        FrameLayout(this@MainActivity).apply {
                            addView(
                                TextView(this@MainActivity).apply {
                                    textSize = spToPx(10.0f)
                                    setTextColor(resources.getColor(R.color.black))
                                    text = "Выбрать файл..."
                                    isClickable = false
                                    isFocusable = false
                                },
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                ).apply {
                                    gravity = Gravity.FILL_HORIZONTAL or Gravity.CENTER_VERTICAL
                                    setPadding(dipToPx(10.0f).toInt())
                                }
                            )



                            foreground = RippleDrawable(
                                ColorStateList.valueOf(pressedColor),
                                null, null
                            )
                            setOnClickListener {
                                openFile()
                                setDismiss()
                            }
                            isClickable = true
                            isFocusable = true
                        },
                        itemParams()
                    )

                    setBackgroundColor(Color.WHITE)


                    elevation = dipToPx(10.0f)
                }

            setBackgroundDrawable(ColorDrawable(Color.WHITE))
            elevation = dipToPx(10.0f)
            isOutsideTouchable = true
            isTouchable = true
            isFocusable = true
        }

        settingsB.setOnClickListener {
            try{ dismissTimer?.cancel() } catch (e: Throwable) {}
            val content = settingsPopup.contentView
            content.measure(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            settingsPopup.width = content.measuredWidth
            settingsPopup.height = content.measuredHeight
            settingsPopup.showAsDropDown(it)
        }

        val lessonsView = findViewById<ViewGroup>(R.id.lessonsView)
        val calendarView = findViewById<ViewGroup>(R.id.calendarView)
        val genImageView = findViewById<ViewGroup>(R.id.genImageView)

        currentEndTV = findViewById(R.id.currentEnd)
        nextEndTV = findViewById(R.id.nextEnd)

        run {
            val toLessonsView = findViewById<ImageView>(R.id.selectDayView)
            val toCalendarView = findViewById<ImageView>(R.id.selectWeekView)
            //val toGenImageView = findViewById<ImageView>(R.id.selectGenImageView)

            toLessonsView.setOnClickListener {
                toLessonsView.setColorFilter(
                    resources.getColor(R.color.purple_500),
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
                toCalendarView.clearColorFilter()
                //toGenImageView.clearColorFilter()
                lessonsView.visibility = View.VISIBLE
                calendarView.visibility = View.GONE
                genImageView.visibility = View.GONE
            }
            toCalendarView.setOnClickListener {
                toCalendarView.setColorFilter(
                    resources.getColor(R.color.purple_500),
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
                toLessonsView.clearColorFilter()
                //toGenImageView.clearColorFilter()
                calendarView.visibility = View.VISIBLE
                lessonsView.visibility = View.GONE
                genImageView.visibility = View.GONE
            }
            //toGenImageView.setOnClickListener {
            //    toGenImageView.setColorFilter(
            //        resources.getColor(R.color.purple_500),
            //        android.graphics.PorterDuff.Mode.SRC_IN
            //    )
            //    toCalendarView.clearColorFilter()
            //    toLessonsView.clearColorFilter()
            //    genImageView.visibility = View.VISIBLE
            //    calendarView.visibility = View.GONE
            //    lessonsView.visibility = View.GONE
            //}

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

        pager.setCurrentItem(1, false)
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                super.onPageScrollStateChanged(state)

                if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    if (pager.currentItem != 1) {
                        daysOffset += pager.currentItem - 1
                        pager.setCurrentItem(1, false)
                        updateScheduleDisplay(Calendar.getInstance())
                    }
                }
            }

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                Log.d("onPageScrolled", "$position $positionOffset $positionOffsetPixels")
            }

            override fun onPageSelected(position: Int) {
                Log.d("OnPageSelected", position.toString())
            }
        })

        updateScheduleWeekDisplay()

        genImageView.findViewById<Button>(R.id.updateGenImageB).setOnClickListener {
            try {
                val layoutT = genImageView.findViewById<EditText>(R.id.daysLayoutET).text
                val layout = run {
                    val layout0 = ArrayList<ArrayList<Int>>()
                    layout0.add(ArrayList())
                    var start = 0
                    for (i in layoutT.indices) {
                        if (layoutT[i] == ',') {
                            val index = layoutT.substring(start, i).trim().toInt()-1
                            layout0[layout0.lastIndex].add(index)
                            start = i+1
                        }
                        else if(layoutT[i] == '\n') {
                            layout0.add(ArrayList())
                        }
                    }

                    if(start != layoutT.length) throw RuntimeException(
                        "Ошибка, `,` должна быть последним символом"
                    )

                    layout0 as List<List<Int>>
                }

                val width0 = findViewById<EditText>(R.id.bitmapWidthET).text.toString().toIntOrNull()
                if(width0 == null || width0 <= 0) throw RuntimeException("Ширина не может быть <= 0")

                val tl = TableLayout(this).also { tl ->
                    //isStretchAllColumns = true
                    tl.setBackgroundColor(resources.getColor(R.color.light_gray))

                    tl.setPaddingRelative(
                        dipToPx(2.0f).toInt(),
                        dipToPx(2.0f).toInt(),
                        0,
                        0
                    )

                    for(row in layout) {
                        val days = TableRow(this@MainActivity)
                        tl.addView(
                            days,
                            TableLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            ).apply {
                                gravity = Gravity.FILL_VERTICAL
                            }
                        )

                        for(dayIndex in row) {
                            val day = TableLayout(this@MainActivity)

                            days.addElement(
                                FrameLayout(this@MainActivity).apply {
                                    addView(
                                        VerticalTextView(this@MainActivity).apply {
                                            text = dayNames[dayIndex]
                                            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
                                            setTextColor(resources.getColor(R.color.black))
                                            textSize = spToPx(12.0f)
                                        },
                                        FrameLayout.LayoutParams(
                                            ViewGroup.LayoutParams.WRAP_CONTENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        ).apply {
                                            gravity = Gravity.CENTER
                                        }
                                    )
                                },
                                TableRow.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )

                            addDayDisplay(day, dayIndex)

                            day.setBackgroundColor(0xffff00ffL.toInt())
                            day.gravity = Gravity.FILL_VERTICAL

                            days.addView(
                                day,
                                TableRow.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                ).apply {
                                    this.weight = 1.0f
                                }
                            )

                            day.gravity = Gravity.FILL_VERTICAL
                            days.gravity = Gravity.FILL_VERTICAL
                        }
                    }
                }

                tl.measure(
                    MeasureSpec.makeMeasureSpec(width0, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                )
                tl.layout(0, 0, tl.measuredWidth, tl.measuredHeight)

                val iv = findViewById<Image_View>(R.id.image_view)

                iv.bitmap?.recycle()
                val bmp = Bitmap.createBitmap(
                    tl.measuredWidth, tl.measuredHeight, Bitmap.Config.ARGB_8888
                )

                tl.draw(Canvas(bmp))

                iv.bitmap = bmp
                iv.layoutParams = LinearLayout.LayoutParams(tl.measuredWidth, tl.measuredHeight)
                iv.invalidate()
            }
            catch (e: Throwable) {
                AlertDialog.Builder(this)
                    .setMessage(e.stackTraceToString())
                    .setPositiveButton(android.R.string.ok) { dialog, which -> dialog.dismiss() }
                    .show();
                val toast = Toast.makeText(
                    applicationContext,
                    "Error generating image",
                    Toast.LENGTH_LONG
                )
                Log.e("ERROR", "", e)
                toast.show()
            }
        }
    }

    private fun updateScheduleWeekDisplay() {
        val week = findViewById<LinearLayout>(R.id.week)
        week.removeAllViews()

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

            addDayDisplay(day, dayIndex)
        }
    }

    private fun addDayDisplay(day: TableLayout, dayIndex: Int) {
        val curDay = schedule.week[dayIndex]

        val lessonIndicesRange = run {
            val range0 = curDay.lessons[0].calculateNozeropaddingRange()
            val range1 = curDay.lessons[1].calculateNozeropaddingRange()
            val range2 = curDay.lessons[2].calculateNozeropaddingRange()
            val range3 = curDay.lessons[3].calculateNozeropaddingRange()

            return@run IntRange(
                range0.first min range1.first min range2.first min range3.first,
                range0.last max range1.last max range2.last max range3.last,
            )
        }

        for(i in lessonIndicesRange) {
            val time = curDay.time[i]
            fun lessonAt(group: Boolean, week: Boolean) =
                curDay.getForGroupAndWeek(group, week).let {
                    if (i in it.indices) it[i] else 0
                }

            val row = TableRow(this)
            day.addView(
                row,
                TableLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ).apply {
                    this.weight = 1.0f
                }
            )

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
                val view = if(lessonIndex <= 0)
                    FrameLayout(this@MainActivity).apply { addView(View(this@MainActivity)) }
                else {
                    val lesson = curDay.lessonsUsed[lessonIndex - 1]
                    FrameLayout(this@MainActivity).apply {
                        addView(
                            AppCompatTextView(this@MainActivity).apply {
                                val noBreakSpace = '\u00A0'
                                val textSB = StringBuilder()
                                textSB.append(lesson.name)
                                fun addOther(text: String) {
                                    if (text.isEmpty()) return
                                    textSB.append(' ')
                                    val newText = if (text.length < 20) text.replace(' ', noBreakSpace)
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
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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
                    },
                    colorRes
                )
            }

            val groupHorizontal = lessonAt(group = false, false) == lessonAt(group = true, false) &&
                lessonAt(group = false, true) == lessonAt(group = true, true)
            val groupVertical = lessonAt(false, week = false) == lessonAt(false, week = true) &&
                lessonAt(true, week = false) == lessonAt(true, week = true)

            row.addView(
                TableLayout(this).apply {
                    isStretchAllColumns = true
                    when {
                        groupHorizontal && groupVertical -> {
                            addView(
                                TableRow(this@MainActivity).apply {
                                    addWrapLesson(lessonAt(false, false))
                                },
                                TableLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                ).apply {
                                    weight = 1.0f //very important!
                                    //match parent for height is not enough, thx Android
                                }
                            )
                        }
                        !groupHorizontal && !groupVertical -> {
                            addView(
                                TableRow(this@MainActivity).apply {
                                    addWrapLesson(lessonAt(group = false, week = false))
                                    addWrapLesson(lessonAt(group = true, week = false))
                                },
                                TableLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                            addView(
                                TableRow(this@MainActivity).apply {
                                    addWrapLesson(lessonAt(group = false, week = true), R.color.yellow)
                                    addWrapLesson(lessonAt(group = true, week = true), R.color.yellow)
                                },
                                TableLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                ).apply {
                                    weight = 1.0f //very important!
                                    //match parent for height is not enough, thx Android
                                }
                            )
                        }
                        groupHorizontal -> {
                            addView(
                                TableRow(this@MainActivity).apply {
                                    addWrapLesson(lessonAt(false, week = false))
                                },
                                TableLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                            addView(
                                TableRow(this@MainActivity).apply {
                                    addWrapLesson(lessonAt(false, week = true), R.color.yellow)
                                },
                                TableLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                ).apply {
                                    weight = 1.0f //very important!
                                    //match parent for height is not enough, thx Android
                                }
                            )
                        }
                        groupVertical -> {
                            addView(
                                TableRow(this@MainActivity).apply {
                                    addWrapLesson(lessonAt(group = false, false))
                                    addWrapLesson(lessonAt(group = true, false))
                                },
                                TableLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                ).apply {
                                    weight = 1.0f //very important!
                                    //match parent for height is not enough, thx Android
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

    private fun updateScheduleDisplayOnce() {
        window.decorView.post{
            val calendar = Calendar.getInstance()
            val curDate = calendar.copyToMinute()
            if(lastUpdate == null || curDate == lastUpdate) {
                lastUpdate = curDate
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

        try { timer?.cancel() } catch (e: Throwable) {}
        val newTimer = Timer()
        timer = newTimer
        newTimer.schedule(object : TimerTask() {
            override fun run() {
                updateScheduleDisplayTimed()
            }
        }, nextDate)
    }

    override fun onPause() {
        super.onPause()
        try { timer?.cancel() } catch (e: Throwable) {}

        unregisterReceiver(timeChangedReceiver)
    }

    override fun onResume() {
        super.onResume()
        updateScheduleDisplayTimed()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        registerReceiver(timeChangedReceiver, filter)
    }

    private fun calculateDayLessons(currentDay: Day, curMinuteOfDay: Int, weekIndex: Boolean): Pair<Array<DayElement>, Int> {
        val currentLessonIndices = currentDay.getForGroupAndWeek(group, weekIndex)
        val lessonIndicesRange = currentLessonIndices.calculateNozeropaddingRange()

        val dayElements = ArrayList<DayElement>()
        fun prevElNotBrake(): Boolean = dayElements.isEmpty() || dayElements.last().lesson != null

        var activeLesson = if(
            lessonIndicesRange.isEmpty() || curMinuteOfDay < currentDay.time[lessonIndicesRange.first].first
        ) -1
        else 0

        for(i in lessonIndicesRange) {
            val lessonIndex = currentLessonIndices[i]
            val lessonMinutes = currentDay.time[i]

            val curLesson = if(lessonIndex <= 0) null else currentDay.lessonsUsed[lessonIndex - 1]
            if(curLesson != null || prevElNotBrake()) dayElements.add(DayElement(curLesson, lessonMinutes))
            else {
                val lastElement = dayElements[dayElements.lastIndex]
                dayElements[dayElements.lastIndex] = DayElement(
                    null,
                    IntRange(lastElement.time.first, lessonMinutes.last),
                )
            }

            if(curMinuteOfDay > lessonMinutes.last) activeLesson = dayElements.size //next one

            if(i != lessonIndicesRange.last) {
                val nextLessonMinutes = currentDay.time[i + 1]

                val breakMinutes = IntRange(lessonMinutes.last, nextLessonMinutes.first)
                if(prevElNotBrake()) dayElements.add(DayElement(
                    null, breakMinutes
                ))
                else {
                    val lastElement = dayElements[dayElements.lastIndex]
                    dayElements[dayElements.lastIndex] = DayElement(
                        null,
                        IntRange(lastElement.time.first, breakMinutes.last),
                    )
                }

                if(curMinuteOfDay > breakMinutes.last) activeLesson = dayElements.size //next one
            }
        }

        return dayElements.toTypedArray() to activeLesson
    }

    private fun updateScheduleDisplay(now: Calendar) {
        fun calcAndUpdateDayLessons(timepoint: Calendar, dayOffset: Int, isCurDay: Boolean): DayLessons {
            val curHour = timepoint.get(Calendar.HOUR_OF_DAY)
            val curMinute = timepoint.get(Calendar.MINUTE)
            val curDayOfWeek = floorMod(timepoint.get(Calendar.DAY_OF_WEEK) - 2, 7) //Monday is 2 but should be 0, Sunday is 0 but should be 6
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
                timepoint.get(Calendar.YEAR) - 1900,
                timepoint.get(Calendar.MONTH),
                timepoint.get(Calendar.DAY_OF_MONTH)
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

            val (dayLessons, activeLesson) = calculateDayLessons(currentDay, curMinuteOfDay, weekIndex)

            if(isCurDay) when {
                dayLessons.isEmpty() -> {
                    currentEndTV.text = "Выходной"
                    nextEndTV.text = ""
                }
                activeLesson == -1 -> {
                    val startLessonMinutes = dayLessons[0].time
                    currentEndTV.text = "До начала учебного дня: ${startLessonMinutes.first - curMinuteOfDay} мин."
                    nextEndTV.text = ""
                }
                activeLesson == dayLessons.size -> {
                    currentEndTV.text = "Конец учебного дня"
                    nextEndTV.text = ""
                }
                else -> {
                    val lessonElement = dayLessons[activeLesson]
                    val lesson = lessonElement.lesson
                    val lessonMinutes = lessonElement.time

                    val nextLessonMinutes = if(activeLesson+1 in dayLessons.indices) dayLessons[activeLesson + 1].time else null

                    if(lesson == null) {
                        currentEndTV.text = "До конца перемены: ${lessonMinutes.last - curMinuteOfDay} мин."
                        if(nextLessonMinutes != null) nextEndTV.text = "До конца след. пары: ${nextLessonMinutes.last - curMinuteOfDay} мин."
                        else {
                            Log.e("ERROR", "nextLessonMinutes is null for empty lesson element (which means that last lesson is a break)")
                            nextEndTV.text = ""
                        }
                    }
                    else {
                        currentEndTV.text = "До конца пары: ${lessonMinutes.last - curMinuteOfDay} мин."
                        if(nextLessonMinutes != null) {
                            nextEndTV.text = "До конца перемены: ${nextLessonMinutes.last - curMinuteOfDay} мин."
                        }
                        else nextEndTV.text = "Последняя пара"
                    }
                }
            }

            return DayLessons(timepoint, dayOffset, weekIndex, dayLessons, if (isCurDay) activeLesson else -1)
        }

        var updateCurDay = true
        val daysLessons = Array(3) {
            val dayOffset = daysOffset + it-1
            val isCurDay = dayOffset == 0
            if(isCurDay) updateCurDay = false
            calcAndUpdateDayLessons(
                (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, dayOffset) },
                dayOffset,
                isCurDay
            )
        }

        if(updateCurDay) calcAndUpdateDayLessons(now, dayOffset = 0, isCurDay = true)

        dayLessonAdapter.updateDays(daysLessons)
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
                    AlertDialog.Builder(this)
                        .setMessage(e.stackTraceToString())
                        .setPositiveButton(android.R.string.ok) { dialog, which -> dialog.dismiss() }
                        .show();
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

    private companion object {
        val dayNames = arrayOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")

        fun Calendar.copyToMinute() = (clone() as Calendar).apply {
            clear()
            set(Calendar.YEAR, get(Calendar.YEAR))
            set(Calendar.MONTH, get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, get(Calendar.MINUTE))
        }
    }
}