package com.shilapi.xcertplay.legacy

import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.shilapi.xcertplay.AirPlayPersistence
import com.shilapi.xcertplay.AutoConnectAction
import com.shilapi.xcertplay.AutoConnectDiagnostics
import com.shilapi.xcertplay.AutoConnectPolicy
import com.shilapi.xcertplay.AutoConnectSelfCheck
import com.shilapi.xcertplay.CarPlayBackgroundSession
import com.shilapi.xcertplay.CarPlayHostActivity
import com.shilapi.xcertplay.DiPlayBootstrap
import com.shilapi.xcertplay.DiPlayPreferences
import com.shilapi.xcertplay.EXTRA_TRANSPORT_WIRELESS
import com.shilapi.xcertplay.compat.systemService
import com.shilapi.xcertplay.network.CarHotspotReadResult
import com.shilapi.xcertplay.network.CarHotspotStartResult
import com.shilapi.xcertplay.network.CarHotspotState
import com.shilapi.xcertplay.network.CarHotspotStatus
import com.shilapi.xcertplay.network.CarHotspotTools
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CarLink Legacy home, step 1: the driver's screen for a 4.x head unit.
 *
 * CarLink's own screens are Compose and cannot run below API 21, so this product carries a small
 * native-View screen set. The projection surface stays the engine's [CarPlayHostActivity]; nothing
 * else from the engine's own UI is reachable from here.
 */
class LegacyHomeActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var statusView: TextView
    private lateinit var readinessView: TextView
    private lateinit var connectButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var autoConnectSwitch: Switch
    private lateinit var autoHotspotSwitch: Switch
    private lateinit var bootSwitch: Switch
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "carlink-legacy-home").apply { isDaemon = true }
    }
    private val busy = AtomicBoolean(false)
    private var attempts = 0
    private var lastAttemptAt = 0L
    private var pausedUntil = 0L
    private var autoHotspotStartAttempted = false
    private var autoConnectSteps = 0
    private var authReady = true
    private var guideShown = false
    private val uiScale: Float by lazy {
        when {
            resources.configuration.screenWidthDp >= 1200 -> 1.35f
            resources.configuration.screenWidthDp >= 900 -> 1.15f
            else -> 1f
        }
    }
    @Volatile private var snapshot: StatusSnapshot? = null
    private var statusPolling = false
    private val statusPoll = object : Runnable {
        override fun run() {
            if (isFinishing || !statusPolling) return
            val context = applicationContext
            worker.execute {
                val next = readSnapshot(context)
                snapshot = next
                handler.post { if (!isFinishing) applySnapshot(next) }
            }
            handler.postDelayed(this, STATUS_POLL_MILLIS)
        }
    }

    private data class StatusSnapshot(
        val bluetoothOn: Boolean,
        val iPhones: Int,
        val hotspot: Boolean?,
        val readiness: AutoConnectSelfCheck.Readiness,
    )
    private val tick = object : Runnable {
        override fun run() {
            refreshStatus()
            maybeAutoConnect()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authReady = runCatching { DiPlayBootstrap.ensure(this) }.isSuccess
        if (!authReady) toast("认证信息加载失败，无法连接 CarPlay")
        setContentView(buildContent())
        statusPolling = true
        handler.post(statusPoll)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, 1000)
        if (!statusPolling) {
            statusPolling = true
            handler.post(statusPoll)
        }
        maybeAutoConnect()
        showFirstRunGuide()
    }

    private fun showFirstRunGuide() {
        if (guideShown) return
        guideShown = true
        val needsPhone = DiPlayPreferences.phoneAddress(this) == null
        val needsProfile = AirPlayPersistence.loadManualHotspotSsid(this) == null
        if (!needsPhone && !needsProfile) return
        AlertDialog.Builder(this)
            .setTitle("首次使用，三步设置")
            .setMessage(
                "1. 在 iPhone 上打开蓝牙，与本车机配对；\n" +
                    "2. 点“选择 iPhone”，选中已配对的那台；\n" +
                    "3. 打开车机热点后点“读取车机热点”，保存热点信息。\n\n" +
                    "之后点“连接 CarPlay”；想上车自动连，就把下面的“打开 CarLink 时连接 / 自动开启车机热点 / 车机启动后打开”都打开。",
            )
            .setPositiveButton("知道了", null)
            .show()
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        statusPolling = false
        handler.removeCallbacks(statusPoll)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        worker.shutdownNow()
        super.onDestroy()
    }

    // ---- UI -------------------------------------------------------------------------------

    private fun buildContent(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(32), dp(24), dp(32), dp(32))
            setBackgroundColor(BG)
        }
        val title = text("CarLink", 34, TEXT, true)
        val subtitle = text("Legacy · Android 4.x 车机版", 15, MUTED)
        root.addView(title)
        root.addView(subtitle, marginTop(6))

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            background = rounded(SURFACE, BORDER)
        }
        statusView = text("", 17, TEXT)
        card.addView(statusView)
        readinessView = text("", 14, MUTED)
        card.addView(readinessView, marginTop(8))
        val hint = text("iPhone 需先在车机蓝牙中配对；无线连接使用车机热点。", 14, MUTED)
        card.addView(hint, marginTop(10))
        root.addView(card, marginTop(24))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        connectButton = action("连接 CarPlay", primary = true) { connect() }
        disconnectButton = action("断开", primary = false) { disconnect() }
        row.addView(connectButton, weight(1))
        row.addView(space(12), LinearLayout.LayoutParams(dp(12), 1))
        row.addView(disconnectButton, weight(1))
        root.addView(row, marginTop(20))

        phoneButtonView = action(phoneLabel()) { choosePhone() }
        root.addView(phoneButtonView, marginTop(12))

        val hotspotRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        hotspotRow.addView(action("读取车机热点") { readHotspot() }, weight(1))
        hotspotRow.addView(space(12), LinearLayout.LayoutParams(dp(12), 1))
        hotspotRow.addView(action("开启热点") { startHotspot() }, weight(1))
        root.addView(hotspotRow, marginTop(12))

        val toolsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        toolsRow.addView(action("连接自检") { showDiagnostics() }, weight(1))
        toolsRow.addView(space(12), LinearLayout.LayoutParams(dp(12), 1))
        toolsRow.addView(action("设置") {
            runCatching { startActivity(Intent(this, LegacySettingsActivity::class.java)) }
        }, weight(1))
        root.addView(toolsRow, marginTop(12))

        root.addView(sectionTitle("自动连接"), marginTop(24))
        autoConnectSwitch = toggleRow(root, "打开 CarLink 时连接", DiPlayPreferences.autoConnect(this)) {
            DiPlayPreferences.saveAutoConnect(this, it)
        }
        autoHotspotSwitch = toggleRow(root, "自动开启车机热点", DiPlayPreferences.autoStartHotspot(this)) {
            DiPlayPreferences.saveAutoStartHotspot(this, it)
        }
        bootSwitch = toggleRow(root, "车机启动后打开", AirPlayPersistence.loadAutoStartOnBoot(this)) {
            AirPlayPersistence.saveAutoStartOnBoot(this, it)
        }

        val scroll = ScrollView(this).apply { addView(root) }
        return scroll
    }

    private var phoneButtonView: Button? = null

    private fun toggleRow(parent: LinearLayout, label: String, checked: Boolean, save: (Boolean) -> Unit): Switch {
        val line = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
        }
        line.addView(text(label, 17, TEXT), weight(1))
        val switch = Switch(this).apply {
            isChecked = checked
            minHeight = dp(48)
            contentDescription = label
            setOnCheckedChangeListener { _, value -> save(value) }
        }
        line.addView(switch)
        parent.addView(line)
        return switch
    }

    /** Cheap main-thread paint; every Binder read happens on the worker in [readSnapshot]. */
    private fun refreshStatus() {
        if (isFinishing) return
        val session = CarPlayBackgroundSession.hasSession()
        if (session) {
            attempts = 0
            lastAttemptAt = 0L
        }
        val current = snapshot
        val phone = DiPlayPreferences.phoneAddress(this)
        val phoneName = DiPlayPreferences.phoneName(this)
        statusView.text = buildString {
            append(if (session) "CarPlay 会话进行中" else "未连接")
            append("\n蓝牙 ").append(
                when (current?.bluetoothOn) {
                    true -> "已开启"
                    false -> "未开启"
                    null -> "读取中…"
                },
            )
            append(" · 已配对 iPhone ").append(current?.iPhones ?: "-").append(" 台")
            append("\n热点 ").append(
                when (current?.hotspot) {
                    true -> "已开启"
                    false -> "未开启"
                    null -> "状态未知"
                },
            )
            append(" · 已选手机 ").append(if (phone == null) "未选择" else phoneName)
        }
        applyReadiness(current?.readiness)
        connectButton.isEnabled = !session
        disconnectButton.isEnabled = session
        autoConnectSwitch.isChecked = DiPlayPreferences.autoConnect(this)
        autoHotspotSwitch.isChecked = DiPlayPreferences.autoStartHotspot(this)
        bootSwitch.isChecked = AirPlayPersistence.loadAutoStartOnBoot(this)
    }

    private fun applySnapshot(@Suppress("UNUSED_PARAMETER") next: StatusSnapshot) {
        if (isFinishing) return
        refreshStatus()
    }

    private fun applyReadiness(readiness: AutoConnectSelfCheck.Readiness?) {
        if (!authReady) {
            readinessView.text = "自检：认证信息不可用，无法连接 CarPlay"
            readinessView.setTextColor(WARNING)
            return
        }
        val now = System.nanoTime() / 1_000_000L
        if (now < pausedUntil) {
            readinessView.text = "自检：已手动断开，自动连接暂停中"
            readinessView.setTextColor(WARNING)
            return
        }
        readinessView.text = when (readiness) {
            null -> "自检：正在读取状态…"
            AutoConnectSelfCheck.Readiness.AUTO_CONNECT_OFF -> "自检：自动连接已关闭"
            AutoConnectSelfCheck.Readiness.PROFILE_INCOMPLETE -> "自检：还没有保存车机热点信息"
            AutoConnectSelfCheck.Readiness.NO_PHONE -> "自检：还没有选择 iPhone"
            AutoConnectSelfCheck.Readiness.BLUETOOTH_OFF -> "自检：蓝牙未开启"
            AutoConnectSelfCheck.Readiness.HOTSPOT_OFF -> "自检：车机热点未开启（可打开下方自动开关）"
            AutoConnectSelfCheck.Readiness.READY -> "自检：就绪，可以连接"
        }
        readinessView.setTextColor(
            if (readiness == AutoConnectSelfCheck.Readiness.READY) MUTED else WARNING,
        )
    }

    private fun readSnapshot(context: android.content.Context): StatusSnapshot {
        val adapter = systemService(BluetoothManager::class.java, "bluetooth")?.adapter
        val bluetoothOn = runCatching { adapter?.isEnabled == true }.getOrDefault(false)
        val iPhones = runCatching {
            adapter?.bondedDevices?.count { it.name?.contains("iPhone", ignoreCase = true) == true } ?: 0
        }.getOrDefault(0)
        val mode = AirPlayPersistence.loadWirelessHotspotMode(context.applicationContext)
        val hotspot = if (mode == WirelessHotspotMode.MANUAL) {
            CarHotspotStatus.isEnabled(context) ?: CarHotspotTools.readState(context)?.let {
                it == CarHotspotState.ENABLED
            }
        } else {
            null
        }
        return StatusSnapshot(bluetoothOn, iPhones, hotspot, AutoConnectSelfCheck.readiness(context))
    }

    private fun phoneLabel(): String =
        "选择 iPhone · " + DiPlayPreferences.phoneName(this)

    // ---- actions --------------------------------------------------------------------------

    private fun maybeAutoConnect() {
        if (CarPlayBackgroundSession.hasSession()) return
        if (!DiPlayPreferences.autoConnect(this)) return
        val now = System.nanoTime() / 1_000_000L
        if (!AutoConnectPause.mayRetry(now, pausedUntil, lastAttemptAt, attempts, AUTO_CONNECT_MAX_ATTEMPTS)) return
        lastAttemptAt = now
        attempts += 1
        autoHotspotStartAttempted = false
        autoConnectSteps = 0
        AutoConnectDiagnostics.record(this, "start", "attempt=$attempts")
        handler.postDelayed(autoConnectStep, 700L)
    }

    private val autoConnectStep = object : Runnable {
        override fun run() {
            if (isFinishing) return
            if (++autoConnectSteps > AUTO_CONNECT_MAX_STEPS) {
                AutoConnectDiagnostics.record(applicationContext, "gave-up", "steps=$autoConnectSteps")
                return
            }
            val mode = AirPlayPersistence.loadWirelessHotspotMode(applicationContext)
            val profileSaved = mode != WirelessHotspotMode.MANUAL ||
                com.shilapi.xcertplay.orchestration.ManualHotspotValidation.error(
                    AirPlayPersistence.loadManualHotspotSsid(applicationContext).orEmpty(),
                    AirPlayPersistence.loadManualHotspotPassphrase(applicationContext).orEmpty(),
                ) == null
            val phoneSelected = DiPlayPreferences.phoneAddress(applicationContext) != null
            val running = CarPlayBackgroundSession.hasSession()
            val autoStartHotspot = DiPlayPreferences.autoStartHotspot(applicationContext)
            val context = applicationContext
            worker.execute {
                val state = readHotspotState(context, mode)
                val action = AutoConnectPolicy.decide(
                    hotspotMode = mode,
                    hotspotState = state,
                    autoStartHotspot = autoStartHotspot,
                    hotspotProfileSaved = profileSaved,
                    phoneSelected = phoneSelected,
                    sessionRunning = running,
                )
                handler.post { applyAutoConnectAction(action, state) }
            }
        }
    }

    private fun applyAutoConnectAction(action: AutoConnectAction, state: CarHotspotState?) {
        if (isFinishing) return
        AutoConnectDiagnostics.action(this, action.name)
        when (action) {
            AutoConnectAction.SKIP -> Unit
            AutoConnectAction.CONNECT -> {
                AutoConnectDiagnostics.record(this, "connect", "hotspot=${state ?: "unknown"}")
                connectWhenReady()
            }
            AutoConnectAction.WAIT_FOR_HOTSPOT -> {
                AutoConnectDiagnostics.record(this, "wait-hotspot", "hotspot=$state")
                handler.postDelayed(autoConnectStep, 2_000L)
            }
            AutoConnectAction.START_HOTSPOT -> startHotspotForAutoConnect()
            AutoConnectAction.NEEDS_SETUP -> {
                AutoConnectDiagnostics.record(this, "needs-setup", "hotspot=${state ?: "unknown"}")
                toast(
                    if (DiPlayPreferences.phoneAddress(this) == null) "请先选择已配对的 iPhone"
                    else "请先读取并保存车机热点信息",
                )
            }
        }
    }

    private fun readHotspotState(context: android.content.Context, mode: WirelessHotspotMode): CarHotspotState? {
        if (mode != WirelessHotspotMode.MANUAL) return null
        CarHotspotTools.readState(context)?.let { return it }
        return when (CarHotspotStatus.isEnabled(context)) {
            true -> CarHotspotState.ENABLED
            false -> CarHotspotState.DISABLED
            null -> null
        }
    }

    private fun startHotspotForAutoConnect() {
        if (autoHotspotStartAttempted) {
            AutoConnectDiagnostics.record(this, "start-hotspot-skipped")
            toast("车机热点未能自动开启，请手动开启后点连接")
            return
        }
        autoHotspotStartAttempted = true
        AutoConnectDiagnostics.record(this, "start-hotspot")
        val context = applicationContext
        worker.execute {
            val result = runCatching {
                CarHotspotTools.start(context, timeoutMillis = 20_000L)
            }
            handler.post {
                when (val outcome = result.getOrNull()) {
                    is CarHotspotStartResult.Enabled, is CarHotspotStartResult.AlreadyEnabled -> {
                        AutoConnectDiagnostics.record(this, "hotspot-enabled")
                        handler.postDelayed(autoConnectStep, 1_500L)
                    }
                    else -> {
                        val reason = (outcome as? CarHotspotStartResult.Failure)?.reason?.name
                            ?: result.exceptionOrNull()?.javaClass?.simpleName
                            ?: "unknown"
                        AutoConnectDiagnostics.record(this, "hotspot-failed", reason)
                        toast("自动开启车机热点失败：$reason")
                    }
                }
            }
        }
    }

    /** Cold boots enable the radio a few seconds after launch; wait before giving up. */
    private fun connectWhenReady() {
        val context = applicationContext
        worker.execute {
            val adapter = systemService(BluetoothManager::class.java, "bluetooth")?.adapter
            val deadline = System.nanoTime() + 20_000_000_000L
            while (adapter != null && !runCatching { adapter.isEnabled }.getOrDefault(false) &&
                System.nanoTime() < deadline) {
                Thread.sleep(500L)
            }
            handler.post { if (!isFinishing) connect() }
        }
    }

    private fun connect() {
        if (DiPlayPreferences.phoneAddress(this) == null) {
            toast("请先选择已配对的 iPhone")
            choosePhone()
            return
        }
        AirPlayPersistence.saveWirelessEnabled(this, true)
        startActivity(
            Intent(this, CarPlayHostActivity::class.java)
                .putExtra(EXTRA_TRANSPORT_WIRELESS, true)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
        )
    }

    private fun disconnect() {
        // The driver asked for this: no unattended start for a while (same idea as the main app's
        // 10-minute auto-connect pause after Disconnect).
        pausedUntil = System.nanoTime() / 1_000_000L + AutoConnectPause.MANUAL_PAUSE_MILLIS
        attempts = 0
        lastAttemptAt = 0L
        CarPlayBackgroundSession.stop { runOnUiThread { refreshStatus() } }
    }

    private fun choosePhone() {
        val adapter = systemService(BluetoothManager::class.java, "bluetooth")?.adapter
        if (adapter == null || !adapter.isEnabled) {
            AlertDialog.Builder(this)
                .setTitle("蓝牙未开启")
                .setMessage("请先在车机设置中打开蓝牙并配对 iPhone。")
                .setPositiveButton("打开蓝牙设置") { _, _ ->
                    runCatching { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                }
                .setNegativeButton("取消", null)
                .show()
            return
        }
        val devices = runCatching { adapter.bondedDevices.sortedBy { it.name ?: "" } }.getOrDefault(emptyList())
        if (devices.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("没有已配对的 iPhone")
                .setMessage("在 iPhone 上打开设置 → 蓝牙，与本车机配对后回到这里。")
                .setPositiveButton("知道了", null)
                .show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("选择 iPhone")
            .setItems(devices.map { it.name ?: "已配对设备" }.toTypedArray()) { _, index ->
                val device = devices[index]
                DiPlayPreferences.savePhone(this, device.address, device.name ?: "iPhone")
                refreshStatus()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** Read-only verdict for a car test: which precondition fails, if any. */
    private fun showDiagnostics() {
        worker.execute {
            val report = AutoConnectSelfCheck.report(applicationContext) + "\n\n" +
                AutoConnectDiagnostics.report(applicationContext)
            handler.post {
                if (isFinishing) return@post
                val text = TextView(this).apply {
                    this.text = report
                    textSize = 14f
                    setTextColor(TEXT)
                    setPadding(dp(20), dp(16), dp(20), dp(16))
                }
                AlertDialog.Builder(this)
                    .setTitle("连接自检")
                    .setView(ScrollView(this).apply { addView(text) })
                    .setPositiveButton("关闭", null)
                    .show()
            }
        }
    }

    private fun readHotspot() {
        if (!busy.compareAndSet(false, true)) return
        worker.execute {
            val result = CarHotspotTools.read(applicationContext)
            handler.post {
                busy.set(false)
                when (result) {
                    is CarHotspotReadResult.Available -> {
                        AirPlayPersistence.saveManualHotspotProfile(
                            this,
                            result.configuration.ssid,
                            result.configuration.passphrase,
                            result.configuration.security,
                            result.configuration.band
                                ?: com.shilapi.xcertplay.orchestration.ManualHotspotBand.AUTO,
                            result.configuration.channel ?: 0,
                        )
                        AirPlayPersistence.saveWirelessHotspotMode(this, WirelessHotspotMode.MANUAL)
                        toast("已保存车机热点：" + result.configuration.ssid)
                        refreshStatus()
                    }
                    is CarHotspotReadResult.Failure -> toast("读取车机热点失败：" + result.reason.name)
                }
            }
        }
    }

    private fun startHotspot() {
        if (!busy.compareAndSet(false, true)) return
        worker.execute {
            val result = CarHotspotTools.start(applicationContext, timeoutMillis = 20_000L)
            handler.post {
                busy.set(false)
                val message = when (result) {
                    is CarHotspotStartResult.Enabled -> "车机热点已开启"
                    is CarHotspotStartResult.AlreadyEnabled -> "车机热点本来就是开启的"
                    is CarHotspotStartResult.Failure -> "开启车机热点失败：" + result.reason.name
                }
                toast(message)
                refreshStatus()
            }
        }
    }

    // ---- small view helpers ---------------------------------------------------------------

    private fun text(value: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size * uiScale
        setTextColor(color)
        typeface = if (bold) Typeface.create("sans-serif-medium", Typeface.NORMAL)
        else Typeface.create("sans-serif", Typeface.NORMAL)
        setLineSpacing(dp(3).toFloat(), 1f)
    }

    private fun action(title: String, primary: Boolean = false, click: () -> Unit) = Button(this).apply {
        text = title
        isAllCaps = false
        textSize = 17f
        setTextColor(if (primary) BG else TEXT)
        background = rounded(if (primary) ACCENT else SURFACE, if (primary) ACCENT else BORDER)
        minHeight = dp(52)
        setOnClickListener { click() }
    }

    private fun sectionTitle(value: String) = text(value, 13, ACCENT, true)

    private fun rounded(fill: Int, stroke: Int) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(18).toFloat()
        setStroke(dp(1), stroke)
    }

    private fun weight(value: Int) = LinearLayout.LayoutParams(0, dp(52), value.toFloat())

    private fun marginTop(value: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = dp(value) }

    private fun space(width: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(width, 1) }

    private fun dp(value: Int) = (value * resources.displayMetrics.density * uiScale).toInt()

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private companion object {
        const val AUTO_CONNECT_MAX_STEPS = 24
        const val AUTO_CONNECT_MAX_ATTEMPTS = 5
        const val STATUS_POLL_MILLIS = 2_000L
        val BG = Color.rgb(12, 17, 27)
        val SURFACE = Color.rgb(21, 30, 44)
        val BORDER = Color.rgb(42, 56, 75)
        val ACCENT = Color.rgb(166, 200, 255)
        val TEXT = Color.rgb(241, 245, 252)
        val MUTED = Color.rgb(168, 182, 202)
        val WARNING = Color.rgb(255, 196, 128)
    }
}
