package sely.phoenix.tapper;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Calendar;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private int targetYear = -1, targetMonth = -1, targetDay = -1;
    private int targetHour = -1, targetMinute = -1;
    private TextView txtDateTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtDateTime = findViewById(R.id.txt_selected_datetime);
        EditText edtButtonText = findViewById(R.id.edt_button_text);
        EditText edtXCoord = findViewById(R.id.edt_x_coord);
        EditText edtYCoord = findViewById(R.id.edt_y_coord);
        EditText edtDuration = findViewById(R.id.edt_duration);

        // Fetch shared preference snapshot to populate previously saved runs
        SharedPreferences prefs = getSharedPreferences("TapperPrefs", Context.MODE_PRIVATE);
        edtButtonText.setText(prefs.getString("button_text", "Apply for unlocking"));
        edtXCoord.setText(String.valueOf(prefs.getInt("x_coord", 450)));
        edtYCoord.setText(String.valueOf(prefs.getInt("y_coord", 2150)));

        edtDuration.setText(String.valueOf(prefs.getInt("duration_secs", 30)));

        findViewById(R.id.btn_open_settings).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        );

        findViewById(R.id.btn_pick_date).setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                targetYear = year;
                targetMonth = month;
                targetDay = dayOfMonth;
                updateDateTimeUI();
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });

        findViewById(R.id.btn_pick_time).setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            new TimePickerDialog(this, (view, hourOfDay, minute) -> {
                targetHour = hourOfDay;
                targetMinute = minute;
                updateDateTimeUI();
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show();
        });

        findViewById(R.id.btn_save_config).setOnClickListener(v -> {
            if (targetYear == -1 || targetHour == -1) {
                Toast.makeText(this, "Please schedule date and time first!", Toast.LENGTH_SHORT).show();
                return;
            }

            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt("year", targetYear);
            editor.putInt("month", targetMonth);
            editor.putInt("day", targetDay);
            editor.putInt("hour", targetHour);
            editor.putInt("minute", targetMinute);
            editor.putString("button_text", edtButtonText.getText().toString());
            editor.putInt("x_coord", Integer.parseInt(edtXCoord.getText().toString().trim()));
            editor.putInt("y_coord", Integer.parseInt(edtYCoord.getText().toString().trim()));
            editor.putInt("duration_secs", Integer.parseInt(edtDuration.getText().toString().trim()));
            editor.apply();

            Toast.makeText(this, "Configuration Saved & Armed!", Toast.LENGTH_SHORT).show();
        });
    }

    private void updateDateTimeUI() {
        if (targetYear != -1 && targetHour != -1) {
            txtDateTime.setText(String.format(Locale.getDefault(),
                    "Scheduled for: %02d/%02d/%d %02d:%02d", targetDay, targetMonth + 1, targetYear, targetHour, targetMinute));
        } else if (targetYear != -1) {
            txtDateTime.setText(String.format(Locale.getDefault(), "Date selected: %02d/%02d/%d (Select Time Next)", targetDay, targetMonth + 1, targetYear));
        } else if (targetHour != -1) {
            txtDateTime.setText(String.format(Locale.getDefault(), "Time selected: %02d:%02d (Select Date Next)", targetHour, targetMinute));
        }
    }
}
