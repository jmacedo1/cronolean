package org.acedo.jmtiempos;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Cronómetro de muchas horas, probablemente olvidado: se corrige antes de enviarlo. */
public class ReviewActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Store s = new Store(this);
        if (!s.reviewPending()) {
            finish();
            return;
        }

        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        TextView info = new TextView(this);
        info.setText("¿Son correctas estas horas para #" + s.ticketId() + " " + s.ticketName() + "?");
        root.addView(info);

        EditText hours = new EditText(this);
        hours.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        hours.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789.,"));
        hours.setText(Timer.format(s.reviewHours()));
        root.addView(hours);

        Button save = new Button(this);
        save.setText("Guardar");
        save.setOnClickListener(v -> {
            double value;
            try {
                value = Double.parseDouble(hours.getText().toString().replace(',', '.'));
            } catch (NumberFormatException e) {
                value = 0;
            }
            if (value <= 0) {
                hours.setError("Indica las horas");
                return;
            }
            Timer.submit(this, s, s.reviewStart(), value);
            TimerWidget.refreshAll(this);
            finish();
        });
        root.addView(save);

        Button discard = new Button(this);
        discard.setText("Descartar");
        discard.setOnClickListener(v -> {
            s.clearReview();
            s.setStatus("Descartado");
            TimerWidget.refreshAll(this);
            finish();
        });
        root.addView(discard);

        setContentView(root);
        getWindow().setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.92),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
