package com.bugdim88.zoomimage

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.viewpager.widget.ViewPager

/**
 * Imlementation of [ViewPager] that can paging [ZoomImageView] child views
 */
class ZoomImagePager : ViewPager {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    var scrollEnabled = true

    override fun canScroll(v: View?, checkV: Boolean, dx: Int, x: Int, y: Int): Boolean {
        if(!scrollEnabled) return true
        if (v is ZoomImageView && scrollEnabled) {
            return v.canScrollHorizontally(-dx)
        }
        return super.canScroll(v, checkV, dx, x, y)
    }
}