package com.example.afluente.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import com.example.afluente.R;
import com.example.afluente.data.DatabaseHelper;
import com.example.afluente.data.PerfilDAO;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DashboardActivity extends AppCompatActivity {
    private final ExecutorService themeExecutor = Executors.newSingleThreadExecutor();
    private volatile boolean destroyed;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_dashboard);
        if (state == null) getSupportFragmentManager().beginTransaction()
                .replace(R.id.dashboard_container, new DashboardFragment()).commit();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        String uid = user.getUid();
        android.content.Context context = getApplicationContext();
        themeExecutor.execute(() -> {
            DatabaseHelper helper = new DatabaseHelper(context);
            try {
                PerfilDAO.Perfil perfil = new PerfilDAO(helper).carregar(uid);
                boolean dark = perfil != null && perfil.temaEscuro;
                new Handler(Looper.getMainLooper()).post(() -> {
                    FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
                    if (destroyed || current == null || !uid.equals(current.getUid())) return;
                    int mode = dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
                    if (AppCompatDelegate.getDefaultNightMode() != mode) AppCompatDelegate.setDefaultNightMode(mode);
                });
            } catch (RuntimeException ignored) {
                // Em caso de erro, mantém o tema atual sem alterar a preferência.
            } finally { helper.close(); }
        });
    }

    @Override protected void onDestroy() { destroyed = true; themeExecutor.shutdown(); super.onDestroy(); }
}
