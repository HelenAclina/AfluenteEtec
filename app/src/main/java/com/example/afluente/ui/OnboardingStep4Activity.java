package com.example.afluente.ui;

import android.content.SharedPreferences;
import android.content.Intent;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.afluente.R;
import com.google.android.material.card.MaterialCardView;

import java.util.HashSet;
import java.util.Set;

/** Mantém os objetivos como rascunho até o perfil do usuário ser criado. */
public class OnboardingStep4Activity extends AppCompatActivity {
    public static final String DRAFT_PREFERENCES = "onboarding_draft";
    public static final String KEY_OBJECTIVES = "objectives";
    private static final int[] CARDS = {R.id.cardEconomizarDinheiro, R.id.cardReduzirConsumo,
            R.id.cardEvitarDesperdicios, R.id.cardHabitosSustentaveis};
    private static final int[] CHECKS = {R.id.cbEconomizarDinheiro, R.id.cbReduzirConsumo,
            R.id.cbEvitarDesperdicios, R.id.cbHabitosSustentaveis};
    private static final String[] VALUES = {"ECONOMIZAR_DINHEIRO", "REDUZIR_CONSUMO",
            "EVITAR_DESPERDICIOS", "HABITOS_SUSTENTAVEIS"};
    private final Set<String> selected = new HashSet<>();
    private SharedPreferences draft;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_onboarding_objetivo);
        EdgeInsets.aplicar(this);
        getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.onboarding_background));
        draft = getSharedPreferences(DRAFT_PREFERENCES, MODE_PRIVATE);
        selected.addAll(draft.getStringSet(KEY_OBJECTIVES, new HashSet<>()));
        for (int i = 0; i < CARDS.length; i++) {
            MaterialCardView card = findViewById(CARDS[i]);
            CheckBox check = findViewById(CHECKS[i]);
            String value = VALUES[i];
            check.setChecked(selected.contains(value));
            updateCard(card, check.isChecked());
            check.setOnCheckedChangeListener((button, checked) -> {
                if (checked) selected.add(value); else selected.remove(value);
                updateCard(card, checked);
                // Copiar o Set evita alterar a coleção armazenada pelo SharedPreferences.
                draft.edit().putStringSet(KEY_OBJECTIVES, new HashSet<>(selected)).apply();
            });
            card.setOnClickListener(v -> check.toggle());
        }
        findViewById(R.id.btnVoltar).setOnClickListener(v -> finish());
        findViewById(R.id.btnContinuar).setOnClickListener(v -> {
            if (selected.isEmpty()) {
                Toast.makeText(this, R.string.select_goal, Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(this, OnboardingStep5Activity.class));
        });
    }

    private void updateCard(MaterialCardView card, boolean checked) {
        card.setStrokeColor(ContextCompat.getColor(this, checked ? R.color.onboarding_blue : R.color.onboarding_border));
        card.setCardBackgroundColor(ContextCompat.getColor(this, checked ? R.color.onboarding_selected : R.color.surface));
    }
}
