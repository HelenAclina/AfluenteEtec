package com.example.afluente;

import androidx.appcompat.app.AppCompatActivity;
import android.content.res.Configuration;
import android.content.Intent;
import com.example.afluente.ui.OnboardingStep2Activity;
import com.example.afluente.ui.OnboardingStep3Activity;
import com.example.afluente.ui.OnboardingSession;
import com.example.afluente.ui.OnboardingStep4Activity;
import com.example.afluente.ui.DashboardActivity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.Patterns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends AppCompatActivity {
    public static final String EXTRA_OPEN_LOGIN = "open_login";
    private static final int BLUE = Color.rgb(30, 58, 104);
    private String screen = "welcome";
    private boolean busy;
    private EditText email;
    private EditText password;
    private Button loginButton;
    // Dados mantidos apenas em memória enquanto o cadastro está em andamento.
    private String draftName = "", draftEmail = "", draftPassword = "";
    private String waterEstimate = "", energyEstimate = "";
    private boolean consumptionUnknown;
    private int residents = 0;
    private String residenceType = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent().getBooleanExtra(EXTRA_OPEN_LOGIN, false)) showLogin();
        else showWelcome();
    }

    private void showWelcome() {
        screen = "welcome";
        getWindow().setNavigationBarColor(Color.rgb(82, 125, 183));
        setContentView(R.layout.activity_main);
        androidx.appcompat.widget.AppCompatButton start = findViewById(R.id.start_button);
        // O tema Material pode aplicar a cor padrão roxa por tint; preserve o fundo branco.
        start.setSupportBackgroundTintList(null);
        start.setBackgroundTintList(null);
        start.setBackgroundResource(R.drawable.start_button_background);
        start.setOnClickListener(view -> showLogin());
    }

    private void showLogin() {
        screen = "login";
        getWindow().setNavigationBarColor(getColor(R.color.onboarding_background));
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        int flags = dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (!dark && android.os.Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(flags);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(getColor(R.color.onboarding_background));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(page);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(22), dp(28), dp(22), dp(29));
        header.setBackgroundColor(getColor(R.color.brand_deep_blue));
        TextView title = text("Bem-vindo", 26, Color.WHITE, true);
        header.addView(title);
        TextView subtitle = text("Acesse sua conta para continuar", 13, getColor(R.color.onboarding_step_text), false);
        header.addView(subtitle);
        page.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(30), dp(24), dp(24));
        page.addView(form);
        form.addView(text("E-MAIL", 12, getColor(R.color.onboarding_muted), true));
        email = input("Digite seu e-mail", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        addWithMargins(form, email, 46, 8, 20);
        form.addView(text("SENHA", 12, getColor(R.color.onboarding_muted), true));

        LinearLayout passwordBox = new LinearLayout(this);
        passwordBox.setGravity(Gravity.CENTER_VERTICAL);
        passwordBox.setBackground(rounded(getColor(R.color.surface), getColor(R.color.onboarding_border), 12));
        password = input("Digite sua senha", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setBackgroundColor(Color.TRANSPARENT);
        passwordBox.addView(password, new LinearLayout.LayoutParams(0, -1, 1));
        TextView toggle = text("◉", 20, 0xFF858B92, false);
        toggle.setGravity(Gravity.CENTER);
        toggle.setContentDescription("Mostrar senha");
        toggle.setClickable(true);
        toggle.setOnClickListener(v -> {
            boolean hidden = password.getInputType() == (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            password.setInputType(InputType.TYPE_CLASS_TEXT | (hidden ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : InputType.TYPE_TEXT_VARIATION_PASSWORD));
            password.setSelection(password.length());
            toggle.setContentDescription(hidden ? "Ocultar senha" : "Mostrar senha");
        });
        passwordBox.addView(toggle, new LinearLayout.LayoutParams(dp(48), -1));
        addWithMargins(form, passwordBox, 46, 8, 8);

        TextView forgot = text("Esqueci minha senha", 11, 0xFF5684C3, true);
        forgot.setPadding(dp(3), 0, 0, 0);
        forgot.setOnClickListener(v -> resetPassword());
        form.addView(forgot);

        loginButton = button("Entrar", true);
        loginButton.setOnClickListener(v -> signIn());
        addWithMargins(form, loginButton, 52, 28, 18);
        View divider = new View(this);
        divider.setBackgroundColor(getColor(R.color.onboarding_border));
        form.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        Button create = button("Criar uma conta", false);
        create.setOnClickListener(v -> showSignUp());
        addWithMargins(form, create, 52, 18, 0);
        setContentView(scroll);
    }

    private void showSignUp() {
        screen = "signup";
        getWindow().setNavigationBarColor(getColor(R.color.onboarding_background));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(getColor(R.color.onboarding_background));
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(page);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(22), dp(34), dp(22), dp(38));
        header.setBackgroundColor(getColor(R.color.brand_deep_blue));
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        android.widget.ImageView mark = new android.widget.ImageView(this);
        mark.setImageResource(R.drawable.brand_mark);
        mark.setContentDescription(getString(R.string.brand_name));
        brand.addView(mark, new LinearLayout.LayoutParams(dp(17), dp(17)));
        TextView wordmark = text("FLUENTE", 16, Color.WHITE, true);
        wordmark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        brand.addView(wordmark);
        heading.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        heading.addView(text("1 de 5", 13, getColor(R.color.onboarding_step_text), false));
        header.addView(heading);
        LinearLayout progress = new LinearLayout(this);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(3));
        progressParams.topMargin = dp(22);
        for (int i = 0; i < 5; i++) {
            View segment = new View(this);
            segment.setBackground(rounded(i == 0 ? Color.WHITE : getColor(R.color.progress_inactive),
                    i == 0 ? Color.WHITE : getColor(R.color.progress_inactive), 2));
            LinearLayout.LayoutParams segmentParams = new LinearLayout.LayoutParams(0, dp(3), 1);
            if (i > 0) segmentParams.leftMargin = dp(5);
            progress.addView(segment, segmentParams);
        }
        header.addView(progress, progressParams);
        page.addView(header);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(14), dp(24), dp(24));
        page.addView(form);
        form.addView(text("Crie sua conta", 22, getColor(R.color.onboarding_text), true));
        TextView subtitle = text("Rápido e gratuito. Tudo começa aqui.", 14, getColor(R.color.onboarding_muted), false);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
        subParams.setMargins(0, dp(4), 0, dp(23));
        form.addView(subtitle, subParams);
        form.addView(text("NOME COMPLETO", 12, getColor(R.color.onboarding_muted), true));
        EditText name = input("Digite seu nome completo", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        name.setText(draftName);
        addWithMargins(form, name, 46, 7, 15);
        form.addView(text("E-MAIL", 12, getColor(R.color.onboarding_muted), true));
        EditText signupEmail = input("Digite seu email", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        signupEmail.setText(draftEmail);
        addWithMargins(form, signupEmail, 46, 7, 15);
        form.addView(text("SENHA", 12, getColor(R.color.onboarding_muted), true));
        EditText signupPassword = input("Digite sua senha", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        signupPassword.setText(draftPassword);
        addWithMargins(form, signupPassword, 46, 7, 15);
        form.addView(text("CONFIRMAR SENHA", 12, getColor(R.color.onboarding_muted), true));
        EditText confirm = input("Confirme sua senha", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        confirm.setText(draftPassword);
        addWithMargins(form, confirm, 46, 7, 0);
        Button next = button("Continuar", true);
        next.setOnClickListener(v -> {
            if (name.getText().toString().trim().isEmpty()) {
                name.setError("Informe seu nome completo.");
                name.requestFocus();
            } else if (!Patterns.EMAIL_ADDRESS.matcher(signupEmail.getText().toString().trim()).matches()) {
                signupEmail.setError("Informe um e-mail válido.");
                signupEmail.requestFocus();
            } else if (signupPassword.length() < 6) {
                signupPassword.setError("A senha deve ter pelo menos 6 caracteres.");
                signupPassword.requestFocus();
            } else if (!signupPassword.getText().toString().equals(confirm.getText().toString())) {
                confirm.setError("As senhas não coincidem.");
                confirm.requestFocus();
            } else {
                draftName = name.getText().toString().trim();
                draftEmail = signupEmail.getText().toString().trim();
                draftPassword = signupPassword.getText().toString();
                if (OnboardingSession.email == null || !OnboardingSession.email.equalsIgnoreCase(draftEmail)) {
                    OnboardingSession.clear();
                    getSharedPreferences(OnboardingStep4Activity.DRAFT_PREFERENCES, MODE_PRIVATE).edit().clear().apply();
                }
                OnboardingSession.name = draftName;
                OnboardingSession.email = draftEmail;
                OnboardingSession.password = draftPassword;
                showResidence();
            }
        });
        addWithMargins(form, next, 52, 22, 12);
        TextView enter = text("Já tem uma conta? Entrar", 13, 0xFF5684C3, false);
        enter.setGravity(Gravity.CENTER);
        enter.setPadding(0, dp(4), 0, dp(8));
        enter.setOnClickListener(v -> showLogin());
        form.addView(enter, new LinearLayout.LayoutParams(-1, -2));
        setContentView(scroll);
    }

    private void showResidence() {
        Intent intent = new Intent(this, OnboardingStep2Activity.class);
        intent.putExtra(OnboardingStep2Activity.EXTRA_RESIDENTS, residents);
        intent.putExtra(OnboardingStep2Activity.EXTRA_RESIDENCE_TYPE, residenceType);
        intent.putExtra(OnboardingStep3Activity.EXTRA_WATER, waterEstimate);
        intent.putExtra(OnboardingStep3Activity.EXTRA_ENERGY, energyEstimate);
        intent.putExtra(OnboardingStep3Activity.EXTRA_UNKNOWN, consumptionUnknown);
        startActivityForResult(intent, 2);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 2 && data != null) {
            residents = data.getIntExtra(OnboardingStep2Activity.EXTRA_RESIDENTS, 0);
            String type = data.getStringExtra(OnboardingStep2Activity.EXTRA_RESIDENCE_TYPE);
            residenceType = type == null ? "" : type;
            waterEstimate = data.getStringExtra(OnboardingStep3Activity.EXTRA_WATER);
            energyEstimate = data.getStringExtra(OnboardingStep3Activity.EXTRA_ENERGY);
            consumptionUnknown = data.getBooleanExtra(OnboardingStep3Activity.EXTRA_UNKNOWN, false);
        }
    }

    private void signIn() {
        if (busy || !validEmail()) return;
        String secret = password.getText().toString();
        if (secret.isEmpty()) {
            password.setError("Informe sua senha.");
            return;
        }
        busy = true;
        loginButton.setEnabled(false);
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email.getText().toString().trim(), secret)
                .addOnCompleteListener(this, task -> {
                    busy = false;
                    loginButton.setEnabled(true);
                    if (task.isSuccessful()) {
                        startActivity(new Intent(this, DashboardActivity.class));
                    } else {
                        toast("Não foi possível entrar. Confira seus dados e sua conexão.");
                    }
                });
    }

    private void resetPassword() {
        if (!validEmail()) return;
        FirebaseAuth.getInstance().sendPasswordResetEmail(email.getText().toString().trim())
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) toast("Se o e-mail estiver cadastrado, você receberá as instruções de recuperação.");
                    else toast("Não foi possível solicitar a recuperação. Verifique sua conexão e tente novamente.");
                });
    }

    private boolean validEmail() {
        if (!Patterns.EMAIL_ADDRESS.matcher(email.getText().toString().trim()).matches()) {
            email.setError("Informe um e-mail válido.");
            return false;
        }
        return true;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private EditText input(String hint, int type) {
        EditText view = new EditText(this);
        view.setSingleLine(true);
        view.setTextSize(14);
        view.setTextColor(getColor(R.color.onboarding_text));
        view.setHintTextColor(getColor(R.color.onboarding_muted));
        view.setHint(hint);
        view.setInputType(type);
        view.setPadding(dp(15), 0, dp(15), 0);
        view.setBackground(rounded(getColor(R.color.surface), getColor(R.color.onboarding_border), 12));
        return view;
    }

    private Button button(String label, boolean filled) {
        Button view = new Button(this);
        view.setText(label);
        view.setTextSize(14);
        view.setAllCaps(false);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setTextColor(filled ? Color.WHITE : getColor(R.color.onboarding_blue));
        view.setBackgroundTintList(null);
        view.setBackground(rounded(filled ? BLUE : Color.TRANSPARENT, BLUE, 14));
        if (filled) view.setElevation(dp(4));
        return view;
    }

    private GradientDrawable rounded(int color, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private void addWithMargins(LinearLayout parent, View view, int height, int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(height));
        params.setMargins(0, dp(top), 0, dp(bottom));
        parent.addView(view, params);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onBackPressed() {
        if (busy) return;
        if ("signup".equals(screen)) showLogin();
        else if ("login".equals(screen)) showWelcome();
        else super.onBackPressed();
    }
}
