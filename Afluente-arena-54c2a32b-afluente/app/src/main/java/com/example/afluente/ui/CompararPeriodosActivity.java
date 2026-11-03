package com.example.afluente.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.afluente.R;
import com.example.afluente.data.ComparacaoDAO;
import com.example.afluente.data.DatabaseHelper;
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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Compara totais mensais registrados, sem converter ausência de dados em zero. */
public class CompararPeriodosActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Locale locale = new Locale("pt", "BR");
    private final List<String> months = new ArrayList<>();
    private DatabaseHelper helper;
    private ComparacaoDAO dao;
    private Spinner first, second;
    private String uid;
    private volatile boolean closed;
    private boolean initializing;
    private int generation;
    private int monthsGeneration;
    private String restoredFirst, restoredSecond;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_comparar_periodos);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }
        uid = user.getUid();
        if (state != null) { restoredFirst = state.getString("period_1"); restoredSecond = state.getString("period_2"); }
        helper = new DatabaseHelper(getApplicationContext());
        dao = new ComparacaoDAO(helper);
        first = findViewById(R.id.spPeriodo1); second = findViewById(R.id.spPeriodo2);
        first.setEnabled(false); second.setEnabled(false);
        BarChart chart = findViewById(R.id.barChartComparativo);
        chart.setNoDataText("Sem dados para comparar.");
        chart.setNoDataTextColor(ContextCompat.getColor(this, R.color.onboarding_muted));
        findViewById(R.id.btnVoltar).setOnClickListener(v -> finish());
        BottomNavigationView navigation = findViewById(R.id.bottomNavigation);
        navigation.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_inicio) { finish(); return true; }
            if (item.getItemId() == R.id.nav_consumo) { startActivity(new Intent(this, RegistrarConsumoActivity.class)); return false; }
            if (item.getItemId() == R.id.nav_relatorios) { startActivity(new Intent(this, RelatoriosActivity.class)); return false; }
            if (item.getItemId() == R.id.nav_perfil) { startActivity(new Intent(this, PerfilActivity.class)); return false; }
            return false;
        });
    }

    @Override protected void onResume() {
        super.onResume();
        if (dao == null) return;
        if (first.getSelectedItem() != null) restoredFirst = first.getSelectedItem().toString();
        if (second.getSelectedItem() != null) restoredSecond = second.getSelectedItem().toString();
        loadMonths();
    }

    private void loadMonths() {
        final int request = ++monthsGeneration;
        ++generation;
        clearValues();
        executor.execute(() -> {
            try {
                List<String> result = dao.listarMeses(uid);
                runOnUiThread(() -> {
                    if (closed || request != monthsGeneration) return;
                    months.clear(); months.addAll(result);
                    boolean empty = months.isEmpty();
                    findViewById(R.id.tvSemDadosComparacao).setVisibility(empty ? View.VISIBLE : View.GONE);
                    first.setEnabled(!empty); second.setEnabled(!empty);
                    if (empty) {
                        first.setAdapter(null); second.setAdapter(null);
                        return;
                    }
                    initializing = true;
                    MonthAdapter adapter = new MonthAdapter(months);
                    first.setAdapter(adapter); second.setAdapter(adapter);
                    int firstIndex = months.indexOf(restoredFirst), secondIndex = months.indexOf(restoredSecond);
                    first.setSelection(firstIndex >= 0 ? firstIndex : Math.min(1, months.size() - 1));
                    second.setSelection(secondIndex >= 0 ? secondIndex : 0);
                    AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
                        @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { if (!initializing) loadComparison(); }
                        @Override public void onNothingSelected(AdapterView<?> parent) { }
                    };
                    first.setOnItemSelectedListener(listener); second.setOnItemSelectedListener(listener);
                    initializing = false;
                    loadComparison();
                });
            } catch (RuntimeException e) { showError(); }
        });
    }

    private void loadComparison() {
        if (first.getSelectedItem() == null || second.getSelectedItem() == null) return;
        final String period1 = first.getSelectedItem().toString(), period2 = second.getSelectedItem().toString();
        final int request = ++generation;
        // Não deixa resultados de uma seleção anterior aparentarem pertencer à seleção atual.
        clearValues();
        executor.execute(() -> {
            try {
                ComparacaoDAO.Periodo a = dao.consultar(uid, period1);
                ComparacaoDAO.Periodo b = period1.equals(period2) ? a : dao.consultar(uid, period2);
                runOnUiThread(() -> { if (!closed && request == generation) render(period1, period2, a, b); });
            } catch (RuntimeException e) { runOnUiThread(() -> { if (!closed && request == generation) Toast.makeText(this, "Não foi possível comparar os períodos.", Toast.LENGTH_LONG).show(); }); }
        });
    }

    private void render(String p1, String p2, ComparacaoDAO.Periodo a, ComparacaoDAO.Periodo b) {
        String label1 = formatMonth(p1), label2 = formatMonth(p2);
        renderMetric(R.id.tvAguaMes1, R.id.tvAguaMes2, R.id.tvAguaPeriodo1, R.id.tvAguaPeriodo2, R.id.tvVariacaoAgua, label1, label2, a.agua, b.agua, " m³", false);
        renderMetric(R.id.tvEnergiaMes1, R.id.tvEnergiaMes2, R.id.tvEnergiaPeriodo1, R.id.tvEnergiaPeriodo2, R.id.tvVariacaoEnergia, label1, label2, a.energia, b.energia, " kWh", false);
        renderMetric(R.id.tvGastoMes1, R.id.tvGastoMes2, R.id.tvGastoPeriodo1, R.id.tvGastoPeriodo2, R.id.tvVariacaoGasto, label1, label2, a.custo, b.custo, "", true);
        TextView note = findViewById(R.id.tvNotaGasto);
        note.setVisibility(a.custoIncompleto || b.custoIncompleto ? View.VISIBLE : View.GONE);
        renderChart(label1, label2, a, b);
    }

    private void renderMetric(int label1Id, int label2Id, int value1Id, int value2Id, int changeId,
                              String label1, String label2, Double a, Double b, String suffix, boolean currency) {
        text(label1Id).setText(label1); text(label2Id).setText(label2);
        text(value1Id).setText(formatValue(a, suffix, currency)); text(value2Id).setText(formatValue(b, suffix, currency));
        TextView variation = text(changeId);
        if (a == null || b == null || a == 0) {
            variation.setText("Sem comparação"); variation.setTextColor(ContextCompat.getColor(this, R.color.onboarding_muted));
        } else {
            double change = (b - a) / a * 100;
            if (!Double.isFinite(change)) {
                variation.setText("Fora da escala");
                variation.setTextColor(ContextCompat.getColor(this, R.color.onboarding_muted));
                return;
            }
            variation.setText(String.format(locale, "%s %.1f%%", change > 0 ? "↑" : change < 0 ? "↓" : "=", Math.abs(change)));
            variation.setTextColor(ContextCompat.getColor(this, change > 0 ? R.color.alert_red_text : change < 0 ? R.color.alert_green_text : R.color.onboarding_muted));
        }
    }

    private String formatValue(Double value, String suffix, boolean currency) {
        if (value == null) return "—";
        NumberFormat f = currency ? NumberFormat.getCurrencyInstance(locale) : NumberFormat.getNumberInstance(locale);
        if (!currency) f.setMaximumFractionDigits(3);
        return f.format(value) + suffix;
    }

    private void renderChart(String label1, String label2, ComparacaoDAO.Periodo a, ComparacaoDAO.Periodo b) {
        BarChart chart = findViewById(R.id.barChartComparativo);
        ChartMarker.attach(chart);
        chart.getDescription().setEnabled(false);
        chart.getAxisRight().setEnabled(false);
        chart.getAxisLeft().setAxisMinimum(0f);
        chart.getAxisLeft().setTextColor(ContextCompat.getColor(this, R.color.onboarding_muted));
        chart.getAxisLeft().setGridColor(ContextCompat.getColor(this, R.color.onboarding_border));
        chart.getLegend().setTextColor(ContextCompat.getColor(this, R.color.onboarding_muted));
        ArrayList<BarEntry> values1 = new ArrayList<>(), values2 = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        if (chartable(a.agua, b.agua)) addGroup(labels, values1, values2, "Água (m³)", a.agua, b.agua);
        if (chartable(a.energia, b.energia)) addGroup(labels, values1, values2, "Energia (kWh)", a.energia, b.energia);
        if (labels.isEmpty()) {
            chart.clear(); chart.setNoDataText("Sem pares completos para comparar."); chart.invalidate(); return;
        }
        BarDataSet set1 = new BarDataSet(values1, label1), set2 = new BarDataSet(values2, label2);
        set1.setColor(ContextCompat.getColor(this, R.color.compare_period_one));
        set2.setColor(ContextCompat.getColor(this, R.color.compare_period_two));
        set1.setDrawValues(false); set2.setDrawValues(false);
        BarData data = new BarData(set1, set2); data.setBarWidth(0.42f);
        chart.setData(data);
        XAxis axis = chart.getXAxis();
        axis.setPosition(XAxis.XAxisPosition.BOTTOM); axis.setDrawGridLines(false);
        axis.setTextColor(ContextCompat.getColor(this, R.color.onboarding_muted));
        axis.setCenterAxisLabels(true); axis.setGranularity(1f);
        axis.setValueFormatter(new IndexAxisValueFormatter(labels.toArray(new String[0])));
        axis.setAxisMinimum(0f); axis.setAxisMaximum(labels.size());
        axis.setLabelCount(labels.size());
        chart.groupBars(0f, 0.08f, 0.04f);
        chart.invalidate();
    }

    private boolean chartable(Double a, Double b) {
        return a != null && b != null && Float.isFinite(a.floatValue()) && Float.isFinite(b.floatValue());
    }

    private void addGroup(List<String> labels, List<BarEntry> one, List<BarEntry> two, String label, double a, double b) {
        int index = labels.size(); labels.add(label);
        String unit = label.startsWith("Água") ? "m³" : "kWh";
        one.add(new BarEntry(index, (float) a, new ChartMarker.Value(a, unit)));
        two.add(new BarEntry(index, (float) b, new ChartMarker.Value(b, unit)));
    }

    private void clearValues() {
        int[] ids = {R.id.tvAguaPeriodo1, R.id.tvAguaPeriodo2, R.id.tvEnergiaPeriodo1, R.id.tvEnergiaPeriodo2,
                R.id.tvGastoPeriodo1, R.id.tvGastoPeriodo2, R.id.tvVariacaoAgua, R.id.tvVariacaoEnergia, R.id.tvVariacaoGasto};
        for (int id : ids) text(id).setText("—");
        findViewById(R.id.tvNotaGasto).setVisibility(View.GONE);
        ((BarChart) findViewById(R.id.barChartComparativo)).clear();
    }

    private String formatMonth(String period) {
        try {
            SimpleDateFormat input = new SimpleDateFormat("yyyy-MM", Locale.ROOT); input.setLenient(false);
            java.util.Date date = input.parse(period);
            return date == null ? period : new SimpleDateFormat("MMM/yyyy", locale).format(date).replace(".", "");
        } catch (ParseException e) { return period; }
    }
    private TextView text(int id) { return findViewById(id); }
    private void showError() { runOnUiThread(() -> { if (!closed) Toast.makeText(this, "Não foi possível carregar os períodos.", Toast.LENGTH_LONG).show(); }); }

    @Override protected void onSaveInstanceState(Bundle state) {
        if (first != null && first.getSelectedItem() != null) state.putString("period_1", first.getSelectedItem().toString());
        if (second != null && second.getSelectedItem() != null) state.putString("period_2", second.getSelectedItem().toString());
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() { closed = true; if (helper != null) executor.execute(helper::close); executor.shutdown(); super.onDestroy(); }

    private class MonthAdapter extends ArrayAdapter<String> {
        MonthAdapter(List<String> data) { super(CompararPeriodosActivity.this, R.layout.item_compare_spinner, data); }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            TextView view = (TextView) (convertView == null ? LayoutInflater.from(getContext()).inflate(R.layout.item_compare_spinner, parent, false) : convertView);
            view.setText(formatMonth(getItem(position))); view.setTextColor(Color.WHITE); return view;
        }
        @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
            TextView view = (TextView) (convertView == null ? LayoutInflater.from(getContext()).inflate(R.layout.item_compare_spinner, parent, false) : convertView);
            view.setText(formatMonth(getItem(position)));
            view.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            view.setTextColor(ContextCompat.getColor(getContext(), R.color.onboarding_text));
            view.setBackgroundColor(ContextCompat.getColor(getContext(), R.color.surface));
            return view;
        }
    }
}
