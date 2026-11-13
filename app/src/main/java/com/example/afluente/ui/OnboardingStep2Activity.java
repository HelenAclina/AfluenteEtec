package com.example.afluente.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.afluente.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

/** Segunda etapa do cadastro. Os dados permanecem no fluxo, sem criar conta prematuramente. */
public class OnboardingStep2Activity extends AppCompatActivity {
    public static final String EXTRA_RESIDENTS = "residents";
    public static final String EXTRA_RESIDENCE_TYPE = "residence_type";
    private static final int[] PEOPLE_IDS = {R.id.btnPessoa1, R.id.btnPessoa2, R.id.btnPessoa3,
            R.id.btnPessoa4, R.id.btnPessoa5};
    private static final int[] CARD_IDS = {R.id.cardCasa, R.id.cardApartamento, R.id.cardOutro};
    private static final String[] TYPES = {"CASA", "APARTAMENTO", "OUTRO"};
    private static final int REQUEST_CONSUMPTION = 3;
    private String water = "", energy = "";
    private boolean unknown;
    private int residents;
    private String residenceType = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding_residencia);
        EdgeInsets.aplicar(this);
        getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.onboarding_background));
        Bundle state = savedInstanceState != null ? savedInstanceState : getIntent().getExtras();
        if (state != null) {
            residents = state.getInt(EXTRA_RESIDENTS, 0);
            residenceType = state.getString(EXTRA_RESIDENCE_TYPE, "");
            water = state.getString(OnboardingStep3Activity.EXTRA_WATER, "");
            energy = state.getString(OnboardingStep3Activity.EXTRA_ENERGY, "");
            unknown = state.getBoolean(OnboardingStep3Activity.EXTRA_UNKNOWN, false);
        }
        for (int i = 0; i < PEOPLE_IDS.length; i++) {
            final int value = i + 1; // 5 representa cinco ou mais moradores.
            findViewById(PEOPLE_IDS[i]).setOnClickListener(v -> {
                residents = value;
                updateSelection();
            });
        }
        for (int i = 0; i < CARD_IDS.length; i++) {
            final String value = TYPES[i];
            findViewById(CARD_IDS[i]).setOnClickListener(v -> {
                residenceType = value;
                updateSelection();
            });
        }
        findViewById(R.id.btnVoltar).setOnClickListener(v -> returnToPreviousStep());
        findViewById(R.id.btnContinuar).setOnClickListener(v -> {
            if (residents == 0) {
                Toast.makeText(this, R.string.select_residents, Toast.LENGTH_SHORT).show();
            } else if (residenceType.isEmpty()) {
                Toast.makeText(this, R.string.select_residence, Toast.LENGTH_SHORT).show();
            } else {
                OnboardingSession.residents = residents;
                OnboardingSession.residenceType = residenceType;
                Intent intent = new Intent(this, OnboardingStep3Activity.class);
                intent.putExtra(OnboardingStep3Activity.EXTRA_WATER, water);
                intent.putExtra(OnboardingStep3Activity.EXTRA_ENERGY, energy);
                intent.putExtra(OnboardingStep3Activity.EXTRA_UNKNOWN, unknown);
                startActivityForResult(intent, REQUEST_CONSUMPTION);
            }
        });
        updateSelection();
    }

    private void updateSelection() {
        int blue = ContextCompat.getColor(this, R.color.onboarding_blue);
        int border = ContextCompat.getColor(this, R.color.onboarding_border);
        int white = ContextCompat.getColor(this, R.color.surface);
        int selectedBackground = ContextCompat.getColor(this, R.color.onboarding_selected);
        for (int i = 0; i < PEOPLE_IDS.length; i++) {
            MaterialButton button = findViewById(PEOPLE_IDS[i]);
            boolean selected = residents == i + 1;
            button.setBackgroundTintList(ColorStateList.valueOf(selected ? selectedBackground : white));
            button.setStrokeColor(ColorStateList.valueOf(selected ? blue : border));
            button.setTextColor(ContextCompat.getColor(this, selected ? R.color.onboarding_blue : R.color.onboarding_muted));
            button.setSelected(selected);
        }
        for (int i = 0; i < CARD_IDS.length; i++) {
            MaterialCardView card = findViewById(CARD_IDS[i]);
            boolean selected = TYPES[i].equals(residenceType);
            card.setCardBackgroundColor(selected ? selectedBackground : white);
            card.setStrokeColor(selected ? ContextCompat.getColor(this, R.color.residence_card_selected_border) : border);
            card.setStrokeWidth((int) ((selected ? 2 : 1) * getResources().getDisplayMetrics().density + 0.5f));
            card.setChecked(selected);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(EXTRA_RESIDENTS, residents);
        outState.putString(EXTRA_RESIDENCE_TYPE, residenceType);
        outState.putString(OnboardingStep3Activity.EXTRA_WATER, water);
        outState.putString(OnboardingStep3Activity.EXTRA_ENERGY, energy);
        outState.putBoolean(OnboardingStep3Activity.EXTRA_UNKNOWN, unknown);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CONSUMPTION && data != null) {
            water = data.getStringExtra(OnboardingStep3Activity.EXTRA_WATER);
            energy = data.getStringExtra(OnboardingStep3Activity.EXTRA_ENERGY);
            unknown = data.getBooleanExtra(OnboardingStep3Activity.EXTRA_UNKNOWN, false);
        }
    }

    private void returnToPreviousStep() {
        Intent result = new Intent();
        result.putExtra(EXTRA_RESIDENTS, residents);
        result.putExtra(EXTRA_RESIDENCE_TYPE, residenceType);
        result.putExtra(OnboardingStep3Activity.EXTRA_WATER, water);
        result.putExtra(OnboardingStep3Activity.EXTRA_ENERGY, energy);
        result.putExtra(OnboardingStep3Activity.EXTRA_UNKNOWN, unknown);
        setResult(RESULT_OK, result);
        finish();
    }

    @Override
    public void onBackPressed() {
        returnToPreviousStep();
    }
}
