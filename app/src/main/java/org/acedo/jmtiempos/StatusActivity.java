package org.acedo.jmtiempos;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Cambiar el estado de la tarea: lista los estados de su proyecto. Funciona sin red con los últimos guardados. */
public class StatusActivity extends Activity {
    private final List<JSONObject> labels = new ArrayList<>();
    private final List<String> rows = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private TextView message;
    private Store store;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        store = new Store(this);
        int ticket = store.ticketId();
        if (ticket == 0) {
            finish();
            return;
        }

        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView info = new TextView(this);
        info.setText("Estado de #" + ticket + " " + store.ticketName());
        root.addView(info);

        message = new TextView(this);
        root.addView(message);

        ListView list = new ListView(this);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows);
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        setContentView(root);
        getWindow().setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.92),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);

        list.setOnItemClickListener((p, v, pos, id) -> choose(labels.get(pos)));

        show(store.statusLabels(store.ticketProject()));
        message.setText("Actualizando…");
        new Thread(() -> {
            try {
                LeantimeApi api = new LeantimeApi(store);
                JSONObject t = api.ticket(ticket);
                int project = t.getInt("projectId");
                JSONArray fresh = api.statusLabels(project);
                store.setStatusLabels(project, fresh);
                // El cronómetro puede haber cerrado la tarea mientras se consultaba.
                if (store.ticketId() == ticket) store.setTicketState(project, t.getInt("status"));
                TimerWidget.refreshAll(this);
                runOnUiThread(() -> {
                    show(fresh);
                    message.setText("");
                });
            } catch (Exception e) {
                runOnUiThread(() -> message.setText(labels.isEmpty()
                        ? "Sin conexión: abre la lista de tareas con cobertura para cargar los estados."
                        : "Sin conexión: se enviará al recuperarla."));
            }
        }).start();
    }

    private void show(JSONArray all) {
        labels.clear();
        rows.clear();
        for (int i = 0; i < all.length(); i++) {
            JSONObject l = all.optJSONObject(i);
            labels.add(l);
            rows.add((l.optInt("id") == store.ticketStatus() ? "● " : "") + l.optString("name"));
        }
        adapter.notifyDataSetChanged();
    }

    private void choose(JSONObject label) {
        int status = label.optInt("id");
        String name = label.optString("name");
        if (label.optBoolean("done")) {
            store.setClose(status, name);
            // En marcha se aplica al parar; parado, ya.
            if (!store.running()) Timer.finishTask(this, store);
        } else {
            store.setMarkDone(false);
            if (status != store.ticketStatus()) {
                store.enqueueStatus(store.ticketId(), store.ticketName(), status, name);
                store.setTicketState(store.ticketProject(), status);
                Timer.scheduleSync(this);
            }
        }
        TimerWidget.refreshAll(this);
        finish();
    }
}
