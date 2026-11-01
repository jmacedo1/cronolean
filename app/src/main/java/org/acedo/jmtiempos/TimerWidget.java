package org.acedo.jmtiempos;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;

/**
 * Widget: cabecera con el cronómetro, tarea, nota, check de hecha y un único botón que cambia de texto y color.
 * Android no admite cajas de texto en un widget: la nota se escribe en una ventana pequeña.
 * Lo que abre cada toque se decide al pintar, porque desde un receptor en segundo plano
 * Android no deja abrir pantallas.
 */
public class TimerWidget extends AppWidgetProvider {
    private static final String ACTION_TOGGLE = "org.acedo.jmtiempos.TOGGLE";
    private static final String ACTION_CHECK = "org.acedo.jmtiempos.CHECK";

    @Override
    public void onReceive(Context c, Intent i) {
        if (ACTION_TOGGLE.equals(i.getAction())) {
            Timer.toggle(c);
            return;
        }
        if (ACTION_CHECK.equals(i.getAction())) {
            Store s = new Store(c);
            s.setMarkDone(!s.markDone());
            refreshAll(c);
            return;
        }
        super.onReceive(c, i);
    }

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        m.updateAppWidget(ids, build(c));
    }

    static void refreshAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, TimerWidget.class));
        if (ids.length > 0) m.updateAppWidget(ids, build(c));
    }

    private static RemoteViews build(Context c) {
        Store s = new Store(c);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_timer);

        v.setTextViewText(R.id.task, s.ticketId() == 0
                ? c.getString(R.string.choose_task)
                : "▾ #" + s.ticketId() + " " + s.ticketName());
        v.setTextViewText(R.id.note, s.note().isEmpty() ? c.getString(R.string.note_hint) : s.note());
        v.setTextViewText(R.id.status, s.status());

        long sinceStart = s.running() ? System.currentTimeMillis() - s.start() : 0;
        v.setChronometer(R.id.chrono, SystemClock.elapsedRealtime() - sinceStart, null, s.running());

        PendingIntent toggle;
        int text;
        int background;
        if (!s.configured()) {
            toggle = activity(c, MainActivity.class, 1);
            text = R.string.configure;
            background = R.drawable.btn_review;
        } else if (s.reviewPending()) {
            toggle = activity(c, ReviewActivity.class, 2);
            text = R.string.review;
            background = R.drawable.btn_review;
        } else if (s.running()) {
            toggle = broadcast(c, ACTION_TOGGLE, 0);
            text = R.string.stop;
            background = R.drawable.btn_stop;
        } else if (s.ticketId() == 0) {
            toggle = activity(c, TaskPickerActivity.class, 3);
            text = R.string.start;
            background = R.drawable.btn_start;
        } else {
            toggle = broadcast(c, ACTION_TOGGLE, 0);
            text = R.string.start;
            background = R.drawable.btn_start;
        }
        v.setInt(R.id.header, "setBackgroundResource", s.running() ? R.drawable.header_running
                : s.reviewPending() ? R.drawable.header_review : R.drawable.header_idle);
        v.setTextViewText(R.id.toggle, c.getString(text));
        v.setInt(R.id.toggle, "setBackgroundResource", background);
        v.setOnClickPendingIntent(R.id.toggle, toggle);

        // En marcha el check solo se apunta, y se aplica al parar; parado, pide confirmación.
        boolean showCheck = s.configured() && s.ticketId() != 0 && !s.reviewPending();
        v.setViewVisibility(R.id.check, showCheck ? View.VISIBLE : View.GONE);
        if (showCheck && s.running()) {
            v.setTextViewText(R.id.check, (s.markDone() ? "☑" : "☐") + " Hecha al parar");
            v.setOnClickPendingIntent(R.id.check, broadcast(c, ACTION_CHECK, 10));
        } else if (showCheck) {
            v.setTextViewText(R.id.check, "☐ Hecha");
            v.setOnClickPendingIntent(R.id.check, activity(c, DoneActivity.class, 7));
        }

        v.setOnClickPendingIntent(R.id.task, activity(c, TaskPickerActivity.class, 4));
        v.setOnClickPendingIntent(R.id.note, activity(c, NoteActivity.class, 5));
        v.setOnClickPendingIntent(R.id.status, activity(c, MainActivity.class, 6));
        return v;
    }

    private static PendingIntent activity(Context c, Class<?> target, int code) {
        Intent i = new Intent(c, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent broadcast(Context c, String action, int code) {
        Intent i = new Intent(c, TimerWidget.class).setAction(action);
        return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
