package com.davipeter.iana.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.davipeter.iana.IanaApp
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Busca manchetes e o próximo compromisso, salva e redesenha o widget. */
class WidgetUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as IanaApp).container
        val store = WidgetStore(applicationContext)
        val snapshot = WidgetSnapshots.build(container.news, container.calendar, LocalDateTime.now(), store.load())
        store.save(snapshot)
        IanaWidget().updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "widget-refresh"
        private const val NOW = "widget-refresh-now"

        /** Atualização a cada 30 min (o Android pode atrasar um pouco para poupar bateria). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun refreshNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        }
    }
}
