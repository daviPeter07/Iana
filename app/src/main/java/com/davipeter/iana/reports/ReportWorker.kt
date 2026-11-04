package com.davipeter.iana.reports

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.davipeter.iana.IanaApp
import com.davipeter.iana.R
import com.davipeter.iana.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDateTime

/** Relatórios guardados nas preferências do app, em JSON. */
class PrefsReportStore(context: Context) : ReportStore {
    private val prefs = context.getSharedPreferences("iana_reports", Context.MODE_PRIVATE)

    override suspend fun list(): List<ReportConfig> = withContext(Dispatchers.IO) {
        ReportCodec.decode(prefs.getString(KEY, null)).sortedBy { it.time }
    }

    override suspend fun save(report: ReportConfig) = withContext(Dispatchers.IO) {
        val others = ReportCodec.decode(prefs.getString(KEY, null)).filter { it.id != report.id }
        prefs.edit().putString(KEY, ReportCodec.encode(others + report)).commit()
        Unit
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val rest = ReportCodec.decode(prefs.getString(KEY, null)).filter { it.id != id }
        prefs.edit().putString(KEY, ReportCodec.encode(rest)).commit()
        Unit
    }

    private companion object {
        const val KEY = "reports"
    }
}

/**
 * Roda um relatório no horário, mostra a notificação e agenda a próxima vez.
 * O relatório é montado por código ([ReportComposer]), sem IA.
 */
class ReportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val container = (applicationContext as IanaApp).container
        val report = container.reports.list().firstOrNull { it.id == id } ?: return Result.success() // foi apagado
        val now = LocalDateTime.now()
        try {
            val composed = container.reportComposer.compose(report, now)
            notify(applicationContext, report, composed)
        } finally {
            // Mesmo se falhar, o relatório de amanhã continua agendado. APPEND: substituir (REPLACE)
            // cancelaria este próprio trabalho, que ainda está rodando.
            ReportScheduler.schedule(applicationContext, report, now, ExistingWorkPolicy.APPEND_OR_REPLACE)
        }
        return Result.success()
    }

    private fun notify(context: Context, report: ReportConfig, composed: ComposedReport) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Relatórios", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Relatórios automáticos da IANA (agenda e notícias)."
            },
        )
        val open = PendingIntent.getActivity(
            context, report.id.hashCode(), Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val body = composed.lines.take(MAX_LINES).joinToString("\n") { it.text }.ifBlank { composed.speech }
        // Na tela de bloqueio aparece só "Relatório pronto": agenda e notícias ficam escondidas.
        val public = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle("IANA")
            .setContentText("Relatório pronto")
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logo)
            .setContentTitle(composed.title)
            .setContentText(composed.lines.firstOrNull()?.text ?: composed.speech)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(report.id.hashCode(), notification)
    }

    companion object {
        const val KEY_ID = "report_id"
        const val TAG = "report"
        private const val CHANNEL_ID = "reports"
        private const val MAX_LINES = 8
    }
}

/**
 * Agenda os relatórios com o WorkManager (sobrevive a reinício do celular).
 * Observação: o Android pode atrasar alguns minutos para economizar bateria; para relatório isso é ok.
 */
object ReportScheduler {

    /** Reagenda todos do zero: apaga os agendamentos antigos e cria os atuais. Pode chamar à vontade. */
    suspend fun syncAll(context: Context) {
        val app = context.applicationContext
        val reports = (app as IanaApp).container.reports.list()
        val workManager = WorkManager.getInstance(app)
        workManager.cancelAllWorkByTag(ReportWorker.TAG)
        val now = LocalDateTime.now()
        reports.forEach { schedule(app, it, now) }
    }

    fun schedule(
        context: Context,
        report: ReportConfig,
        now: LocalDateTime,
        policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE,
    ) {
        val delay = Duration.between(now, ReportSchedule.nextRun(report, now))
        val request = OneTimeWorkRequestBuilder<ReportWorker>()
            .setInitialDelay(delay)
            .setInputData(workDataOf(ReportWorker.KEY_ID to report.id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag(ReportWorker.TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("report-${report.id}", policy, request)
    }
}
