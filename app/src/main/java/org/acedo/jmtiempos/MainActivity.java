package org.acedo.jmtiempos;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Ajustes de conexión, registros pendientes y acceso a añadir el widget. */
public class MainActivity extends Activity {
    private Store store;
    private LinearLayout pendingBox;
    private final List<String> companies = new ArrayList<>();
    private ArrayAdapter<String> companyAdapter;
    private Spinner company;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        store = new Store(this);

        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);
        // Con targetSdk 36 la app se dibuja bajo las barras del sistema.
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars()
                    | android.view.WindowInsets.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        title(root, "Conexión con Leantime");
        EditText url = field(root, "Dirección", store.url(), InputType.TYPE_TEXT_VARIATION_URI);
        url.setHint("https://leantime.ejemplo.com");
        EditText key = field(root, store.configured() ? "Clave de API (guardada; vacío para mantenerla)" : "Clave de API",
                "", InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText user = field(root, "Nº de tu usuario en Leantime (opcional)", store.userId(), InputType.TYPE_CLASS_NUMBER);
        CheckBox billable = new CheckBox(this);
        billable.setText("Registrar como facturable");
        billable.setChecked(Store.KIND_BILLABLE.equals(store.kind()));
        root.addView(billable);

        TextView companyLabel = new TextView(this);
        companyLabel.setText("Empresa por defecto al elegir tarea");
        root.addView(companyLabel);
        company = new Spinner(this);
        companyAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, companies);
        companyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        company.setAdapter(companyAdapter);
        root.addView(company);
        showCompanies();
        company.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                // Sin lista todavía solo existe "Todas": no debe pisar la empresa guardada.
                if (companies.size() > 1) store.setDefaultProject(pos == 0 ? "" : companies.get(pos));
            }

            @Override
            public void onNothingSelected(AdapterView<?> p) {}
        });

        TextView result = new TextView(this);
        Button save = new Button(this);
        save.setText("Guardar y probar");
        save.setOnClickListener(v -> {
            store.saveSettings(url.getText().toString(), key.getText().toString(), user.getText().toString(),
                    billable.isChecked() ? Store.KIND_BILLABLE : Store.KIND_NOT_BILLABLE);
            key.setText("");
            TimerWidget.refreshAll(this);
            result.setText("Probando…");
            new Thread(() -> {
                String text;
                try {
                    JSONArray tasks = new LeantimeApi(store).openTasks();
                    store.setTasksJson(tasks.toString());
                    text = "✓ Conectado: " + tasks.length() + " tareas abiertas";
                } catch (Exception e) {
                    text = "⚠ No se ha podido conectar: " + e.getMessage();
                }
                String finalText = text;
                runOnUiThread(() -> {
                    result.setText(finalText);
                    showCompanies();
                });
            }).start();
        });
        root.addView(save);
        root.addView(result);

        title(root, "Widget");
        TextView help = new TextView(this);
        help.setText("Añádelo a la pantalla de inicio: toca la tarea para elegirla, el recuadro para escribir "
                + "qué haces y el botón para iniciar y parar. Al parar, el tiempo se envía a Leantime.");
        root.addView(help);
        AppWidgetManager manager = getSystemService(AppWidgetManager.class);
        if (manager.isRequestPinAppWidgetSupported()) {
            Button pin = new Button(this);
            pin.setText("Añadir widget");
            pin.setOnClickListener(v -> manager.requestPinAppWidget(new ComponentName(this, TimerWidget.class), null, null));
            root.addView(pin);
        }

        title(root, "Pendientes de enviar");
        pendingBox = new LinearLayout(this);
        pendingBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(pendingBox);

        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        showPending();
    }

    private void showCompanies() {
        companies.clear();
        companies.add("Todas las empresas");
        companies.addAll(store.projects());
        companyAdapter.notifyDataSetChanged();
        company.setSelection(Math.max(companies.indexOf(store.defaultProject()), 0));
    }

    private void showPending() {
        pendingBox.removeAllViews();
        JSONArray q = store.queue();
        if (q.length() == 0) {
            TextView none = new TextView(this);
            none.setText("Nada pendiente.");
            pendingBox.addView(none);
            return;
        }
        if (!store.lastError().isEmpty()) {
            TextView error = new TextView(this);
            error.setText("Último error: " + store.lastError());
            pendingBox.addView(error);
        }
        for (int i = 0; i < q.length(); i++) {
            JSONObject item = q.optJSONObject(i);
            TextView row = new TextView(this);
            row.setText("#" + item.optInt("ticketId") + " " + item.optString("ticketName") + ": "
                    + Timer.format(item.optDouble("hours")) + " h");
            pendingBox.addView(row);
            Button discard = new Button(this);
            discard.setText("Descartar");
            discard.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setMessage("¿Descartar este registro? No se enviará a Leantime.")
                    .setPositiveButton("Descartar", (d, w) -> {
                        store.removeFromQueue(item.optString("id"));
                        showPending();
                    })
                    .setNegativeButton("Cancelar", null)
                    .show());
            pendingBox.addView(discard);
        }
        Button retry = new Button(this);
        retry.setText("Reintentar ahora");
        retry.setOnClickListener(v -> Timer.scheduleSync(this));
        pendingBox.addView(retry);
    }

    private void title(LinearLayout root, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(18);
        t.setPadding(0, (int) (16 * getResources().getDisplayMetrics().density), 0, 0);
        root.addView(t);
    }

    private EditText field(LinearLayout root, String label, String value, int variation) {
        TextView l = new TextView(this);
        l.setText(label);
        root.addView(l);
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setText(value);
        e.setInputType(variation == InputType.TYPE_CLASS_NUMBER ? variation : InputType.TYPE_CLASS_TEXT | variation);
        root.addView(e);
        return e;
    }
}
