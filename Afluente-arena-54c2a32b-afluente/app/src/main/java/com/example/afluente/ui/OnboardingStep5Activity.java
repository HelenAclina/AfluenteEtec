package com.example.afluente.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.afluente.R;
import com.example.afluente.data.ConfiguracaoUsuarioDAO;
import com.example.afluente.data.DatabaseHelper;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OnboardingStep5Activity extends AppCompatActivity {
    public static final String KEY_WATER_TARIFF = "water_tariff";
    public static final String KEY_ENERGY_TARIFF = "energy_tariff";
    public static final String KEY_SKIPPED = "tariffs_skipped";
    private TextInputEditText water, energy;
    private TextInputLayout waterLayout, energyLayout;
    private SharedPreferences draft;
    private boolean busy;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding_tarifas);
        EdgeInsets.aplicar(this);
        getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.onboarding_background));
        draft = getSharedPreferences(OnboardingStep4Activity.DRAFT_PREFERENCES, MODE_PRIVATE);
        water = findViewById(R.id.etTarifaAgua);
        energy = findViewById(R.id.etTarifaEnergia);
        waterLayout = findViewById(R.id.inputTarifaAgua);
        energyLayout = findViewById(R.id.inputTarifaEnergia);
        if (savedInstanceState == null) {
            // Exemplos visuais, não tarifas oficiais da concessionária.
            water.setText(draft.getString(KEY_WATER_TARIFF, "5,00"));
            energy.setText(draft.getString(KEY_ENERGY_TARIFF, "0,60"));
        }
        findViewById(R.id.btnVoltar).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.btnConcluirConfiguracao).setOnClickListener(v -> {
            BigDecimal waterValue = parse(water, waterLayout);
            BigDecimal energyValue = parse(energy, energyLayout);
            if (waterValue == null || energyValue == null) return;
            complete(waterValue.doubleValue(), energyValue.doubleValue());
        });
        // Tarifas desconhecidas são NULL: não calcular gasto como se fosse gratuito.
        findViewById(R.id.tvPularConfiguracao).setOnClickListener(v -> complete(null, null));
    }

    private void complete(Double waterTariff, Double energyTariff) {
        if (busy) return;
        if (OnboardingSession.name == null || OnboardingSession.email == null ||
                (OnboardingSession.password == null && OnboardingSession.createdUid == null)) {
            Toast.makeText(this, "O cadastro expirou. Volte à primeira etapa e preencha seus dados novamente.", Toast.LENGTH_LONG).show();
            return;
        }
        setBusy(true);
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser current = auth.getCurrentUser();
        if (current != null && current.getUid().equals(OnboardingSession.createdUid)) {
            saveProfile(current, waterTariff, energyTariff);
            return;
        }
        auth.createUserWithEmailAndPassword(OnboardingSession.email, OnboardingSession.password)
                .addOnCompleteListener(this, task -> {
                    if (!task.isSuccessful() || task.getResult() == null || task.getResult().getUser() == null) {
                        setBusy(false);
                        String message = task.getException() instanceof FirebaseAuthUserCollisionException
                                ? "Este e-mail já está cadastrado. Volte e entre na sua conta."
                                : "Não foi possível criar a conta. Verifique a conexão e se o login por e-mail está habilitado no Firebase.";
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        return;
                    }
                    FirebaseUser user = task.getResult().getUser();
                    OnboardingSession.createdUid = user.getUid();
                    OnboardingSession.password = null;
                    UserProfileChangeRequest profile = new UserProfileChangeRequest.Builder().setDisplayName(OnboardingSession.name).build();
                    user.updateProfile(profile).addOnCompleteListener(t -> saveProfile(user, waterTariff, energyTariff));
                });
    }

    private void saveProfile(FirebaseUser user, Double waterTariff, Double energyTariff) {
        final String name = OnboardingSession.name;
        final int residents = OnboardingSession.residents;
        final String type = OnboardingSession.residenceType;
        final String waterEstimate = OnboardingSession.waterEstimate;
        final String energyEstimate = OnboardingSession.energyEstimate;
        Set<String> objectives = new HashSet<>(draft.getStringSet(OnboardingStep4Activity.KEY_OBJECTIVES, new HashSet<>()));
        final String joinedObjectives = android.text.TextUtils.join(",", objectives);
        EXECUTOR.execute(() -> {
            DatabaseHelper helper = new DatabaseHelper(getApplicationContext());
            try {
                new ConfiguracaoUsuarioDAO(helper).save(user.getUid(), name, residents, type,
                        waterEstimate, energyEstimate, joinedObjectives, waterTariff, energyTariff);
                runOnUiThread(() -> {
                    draft.edit().clear().apply();
                    OnboardingSession.clear();
                    Intent dashboard = new Intent(this, DashboardActivity.class);
                    dashboard.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(dashboard);
                    finish();
                });
            } catch (RuntimeException e) {
                runOnUiThread(() -> {
                    setBusy(false);
                    Toast.makeText(this, "Conta criada, mas não foi possível salvar os dados locais. Tente concluir novamente.", Toast.LENGTH_LONG).show();
                });
            } finally { helper.close(); }
        });
    }

    private void setBusy(boolean value) {
        busy = value;
        findViewById(R.id.btnConcluirConfiguracao).setEnabled(!value);
        findViewById(R.id.tvPularConfiguracao).setEnabled(!value);
        findViewById(R.id.btnVoltar).setEnabled(!value);
        if (value) Toast.makeText(this, "Criando sua conta...", Toast.LENGTH_SHORT).show();
    }

    private BigDecimal parse(TextInputEditText field, TextInputLayout container) {
        container.setError(null);
        String text = field.getText() == null ? "" : field.getText().toString().trim();
        try {
            if (!text.matches("[0-9]+([.,][0-9]+)?")) throw new NumberFormatException();
            BigDecimal value = new BigDecimal(text.replace(',', '.'));
            if (value.signum() <= 0 || Double.isInfinite(value.doubleValue())) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException e) {
            container.setError(getString(R.string.invalid_tariff));
            return null;
        }
    }

    @Override public void onBackPressed() { if (!busy) super.onBackPressed(); }
}
