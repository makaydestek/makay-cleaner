package com.makay.cleaner.util

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * Pil yüzdesi — Xiaomi Güvenlik / durum çubuğu ile aynı kaynak:
 * [BatteryManager.BATTERY_PROPERTY_CAPACITY] (0–100 tam sayı).
 * EXTRA_LEVEL/SCALE + roundToInt ±1% sapma üretebildiği için birincil yol değildir.
 */
object BatteryStatsHelper {

    data class Snapshot(
        val percent: Int,
        val charging: Boolean,
        val health: Int,
        val tempTenths: Int,
        val voltage: Int,
        val plugged: Int
    )

    fun readPercent(context: Context): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val capacity = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: Int.MIN_VALUE
        if (capacity in 0..100) return capacity

        val intent = stickyBattery(context) ?: return 0
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        if (level < 0) return 0
        // Truncate (floor) — roundToInt Xiaomi ile ±1 sapma yapıyordu
        return ((level * 100) / scale).coerceIn(0, 100)
    }

    fun readSnapshot(context: Context): Snapshot {
        val intent = stickyBattery(context)
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL ||
            plugged != 0
        return Snapshot(
            percent = readPercent(context),
            charging = charging,
            health = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1,
            tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1,
            voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1,
            plugged = plugged
        )
    }

    private fun stickyBattery(context: Context): Intent? =
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
}
