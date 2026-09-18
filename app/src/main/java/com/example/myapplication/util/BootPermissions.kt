package com.example.myapplication.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.example.myapplication.clipboard.ClipboardImeService

/**
 * 开机启动 / 后台运行相关权限检查与系统设置页引导。
 *
 * 说明：厂商自启动开关没有标准 API 可查询，因此这里：
 *  - 若识别到厂商自启动管理页，给出直达入口；
 *  - 是否真正开启由用户确认后记录（[autostartAcknowledged]）。
 */
object BootPermissions {

    data class Status(
        val notificationGranted: Boolean,
        val batteryOptimizationIgnored: Boolean,
        val autostartIntent: Intent?,
        val autostartAcknowledged: Boolean,
        val imeEnabled: Boolean,
        val imeSelected: Boolean
    )

    fun status(context: Context, autostartAcknowledged: Boolean): Status = Status(
        notificationGranted = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        batteryOptimizationIgnored = isIgnoringBatteryOptimizations(context),
        autostartIntent = autostartIntent(context),
        autostartAcknowledged = autostartAcknowledged,
        imeEnabled = isImeEnabled(context),
        imeSelected = isDefaultIme(context)
    )

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** 我们的输入法是否已在系统输入法列表中启用 */
    fun isImeEnabled(context: Context): Boolean {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        return imm.enabledInputMethodList.any { it.packageName == context.packageName }
    }

    /** 我们的输入法是否为当前默认输入法（只有默认输入法可获得后台剪贴板读取豁免） */
    fun isDefaultIme(context: Context): Boolean {
        val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        return id?.startsWith(context.packageName) == true
    }

    /** 请求忽略电池优化（后台运行权限） */
    fun requestIgnoreBatteryOptimizations(context: Context): Intent {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        return if (intent.resolveActivity(context.packageManager) != null) intent else appDetailsIntent(context)
    }

    fun notificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun inputMethodSettingsIntent(): Intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)

    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    /**
     * 厂商自启动管理页。返回 null 表示未识别到对应厂商页面。
     */
    fun autostartIntent(context: Context): Intent? {
        val pm = context.packageManager
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        val brand = Build.BRAND.orEmpty().lowercase()

        val candidates = OEM_AUTOSTART[manufacturer]
            ?: OEM_AUTOSTART[brand]
            ?: OEM_AUTOSTART.entries.firstOrNull { (key, _) ->
                manufacturer.contains(key) || brand.contains(key)
            }?.value

        candidates?.forEach { component ->
            val intent = Intent().setComponent(component).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching {
                if (intent.resolveActivity(pm) != null) return intent
            }
        }
        return null
    }

    private fun c(pkg: String, cls: String) = ComponentName(pkg, cls)

    private val OEM_AUTOSTART: Map<String, List<ComponentName>> = mapOf(
        "xiaomi" to listOf(
            c("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            c("com.lbe.security.miui", "com.lbe.security.ui.SecurityCenterMainActivity")
        ),
        "huawei" to listOf(
            c("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            c("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.ui.StartupAppListActivity")
        ),
        "honor" to listOf(
            c("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            c("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
        ),
        "oppo" to listOf(
            c("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            c("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            c("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
        ),
        "realme" to listOf(
            c("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            c("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")
        ),
        "vivo" to listOf(
            c("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            c("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManagerActivity")
        ),
        "oneplus" to listOf(
            c("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")
        ),
        "samsung" to listOf(
            c("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.AppSleepListActivity"),
            c("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.AppSleepListActivity")
        ),
        "meizu" to listOf(
            c("com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity")
        ),
        "zte" to listOf(
            c("com.zte.heartyservice", "com.zte.heartyservice.autorun.AppAutoRunManagerActivity")
        ),
        "nubia" to listOf(
            c("cn.nubia.security", "cn.nubia.security.appmanage.AppManageActivity")
        ),
        "asus" to listOf(
            c("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity")
        ),
        "sony" to listOf(
            c("com.sonymobile.cta", "com.sonymobile.cta.SomcCTAMainActivity")
        ),
        "htc" to listOf(
            c("com.htc.pitroad", "com.htc.pitroad.landingpage.activity.LandingPageActivity")
        )
    )
}
