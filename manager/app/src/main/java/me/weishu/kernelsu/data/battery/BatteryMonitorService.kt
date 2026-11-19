package me.weishu.kernelsu.data.battery

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import me.weishu.kernelsu.R
import me.weishu.kernelsu.data.repository.SettingsRepositoryImpl
import java.util.Locale
import kotlin.math.abs

/**
 * Ongoing battery telemetry notification, in the shape Franco Kernel Manager
 * uses: level, temperature and current on the first line, drain rates and the
 * screen-on / screen-off / deep-sleep / awake breakdown underneath.
 *
 * Deliberately cheap:
 *
 *  - Values come from the framework ([BatteryManager], the sticky
 *    `ACTION_BATTERY_CHANGED` broadcast, and [PowerManager.isInteractive]).
 *    Nothing here shells out for root, so a refresh costs a few binder calls
 *    instead of spawning `su` + `cat` per sample.
 *  - Screen and power events drive most refreshes, so the notification updates
 *    exactly when something changed. The slow tick exists only to refresh the
 *    current reading, and it holds no wakelock: the handler simply stops while
 *    the device suspends, and [BatteryStatsStore] recovers the suspended time
 *    from the clock delta instead of measuring it live.
 */
class BatteryMonitorService : Service() {

    private lateinit var notificationManager: NotificationManager
    private lateinit var batteryManager: BatteryManager
    private lateinit var powerManager: PowerManager
    private lateinit var stats: BatteryStatsStore
    private val settings = SettingsRepositoryImpl()

    private val handler = Handler(Looper.getMainLooper())
    private var foregroundStarted = false

    /** Throttles notification reposts; the accounting itself is free. */
    private var lastNotifyElapsed = 0L
    private var lastPersistElapsed = 0L

    /** Latest values decoded from the sticky battery broadcast. */
    private var level = -1
    private var temperatureTenths = Int.MIN_VALUE
    private var status = BatteryManager.BATTERY_STATUS_UNKNOWN
    private var plugged = 0

    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, TICK_MILLIS)
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    readBattery(intent)
                    // Charging devices can broadcast this every few seconds; the
                    // stats settle either way, but the notification is throttled.
                    refresh()
                }

                Intent.ACTION_SCREEN_ON, Intent.ACTION_SCREEN_OFF,
                Intent.ACTION_POWER_CONNECTED, Intent.ACTION_POWER_DISCONNECTED -> {
                    // Settle immediately so time is booked to the state that was
                    // actually active, rather than to whatever state ends up
                    // being sampled at the next tick. The user just did
                    // something visible, so repost immediately too.
                    refresh(forceNotify = true)
                    // Screen and power transitions are rare, and they are
                    // exactly when the stats screen would otherwise show
                    // numbers up to a minute old.
                    persistNow()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        batteryManager = getSystemService(BatteryManager::class.java)
        powerManager = getSystemService(PowerManager::class.java)
        stats = BatteryStatsStore(this)

        createNotificationChannel()

        // Reading the sticky broadcast before starting the foreground
        // notification means the first notification is already populated
        // instead of showing placeholders for a fraction of a second.
        readBattery(
            runCatching {
                registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            }.getOrNull()
        )

        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground()

        if (intent?.action == ACTION_RESET) {
            stats.reset()
            refresh(forceNotify = true)
            handler.removeCallbacks(ticker)
            handler.postDelayed(ticker, TICK_MILLIS)
            return START_STICKY
        }

        refresh(forceNotify = true)
        handler.removeCallbacks(ticker)
        handler.postDelayed(ticker, TICK_MILLIS)
        // The user asked for a persistent monitor, so ask the system to restart
        // us if it ever kills the process.
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        runCatching { unregisterReceiver(receiver) }
        stats.persist()
        super.onDestroy()
    }

    private fun startInForeground() {
        if (foregroundStarted) return
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        foregroundStarted = true
    }

    private fun readBattery(intent: Intent?) {
        if (intent == null) return
        val rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (rawLevel >= 0 && scale > 0) {
            level = rawLevel * 100 / scale
        }
        temperatureTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
    }

    private fun refresh(forceNotify: Boolean = false) {
        if (level < 0) return
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL ||
            plugged != 0
        val screenOn = powerManager.isInteractive
        val newCycle = stats.settle(
            level = level,
            screenOn = screenOn,
            charging = charging,
            resetOnUnplug = settings.batteryMonitorResetOnUnplug,
        )

        val now = SystemClock.elapsedRealtime()
        // Leaving the charger zeroed every counter; write the zeros out before
        // the process can die with a stale snapshot in preferences.
        if (newCycle) persistNow(now)
        if (forceNotify || now - lastNotifyElapsed >= MIN_NOTIFY_INTERVAL) {
            lastNotifyElapsed = now
            notificationManager.notify(NOTIFICATION_ID, buildNotification())
        }
        // Writing preferences on every battery broadcast would be the most
        // expensive thing this service does, so persistence is throttled and
        // also happens in onDestroy.
        if (now - lastPersistElapsed >= PERSIST_INTERVAL) {
            persistNow(now)
        }
    }

    private fun persistNow(now: Long = SystemClock.elapsedRealtime()) {
        stats.persist()
        lastPersistElapsed = now
    }

    // -----------------------------------------------------------------------
    // Notification
    // -----------------------------------------------------------------------

    private fun buildNotification(): Notification {
        val header = buildHeader()
        val body = buildBody()

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_battery_monitor)
            .setContentTitle(header)
            .setContentText(body.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)

        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            builder.setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    launch,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
        }
        return builder.build()
    }

    /** `71% · 31°C · Discharging 605 mA`, matching the reference layout. */
    private fun buildHeader(): String = buildString {
        append(level).append('%')
        if (temperatureTenths != Int.MIN_VALUE) {
            append(" · ").append(formatTemperature(temperatureTenths))
        }
        stateLabel()?.let { append(" · ").append(it) }
    }

    private fun stateLabel(): String? {
        val state = when {
            status == BatteryManager.BATTERY_STATUS_FULL -> getString(R.string.battery_monitor_full)
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
                (plugged != 0 && status != BatteryManager.BATTERY_STATUS_DISCHARGING) ->
                getString(R.string.battery_monitor_charging)

            status == BatteryManager.BATTERY_STATUS_DISCHARGING ->
                getString(R.string.battery_monitor_discharging)

            else -> getString(R.string.battery_monitor_not_charging)
        }
        val current = readCurrentMilliAmps() ?: return state
        return getString(R.string.battery_monitor_state_current, state, current)
    }

    private fun buildBody(): String = listOf(
        getString(
            R.string.battery_monitor_drain,
            formatRate(stats.activeDrainPerHour),
            formatRate(stats.idleDrainPerHour),
        ),
        getString(
            R.string.battery_monitor_screen_on,
            formatSpan(stats.screenOnMillis),
            formatPercent(stats.screenOnPercent),
        ),
        getString(
            R.string.battery_monitor_screen_off,
            formatSpan(stats.screenOffMillis),
            formatPercent(stats.screenOffPercent),
        ),
        getString(
            R.string.battery_monitor_deep_sleep,
            formatSpan(stats.deepSleepMillis),
            formatShare(stats.deepSleepShare),
        ),
        getString(
            R.string.battery_monitor_awake,
            formatSpan(stats.awakeMillis),
            formatShare(stats.awakeShare),
        ),
    ).joinToString("\n")

    /**
     * Instantaneous current in mA, or null when the device does not report it.
     * The framework returns microamps; the sign convention varies by vendor, so
     * direction is taken from the charge status instead of the sign.
     */
    private fun readCurrentMilliAmps(): Int? = runCatching {
        val microAmps = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (microAmps == Int.MIN_VALUE) null else abs(microAmps) / 1000
    }.getOrNull()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.battery_monitor_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.battery_monitor_summary)
            setShowBadge(false)
            setSound(null, null)
        }
        notificationManager.createNotificationChannel(channel)
    }

    // -----------------------------------------------------------------------
    // Formatting
    // -----------------------------------------------------------------------

    private fun formatTemperature(tenths: Int): String =
        String.format(Locale.US, "%.0f°C", tenths / 10f)

    private fun formatRate(value: Float): String =
        String.format(Locale.US, "%.2f", value)

    private fun formatPercent(value: Float): String =
        String.format(Locale.US, "%.0f", value)

    private fun formatShare(value: Float): String =
        String.format(Locale.US, "%.2f", value * 100f)

    /** `14 days 15h 57m 39s`, with leading units dropped while still zero. */
    private fun formatSpan(millis: Long): String {
        val totalSeconds = millis / 1000
        val days = totalSeconds / 86_400
        val hours = (totalSeconds % 86_400) / 3_600
        val minutes = (totalSeconds % 3_600) / 60
        val seconds = totalSeconds % 60
        return buildString {
            when {
                days > 1 -> append("$days days ")
                days == 1L -> append("1 day ")
            }
            if (days > 0 || hours > 0) append("${hours}h ")
            if (days > 0 || hours > 0 || minutes > 0) append("${minutes}m ")
            append("${seconds}s")
        }
    }

    companion object {
        private const val CHANNEL_ID = "battery_monitor"
        private const val NOTIFICATION_ID = 0x0BA7

        /** Slow tick; real updates are driven by battery and screen broadcasts. */
        private const val TICK_MILLIS = 60_000L

        /** Never repost the notification more often than this. */
        private const val MIN_NOTIFY_INTERVAL = 10_000L

        /** How often accumulated stats are flushed to preferences. */
        private const val PERSIST_INTERVAL = 60_000L

        const val ACTION_STOP = "me.weishu.kernelsu.action.BATTERY_MONITOR_STOP"
        const val ACTION_RESET = "me.weishu.kernelsu.action.BATTERY_MONITOR_RESET"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, BatteryMonitorService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BatteryMonitorService::class.java))
        }

        fun reset(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, BatteryMonitorService::class.java).apply { action = ACTION_RESET }
            )
        }
    }
}
