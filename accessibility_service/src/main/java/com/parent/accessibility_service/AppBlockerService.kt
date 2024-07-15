package com.parent.accessibility_service

import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Context.APP_OPS_SERVICE
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.io.ByteArrayOutputStream
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class AppBlockerService {
    companion object {
        const val SERVICE_DISABLED = "SERVICE_DISABLED"
        const val INTENT_BUNDLE_KEY = "INTENT_BUNDLE_KEY"
        const val OVERLAY_REQUEST_CODE = 101
        const val POPUP_REQUEST_CODE = 102
        @SuppressLint("StaticFieldLeak")
        private var activity: Activity? = null

        private var blockedApp: String? = null
        private var serviceDisabled = false

        @JvmStatic
        fun init(activity: Activity) {
            this.activity = activity
        }
        private fun bringAppToForeground(data: String?, context: Context) {
            val intent = Intent("com.phys.intent.action.ACTION_LAUNCH_MAIN")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ContextCompat.startActivity(context, intent, bundleOf(INTENT_BUNDLE_KEY to data))
        }

        @JvmStatic
        fun whichAppBlocked(): String? {
            val blocked = blockedApp
            blockedApp = null
            return blocked
        }

        @JvmStatic
        fun isServiceDisabled(): Boolean {
            val disabled = serviceDisabled
            serviceDisabled = false
            return disabled
        }

        @JvmStatic
        fun getAllApps(activity: Activity): Array<AppData> {
            val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            val resolveInfoList: List<ResolveInfo> =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    activity.packageManager.queryIntentActivities(
                        intent,
                        PackageManager.ResolveInfoFlags.of(0)
                    )
                } else {
                    activity.packageManager.queryIntentActivities(
                        intent,
                        PackageManager.GET_META_DATA
                    )
                }

            val appList = mutableListOf<String>()
            for (resolveInfo in resolveInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                if (!appList.contains(packageName)) {
                    appList.add(packageName)
                }
            }

            return appList.filterNot { it == activity.packageName }.map { packageName ->
                val applicationInfo = activity.appInfo(packageName)
                val appName =
                    activity.packageManager.getApplicationLabel(applicationInfo).toString()
                val appIcon = activity.packageManager.getApplicationIcon(applicationInfo)
                val category =
                    ApplicationInfo.getCategoryTitle(activity, applicationInfo.category)?.toString()

                AppData(
                    appName = appName,
                    appPackage = packageName,
                    icon = appIcon.toByteArray(),
                    category = category,
                )
            }.toTypedArray()
        }

        /**
         * @param apps - apps packages to be blocked
         */
        @JvmStatic
        fun appsToBeBlocked(activity: Activity, apps: Array<String>) {
            val contentResolver = activity.contentResolver
            contentResolver.delete(ContentProviderContract.CONTENT_URI_APP, null, null)
            apps.forEach {
                val contentValues = ContentValues().apply {
                    put(ContentProviderContract.Data.COLUMN_PACKAGES, it)
                }
                contentResolver.insert(ContentProviderContract.CONTENT_URI_APP, contentValues)
            }
        }

        @JvmStatic
        fun workingTime(activity: Activity, from: Long, to: Long) {
            activity.applicationContext.addToContentProvider(from, to)

            startDailyWorker(activity.applicationContext, from, to)
        }

        private fun startDailyWorker(context: Context, from: Long, to: Long) {
            val inputData = Data.Builder()
                .putLong(WORK_TO_KEY, to)
                .putLong(WORK_FROM_KEY, from)
                .build()

            val dailyWorkRequest = OneTimeWorkRequestBuilder<DailyWorker>()
                .setInitialDelay(calculateDelay(), TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                .build()
            WorkManager.getInstance(context).enqueue(dailyWorkRequest)
        }

        @JvmStatic
        fun calculateDelay(): Long {
            val currentDate = Calendar.getInstance()
            val dueDate = Calendar.getInstance()

            // Set Execution around 11:50:00 PM
            dueDate.set(Calendar.HOUR_OF_DAY, 23)
            dueDate.set(Calendar.MINUTE, 50)
            dueDate.set(Calendar.SECOND, 0)
            if (dueDate.before(currentDate)) {
                dueDate.add(Calendar.HOUR_OF_DAY, 24)
            }
            return dueDate.timeInMillis - currentDate.timeInMillis
        }

        private fun Activity.appInfo(packageName: String): ApplicationInfo {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0)
                )
            } else {
                packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            }
        }

        private fun Drawable.toByteArray(): ByteArray {
            val bitmap =
                Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            setBounds(0, 0, canvas.width, canvas.height)
            draw(canvas)

            ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                return stream.toByteArray()
            }
        }

        @JvmStatic
        fun isAccessibilityServiceEnabled(activity: Activity): Boolean {
            val accessibilityManager =
                activity.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val enabledServices =
                accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            val serviceComponentName =
                ComponentName(activity, AppBlockerAccessibilityService::class.java)

            return enabledServices.any {
                it.resolveInfo.serviceInfo.packageName == activity.packageName && it.resolveInfo.serviceInfo.name == serviceComponentName.className
            }
            return false
        }
        @JvmStatic
        private fun navigateToAccessibilitySettings() {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            activity?.startActivity(intent)
        }

        @JvmStatic
        fun requestPackageUsageStatsPermission() {
            activity?.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        @JvmStatic
        fun checkUsageStatsPermission(): Boolean {
            activity?.let {
                val appOpsManager = it.getSystemService(APP_OPS_SERVICE) as AppOpsManager
                val mode = appOpsManager.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    it.packageName
                )
                return mode == AppOpsManager.MODE_ALLOWED
            }
            return false
        }
        @JvmStatic
        fun requestSystemAlertWindowPermission() {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            activity?.startActivity(intent)
        }
        @JvmStatic
        fun checkSystemAlertWindowPermission(): Boolean {
            return Settings.canDrawOverlays(activity)
        }
        @JvmStatic
        fun checkOverlayPermission(): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(activity)
            } else {
                true
            }
        }
        @JvmStatic
        fun requestIgnoreBatteryOptimizationsPermission() {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:${activity?.packageName}")
            activity?.startActivity(intent)
        }
        @JvmStatic
        fun checkIgnoreBatteryOptimizationsPermission(): Boolean {
            val powerManager = activity?.getSystemService(Context.POWER_SERVICE) as PowerManager
            return powerManager.isIgnoringBatteryOptimizations(activity?.packageName)
        }
        @JvmStatic
        fun hasAccessRestrictedPerm(): Boolean {
            return if (Build.VERSION.SDK_INT < 33) {
                true
            } else {
                activity?.applicationContext.let {
                    try {
                        val packageManager = it?.packageManager as PackageManager
                        val applicationInfo = packageManager.getApplicationInfo(it.packageName, 0)
                        val appOpsManager =
                            it.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                        val mode = appOpsManager.unsafeCheckOpNoThrow(
                            "android:access_restricted_settings",
                            applicationInfo.uid,
                            applicationInfo.packageName
                        )
                        mode == AppOpsManager.MODE_ALLOWED
                    } catch (e: PackageManager.NameNotFoundException) {
                        false
                    } catch (e: SecurityException) {
                        false
                    }
                }
            }
        }


        @JvmStatic
        fun requestOverlayPermission() {
            activity?.let { act ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

                    if ("xiaomi".equals(Build.MANUFACTURER.lowercase(Locale.ROOT))) {
                        val intent = Intent().apply {
                            setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                            putExtra("extra_pkgname", act.packageName)
                        }
                        act.startActivity(intent)
                    } else {
                        val overlaySettings = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + act.packageName))
                        act.startActivityForResult(overlaySettings, OVERLAY_REQUEST_CODE)

                    }
                }
            }
        }
        @JvmStatic
        fun checkPopupWindowPermission(): Boolean {
            val context = activity?.applicationContext
            val appOpsManager = context?.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            val mode = appOpsManager?.unsafeCheckOpNoThrow(
                "android:system_alert_window",
                android.os.Process.myUid(),
                context.packageName
            )
            return mode == AppOpsManager.MODE_ALLOWED
        }

        @JvmStatic
        fun requestPopupWindowPermission() {
            activity?.let { act ->
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + act.packageName))
                act.startActivityForResult(intent, POPUP_REQUEST_CODE)
            }
        }
        @JvmStatic
        fun getAppUsageData(activity: Activity, beginTime: Long, endTime: Long): Array<UsageStats> {
            val usageStatsManager =
                activity.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            return usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                beginTime,
                endTime
            ).toTypedArray()
        }

        @JvmStatic
        fun appData(activity: Activity, packageName: String): AppData? {
            return getAllApps(activity).find { it.appPackage == packageName }
        }

        @JvmStatic
        fun getAppUsageStatsWithData(
            activity: Activity,
            beginTime: Long,
            endTime: Long
        ): Array<AppUsageData> {
            val apps = getAllApps(activity)
            val usageStats = getAppUsageData(activity, beginTime, endTime)

            return usageStats.map { usage ->
                apps.find { it.appPackage == usage.packageName }?.let {
                    return@map AppUsageData(usage, it)
                }
            }.mapNotNull { it }.toTypedArray()
        }

        fun onInterrupt(context: Context) {
            serviceDisabled = true
            bringAppToForeground(SERVICE_DISABLED, context)
        }

        fun onDestroy(context: Context) {
            serviceDisabled = true
            bringAppToForeground(SERVICE_DISABLED, context)
        }

        fun onBlock(packageName: String, context: Context) {
            bringAppToForeground(packageName, context)
        }
    }
}

fun Context.addToContentProvider(from: Long, to: Long) {
    val contentValues = ContentValues().apply {
        put(ContentProviderContract.Data.COLUMN_FROM, from)
        put(ContentProviderContract.Data.COLUMN_TO, to)
    }

    contentResolver.delete(ContentProviderContract.CONTENT_URI_WORK, null, null)
    contentResolver.insert(ContentProviderContract.CONTENT_URI_WORK, contentValues)
}