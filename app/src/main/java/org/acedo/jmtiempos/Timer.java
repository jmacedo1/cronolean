package org.acedo.jmtiempos;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

import org.json.JSONArray;

import java.util.Locale;

/** Lógica del cronómetro: el tiempo corre en el dispositivo y se envía a Leantime al parar. */
final class Timer {
    /** Por debajo de esto se entiende que se ha pulsado sin querer y no se registra nada. */
    static final long MIN_MS = 30_000;
    /** A partir de esto se pide confirmar las horas: suele ser un cronómetro olvidado. */
    static final long REVIEW_MS = 8 * 3_600_000L;
    private static final int IN_PROGRESS = 4;

    private Timer() {}

    static void toggle(Context c) {
        Store s = new Store(c);
        if (s.running()) {
            stop(c, s, System.currentTimeMillis());
        } else if (s.ticketId() != 0 && s.configured() && !s.reviewPending()) {
            s.setStart(System.currentTimeMillis());
            s.setStatus("");
            startInProgress(c, s);
        }
        TimerWidget.refreshAll(c);
    }

    static void stop(Context c, Store s, long nowMs) {
        long start = s.start();
        long elapsed = Math.max(0, nowMs - start);
        int previous = s.startPrevStatus();
        s.clearStartPrevStatus();
        s.setStart(0);
        if (elapsed < MIN_MS) {
            s.setStatus("Cancelado: menos de 30 segundos");
            if (s.hasStopStatus()) {
                applyStopStatus(c, s);
            } else if (previous != Integer.MIN_VALUE) {
                sendStatus(c, s, previous, Store.statusName(s.statusLabels(s.ticketProject()), previous));
            }
            releaseIfClosed(s);
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
        if (s.hasStopStatus()) applyStopStatus(c, s);
        releaseIfClosed(s);
        scheduleSync(c);
    }

    /** Al iniciar, la tarea pasa a "En progreso" (el estado 4 de Leantime) si su proyecto lo tiene. */
    private static void startInProgress(Context c, Store s) {
        JSONArray labels = s.statusLabels(s.ticketProject());
        String name = Store.statusName(labels, IN_PROGRESS);
        if (name.isEmpty() || Store.isDone(labels, IN_PROGRESS) || s.ticketStatus() == IN_PROGRESS) return;
        s.setStartPrevStatus(s.ticketStatus());
        sendStatus(c, s, IN_PROGRESS, name);
    }

    /** Aplica el estado elegido para el final; si cierra la tarea, deja el widget listo para elegir otra. */
    private static void applyStopStatus(Context c, Store s) {
        if (s.stopStatusName().isEmpty()) {
            s.enqueueDone(s.ticketId(), s.ticketName());
            scheduleSync(c);
            release(s);
        } else if (s.stopStatusCloses()) {
            sendStatus(c, s, s.stopStatus(), s.stopStatusName());
            release(s);
        } else {
            sendStatus(c, s, s.stopStatus(), s.stopStatusName());
        }
        s.clearStopStatus();
    }

    /** Cambio directo y ya aceptado. En marcha la tarea sigue elegida: el tiempo aún tiene que registrarse en ella. */
    static void changeNow(Context c, Store s, int status, String name, boolean closes) {
        s.clearStartPrevStatus();
        s.clearStopStatus();
        sendStatus(c, s, status, name);
        if (closes && !s.running()) release(s);
    }

    private static void sendStatus(Context c, Store s, int status, String name) {
        s.enqueueStatus(s.ticketId(), s.ticketName(), status, name);
        s.setTicketState(s.ticketProject(), status);
        scheduleSync(c);
    }

    /** Una tarea cerrada a mano durante el cronómetro se suelta al parar; si no, iniciar la reabriría. */
    private static void releaseIfClosed(Store s) {
        if (s.ticketId() != 0 && s.ticketStatus() != Integer.MIN_VALUE
                && Store.isDone(s.statusLabels(s.ticketProject()), s.ticketStatus())) {
            release(s);
        }
    }

    private static void release(Store s) {
        s.removeCachedTask(s.ticketId());
        s.clearTicket();
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
