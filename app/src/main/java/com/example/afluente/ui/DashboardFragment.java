package com.example.afluente.ui;

import android.graphics.Color;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.content.Context;
import android.widget.ImageView;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
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
import com.example.afluente.data.DashboardDAO;
import com.example.afluente.data.DatabaseHelper;
import com.example.afluente.data.PerfilDAO;
import com.example.afluente.service.FotoPerfilService;
import com.example.afluente.service.DashboardService;
import com.example.afluente.service.AlertaService;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DashboardFragment extends Fragment {
    private DashboardService service;
    private DatabaseHelper helper;
    private View root;
    private final Locale locale = new Locale("pt", "BR");
    private ExecutorService avatarExecutor;
    private final Handler main = new Handler(Looper.getMainLooper());
    private int avatarRequest;
    private String avatarUid;
    private final FotoPerfilService.Listener avatarListener = success -> {
        FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
        if (success && root != null && isAdded() && current != null && current.getUid().equals(avatarUid)) loadAvatar(current);
    };

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_dashboard, parent, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        root = view;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(requireContext(), "Entre na sua conta para acessar o painel.", Toast.LENGTH_LONG).show();
            requireActivity().finish();
            return;
        }
        avatarExecutor = Executors.newSingleThreadExecutor();
        avatarUid = user.getUid();
        FotoPerfilService.observar(avatarUid, avatarListener);
        String name = user.getDisplayName();
        text(R.id.tvAvatarDashboard).setText(FotoPerfilService.iniciais(name));
        view.findViewById(R.id.avatarDashboard).setOnClickListener(v -> startActivity(new Intent(requireContext(), PerfilActivity.class)));
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String greeting = hour < 12 ? "Bom dia" : hour < 18 ? "Boa tarde" : "Boa noite";
        text(R.id.tvSaudacaoUsuario).setText(name == null || name.trim().isEmpty() ? greeting + "!" : greeting + ",\n" + name);
        view.findViewById(R.id.cardAguaDashboard).setOnClickListener(v -> startActivity(new Intent(requireContext(), ConsumptionAnalysisActivity.class)));
        view.findViewById(R.id.cardEnergiaDashboard).setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), ConsumptionAnalysisActivity.class);
            intent.putExtra(ConsumptionAnalysisActivity.EXTRA_ENERGY, true);
            startActivity(intent);
        });
        view.findViewById(R.id.fabAdicionarConsumo).setOnClickListener(v -> openConsumption());
        view.findViewById(R.id.tvAlertCount).setOnClickListener(v -> openAlerts());
        view.findViewById(R.id.cardAlertaConsumo).setOnClickListener(v -> openAlerts());
        view.findViewById(R.id.atalhoAlertas).setOnClickListener(v -> openAlerts());
        view.findViewById(R.id.atalhoHistorico).setOnClickListener(v -> startActivity(new Intent(requireContext(), HistoricoActivity.class)));
        view.findViewById(R.id.atalhoComparar).setOnClickListener(v -> startActivity(new Intent(requireContext(), CompararPeriodosActivity.class)));
        view.findViewById(R.id.btnVerMetas).setOnClickListener(v -> startActivity(new Intent(requireContext(), MetasConsumoActivity.class)));
        BottomNavigationView navigation = view.findViewById(R.id.bottomNavigation);
        navigation.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_inicio) return true;
            if (item.getItemId() == R.id.nav_perfil) { startActivity(new Intent(requireContext(), PerfilActivity.class)); return false; }
            if (item.getItemId() == R.id.nav_consumo) { startActivity(new Intent(requireContext(), ConsumptionAnalysisActivity.class)); return false; }
            if (item.getItemId() == R.id.nav_relatorios) { startActivity(new Intent(requireContext(), RelatoriosActivity.class)); return false; }
            pending();
            return false;
        });
        LineChart chart = view.findViewById(R.id.chartEvolucaoMensal);
        chart.setNoDataText("Nenhum consumo registrado ainda.");
        chart.setNoDataTextColor(ContextCompat.getColor(requireContext(), R.color.onboarding_muted));
        helper = new DatabaseHelper(requireContext().getApplicationContext());
        service = new DashboardService(new DashboardDAO(helper));
    }

    @Override public void onResume() {
        super.onResume();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (service == null || user == null) return;
        loadAvatar(user);
        service.load(user.getUid(), new DashboardService.Callback() {
            @Override public void onSuccess(DashboardDAO.Snapshot data) { if (root != null) render(data); }
            @Override public void onError() { if (isAdded() && root != null) Toast.makeText(requireContext(), "Não foi possível carregar o painel.", Toast.LENGTH_LONG).show(); }
        });
    }

    private void loadAvatar(FirebaseUser currentUser) {
        final View view = root;
        final String uid = currentUser.getUid();
        final String firebaseName = currentUser.getDisplayName();
        final int request = ++avatarRequest;
        final Context app = requireContext().getApplicationContext();
        avatarExecutor.execute(() -> {
            DatabaseHelper photoHelper = new DatabaseHelper(app);
            try {
                PerfilDAO.Perfil data = new PerfilDAO(photoHelper).carregar(uid);
                Bitmap bitmap = data == null ? null : FotoPerfilService.carregar(app, data.fotoUri, 128);
                String name = firebaseName == null || firebaseName.trim().isEmpty() ? (data == null ? null : data.nome) : firebaseName;
                main.post(() -> {
                    FirebaseUser signedIn = FirebaseAuth.getInstance().getCurrentUser();
                    if (root != view || request != avatarRequest || signedIn == null || !uid.equals(signedIn.getUid())) {
                        if (bitmap != null) bitmap.recycle();
                        return;
                    }
                    ImageView image = view.findViewById(R.id.imgFotoDashboard);
                    image.setImageBitmap(bitmap); image.setVisibility(bitmap == null ? View.GONE : View.VISIBLE);
                    TextView initials = view.findViewById(R.id.tvAvatarDashboard);
                    initials.setText(FotoPerfilService.iniciais(name)); initials.setVisibility(bitmap == null ? View.VISIBLE : View.GONE);
                });
            } catch (RuntimeException ignored) {
                // O avatar mantém as iniciais caso os dados locais não possam ser lidos.
            } finally { photoHelper.close(); }
        });
    }

    private void openConsumption() {
        startActivity(new Intent(requireContext(), RegistrarConsumoActivity.class));
    }

    private void openAlerts() {
        startActivity(new Intent(requireContext(), NotificacoesActivity.class));
    }

    private void render(DashboardDAO.Snapshot data) {
        text(R.id.tvAguaMes).setText("💧 Água · " + data.months[5]);
        text(R.id.tvEnergiaMes).setText("⚡ Energia · " + data.months[5]);
        text(R.id.tvConsumoAgua).setText(data.hasWater[5] ? number(data.water[5]) + " m³" : "— m³");
        text(R.id.tvConsumoEnergia).setText(data.hasEnergy[5] ? number(data.energy[5]) + " kWh" : "— kWh");
        text(R.id.tvVariacaoAgua).setText(variation(data.water, data.hasWater));
        text(R.id.tvVariacaoEnergia).setText(variation(data.energy, data.hasEnergy));
        Double cost = cost(data, 5), previous = cost(data, 4);
        NumberFormat currency = NumberFormat.getCurrencyInstance(locale);
        text(R.id.tvGastoEstimado).setText(cost == null ? "—" : currency.format(cost));
        text(R.id.tvComparacaoGasto).setText(cost == null ? "Cadastre consumos e tarifas para calcular" : previous == null ? "Sem comparação com o mês anterior" : String.format(locale, "%s %s vs. mês anterior", cost >= previous ? "↑" : "↓", currency.format(Math.abs(cost - previous))));
        text(R.id.tvAlertCount).setText("🔔 " + data.unreadCount);
        root.findViewById(R.id.cardAlertaConsumo).setVisibility(data.unreadCount > 0 ? View.VISIBLE : View.GONE);
        if (data.unreadCount > 0) {
            text(R.id.tvTituloAlerta).setText(data.alertTitle);
            text(R.id.tvDescricaoAlerta).setText(data.alertDescription);
        }
        renderGoal(R.id.tvMetaAgua, R.id.progressoMetaAgua, data.waterGoal, data.hasWater[5] ? data.water[5] : null, "m³", R.color.water_accent);
        renderGoal(R.id.tvMetaEnergia, R.id.progressoMetaEnergia, data.energyGoal, data.hasEnergy[5] ? data.energy[5] : null, "kWh", R.color.energy_accent);
        renderChart(data);
    }

    private void renderGoal(int textId, int progressId, Double goal, Double consumption, String unit, int color) {
        com.google.android.material.progressindicator.LinearProgressIndicator progress = root.findViewById(progressId);
        if (goal == null) {
            text(textId).setText("Sem meta"); progress.setProgress(0);
            progress.setContentDescription("Meta não configurada");
        } else if (consumption == null) {
            text(textId).setText("Sem registros / " + number(goal) + " " + unit); progress.setProgress(0);
            progress.setContentDescription("Sem registros neste mês");
        } else {
            double percent = consumption / goal * 100;
            text(textId).setText(number(consumption) + " / " + number(goal) + " " + unit + " · " + number(percent) + "%");
            progress.setProgress((int) Math.min(100, percent));
            progress.setContentDescription("Progresso da meta: " + number(percent) + "%");
        }
        progress.setIndicatorColor(ContextCompat.getColor(requireContext(), goal != null && consumption != null && AlertaService.excedeu(consumption, goal) ? R.color.alert_red_text : color));
    }

    private Double cost(DashboardDAO.Snapshot d, int i) {
        if (!d.hasWater[i] && !d.hasEnergy[i]) return null;
        if ((d.hasWater[i] && (d.waterTariff == null || !Double.isFinite(d.waterTariff) || d.waterTariff <= 0)) || (d.hasEnergy[i] && (d.energyTariff == null || !Double.isFinite(d.energyTariff) || d.energyTariff <= 0))) return null;
        double estimate = (d.hasWater[i] ? d.water[i] * d.waterTariff : 0) + (d.hasEnergy[i] ? d.energy[i] * d.energyTariff : 0);
        return Double.isFinite(estimate) ? estimate : null;
    }

    private String variation(double[] values, boolean[] has) {
        if (!has[5] || !has[4] || values[4] == 0) return "Sem comparação";
        double change = (values[5] - values[4]) / values[4] * 100;
        return String.format(locale, "%s %.1f%% vs. mês anterior", change >= 0 ? "↑" : "↓", Math.abs(change));
    }

    private String number(double n) {
        NumberFormat format = NumberFormat.getNumberInstance(locale);
        format.setMaximumFractionDigits(2);
        return format.format(n);
    }

    private void renderChart(DashboardDAO.Snapshot d) {
        LineChart chart = root.findViewById(R.id.chartEvolucaoMensal);
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(true);
        ChartMarker.attach(chart);
        chart.getAxisRight().setEnabled(false);
        chart.getAxisLeft().setDrawGridLines(false);
        int axisColor = ContextCompat.getColor(requireContext(), R.color.onboarding_muted);
        chart.getAxisLeft().setTextColor(axisColor);
        chart.getLegend().setTextColor(axisColor);
        XAxis x = chart.getXAxis();
        x.setTextColor(axisColor);
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setValueFormatter(new IndexAxisValueFormatter(d.months));
        x.setGranularity(1f);
        x.setAxisMinimum(0f);
        x.setAxisMaximum(5f);
        x.setDrawGridLines(false);
        List<com.github.mikephil.charting.interfaces.datasets.ILineDataSet> series = new ArrayList<>();
        addSeries(series, d.water, d.hasWater, 1f, ContextCompat.getColor(requireContext(), R.color.chart_water), "Água (m³)");
        addSeries(series, d.energy, d.hasEnergy, 0.1f, ContextCompat.getColor(requireContext(), R.color.energy_accent), "Energia (kWh × 0,1)");
        if (series.isEmpty()) chart.clear(); else chart.setData(new LineData(series));
        chart.invalidate();
    }

    // Cada trecho contínuo forma uma série: meses ausentes não são inventados como zero.
    private void addSeries(List<com.github.mikephil.charting.interfaces.datasets.ILineDataSet> result, double[] values, boolean[] has, float scale, int color, String label) {
        ArrayList<Entry> entries = new ArrayList<>();
        boolean first = true;
        for (int i = 0; i <= values.length; i++) {
            if (i < values.length && has[i]) entries.add(new Entry(i, (float) values[i] * scale, new ChartMarker.Value(values[i], scale == 1f ? "m³" : "kWh")));
            else if (!entries.isEmpty()) {
                LineDataSet set = new LineDataSet(entries, first ? label : "");
                set.setColor(color); set.setCircleColor(color); set.setCircleRadius(3f); set.setLineWidth(2f);
                set.setMode(LineDataSet.Mode.CUBIC_BEZIER); set.setDrawValues(false);
                set.setDrawFilled(true);
                GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                        new int[]{(color & 0x00FFFFFF) | 0x33000000, Color.TRANSPARENT});
                set.setFillDrawable(gradient);
                result.add(set); entries = new ArrayList<>(); first = false;
            }
        }
    }

    private TextView text(int id) { return root.findViewById(id); }
    private void pending() { Toast.makeText(requireContext(), "Esta funcionalidade ainda será implementada.", Toast.LENGTH_SHORT).show(); }
    @Override public void onDestroyView() {
        if (service != null) service.close();
        if (avatarUid != null) FotoPerfilService.removerObservador(avatarUid, avatarListener);
        avatarUid = null;
        if (avatarExecutor != null) avatarExecutor.shutdown();
        // O serviço fecha o banco após concluir a consulta em segundo plano.
        root = null;
        super.onDestroyView();
    }
}
