package com.idk.schedule

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.provider.SyncStateContract.Helpers.update
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

class Image_View : View {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    constructor(
        context: Context?,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    var bitmap: Bitmap? = null
    //var originalSize: Boolean = false

    //fun update() {
    //    layoutParams = if(originalSize && bitmap != null) {
    //        layoutParams?.apply {
    //            width = bitmap!!.width
    //            height = bitmap!!.height
    //        } ?: ViewGroup.LayoutParams(bitmap!!.width, bitmap!!.height)
    //    }
    //    else layoutParams?.apply {
    //        width = ViewGroup.LayoutParams.MATCH_PARENT
    //        height = ViewGroup.LayoutParams.MATCH_PARENT
    //    } ?: ViewGroup.LayoutParams(
    //        ViewGroup.LayoutParams.MATCH_PARENT,
    //        ViewGroup.LayoutParams.MATCH_PARENT
    //    )
    //
    //    invalidate()
    //}

    private val paint = Paint()

    @SuppressLint("MissingSuperCall")
    override fun draw(canvas: Canvas?) {
        super.draw(canvas)
        val bmp = bitmap ?: return
        canvas ?: return

        canvas.drawBitmap(bmp, 0.0f, 0.0f, paint)

        /*if(originalSize) {
            canvas.drawBitmap(bmp, 0.0f, 0.0f, paint)
        }
        else {
            val width = measuredWidth
            val height = measuredHeight

            val (sWidth, sHeight) = run {
                val height0 = bmp.height * width / bmp.width
                if(height0 <= height) width to height0
                else (bmp.width * height / bmp.height) to height
            }

            canvas.drawBitmap(
                bmp,
                Rect(0, 0, bmp.width, bmp.height),
                Rect(
                    (width - sWidth)/2, (height - sHeight)/2,
                    (width + sWidth)/2, (height + sHeight)/2
                ),
                paint
            )
        }*/
    }
}