package com.shilapi.xcertplay.legacy

import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Smoke test for the Legacy home: it must inflate and survive a resume/pause cycle. This cannot
 * prove a 4.4 head unit, but it catches a broken theme, view or lifecycle before a car test does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class LegacyHomeActivityTest {
    @Test
    fun homeBuildsAndShowsReadiness() {
        val controller = Robolectric.buildActivity(LegacyHomeActivity::class.java).setup()
        val activity = controller.get()
        val content = activity.findViewById<View>(android.R.id.content)
        assertNotNull(content)
        val scroll = (content as ViewGroup).getChildAt(0) as ScrollView
        val root = scroll.getChildAt(0) as ViewGroup
        val texts = mutableListOf<String>()
        for (index in 0 until root.childCount) {
            val child = root.getChildAt(index)
            if (child is TextView) texts += child.text?.toString().orEmpty()
            if (child is ViewGroup) {
                for (inner in 0 until child.childCount) {
                    (child.getChildAt(inner) as? TextView)?.text?.let { texts += it.toString() }
                }
            }
        }
        assertTrue("home must show a self-check line: $texts", texts.any { it.startsWith("自检：") })
        controller.pause().stop().destroy()
    }
}
