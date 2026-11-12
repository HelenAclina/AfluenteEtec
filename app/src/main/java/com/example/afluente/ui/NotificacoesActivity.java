package com.example.afluente.ui;

import android.os.Bundle;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.afluente.R;
import com.example.afluente.data.AlertaDAO;
import com.example.afluente.data.DatabaseHelper;
import com.example.afluente.model.Alerta;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotificacoesActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<Alerta> items = new ArrayList<>();
    private AlertaAdapter adapter;
    private DatabaseHelper helper;
    private AlertaDAO dao;
    private String uid;
    private volatile boolean closed;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_notificacoes);
        EdgeInsets.aplicar(this);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }
        uid = user.getUid();
        helper = new DatabaseHelper(getApplicationContext());
        dao = new AlertaDAO(helper);
        RecyclerView list = findViewById(R.id.rvAlertas);
        adapter = new AlertaAdapter(items, this::markRead);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        findViewById(R.id.btnVoltar).setOnClickListener(v -> finish());
        BottomNavigationView nav = findViewById(R.id.bottomNavigation);
        nav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_inicio) { finish(); return true; }
            if (item.getItemId() == R.id.nav_perfil) { startActivity(new Intent(this, PerfilActivity.class)); return false; }
            if (item.getItemId() == R.id.nav_consumo) {
                startActivity(new Intent(this, ConsumptionAnalysisActivity.class));
                return false;
            }
            if (item.getItemId() == R.id.nav_relatorios) {
                startActivity(new Intent(this, RelatoriosActivity.class));
                return false;
            }
            Toast.makeText(this, "Esta funcionalidade ainda será implementada.", Toast.LENGTH_SHORT).show();
            return false;
        });
    }

    @Override protected void onResume() { super.onResume(); if (dao != null) loadAlerts(); }

    private void loadAlerts() {
        executor.execute(() -> {
            try {
                List<Alerta> result = dao.buscarAlertasPorUsuario(uid);
                runOnUiThread(() -> {
                    if (closed) return;
                    items.clear(); items.addAll(result); adapter.notifyDataSetChanged();
                    int count = items.size();
                    ((TextView) findViewById(R.id.tvQtdNotificacoes)).setText(getResources().getQuantityString(R.plurals.alerts_count, count, count));
                    findViewById(R.id.tvSemAlertas).setVisibility(count == 0 ? View.VISIBLE : View.GONE);
                });
            } catch (RuntimeException e) { showError(); }
        });
    }

    private void markRead(Alerta alerta) {
        if (alerta.lido) return;
        executor.execute(() -> {
            try {
                if (!dao.marcarComoLido(alerta.id, uid)) { showError(); return; }
                runOnUiThread(() -> {
                    if (closed) return;
                    alerta.lido = true;
                    int position = items.indexOf(alerta);
                    if (position >= 0) adapter.notifyItemChanged(position);
                });
            } catch (RuntimeException e) { showError(); }
        });
    }

    private void showError() {
        runOnUiThread(() -> { if (!closed) Toast.makeText(this, R.string.alerts_error, Toast.LENGTH_LONG).show(); });
    }

    @Override protected void onDestroy() {
        closed = true;
        if (helper != null) executor.execute(helper::close);
        executor.shutdown();
        super.onDestroy();
    }
}
