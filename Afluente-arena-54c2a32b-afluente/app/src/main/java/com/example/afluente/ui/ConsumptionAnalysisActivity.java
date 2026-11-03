package com.example.afluente.ui;

import android.os.Bundle;
import android.content.Intent;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.afluente.R;
import com.example.afluente.data.DashboardDAO;
import com.example.afluente.data.DatabaseHelper;
import com.example.afluente.service.DashboardService;
import com.example.afluente.service.AlertaService;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Locale;

/** Tela compartilhada por água e energia; apenas os dados e a cor mudam. */
public class ConsumptionAnalysisActivity extends AppCompatActivity {
    public static final String EXTRA_ENERGY = "energy";
    private boolean energy;
    private String unit;
    private DashboardService service;
    private final Locale locale = new Locale("pt", "BR");

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_consumption_analysis);
        energy = getIntent().getBooleanExtra(EXTRA_ENERGY, false);
        unit = energy ? "kWh" : "m³";
        text(R.id.tvRecursoTitulo).setText(energy ? "⚡ Energia elétrica" : "💧 Água");
        text(R.id.tvTituloGrafico).setText("Evolução mensal (" + unit + ")");
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }
        findViewById(R.id.btnVoltar).setOnClickListener(v -> finish());
        BottomNavigationView nav = findViewById(R.id.bottomNavigation);
        nav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_inicio) { finish(); return true; }
            if (item.getItemId() == R.id.nav_perfil) { startActivity(new Intent(this, PerfilActivity.class)); return false; }
            if (item.getItemId() == R.id.nav_consumo) {
                startActivity(new Intent(this, RegistrarConsumoActivity.class));
                return false;
            }
            if (item.getItemId() == R.id.nav_relatorios) {
                startActivity(new Intent(this, RelatoriosActivity.class));
                return false;
            }
            Toast.makeText(this, "Esta funcionalidade ainda será implementada.", Toast.LENGTH_SHORT).show();
            return false;
        });
        BarChart chart = findViewById(R.id.chartConsumo);
        chart.setNoDataText(energy ? "Nenhum consumo de energia registrado." : "Nenhum consumo de água registrado.");
        chart.setNoDataTextColor(ContextCompat.getColor(this, R.color.onboarding_muted));
        DatabaseHelper helper = new DatabaseHelper(getApplicationContext());
        service = new DashboardService(new DashboardDAO(helper));
    }

    @Override protected void onResume() {
        super.onResume();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (service == null || user == null) return;
        service.load(user.getUid(), new DashboardService.Callback() {
            @Override public void onSuccess(DashboardDAO.Snapshot data) { render(data); }
            @Override public void onError() { Toast.makeText(ConsumptionAnalysisActivity.this, "Não foi possível carregar os dados.", Toast.LENGTH_LONG).show(); }
        });
    }

    private void render(DashboardDAO.Snapshot d) {
        double[] values = energy ? d.energy : d.water;
        boolean[] available = energy ? d.hasEnergy : d.hasWater;
        Double tariff = energy ? d.energyTariff : d.waterTariff;
        Double goal = energy ? d.energyGoal : d.waterGoal;
        text(R.id.tvSituacao).setText(goal == null ? "Sem meta" : !available[5] ? "Sem registros" : AlertaService.excedeu(values[5], goal) ? "Elevado" : "Dentro da meta");
        text(R.id.tvMesAtual).setText("Atual (" + d.months[5] + ")");
        text(R.id.tvMesAnterior).setText("Anterior (" + d.months[4] + ")");
        text(R.id.tvAtual).setText(available[5] ? number(values[5]) + " " + unit : "—");
        text(R.id.tvAnterior).setText(available[4] ? number(values[4]) + " " + unit : "—");
        text(R.id.tvVariacao).setText("Sem comparação");
        if (available[5] && available[4] && values[4] > 0) {
            double variation = (values[5] - values[4]) / values[4] * 100;
            text(R.id.tvVariacao).setText(String.format(locale, "%s %.1f%%", variation >= 0 ? "↑" : "↓", Math.abs(variation)));
        }
        double total = 0; int count = 0;
        ArrayList<BarEntry> entries = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            if (available[i]) { total += values[i]; count++; entries.add(new BarEntry(i, (float) values[i], new ChartMarker.Value(values[i], unit))); }
        }
        // Médias mensais usam apenas meses com registros, não cada lançamento individual.
        text(R.id.tvMedia).setText(count == 0 ? "—" : number(total / count) + " " + unit);
        text(R.id.tvGastoLabel).setText("Gasto estimado (" + d.months[5] + ")");
        double estimate = available[5] && tariff != null && Double.isFinite(tariff) && tariff > 0 ? values[5] * tariff : Double.NaN;
        text(R.id.tvGasto).setText(Double.isFinite(estimate) ? NumberFormat.getCurrencyInstance(locale).format(estimate) : "—");
        BarChart chart = findViewById(R.id.chartConsumo);
        ChartMarker.attach(chart);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.getAxisRight().setEnabled(false);
        chart.getAxisLeft().setAxisMinimum(0f);
        chart.getAxisLeft().setDrawGridLines(true);
        chart.getAxisLeft().setTextColor(ContextCompat.getColor(this, R.color.onboarding_muted));
        chart.getAxisLeft().setGridColor(ContextCompat.getColor(this, R.color.onboarding_border));
        XAxis x = chart.getXAxis();
        x.setTextColor(ContextCompat.getColor(this, R.color.onboarding_muted));
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setValueFormatter(new IndexAxisValueFormatter(d.months));
        x.setGranularity(1f); x.setAxisMinimum(-0.5f); x.setAxisMaximum(5.5f); x.setDrawGridLines(false);
        if (entries.isEmpty()) chart.clear();
        else {
            BarDataSet set = new BarDataSet(entries, energy ? "Energia (kWh)" : "Água (m³)");
            set.setColor(ContextCompat.getColor(this, energy ? R.color.energy_accent : R.color.chart_water));
            set.setDrawValues(false);
            BarData data = new BarData(set); data.setBarWidth(0.72f); chart.setData(data);
        }
        chart.invalidate();
    }

    private String number(double value) {
        NumberFormat format = NumberFormat.getNumberInstance(locale);
        format.setMaximumFractionDigits(2);
        return format.format(value);
    }
    private TextView text(int id) { return findViewById(id); }
    @Override protected void onDestroy() { if (service != null) service.close(); super.onDestroy(); }
}
