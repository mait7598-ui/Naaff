package com.example.databudget

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class CheckWorker(c: Context, p: WorkerParameters) : Worker(c, p) {
    override fun doWork(): Result {
        Budget.check(applicationContext, alert = true)
        return Result.success()
    }
}
