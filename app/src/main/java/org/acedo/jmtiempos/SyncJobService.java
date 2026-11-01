package org.acedo.jmtiempos;

import android.app.job.JobParameters;
import android.app.job.JobService;

import org.json.JSONArray;
import org.json.JSONObject;

/** Envía los registros pendientes. Si falla se reintenta: parar sin cobertura no pierde tiempo. */
public class SyncJobService extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> jobFinished(params, !flush())).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }

    private boolean flush() {
        Store s = new Store(this);
        LeantimeApi api = new LeantimeApi(s);
        JSONArray q = s.queue();
        String sent = "";
        for (int i = 0; i < q.length(); i++) {
            JSONObject item = q.optJSONObject(i);
            try {
                if ("done".equals(item.optString("type"))) {
                    api.markDone(item.getInt("ticketId"));
                    sent = sent.isEmpty() ? "✓ #" + item.getInt("ticketId") + " hecha" : sent + " · hecha";
                } else {
                    api.logTime(item);
                    sent = "✓ " + Timer.format(item.getDouble("hours")) + " h en #" + item.getInt("ticketId");
                }
                s.removeFromQueue(item.getString("id"));
                s.setLastError("");
            } catch (Exception e) {
                s.setLastError(String.valueOf(e.getMessage()));
                s.setStatus("⚠ Sin enviar: " + s.queue().length() + ". Toca para ver");
                TimerWidget.refreshAll(this);
                return false;
            }
        }
        if (!sent.isEmpty()) {
            s.setStatus(sent);
            TimerWidget.refreshAll(this);
        }
        return true;
    }
}
