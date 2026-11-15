package com.shilapi.xcertplay.legacy

import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import com.shilapi.xcertplay.AirPlayPersistence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Dalvik-era smoke test: CarLink Legacy must inflate and survive its lifecycle on the Android
 * 4.4 (API 19) framework, where `values/` resources win (Theme.Holo, no Theme.Material) and every
 * API used by the screens has to exist on the device.
 *
 * Run it with Robolectric 4.12.2 (4.13+ dropped API 19):
 * `./gradlew :legacy-common:testDebugUnitTest -Pdiplay.robolectric=4.12.2 \
 *    --tests "com.shilapi.xcertplay.legacy.LegacyApi19SmokeTest"`
 *
 * It is a JVM test under the real 4.4 framework classes, not Dalvik: it cannot prove dexopt, the
 * GPU, Bluetooth or the head unit's own hotspot firmware. Those still need the car test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [19], qualifiers = "w1280dp-h720dp-mdpi")
class LegacyApi19SmokeTest {
    @Test
    fun theTestReallyRunsOnTheApi19Framework() {
        assertEquals(19, Build.VERSION.SDK_INT)
    }

    @Test
    fun homeInflatesOnApi19() {
        val controller = Robolectric.buildActivity(LegacyHomeActivity::class.java).setup()
        val activity = controller.get()
        val content = activity.findViewById<View>(android.R.id.content)
        assertNotNull(content)
        val scroll = (content as ViewGroup).getChildAt(0) as ScrollView
        val root = scroll.getChildAt(0) as ViewGroup
        val texts = collectTexts(root)
        assertTrue("home must show a self-check line on API 19: $texts", texts.any { it.startsWith("自检：") })
        assertTrue("home must show the connect action on API 19: $texts", texts.any { it == "连接 CarPlay" })
        assertTrue("home must show the automatic-connect switch on API 19: $texts", texts.any { it == "打开 CarLink 时连接" })
        controller.pause().stop().destroy()
    }

    @Test
    fun settingsInflateOnApi19() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        AirPlayPersistence.saveMediaBufferMillis(app, 500)
        val controller = Robolectric.buildActivity(LegacySettingsActivity::class.java).setup()
        val activity = controller.get()
        val content = activity.findViewById<View>(android.R.id.content) as ViewGroup
        val root = (content.getChildAt(0) as ScrollView).getChildAt(0) as ViewGroup
        val texts = collectTexts(root)
        assertTrue("settings must list the display scale on API 19: $texts", texts.any { it == "分辨率" })
        assertTrue("settings must list the audio buffer on API 19: $texts", texts.any { it == "音乐缓冲" })
        assertTrue("settings must show the product name on API 19: $texts", texts.any { it == "CarLink Legacy" })
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
