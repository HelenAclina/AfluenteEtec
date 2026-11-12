package com.example.afluente.ui;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.example.afluente.R;
import com.example.afluente.data.DatabaseHelper;
import com.example.afluente.data.MetaDAO;
import com.example.afluente.service.AlertaService;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MetasConsumoActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Locale locale = new Locale("pt", "BR");
    private DatabaseHelper helper;
    private MetaDAO dao;
    private String uid;
    private volatile boolean closed;
    private boolean busy;
    private int request;
    private MetaDAO.Dados water, energy;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_metas_consumo);
        EdgeInsets.aplicar(this);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }
        uid = user.getUid();
        helper = new DatabaseHelper(getApplicationContext()); dao = new MetaDAO(helper);
        findViewById(R.id.btnVoltar).setOnClickListener(v -> finish());
        bind("AGUA", R.id.btnDiminuirMetaAgua, R.id.btnAumentarMetaAgua, R.id.tvValorMetaAgua, 1);
        bind("ENERGIA", R.id.btnDiminuirMetaEnergia, R.id.btnAumentarMetaEnergia, R.id.tvValorMetaEnergia, 10);
        BottomNavigationView nav = findViewById(R.id.bottomNavigation);
        nav.setOnItemSelectedListener(item -> Navegacao.abrir(this, item.getItemId()));
        setBusy(true);
    }
    @Override protected void onResume() { super.onResume(); if (dao != null) load(); }
    private void bind(String type, int minus, int plus, int value, int step) {
        findViewById(minus).setOnClickListener(v -> change(type, BigDecimal.valueOf(-step), true));
        findViewById(plus).setOnClickListener(v -> change(type, BigDecimal.valueOf(step), true));
        findViewById(value).setOnClickListener(v -> showInput(type));
    }
    private void load() {
        final int version = ++request;
        setBusy(true);
        executor.execute(() -> {
            try {
                dao.atualizarAlertas(uid);
                MetaDAO.Dados a = dao.consultar(uid, "AGUA"), b = dao.consultar(uid, "ENERGIA");
                runOnUiThread(() -> {
                    if (closed || version != request) return;
                    water = a; energy = b; render(); setBusy(false);
                });
            } catch (RuntimeException e) { fail("Não foi possível carregar as metas. Verifique seu perfil neste aparelho.", version); }
        });
    }
    private void change(String type, BigDecimal value, boolean increment) {
        if (busy) return;
        setBusy(true);
        final int version = ++request;
        executor.execute(() -> {
            try {
                if (increment) dao.incrementar(uid, type, value); else dao.salvarOuAtualizarMeta(uid, type, value);
                MetaDAO.Dados a = dao.consultar(uid, "AGUA"), b = dao.consultar(uid, "ENERGIA");
                runOnUiThread(() -> { if (!closed && version == request) { water = a; energy = b; render(); setBusy(false); } });
            } catch (IllegalArgumentException e) { fail(e.getMessage(), version); }
            catch (RuntimeException e) { fail("Não foi possível salvar a meta.", version); }
        });
    }
    private void showInput(String type) {
        if (busy) return;
        MetaDAO.Dados data = "AGUA".equals(type) ? water : energy;
        EditText input = new EditText(this);
        input.setSingleLine(true); input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("Valor maior que zero");
        if (data != null && data.meta != null) input.setText(BigDecimal.valueOf(data.meta).stripTrailingZeros().toPlainString().replace('.', ','));
        AlertDialog dialog = new MaterialAlertDialogBuilder(this).setTitle("Definir meta de " + ("AGUA".equals(type) ? "água (m³)" : "energia (kWh)"))
                .setMessage("Informe um valor positivo, com até três casas decimais.")
                .setView(input).setNegativeButton("Cancelar", null).setPositiveButton("Salvar", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
            try {
                String raw = input.getText().toString().trim().replace(',', '.');
                BigDecimal value = new BigDecimal(raw);
                if (value.signum() <= 0 || value.stripTrailingZeros().scale() > 3 || value.compareTo(new BigDecimal("1000000000")) > 0) throw new NumberFormatException();
                dialog.dismiss(); change(type, value, false);
            } catch (NumberFormatException e) { input.setError("Informe um valor válido de 0,001 a 1.000.000.000."); }
        }));
        dialog.show();
    }
    private void render() {
        renderMetric(water, "m³", R.id.tvTagStatusAgua, R.id.tvConsumidoAgua, R.id.tvMetaAgua, R.id.pbMetaAgua, R.id.tvExcedidoAgua, R.id.tvValorMetaAgua, R.color.water_accent);
        renderMetric(energy, "kWh", R.id.tvTagStatusEnergia, R.id.tvConsumidoEnergia, R.id.tvMetaEnergia, R.id.pbMetaEnergia, R.id.tvExcedidoEnergia, R.id.tvValorMetaEnergia, R.color.energy_accent);
    }
    private void renderMetric(MetaDAO.Dados data, String unit, int tagId, int consumedId, int goalId, int progressId, int excessId, int valueId, int normalColor) {
        Double goal = data.meta, consumed = data.consumo;
        boolean exceeded = goal != null && consumed != null && AlertaService.excedeu(consumed, goal);
        TextView tag = findViewById(tagId);
        tag.setText(goal == null ? "Sem meta" : consumed == null ? "Sem registros" : exceeded ? "Elevado" : "Dentro da meta");
        tag.setTextColor(ContextCompat.getColor(this, exceeded ? R.color.alert_red_text : R.color.onboarding_muted));
        GradientDrawable bg = new GradientDrawable(); bg.setCornerRadius(20 * getResources().getDisplayMetrics().density);
        bg.setColor(ContextCompat.getColor(this, exceeded ? R.color.alert_red_background : R.color.onboarding_selected)); tag.setBackground(bg);
        text(consumedId).setText("Consumido: " + (consumed == null ? "—" : number(consumed) + " " + unit));
        text(goalId).setText("Meta: " + (goal == null ? "—" : number(goal) + " " + unit));
        text(valueId).setText(goal == null ? "Definir meta" : number(goal) + " " + unit);
        LinearProgressIndicator progress = findViewById(progressId);
        int percentage = goal == null || consumed == null ? 0 : (int) Math.min(100, consumed / goal * 100);
        progress.setProgress(percentage); progress.setIndicatorColor(ContextCompat.getColor(this, exceeded ? R.color.alert_red_text : normalColor));
        progress.setContentDescription(goal == null || consumed == null ? "Progresso indisponível" : "Progresso da meta: " + number(consumed / goal * 100) + "%");
        TextView excess = findViewById(excessId); excess.setVisibility(exceeded ? View.VISIBLE : View.GONE);
        if (exceeded) excess.setText("Excedeu em " + number(consumed - goal) + " " + unit);
    }
    private String number(double value) { NumberFormat f = NumberFormat.getNumberInstance(locale); f.setMaximumFractionDigits(3); return f.format(value); }
    private TextView text(int id) { return findViewById(id); }
    private void setBusy(boolean value) {
        busy = value;
        int[] ids = {R.id.btnDiminuirMetaAgua, R.id.btnAumentarMetaAgua, R.id.tvValorMetaAgua, R.id.btnDiminuirMetaEnergia, R.id.btnAumentarMetaEnergia, R.id.tvValorMetaEnergia};
        for (int id : ids) findViewById(id).setEnabled(!value);
    }
    private void fail(String message, int version) { runOnUiThread(() -> { if (!closed && version == request) { setBusy(false); Toast.makeText(this, message, Toast.LENGTH_LONG).show(); } }); }
    @Override protected void onDestroy() { closed = true; if (helper != null) executor.execute(helper::close); executor.shutdown(); super.onDestroy(); }
}
