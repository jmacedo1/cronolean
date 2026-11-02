package org.acedo.jmtiempos;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Estado de la tarea: lo normal es elegir a cuál pasará al parar el cronómetro, que no cambia nada todavía.
 * Cambiarlo en el momento va aparte y pide aceptar. Funciona sin red con los últimos estados guardados.
 */
public class StatusActivity extends Activity {
    private final List<JSONObject> labels = new ArrayList<>();
    private final List<String> rows = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private TextView message;
    private TextView current;
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
        info.setText("#" + ticket + " " + store.ticketName());
        root.addView(info);

        current = new TextView(this);
        root.addView(current);

        TextView title = new TextView(this);
        title.setText("Al parar el cronómetro, pasar a:");
        title.setPadding(0, pad, 0, 0);
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title);

        message = new TextView(this);
        root.addView(message);

        ListView list = new ListView(this);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows);
        list.setAdapter(adapter);
        int height = (int) (getResources().getDisplayMetrics().heightPixels * 0.45);
        root.addView(list, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height));

        Button now = new Button(this);
        now.setText("Cambiar el estado ahora…");
        now.setOnClickListener(v -> askChangeNow());
        LinearLayout.LayoutParams nowParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        nowParams.topMargin = pad;
        root.addView(now, nowParams);
        setContentView(root);
        getWindow().setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.92),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);

        list.setOnItemClickListener((p, v, pos, id) -> chooseAtStop(pos == 0 ? null : labels.get(pos - 1)));

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
        rows.add((store.hasStopStatus() ? "" : "● ") + "Dejarla como está");
        for (int i = 0; i < all.length(); i++) {
            JSONObject l = all.optJSONObject(i);
            labels.add(l);
            boolean chosen = store.hasStopStatus() && l.optInt("id") == store.stopStatus();
            rows.add((chosen ? "● " : "") + l.optString("name"));
        }
        adapter.notifyDataSetChanged();
        String name = store.ticketStatusName();
        current.setText("Estado actual: " + (name.isEmpty() ? "sin datos" : name));
    }

    /** Solo se apunta: no se envía nada hasta parar. */
    private void chooseAtStop(JSONObject label) {
        if (label == null || label.optInt("id") == store.ticketStatus()) {
            store.clearStopStatus();
        } else {
            store.setStopStatus(label.optInt("id"), label.optString("name"), label.optBoolean("done"));
        }
        TimerWidget.refreshAll(this);
        finish();
    }

    private void askChangeNow() {
        if (labels.isEmpty()) return;
        String[] names = new String[labels.size()];
        for (int i = 0; i < names.length; i++) names[i] = labels.get(i).optString("name");
        new AlertDialog.Builder(this)
                .setTitle("Cambiar el estado ahora")
                .setItems(names, (d, pos) -> confirmChangeNow(labels.get(pos)))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void confirmChangeNow(JSONObject label) {
        String name = label.optString("name");
        new AlertDialog.Builder(this)
                .setMessage("¿Cambiar #" + store.ticketId() + " a «" + name + "» ahora?")
                .setPositiveButton("Aceptar", (d, w) -> {
                    Timer.changeNow(this, store, label.optInt("id"), name, label.optBoolean("done"));
                    TimerWidget.refreshAll(this);
                    finish();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
}
