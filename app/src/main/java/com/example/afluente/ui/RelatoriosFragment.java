package com.example.afluente.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;

import com.example.afluente.R;
import com.example.afluente.data.DatabaseHelper;
import com.example.afluente.data.RelatorioDAO;
import com.example.afluente.data.RelatorioAnualDAO;
import com.example.afluente.service.RelatorioService;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RelatoriosFragment extends Fragment {
    private static final int EXPORT_REQUEST = 411;
    private final Locale locale = new Locale("pt", "BR");
    private final ExecutorService exportExecutor = Executors.newSingleThreadExecutor();
    private RelatorioService service;
    private View root;
    private String uid;
    private int months = 6;
    private RelatorioDAO.Resultado current;
    private String pendingCsv;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_relatorios, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        root = view;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { requireActivity().finish(); return; }
        uid = user.getUid();
        if (state != null) { months = state.getInt("months", 6); pendingCsv = state.getString("pending_csv"); }
        DatabaseHelper helper = new DatabaseHelper(requireContext().getApplicationContext());
        RelatorioDAO dao = new RelatorioDAO(helper);
        service = new RelatorioService(dao, new RelatorioAnualDAO(helper, dao));
        MaterialButtonToggleGroup toggle = view.findViewById(R.id.toggleTipoRelatorio);
        toggle.check(months == 12 ? R.id.btnTabAnual : R.id.btnTabMensal);
        toggle.addOnButtonCheckedListener((group, id, checked) -> {
            if (checked) { months = id == R.id.btnTabAnual ? 12 : 6; load(); }
        });
        view.findViewById(R.id.btnVoltar).setOnClickListener(v -> requireActivity().finish());
        view.findViewById(R.id.btnExportar).setEnabled(false);
        view.findViewById(R.id.btnExportar).setOnClickListener(v -> export());
        BottomNavigationView nav = view.findViewById(R.id.bottomNavigation);
        nav.setSelectedItemId(R.id.nav_relatorios);
        nav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_relatorios) return true;
            if (item.getItemId() == R.id.nav_perfil) { startActivity(new Intent(requireContext(), PerfilActivity.class)); return false; }
            if (item.getItemId() == R.id.nav_inicio) { requireActivity().finish(); return false; }
            if (item.getItemId() == R.id.nav_consumo) { startActivity(new Intent(requireContext(), ConsumptionAnalysisActivity.class)); return false; }
            Toast.makeText(requireContext(), "Esta funcionalidade ainda será implementada.", Toast.LENGTH_SHORT).show();
            return false;
        });
    }

    @Override public void onResume() { super.onResume(); if (service != null) load(); }
    private void load() {
        current = null;
        root.findViewById(R.id.btnExportar).setEnabled(false);
        setReportVisible(false);
        text(R.id.tvPeriodoRelatorio).setText("Carregando relatório…");
        service.carregar(uid, months, new RelatorioService.Callback() {
            @Override public void onSuccess(RelatorioDAO.Resultado data) {
                if (root == null) return;
                current = data; render(data); setReportVisible(true); root.findViewById(R.id.btnExportar).setEnabled(true);
            }
            @Override public void onError() { if (isAdded() && root != null) { text(R.id.tvPeriodoRelatorio).setText("Não foi possível carregar o relatório."); Toast.makeText(requireContext(), "Não foi possível carregar os relatórios.", Toast.LENGTH_LONG).show(); } }
        });
    }

    private void setReportVisible(boolean visible) {
        int[] ids = {R.id.gridPicos, R.id.cardLinha, R.id.cardLinhaEnergia, R.id.cardBarras, R.id.cardResumoPeriodo};
        for (int id : ids) root.findViewById(id).setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
    }

    private void render(RelatorioDAO.Resultado data) {
        boolean annual = months == 12;
        String year = data.meses[0].periodo.substring(0, 4);
        text(R.id.tvPeriodoRelatorio).setText(annual ? "Ano civil: " + year : "Últimos 6 meses (incluindo o atual)");
        text(R.id.tvTituloGraficoAgua).setText(annual ? "Evolução anual — Água (m³)" : "Consumo de água por mês (m³)");
        text(R.id.tvTituloGraficoEnergia).setText(annual ? "Evolução anual — Energia (kWh)" : "Consumo de energia por mês (kWh)");
        text(R.id.tvTituloGraficoGastos).setText(annual ? "Gastos totais — " + year + " (R$)" : "Gastos totais por mês (R$)");
        RelatorioDAO.Mes maxWater = null, minWater = null, maxEnergy = null, minEnergy = null;
        double water = 0, energy = 0, cost = 0;
        boolean anyWater = false, anyEnergy = false, anyRecords = false, incomplete = false;
        String[] labels = new String[data.meses.length];
        ArrayList<BarEntry> bars = new ArrayList<>();
        ArrayList<ILineDataSet> lines = new ArrayList<>();
        ArrayList<Entry> segment = new ArrayList<>();
        ArrayList<ILineDataSet> energyLines = new ArrayList<>();
        ArrayList<Entry> energySegment = new ArrayList<>();
        int color = ContextCompat.getColor(requireContext(), R.color.chart_water);
        int energyColor = ContextCompat.getColor(requireContext(), R.color.energy_accent);
        for (int i = 0; i < data.meses.length; i++) {
            RelatorioDAO.Mes m = data.meses[i];
            labels[i] = m.rotulo;
            if (m.temAgua) {
                anyWater = true; water += m.agua; segment.add(new Entry(i, (float) m.agua, new ChartMarker.Value(m.agua, "m³")));
                if (maxWater == null || m.agua > maxWater.agua) maxWater = m;
                if (minWater == null || m.agua < minWater.agua) minWater = m;
            } else if (!segment.isEmpty()) { addLine(lines, segment, color); segment = new ArrayList<>(); }
            if (m.temEnergia) {
                anyEnergy = true; energy += m.energia; energySegment.add(new Entry(i, (float) m.energia, new ChartMarker.Value(m.energia, "kWh")));
                if (maxEnergy == null || m.energia > maxEnergy.energia) maxEnergy = m;
                if (minEnergy == null || m.energia < minEnergy.energia) minEnergy = m;
            } else if (!energySegment.isEmpty()) { addLine(energyLines, energySegment, energyColor); energySegment = new ArrayList<>(); }
            if (m.temRegistros) {
                anyRecords = true;
                if (m.custoIncompleto) incomplete = true;
                else { bars.add(new BarEntry(i, (float) m.custo, new ChartMarker.Value(m.custo, "R$"))); cost += m.custo; }
            }
        }
        if (!segment.isEmpty()) addLine(lines, segment, color);
        if (!energySegment.isEmpty()) addLine(energyLines, energySegment, energyColor);
        peak(R.id.tvMaiorAguaValor, R.id.tvMaiorAguaMes, maxWater, true);
        peak(R.id.tvMenorAguaValor, R.id.tvMenorAguaMes, minWater, true);
        peak(R.id.tvMaiorEnergiaValor, R.id.tvMaiorEnergiaMes, maxEnergy, false);
        peak(R.id.tvMenorEnergiaValor, R.id.tvMenorEnergiaMes, minEnergy, false);
        text(R.id.tvTotalAguaPeriodo).setText(anyWater ? number(water) + " m³" : "—");
        text(R.id.tvTotalEnergiaPeriodo).setText(anyEnergy ? number(energy) + " kWh" : "—");
        text(R.id.tvGastoTotalPeriodo).setText(anyRecords && !incomplete ? NumberFormat.getCurrencyInstance(locale).format(cost) : "—");
        TextView notice = text(R.id.tvAvisoCustos);
        notice.setVisibility(incomplete ? View.VISIBLE : View.GONE);
        notice.setText("Há registros sem custo calculado; o total e os meses incompletos não são exibidos.");
        configureLine(root.findViewById(R.id.lineChartConsumoAgua), lines, labels, "Nenhum consumo de água no período.");
        configureLine(root.findViewById(R.id.lineChartConsumoEnergia), energyLines, labels, "Nenhum consumo de energia no período.");
        BarChart bar = root.findViewById(R.id.barChartGastosTotais);
        bar.setNoDataText("Nenhum gasto completo no período.");
        bar.setNoDataTextColor(ContextCompat.getColor(requireContext(), R.color.onboarding_muted));
        bar.getAxisLeft().setTextColor(ContextCompat.getColor(requireContext(), R.color.onboarding_muted));
        bar.getAxisLeft().setGridColor(ContextCompat.getColor(requireContext(), R.color.onboarding_border));
        ChartMarker.attach(bar);
        bar.getDescription().setEnabled(false); bar.getLegend().setEnabled(false); bar.getAxisRight().setEnabled(false);
        bar.getAxisLeft().setAxisMinimum(0f); bar.getAxisLeft().setDrawGridLines(true);
        configureXAxis(bar.getXAxis(), labels);
        if (bars.isEmpty()) bar.clear();
        else {
            BarDataSet dataset = new BarDataSet(bars, "R$");
            dataset.setColor(ContextCompat.getColor(requireContext(), R.color.water_accent)); dataset.setDrawValues(false);
            BarData barData = new BarData(dataset); barData.setBarWidth(0.7f); bar.setData(barData);
        }
        bar.invalidate();
    }

    private void configureLine(LineChart line, ArrayList<ILineDataSet> lines, String[] labels, String emptyMessage) {
        ChartMarker.attach(line);
        line.setNoDataText(emptyMessage);
        line.setNoDataTextColor(ContextCompat.getColor(requireContext(), R.color.onboarding_muted));
        line.getAxisLeft().setTextColor(ContextCompat.getColor(requireContext(), R.color.onboarding_muted));
        line.getAxisLeft().setGridColor(ContextCompat.getColor(requireContext(), R.color.onboarding_border));
        line.getDescription().setEnabled(false); line.getLegend().setEnabled(false); line.getAxisRight().setEnabled(false);
        line.getAxisLeft().setAxisMinimum(0f); line.getAxisLeft().setDrawGridLines(true);
        configureXAxis(line.getXAxis(), labels);
        if (lines.isEmpty()) line.clear(); else line.setData(new LineData(lines));
        line.invalidate();
    }

    private void configureXAxis(XAxis x, String[] labels) {
        x.setTextColor(ContextCompat.getColor(requireContext(), R.color.onboarding_muted));
        x.setPosition(XAxis.XAxisPosition.BOTTOM); x.setValueFormatter(new IndexAxisValueFormatter(labels));
        x.setGranularity(1f); x.setAxisMinimum(-0.5f); x.setAxisMaximum(labels.length - 0.5f);
        x.setDrawGridLines(false); x.setTextSize(labels.length == 12 ? 8f : 9f);
        x.setLabelRotationAngle(labels.length == 12 ? -45f : 0f);
        x.setLabelCount(labels.length, false);
    }
    private void addLine(ArrayList<ILineDataSet> lines, ArrayList<Entry> values, int color) {
        LineDataSet set = new LineDataSet(values, "Consumo");
        set.setColor(color); set.setCircleColor(color); set.setCircleRadius(3f); set.setLineWidth(2f);
        set.setDrawValues(false); set.setMode(LineDataSet.Mode.CUBIC_BEZIER); lines.add(set);
    }
    private void peak(int valueId, int monthId, RelatorioDAO.Mes m, boolean water) {
        text(valueId).setText(m == null ? "—" : number(water ? m.agua : m.energia) + (water ? " m³" : " kWh"));
        if (m == null) { text(monthId).setText("Sem dados"); return; }
        try {
            Date date = new SimpleDateFormat("yyyy-MM", Locale.ROOT).parse(m.periodo);
            text(monthId).setText(date == null ? m.periodo : new SimpleDateFormat("MMMM yyyy", locale).format(date));
        } catch (java.text.ParseException e) { text(monthId).setText(m.periodo); }
    }
    private String number(double n) { NumberFormat f = NumberFormat.getNumberInstance(locale); f.setMaximumFractionDigits(2); return f.format(n); }
    private TextView text(int id) { return root.findViewById(id); }

    private void export() {
        if (current == null) return;
        StringBuilder csv = new StringBuilder("\uFEFFMês;Água (m³);Energia (kWh);Gasto (R$);Situação do gasto\r\n");
        for (RelatorioDAO.Mes m : current.meses) {
            csv.append(m.periodo).append(';').append(m.temAgua ? number(m.agua) : "").append(';')
                    .append(m.temEnergia ? number(m.energia) : "").append(';')
                    .append(m.temRegistros && !m.custoIncompleto ? number(m.custo) : "").append(';')
                    .append(!m.temRegistros ? "Sem registros" : m.custoIncompleto ? "Custo incompleto" : "Completo").append("\r\n");
        }
        pendingCsv = csv.toString();
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, months == 12 ? "afluente_relatorio_anual_" + current.meses[0].periodo.substring(0, 4) + ".csv" : "afluente_relatorio_6_meses.csv");
        startActivityForResult(intent, EXPORT_REQUEST);
    }

    @Override public void onActivityResult(int request, int result, @Nullable Intent data) {
        super.onActivityResult(request, result, data);
        if (request != EXPORT_REQUEST || result != Activity.RESULT_OK || data == null || data.getData() == null || pendingCsv == null) return;
        Uri uri = data.getData(); String csv = pendingCsv; pendingCsv = null;
        android.content.Context context = requireContext().getApplicationContext();
        exportExecutor.execute(() -> {
            try (OutputStream stream = context.getContentResolver().openOutputStream(uri)) {
                if (stream == null) throw new java.io.IOException("Destino indisponível");
                try (Writer writer = new OutputStreamWriter(stream, StandardCharsets.UTF_8)) { writer.write(csv); }
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> { if (isAdded()) Toast.makeText(requireContext(), "Relatório exportado.", Toast.LENGTH_SHORT).show(); });
            } catch (Exception ex) {
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> { if (isAdded()) Toast.makeText(requireContext(), "Não foi possível exportar o relatório.", Toast.LENGTH_LONG).show(); });
            }
        });
    }
    @Override public void onSaveInstanceState(@NonNull Bundle state) { state.putInt("months", months); state.putString("pending_csv", pendingCsv); super.onSaveInstanceState(state); }
    @Override public void onDestroyView() { if (service != null) service.close(); service = null; root = null; super.onDestroyView(); }
    @Override public void onDestroy() { exportExecutor.shutdown(); super.onDestroy(); }
}
