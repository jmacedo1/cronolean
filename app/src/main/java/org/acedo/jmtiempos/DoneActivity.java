package org.acedo.jmtiempos;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Confirma cerrar una tarea sin cronómetro en marcha: un toque sin querer no debe cerrarla. */
public class DoneActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Store s = new Store(this);
        if (s.ticketId() == 0) {
            finish();
            return;
        }

        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView info = new TextView(this);
        info.setText("¿Marcar como hecha #" + s.ticketId() + " " + s.ticketName() + "?");
        root.addView(info);

        Button done = new Button(this);
        done.setText("Marcar como hecha");
        done.setOnClickListener(v -> {
            s.setStatus("Enviando…");
            Timer.finishTask(this, s);
            TimerWidget.refreshAll(this);
            finish();
        });
        root.addView(done);

        Button cancel = new Button(this);
        cancel.setText("Cancelar");
        cancel.setOnClickListener(v -> finish());
        root.addView(cancel);

        setContentView(root);
        getWindow().setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.92),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
