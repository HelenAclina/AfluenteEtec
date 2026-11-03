package com.example.afluente.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.afluente.R;
import com.example.afluente.data.DatabaseHelper;
import com.example.afluente.data.RegistroConsumoDAO;
import com.example.afluente.model.RegistroConsumo;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HistoricoActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<RegistroConsumo> items = new ArrayList<>();
    private HistoricoAdapter adapter;
    private DatabaseHelper helper;
    private RegistroConsumoDAO dao;
    private MaterialButtonToggleGroup filter;
    private String uid;
    private volatile boolean closed;
    private int requestVersion;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_historico);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }
        uid = user.getUid();
        helper = new DatabaseHelper(getApplicationContext());
        dao = new RegistroConsumoDAO(helper);
        RecyclerView list = findViewById(R.id.rvHistorico);
        adapter = new HistoricoAdapter(items, new HistoricoAdapter.Listener() {
            @Override public void editar(RegistroConsumo registro) {
                Intent intent = new Intent(HistoricoActivity.this, RegistrarConsumoActivity.class);
                intent.putExtra(RegistrarConsumoActivity.EXTRA_REGISTRO_ID, registro.id);
                startActivity(intent);
            }
            @Override public void excluir(RegistroConsumo registro) { confirmDelete(registro); }
        });
        list.setLayoutManager(new LinearLayoutManager(this)); list.setAdapter(adapter);
        filter = findViewById(R.id.toggleFiltro);
        filter.addOnButtonCheckedListener((group, id, checked) -> { if (checked) load(); });
        findViewById(R.id.btnVoltar).setOnClickListener(v -> finish());
        BottomNavigationView nav = findViewById(R.id.bottomNavigation);
        nav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_inicio) { finish(); return true; }
            if (item.getItemId() == R.id.nav_consumo) { startActivity(new Intent(this, RegistrarConsumoActivity.class)); return false; }
            if (item.getItemId() == R.id.nav_relatorios) { startActivity(new Intent(this, RelatoriosActivity.class)); return false; }
            if (item.getItemId() == R.id.nav_perfil) { startActivity(new Intent(this, PerfilActivity.class)); return false; }
            return false;
        });
    }

    @Override protected void onResume() { super.onResume(); if (dao != null) load(); }
    private void load() {
        final int selected = filter.getCheckedButtonId();
        final String type = selected == R.id.filtroAgua ? "AGUA" : selected == R.id.filtroEnergia ? "ENERGIA" : null;
        final int request = ++requestVersion;
        executor.execute(() -> {
            try {
                List<RegistroConsumo> result = dao.listar(uid, type);
                runOnUiThread(() -> {
                    if (closed || request != requestVersion) return;
                    items.clear(); items.addAll(result); adapter.notifyDataSetChanged();
                    findViewById(R.id.tvHistoricoVazio).setVisibility(result.isEmpty() ? View.VISIBLE : View.GONE);
                });
            } catch (RuntimeException e) { runOnUiThread(() -> { if (!closed && request == requestVersion) Toast.makeText(this, "Não foi possível carregar o histórico.", Toast.LENGTH_LONG).show(); }); }
        });
    }

    private void confirmDelete(RegistroConsumo record) {
        new MaterialAlertDialogBuilder(this).setTitle("Excluir registro?")
                .setMessage("Essa ação não poderá ser desfeita.")
                .setNegativeButton("Cancelar", null).setPositiveButton("Excluir", (dialog, which) -> executor.execute(() -> {
                    try {
                        boolean removed = dao.excluir(uid, record.id);
                        runOnUiThread(() -> {
                            if (closed) return;
                            Toast.makeText(this, removed ? "Registro excluído." : "Registro não encontrado.", Toast.LENGTH_SHORT).show();
                            load();
                        });
                    } catch (RuntimeException e) { runOnUiThread(() -> { if (!closed) Toast.makeText(this, "Não foi possível excluir o registro.", Toast.LENGTH_LONG).show(); }); }
                })).show();
    }
    @Override protected void onDestroy() {
        closed = true;
        if (helper != null) executor.execute(helper::close);
        executor.shutdown();
        super.onDestroy();
    }
}
