package org.acedo.jmtiempos;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

import java.util.Locale;

/** Lógica del cronómetro: el tiempo corre en el dispositivo y se envía a Leantime al parar. */
final class Timer {
    /** Por debajo de esto se entiende que se ha pulsado sin querer y no se registra nada. */
    static final long MIN_MS = 30_000;
    /** A partir de esto se pide confirmar las horas: suele ser un cronómetro olvidado. */
    static final long REVIEW_MS = 8 * 3_600_000L;

    private Timer() {}

    static void toggle(Context c) {
        Store s = new Store(c);
        if (s.running()) {
            stop(c, s, System.currentTimeMillis());
        } else if (s.ticketId() != 0 && s.configured() && !s.reviewPending()) {
            s.setMarkDone(false);
            s.setStart(System.currentTimeMillis());
            s.setStatus("");
        }
        TimerWidget.refreshAll(c);
    }

    static void stop(Context c, Store s, long nowMs) {
        long start = s.start();
        long elapsed = Math.max(0, nowMs - start);
        s.setStart(0);
        if (elapsed < MIN_MS) {
            s.setStatus("Cancelado: menos de 30 segundos");
            if (s.markDone()) finishTask(c, s);
        } else if (elapsed >= REVIEW_MS) {
            s.setReview(start, hours(elapsed));
            s.setStatus("Llevaba " + format(hours(elapsed)) + " h. Revísalo antes de guardar");
        } else {
            submit(c, s, start, hours(elapsed));
        }
    }

    static void submit(Context c, Store s, long startMs, double hours) {
        s.enqueue(s.ticketId(), s.ticketName(), startMs, hours, s.note());
        s.setNote("");
        s.clearReview();
        s.setStatus("Enviando " + format(hours) + " h…");
        if (s.markDone()) finishTask(c, s);
        scheduleSync(c);
    }

    /** Marca la tarea actual como hecha y deja el widget listo para elegir otra. */
    static void finishTask(Context c, Store s) {
        s.enqueueDone(s.ticketId(), s.ticketName());
        s.removeCachedTask(s.ticketId());
        s.clearTicket();
        s.setMarkDone(false);
        scheduleSync(c);
    }

    static double hours(long ms) {
        return Math.max(0.01, Math.round(ms / 36_000.0) / 100.0);
    }

    static String format(double hours) {
        return String.format(Locale.forLanguageTag("es-ES"), "%.2f", hours);
    }

    static void scheduleSync(Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        js.schedule(new JobInfo.Builder(1, new ComponentName(c, SyncJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setBackoffCriteria(30_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build());
    }
}
