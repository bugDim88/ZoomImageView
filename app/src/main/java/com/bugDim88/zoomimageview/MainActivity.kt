package com.bugDim88.zoomimageview

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.viewpager.widget.PagerAdapter
import com.bugdim88.zoomimage.ZoomImagePager
import com.bugdim88.zoomimage.ZoomImageView
import java.io.IOException
import java.io.InputStream

class MainActivity : AppCompatActivity() {

    private val imagesFiles = listOf(
        "sea1.jpg",
        "sea2.jpg",
        "sea3.jpg",
        "sea4.jpg"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val viewPager = findViewById<ZoomImagePager>(R.id.view_pager)
        val images = imagesFiles.mapNotNull(::getImageFromAsset)
        val pagerAdapter = ImageAdapter(images)
        viewPager.adapter = pagerAdapter
    }

    private fun getImageFromAsset(fileName: String): Drawable? {
        var result: Drawable? = null
        var imageStream: InputStream? = null
        try {
            imageStream = assets.open(fileName)
            result = Drawable.createFromStream(imageStream, null)
        } catch (ex: IOException) {
            ex.printStackTrace()
        } finally {
            imageStream?.close()
            return result
        }
    }

    class ImageAdapter(private val items: List<Drawable>) : PagerAdapter() {
        override fun isViewFromObject(view: View, `object`: Any): Boolean = `object` == view

        override fun getCount(): Int = items.size

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val zoomImageView = ZoomImageView(container.context)
            val layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
            zoomImageView.layoutParams = layoutParams
            zoomImageView.setImageDrawable(items[position])
            container.addView(zoomImageView)
            return zoomImageView
        }

        override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) =
            container.removeView(`object` as View)
    }
}
