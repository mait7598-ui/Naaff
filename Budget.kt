package com.example.databudget

import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Process
import androidx.core.app.NotificationCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

object Budget {
    // ---- إعدادات تقدر تبدلها ----
    const val CHECK_EVERY_DAYS = 3L      // كل كم يوم يدير الفحص
    const val GAP_GB = 2.0               // فرق GB باش يقول ليك زيد الاستهلاك
    const val TOLERANCE_GB = 0.1         // تسامح صغير قبل تحذير "نقص"
    const val GB = 1024.0 * 1024.0 * 1024.0
    const val DAY = 24L * 60 * 60 * 1000
    // -----------------------------

    private const val CHANNEL = "budget"

    enum class Level { OVER, UNDER, OK }

    class Report(
        val level: Level,
        val usedGb: Double,
        val remainingGb: Double,
        val expectedRemainingGb: Double,
        val newDailyGb: Double
    )

    private fun prefs(c: Context) = c.getSharedPreferences("budget", Context.MODE_PRIVATE)

    fun save(c: Context, totalGb: Double, days: Int, startMillis: Long) {
        prefs(c).edit()
            .putFloat("total", totalGb.toFloat())
            .putInt("days", days)
            .putLong("start", startMillis)
            .apply()
    }

    fun hasConfig(c: Context) = prefs(c).contains("start")
    fun totalGb(c: Context) = prefs(c).getFloat("total", 0f).toDouble()
    fun days(c: Context) = prefs(c).getInt("days", 30)

    fun hasUsageAccess(c: Context): Boolean {
        val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun schedule(c: Context) {
        val req = PeriodicWorkRequestBuilder<CheckWorker>(CHECK_EVERY_DAYS, TimeUnit.DAYS)
            .setInitialDelay(CHECK_EVERY_DAYS, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(c)
            .enqueueUniquePeriodicWork("check", ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    private fun compute(c: Context): Report? {
        val p = prefs(c)
        val total = p.getFloat("total", 0f).toDouble()
        val days = p.getInt("days", 30)
        val start = p.getLong("start", 0L)
        val now = System.currentTimeMillis()

        val bytes = try {
            val nsm = c.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
            val b = nsm.querySummaryForDevice(ConnectivityManager.TYPE_MOBILE, null, start, now)
            b.rxBytes + b.txBytes
        } catch (e: Exception) {
            return null
        }

        val used = bytes / GB
        val elapsed = min((now - start).toDouble() / DAY, days.toDouble())
        val expectedUsed = total / days * elapsed
        val remaining = total - used
        val expectedRemaining = total - expectedUsed
        val daysLeft = max(1, ceil(days - elapsed).toInt())

        val level = when {
            remaining < expectedRemaining - TOLERANCE_GB -> Level.OVER
            remaining - expectedRemaining >= GAP_GB -> Level.UNDER
            else -> Level.OK
        }
        return Report(level, used, remaining, expectedRemaining, max(0.0, remaining) / daysLeft)
    }

    private fun f(x: Double) = String.format(Locale.US, "%.2f", x)

    private fun title(r: Report) = when (r.level) {
        Level.OVER -> "⚠️ نقص الاستهلاك"
        Level.UNDER -> "🟢 زيد الاستهلاك"
        Level.OK -> "✅ كلشي مزيان"
    }

    private fun text(r: Report): String {
        val base = "الرصيد الباقي: ${f(r.remainingGb)} GB — المفروض: ${f(r.expectedRemainingGb)} GB"
        return when (r.level) {
            Level.OVER -> "$base\nما تزيدش فـ ${f(r.newDailyGb)} GB فاليوم باش يكفيك."
            Level.UNDER -> "$base\nتقدر تستهلك حتى ${f(r.newDailyGb)} GB فاليوم."
            Level.OK -> base
        }
    }

    /** كيحسب الرصيد، وإلا alert=true وكان مشكل كيبعت إشعار. كيرجع نص للعرض. */
    fun check(c: Context, alert: Boolean): String {
        if (!hasConfig(c)) return "دخل الرصيد والأيام وضغط على «بدا / حفظ»."
        if (!hasUsageAccess(c)) return "خاصك تعطي إذن «الوصول للاستخدام» للتطبيق."
        val r = compute(c) ?: return "ما قدرتش نقرا الاستهلاك."
        if (alert && r.level != Level.OK) sendNotification(c, title(r), text(r))
        return "${title(r)}\n${text(r)}\n\nمستهلك: ${f(r.usedGb)} GB"
    }

    private fun sendNotification(c: Context, title: String, text: String) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Data Budget", NotificationManager.IMPORTANCE_HIGH)
        )
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        nm.notify(1, n)
    }
}
