package com.shilapi.xcertplay.legacy

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.shilapi.xcertplay.AirPlayPersistence
import com.shilapi.xcertplay.media.GeelyAudioCapabilities
import com.shilapi.xcertplay.media.MediaAudioBuffer
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode

/**
 * Legacy settings, step 2: the knobs a GL19-class unit actually needs, in native Views.
 *
 * Every value is written to the engine's own preferences right away and applies to the next
 * session start, matching what the engine's own screens do. No engine screen is shown here.
 */
class LegacySettingsActivity : Activity() {
    private lateinit var hotspotModeValue: TextView
    private lateinit var scaleValue: TextView
    private lateinit var fpsValue: TextView
    private lateinit var bufferValue: TextView
    private lateinit var hevcSwitch: Switch
    private var geelyRoutingSwitch: Switch? = null
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
        Thread(task, "carlink-legacy-settings").apply { isDaemon = true }
    }

    private val scales = listOf(10, 8, 6)
    private val scaleLabels = listOf("原始分辨率", "80%（更轻）", "60%（最轻）")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun buildContent(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(32), dp(24), dp(32), dp(32))
            setBackgroundColor(BG)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(text("设置", 28, TEXT, true), LinearLayout.LayoutParams(0, WRAP, 1f))
        header.addView(action("返回") { finish() }, LinearLayout.LayoutParams(dp(110), dp(48)))
        root.addView(header)

        section(root, "连接")
        hotspotModeValue = valueRow(root, "热点方式") {
            val modes = listOf(WirelessHotspotMode.MANUAL, WirelessHotspotMode.WIFI_P2P)
            val labels = listOf("车机原厂热点", "本地直连（不接管手机流量）")
            val current = modes.indexOf(AirPlayPersistence.loadWirelessHotspotMode(this)).coerceAtLeast(0)
            choose("热点方式", labels, current) { index ->
                AirPlayPersistence.saveWirelessHotspotMode(this, modes[index])
                refreshValues()
            }
        }
        root.addView(
            text(
                "本地直连：用 Wi-Fi Direct 组做车机与手机的专用网络，不提供互联网，手机继续用移动数据，" +
                    "状态栏不会把它当成上网的 Wi-Fi（与 GLAPHotspot 的本地 P2P 同一机制）。" +
                    "部分车机固件不支持建组，失败时可切回车机原厂热点。\n" +
                    "车机原厂热点：使用车机自己的热点；手机能否继续用移动数据取决于车机是否给它上网。",
                13, MUTED,
            ),
            marginTop(8),
        )

        section(root, "画面")
        scaleValue = valueRow(root, "分辨率") {
            val selected = scales.indexOf(AirPlayPersistence.loadDisplayScaleTenths(this)).coerceAtLeast(0)
            choose("分辨率", scaleLabels, selected) { index ->
                AirPlayPersistence.saveDisplayScaleTenths(this, scales[index])
                refreshValues()
            }
        }
        fpsValue = valueRow(root, "帧率") {
            val current = if (AirPlayPersistence.loadFps(this) == 60) 1 else 0
            choose("帧率", listOf("30 fps（更省）", "60 fps（更顺）"), current) { index ->
                AirPlayPersistence.saveFps(this, if (index == 1) 60 else 30)
                refreshValues()
            }
        }
        hevcSwitch = toggleRow(root, "HEVC 视频（更省带宽，老车机不一定支持）") { checked ->
            AirPlayPersistence.saveHevcEnabled(this, checked)
        }

        section(root, "音频")
        bufferValue = valueRow(root, "音乐缓冲") {
            val presets = MediaAudioBuffer.presets
            val labels = presets.map { if (it == MediaAudioBuffer.DEFAULT_MILLIS) "$it ms（默认）" else "$it ms" }
            val current = presets.indexOf(AirPlayPersistence.loadMediaBufferMillis(this)).coerceAtLeast(0)
            choose("音乐缓冲", labels, current) { index ->
                AirPlayPersistence.saveMediaBufferMillis(this, presets[index])
                refreshValues()
            }
        }
        if (GeelyAudioCapabilities.detect() != null) {
            geelyRoutingSwitch = toggleRow(root, "吉利原厂音频通道（热点语音/音乐分离）") { checked ->
                AirPlayPersistence.saveGeelyAudioRouting(this, checked)
            }
        }

        section(root, "诊断")
        fullRow(root, "导出诊断报告（优先写到 U 盘）") { exportReport() }
        fullRow(root, "连接自检") { showSelfCheck() }

        section(root, "关于")
        infoRow(root, "应用", "CarLink Legacy")
        infoRow(root, "版本", versionName())
        infoRow(root, "包名", packageName)
        infoRow(root, "适用系统", "Android 4.4 及以上（API 19+）")
        root.addView(
            text("设置在下一次连接时生效；连接中修改会等到重连后应用。", 14, MUTED),
            marginTop(16),
        )

        refreshValues()
        return ScrollView(this).apply { addView(root) }
    }

    private fun fullRow(parent: LinearLayout, title: String, click: () -> Unit) {
        val button = action(title) { click() }
        parent.addView(button, marginTop(10))
    }

    private fun exportReport() {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        val fileName = "CarLinkLegacy-report-$stamp.txt"
        val appContext = applicationContext
        worker.execute {
            val outcome = runCatching { LegacyDiagnosticExport.export(appContext, fileName) }
            runOnUiThread {
                outcome.getOrNull()?.let { result ->
                    val message = if (result.usedPrivateStorage) {
                        "已保存到应用私有目录（未找到可写的外部存储）：\n" + result.path
                    } else {
                        "已保存：\n" + result.path
                    }
                    AlertDialog.Builder(this).setTitle("诊断报告").setMessage(message)
                        .setPositiveButton("知道了", null).show()
                } ?: AlertDialog.Builder(this).setTitle("导出失败")
                    .setMessage(outcome.exceptionOrNull()?.message ?: "未知错误")
                    .setPositiveButton("知道了", null).show()
            }
        }
    }

    private fun showSelfCheck() {
        val appContext = applicationContext
        worker.execute {
            val report = com.shilapi.xcertplay.AutoConnectSelfCheck.report(appContext) + "\n\n" +
                com.shilapi.xcertplay.AutoConnectDiagnostics.report(appContext)
            runOnUiThread {
                val text = text(report, 14, TEXT).apply { setPadding(dp(20), dp(16), dp(20), dp(16)) }
                AlertDialog.Builder(this).setTitle("连接自检")
                    .setView(ScrollView(this).apply { addView(text) })
                    .setPositiveButton("关闭", null).show()
            }
        }
    }

    private fun section(parent: LinearLayout, title: String) {
        parent.addView(text(title, 13, ACCENT, true), marginTop(24))
    }

    private fun valueRow(parent: LinearLayout, title: String, click: () -> Unit): TextView {
        val line = row()
        line.addView(text(title, 17, TEXT), LinearLayout.LayoutParams(0, WRAP, 1f))
        val value = text("", 15, MUTED)
        line.addView(value)
        line.setOnClickListener { click() }
        parent.addView(line)
        return value
    }

    private fun infoRow(parent: LinearLayout, title: String, value: String) {
        val line = row()
        line.addView(text(title, 15, MUTED), LinearLayout.LayoutParams(0, WRAP, 1f))
        line.addView(text(value, 15, TEXT))
        parent.addView(line)
    }

    private fun toggleRow(parent: LinearLayout, title: String, save: (Boolean) -> Unit): Switch {
        val line = row()
        line.addView(text(title, 17, TEXT), LinearLayout.LayoutParams(0, WRAP, 1f))
        val switch = Switch(this).apply {
            contentDescription = title
            setOnCheckedChangeListener { _, checked -> save(checked) }
        }
        line.addView(switch)
        parent.addView(line)
        return switch
    }

    private fun choose(title: String, options: List<String>, selected: Int, onPick: (Int) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(options.toTypedArray(), selected) { dialog, which ->
                onPick(which)
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun refreshValues() {
        hotspotModeValue.text =
            if (AirPlayPersistence.loadWirelessHotspotMode(this) == WirelessHotspotMode.WIFI_P2P) {
                "本地直连"
            } else {
                "车机原厂热点"
            }
        scaleValue.text = scaleLabels[scales.indexOf(AirPlayPersistence.loadDisplayScaleTenths(this)).coerceAtLeast(0)]
        fpsValue.text = if (AirPlayPersistence.loadFps(this) == 60) "60 fps" else "30 fps"
        bufferValue.text = AirPlayPersistence.loadMediaBufferMillis(this).toString() + " ms"
        hevcSwitch.isChecked = AirPlayPersistence.loadHevcEnabled(this)
        geelyRoutingSwitch?.isChecked = AirPlayPersistence.loadGeelyAudioRouting(this)
    }

    private fun versionName(): String = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
    }.getOrDefault("unknown")

    // ---- small view helpers ---------------------------------------------------------------

    private fun row() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(14), 0, dp(14))
        minimumHeight = dp(56)
    }

    private fun text(value: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        typeface = if (bold) Typeface.create("sans-serif-medium", Typeface.NORMAL)
        else Typeface.create("sans-serif", Typeface.NORMAL)
        setLineSpacing(dp(3).toFloat(), 1f)
    }

    private fun action(title: String, click: () -> Unit) = Button(this).apply {
        text = title
        isAllCaps = false
        textSize = 16f
        setTextColor(TEXT)
        background = GradientDrawable().apply {
            setColor(SURFACE)
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), BORDER)
        }
        minHeight = dp(48)
        setOnClickListener { click() }
    }

    private fun marginTop(value: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(value) }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        val BG = Color.rgb(12, 17, 27)
        val SURFACE = Color.rgb(21, 30, 44)
        val BORDER = Color.rgb(42, 56, 75)
        val ACCENT = Color.rgb(166, 200, 255)
        val TEXT = Color.rgb(241, 245, 252)
        val MUTED = Color.rgb(168, 182, 202)
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
