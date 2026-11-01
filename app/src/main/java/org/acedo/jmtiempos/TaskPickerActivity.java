package org.acedo.jmtiempos;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Elegir tarea: filtro por empresa, buscador y primero las últimas usadas. Funciona sin red con la última lista. */
public class TaskPickerActivity extends Activity {
    private static final class Task {
        final int id;
        final String headline;
        final String project;

        Task(JSONObject o) {
            id = o.optInt("id");
            headline = o.optString("headline");
            project = o.optString("projectName");
        }

        @Override
        public String toString() {
            return "#" + id + " " + headline + "\n" + project;
        }
    }

    private final List<Task> all = new ArrayList<>();
    private final List<Task> shown = new ArrayList<>();
    private ArrayAdapter<Task> adapter;
    private final List<String> companies = new ArrayList<>();
    private ArrayAdapter<String> companyAdapter;
    private Spinner company;
    private String selectedCompany;
    private EditText search;
    private TextView message;
    private Store store;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        store = new Store(this);

        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        selectedCompany = store.defaultProject();
        company = new Spinner(this);
        companyAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, companies);
        companyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        company.setAdapter(companyAdapter);
        root.addView(company);

        search = new EditText(this);
        search.setHint("Buscar tarea");
        search.setSingleLine(true);
        root.addView(search);

        message = new TextView(this);
        root.addView(message);

        ListView list = new ListView(this);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, shown);
        list.setAdapter(adapter);
        int height = (int) (getResources().getDisplayMetrics().heightPixels * 0.6);
        root.addView(list, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height));
        setContentView(root);
        getWindow().setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.92),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);

        list.setOnItemClickListener((p, v, pos, id) -> {
            Task t = shown.get(pos);
            store.setTicket(t.id, t.headline);
            TimerWidget.refreshAll(this);
            finish();
        });
        company.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                // Sin lista todavía solo existe "Todas": no debe pisar la empresa por defecto.
                if (companies.size() <= 1) return;
                selectedCompany = pos == 0 ? "" : companies.get(pos);
                filter();
            }

            @Override
            public void onNothingSelected(AdapterView<?> p) {}
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) {}
            @Override public void afterTextChanged(Editable s) { filter(); }
        });

        load(store.tasksJson());
        if (!store.configured()) {
            message.setText("Configura primero la conexión en la app JM Tiempos.");
            return;
        }
        message.setText(all.isEmpty() ? "Cargando tareas…" : "Actualizando…");
        new Thread(() -> {
            try {
                String json = new LeantimeApi(store).openTasks().toString();
                store.setTasksJson(json);
                runOnUiThread(() -> {
                    load(json);
                    message.setText("");
                });
            } catch (Exception e) {
                runOnUiThread(() -> message.setText("Sin conexión: se muestra la última lista."));
            }
        }).start();
    }

    private void load(String json) {
        all.clear();
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) all.add(new Task(a.getJSONObject(i)));
        } catch (Exception ignored) {
            // Una lista guardada ilegible equivale a no tener lista: se vuelve a pedir.
        }
        List<Integer> recents = store.recents();
        all.sort(Comparator.<Task>comparingInt(t -> {
            int i = recents.indexOf(t.id);
            return i < 0 ? Integer.MAX_VALUE : i;
        }).thenComparing(t -> -t.id));
        companies.clear();
        companies.add("Todas las empresas");
        companies.addAll(store.projects());
        companyAdapter.notifyDataSetChanged();
        // Una empresa que ya no tiene tareas abiertas no debe dejar la lista vacía.
        int position = companies.indexOf(selectedCompany);
        if (position < 0 && companies.size() > 1) selectedCompany = "";
        company.setSelection(Math.max(position, 0));
        filter();
    }

    private void filter() {
        String q = search.getText().toString().toLowerCase(Locale.ROOT).trim();
        shown.clear();
        for (Task t : all) {
            boolean inCompany = selectedCompany.isEmpty() || selectedCompany.equals(t.project);
            if (inCompany && (q.isEmpty() || t.toString().toLowerCase(Locale.ROOT).contains(q))) shown.add(t);
        }
        adapter.notifyDataSetChanged();
    }
}
