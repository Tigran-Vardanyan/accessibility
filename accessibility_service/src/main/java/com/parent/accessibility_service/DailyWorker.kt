package com.parent.accessibility_service

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

const val WORK_FROM_KEY = "WORK_FROM_KEY"
const val WORK_TO_KEY = "WORK_TO_KEY"

class DailyWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        val inputTo = inputData.getLong(WORK_TO_KEY, 0L)
        val inputFrom = inputData.getLong(WORK_FROM_KEY, 0L)

        val newFrom = localDateTimeToMilliseconds(
            LocalDateTime.ofInstant(Instant.ofEpochMilli(inputFrom), ZoneId.systemDefault())
                .plusDays(1)
        )

        val newTo = localDateTimeToMilliseconds(
            LocalDateTime.ofInstant(Instant.ofEpochMilli(inputTo), ZoneId.systemDefault())
                .plusDays(1)
        )

        applicationContext.addToContentProvider(newFrom, newTo)

        val dailyWorkRequest = OneTimeWorkRequestBuilder<DailyWorker>()
            .setInitialDelay(AppBlockerService.calculateDelay(), TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(applicationContext).enqueue(dailyWorkRequest)

        return Result.success()
    }

    private fun localDateTimeToMilliseconds(localDateTime: LocalDateTime): Long {
        val instant = localDateTime.toInstant(ZoneOffset.UTC)
        return instant.toEpochMilli()
    }
}