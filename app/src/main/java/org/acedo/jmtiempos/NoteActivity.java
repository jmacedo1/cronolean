package org.acedo.jmtiempos;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

/** Ventana pequeña para escribir la nota, que se envía como descripción del registro. */
public class NoteActivity extends Activity {
    private Store store;
    private EditText note;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        store = new Store(this);

        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        note = new EditText(this);
        note.setHint("Qué estás haciendo");
        note.setText(store.note());
        note.setSelection(note.getText().length());
        note.setSingleLine(true);
        note.setImeOptions(EditorInfo.IME_ACTION_DONE);
        note.setOnEditorActionListener((v, action, e) -> {
            if (action == EditorInfo.IME_ACTION_DONE) {
                save();
                return true;
            }
            return false;
        });
        root.addView(note);

        Button ok = new Button(this);
        ok.setText("Guardar");
        ok.setOnClickListener(v -> save());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.END;
        root.addView(ok, lp);

        setContentView(root);
        getWindow().setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.92),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        note.requestFocus();
    }

    private void save() {
        store.setNote(note.getText().toString());
        TimerWidget.refreshAll(this);
        finish();
    }
}
