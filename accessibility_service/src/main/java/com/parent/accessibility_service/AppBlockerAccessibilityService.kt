package com.parent.accessibility_service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED
import android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SCROLLED
import android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
import android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED


class AppBlockerAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        if (!isWorkingTime()) {
            return
        }

        when (event.eventType) {
            TYPE_VIEW_CLICKED,
            TYPE_VIEW_SCROLLED,
            TYPE_WINDOW_STATE_CHANGED,
            TYPE_WINDOW_CONTENT_CHANGED -> {
                val packageName = event.packageName
                if (packageName != null && blockedApps().contains(packageName)) {
                    AppBlockerService.onBlock(packageName.toString(), applicationContext)
                }
            }

            else -> Unit
        }
    }

    private fun blockedApps(): List<String> {
        val apps = mutableListOf<String>()
        contentResolver.query(
            ContentProviderContract.CONTENT_URI_APP,
            null,
            null,
            null,
            null
        )?.use {
            while (it.moveToNext()) {
                val `package` =
                    it.getString(it.getColumnIndexOrThrow(ContentProviderContract.Data.COLUMN_PACKAGES))
                if (!`package`.isNullOrEmpty()) {
                    apps.add(`package`)
                }
            }
        }
        return apps
    }

    private fun workingTimes(): Pair<Long, Long>? {
        contentResolver.query(
            ContentProviderContract.CONTENT_URI_WORK,
            null,
            null,
            null,
            null
        )?.use {
            while (it.moveToNext()) {
                val from =
                    it.getLong(it.getColumnIndexOrThrow(ContentProviderContract.Data.COLUMN_FROM))
                val to =
                    it.getLong(it.getColumnIndexOrThrow(ContentProviderContract.Data.COLUMN_TO))
                if (from >= 0 && to >= 0) {
                    return Pair(from, to)
                }
            }
        }
        return null
    }

    private fun isWorkingTime(): Boolean {
        val now = System.currentTimeMillis()
        val workingTime = workingTimes() ?: return false

        return now in workingTime.first..workingTime.second
    }

    override fun onDestroy() {
        AppBlockerService.onDestroy(applicationContext)
        super.onDestroy()
    }

    override fun onInterrupt() {
        AppBlockerService.onInterrupt(applicationContext)
    }
}