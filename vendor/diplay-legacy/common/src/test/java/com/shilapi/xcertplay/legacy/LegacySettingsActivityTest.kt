package com.shilapi.xcertplay.legacy

import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import com.shilapi.xcertplay.AirPlayPersistence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class LegacySettingsActivityTest {
    @Test
    fun settingsShowTheEngineValuesAndSurviveLifecycle() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        AirPlayPersistence.saveFps(app, 30)
        AirPlayPersistence.saveMediaBufferMillis(app, 500)
        AirPlayPersistence.saveDisplayScaleTenths(app, 10)

        val controller = Robolectric.buildActivity(LegacySettingsActivity::class.java).setup()
        val activity = controller.get()
        val content = activity.findViewById<View>(android.R.id.content) as ViewGroup
        val root = (content.getChildAt(0) as ScrollView).getChildAt(0) as ViewGroup
        val texts = collectTexts(root)
        assertTrue("settings must list the display scale: $texts", texts.any { it == "分辨率" })
        assertTrue("settings must list the audio buffer: $texts", texts.any { it == "音乐缓冲" })
        assertTrue(
            "the buffer value must reflect the stored preference: $texts",
            texts.any { it == "500 ms" },
        )
        assertEquals(true, texts.any { it == "CarLink Legacy" })
        controller.pause().stop().destroy()
    }

    private fun collectTexts(group: ViewGroup): List<String> {
        val out = mutableListOf<String>()
        for (index in 0 until group.childCount) {
            when (val child = group.getChildAt(index)) {
                is TextView -> out += child.text?.toString().orEmpty()
                is ViewGroup -> out += collectTexts(child)
            }
        }
        return out
    }
}
