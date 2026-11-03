package com.example.afluente.ui;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import com.example.afluente.model.RegistroConsumo;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.afluente.R;
import com.example.afluente.data.DatabaseHelper;
import com.example.afluente.data.RegistroConsumoDAO;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.math.BigDecimal;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RegistrarConsumoActivity extends AppCompatActivity {
    public static final String EXTRA_REGISTRO_ID = "registro_id";
    private long editId = -1;
    private boolean recordLoaded;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextInputEditText dateField, amountField, noteField;
    private TextInputLayout dateLayout, amountLayout;
    private MaterialButtonToggleGroup toggle;
    private String uid;
    private boolean busy;
    private volatile boolean closed;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_registrar_consumo);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }
        uid = user.getUid();
        editId = getIntent().getLongExtra(EXTRA_REGISTRO_ID, -1);
        dateField = findViewById(R.id.etDataRegistro);
        amountField = findViewById(R.id.etValorConsumo);
        noteField = findViewById(R.id.etObservacao);
        dateLayout = findViewById(R.id.inputData);
        amountLayout = findViewById(R.id.inputConsumo);
        toggle = findViewById(R.id.toggleTipoRecurso);
        findViewById(R.id.btnVoltar).setOnClickListener(v -> onBackPressed());
        dateField.setOnClickListener(v -> pickDate());
        toggle.addOnButtonCheckedListener((group, id, checked) -> { if (checked) updateResource(id); });
        updateResource(toggle.getCheckedButtonId());
        findViewById(R.id.btnSalvarRegistro).setOnClickListener(v -> save());
        if (editId > 0) {
            ((TextView) findViewById(R.id.tvTituloRegistro)).setText("Editar consumo");
            ((TextView) findViewById(R.id.btnSalvarRegistro)).setText("Salvar alterações");
            findViewById(R.id.tvAvisoEdicao).setVisibility(View.VISIBLE);
            recordLoaded = state != null && state.getBoolean("record_loaded", false);
            if (!recordLoaded) loadRecord();
        }
    }

    private void loadRecord() {
        setBusy(true);
        executor.execute(() -> {
            DatabaseHelper helper = new DatabaseHelper(getApplicationContext());
            try {
                RegistroConsumo record = new RegistroConsumoDAO(helper).buscar(uid, editId);
                runOnUiThread(() -> {
                    if (closed) return;
                    if (record == null) { Toast.makeText(this, "Registro não encontrado.", Toast.LENGTH_LONG).show(); finish(); return; }
                    toggle.check("ENERGIA".equals(record.tipo) ? R.id.btnTabEnergia : R.id.btnTabAgua);
                    updateResource(toggle.getCheckedButtonId());
                    dateField.setText(record.data);
                    amountField.setText(BigDecimal.valueOf(record.valor).stripTrailingZeros().toPlainString().replace('.', ','));
                    noteField.setText(record.observacao == null ? "" : record.observacao);
                    recordLoaded = true;
                    setBusy(false);
                });
            } catch (RuntimeException e) { showFailure("Não foi possível carregar o registro."); }
            finally { helper.close(); }
        });
    }

    private void updateResource(int id) {
        boolean energy = id == R.id.btnTabEnergia;
        ((TextView) findViewById(R.id.labelConsumo)).setText(energy ? R.string.register_energy_label : R.string.register_water_label);
        amountLayout.setSuffixText(energy ? "kWh" : "m³");
        amountField.setHint(energy ? R.string.register_energy_hint : R.string.register_water_hint);
    }

    private void pickDate() {
        Calendar calendar = Calendar.getInstance();
        Date selected = parseDate(text(dateField));
        if (selected != null) calendar.setTime(selected);
        new DatePickerDialog(this, (picker, year, month, day) -> {
            dateField.setText(String.format(Locale.ROOT, "%04d-%02d-%02d", year, month + 1, day));
            dateLayout.setError(null);
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void save() {
        if (busy) return;
        String date = text(dateField);
        String raw = text(amountField);
        dateLayout.setError(null);
        amountLayout.setError(null);
        if (parseDate(date) == null) {
            dateLayout.setError("Selecione uma data válida.");
            return;
        }
        BigDecimal value;
        try {
            if (!raw.matches("[0-9]+([.,][0-9]+)?")) throw new NumberFormatException();
            value = new BigDecimal(raw.replace(',', '.'));
            if (value.signum() <= 0 || !Double.isFinite(value.doubleValue())) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            amountLayout.setError("Informe um valor válido maior que zero.");
            return;
        }
        String type = toggle.getCheckedButtonId() == R.id.btnTabEnergia ? "ENERGIA" : "AGUA";
        String note = text(noteField);
        setBusy(true);
        executor.execute(() -> {
            DatabaseHelper helper = new DatabaseHelper(getApplicationContext());
            try {
                RegistroConsumoDAO dao = new RegistroConsumoDAO(helper);
                boolean hasTariff = editId > 0 ? dao.atualizarRegistro(uid, editId, type, value, date, note)
                        : dao.inserirRegistro(uid, type, value, date, note);
                runOnUiThread(() -> {
                    if (closed) return;
                    Toast.makeText(this, hasTariff ? "Registro salvo com sucesso." : "Registro salvo. Configure a tarifa para calcular o custo.", Toast.LENGTH_LONG).show();
                    finish();
                });
            } catch (IllegalStateException e) {
                showFailure(editId > 0 ? "Registro não encontrado ou indisponível." : "Perfil local não encontrado. Conclua o cadastro neste aparelho antes de registrar consumo.");
            } catch (RuntimeException e) {
                showFailure("Não foi possível salvar o registro. Verifique os dados e tente novamente.");
            } finally { helper.close(); }
        });
    }

    private void showFailure(String message) {
        runOnUiThread(() -> {
            if (closed) return;
            setBusy(false);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
    }

    private Date parseDate(String value) {
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) return null;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        format.setLenient(false);
        ParsePosition position = new ParsePosition(0);
        Date parsed = format.parse(value, position);
        return position.getIndex() == value.length() ? parsed : null;
    }

    private String text(TextInputEditText field) { return field.getText() == null ? "" : field.getText().toString().trim(); }
    private void setBusy(boolean value) {
        busy = value;
        findViewById(R.id.btnSalvarRegistro).setEnabled(!value);
        findViewById(R.id.btnVoltar).setEnabled(!value);
        toggle.setEnabled(!value);
        dateField.setEnabled(!value);
        amountField.setEnabled(!value);
        noteField.setEnabled(!value);
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("record_loaded", recordLoaded);
        super.onSaveInstanceState(state);
    }
    @Override public void onBackPressed() { if (!busy) super.onBackPressed(); }
    @Override protected void onDestroy() { closed = true; executor.shutdown(); super.onDestroy(); }
}
