package com.example.afluente.ui;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.widget.ImageView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.example.afluente.MainActivity;
import com.example.afluente.R;
import com.example.afluente.data.DatabaseHelper;
import com.example.afluente.data.PerfilDAO;
import com.example.afluente.service.FotoPerfilService;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Firebase identifica a conta; tarifas e preferências permanecem neste aparelho. */
public class PerfilFragment extends Fragment {
    private final Handler main = new Handler(Looper.getMainLooper());
    private ExecutorService executor;
    private DatabaseHelper helper;
    private PerfilDAO dao;
    private View root;
    private FirebaseUser user;
    private PerfilDAO.Perfil perfil;
    private boolean applying;
    private final Locale locale = new Locale("pt", "BR");
    private final FotoPerfilService.Listener photoListener = sucesso -> {
        FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
        if (root == null || user == null || current == null || !user.getUid().equals(current.getUid())) return;
        root.findViewById(R.id.avatarPerfil).setEnabled(true);
        if (sucesso) load(); else toast("Não foi possível salvar a foto. Selecione uma imagem de até 25 MB.");
    };
    // GetContent concede acesso temporário à imagem escolhida sem permissões amplas de mídia.
    private final ActivityResultLauncher<String> photoPicker = registerForActivityResult(new ActivityResultContracts.GetContent(), this::onPhotoSelected);

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_perfil, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        root = view;
        user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { requireActivity().finish(); return; }
        executor = Executors.newSingleThreadExecutor();
        helper = new DatabaseHelper(requireContext().getApplicationContext());
        dao = new PerfilDAO(helper);
        String email = user.getEmail() == null ? "—" : user.getEmail();
        text(R.id.tvEmailCabecalho).setText(email);
        text(R.id.tvEmailPerfil).setText(email);
        setName(user.getDisplayName());
        FotoPerfilService.observar(user.getUid(), photoListener);
        view.findViewById(R.id.avatarPerfil).setEnabled(!FotoPerfilService.ocupado(user.getUid()));
        view.findViewById(R.id.avatarPerfil).setOnClickListener(v -> {
            try { photoPicker.launch("image/*"); }
            catch (android.content.ActivityNotFoundException e) { toast("Nenhum seletor de imagens disponível."); }
        });
        view.findViewById(R.id.rowNome).setOnClickListener(v -> editName());
        view.findViewById(R.id.rowEmail).setOnClickListener(v -> editEmail());
        view.findViewById(R.id.rowAlterarSenha).setOnClickListener(v -> resetPassword());
        view.findViewById(R.id.rowTarifaAgua).setOnClickListener(v -> editTariff(true));
        view.findViewById(R.id.rowTarifaEnergia).setOnClickListener(v -> editTariff(false));
        view.findViewById(R.id.btnSairConta).setOnClickListener(v -> confirmLogout());
        SwitchMaterial notifications = view.findViewById(R.id.switchNotificacoes);
        notifications.setOnCheckedChangeListener((button, checked) -> {
            if (applying || perfil == null) return;
            button.setEnabled(false);
            ContentValues changes = new ContentValues(); changes.put("notificacao_ativa", checked ? 1 : 0);
            update(changes, () -> { perfil.notificacoes = checked; button.setEnabled(true); }, () -> {
                applying = true; button.setChecked(perfil.notificacoes); applying = false; button.setEnabled(true);
            });
        });
        SwitchMaterial darkMode = view.findViewById(R.id.switchTemaEscuro);
        darkMode.setOnCheckedChangeListener((button, checked) -> {
            if (applying || perfil == null) return;
            button.setEnabled(false);
            ContentValues changes = new ContentValues(); changes.put("tema_escuro", checked ? 1 : 0);
            update(changes, () -> {
                perfil.temaEscuro = checked;
                button.setEnabled(true);
                AppCompatDelegate.setDefaultNightMode(checked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
            }, () -> {
                applying = true; button.setChecked(perfil.temaEscuro); applying = false; button.setEnabled(true);
            });
        });
        BottomNavigationView nav = view.findViewById(R.id.bottomNavigation);
        nav.setSelectedItemId(R.id.nav_perfil);
        nav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_perfil) return true;
            if (item.getItemId() == R.id.nav_inicio) { requireActivity().finish(); return false; }
            if (item.getItemId() == R.id.nav_consumo) { startActivity(new Intent(requireContext(), RegistrarConsumoActivity.class)); return false; }
            if (item.getItemId() == R.id.nav_relatorios) { startActivity(new Intent(requireContext(), RelatoriosActivity.class)); return false; }
            return false;
        });
        load();
    }

    private void onPhotoSelected(Uri uri) {
        if (uri == null || root == null || user == null) return;
        root.findViewById(R.id.avatarPerfil).setEnabled(false);
        FotoPerfilService.salvar(requireContext(), user.getUid(), user.getDisplayName(), uri);
    }

    private void load() {
        final View view = root;
        final String uid = user.getUid();
        final PerfilDAO currentDao = dao;
        final Context app = requireContext().getApplicationContext();
        executor.execute(() -> {
            try {
                PerfilDAO.Perfil data = currentDao.carregar(uid);
                Bitmap photo = data == null ? null : FotoPerfilService.carregar(app, data.fotoUri, 256);
                main.post(() -> {
                    if (root != view) { if (photo != null) photo.recycle(); return; }
                    ImageView image = view.findViewById(R.id.imgFotoPerfil);
                    image.setImageBitmap(photo);
                    image.setVisibility(photo == null ? View.GONE : View.VISIBLE);
                    view.findViewById(R.id.tvAvatar).setVisibility(photo == null ? View.VISIBLE : View.GONE);
                    perfil = data == null ? new PerfilDAO.Perfil() : data;
                    if (data == null) perfil.notificacoes = true;
                    String firebaseName = user.getDisplayName();
                    if (firebaseName != null && !firebaseName.trim().isEmpty()) setName(firebaseName);
                    else if (perfil.nome != null && !perfil.nome.trim().isEmpty()) setName(perfil.nome);
                    text(R.id.tvTarifaAgua).setText(tariff(perfil.tarifaAgua, "m³"));
                    text(R.id.tvTarifaEnergia).setText(tariff(perfil.tarifaEnergia, "kWh"));
                    SwitchMaterial notifications = view.findViewById(R.id.switchNotificacoes);
                    applying = true; notifications.setChecked(perfil.notificacoes); applying = false;
                    notifications.setEnabled(true);
                    SwitchMaterial darkMode = view.findViewById(R.id.switchTemaEscuro);
                    applying = true; darkMode.setChecked(perfil.temaEscuro); applying = false;
                    darkMode.setEnabled(true);
                });
            } catch (RuntimeException e) { main.post(() -> { if (root == view) toast("Não foi possível carregar o perfil."); }); }
        });
    }

    @Override public void onResume() {
        super.onResume();
        // A troca de e-mail só se efetiva depois que o link enviado pelo Firebase é confirmado.
        if (user == null || root == null) return;
        final View view = root;
        user.reload().addOnCompleteListener(task -> {
            if (root != view || !task.isSuccessful()) return;
            String email = user.getEmail();
            text(R.id.tvEmailCabecalho).setText(email == null ? "—" : email);
            text(R.id.tvEmailPerfil).setText(email == null ? "—" : email);
        });
    }

    private EditText dialogField(int type, String initial) {
        EditText field = new EditText(requireContext());
        field.setInputType(type);
        field.setSingleLine(true);
        if (initial != null) { field.setText(initial); field.setSelection(field.length()); }
        return field;
    }

    private android.widget.FrameLayout fieldContainer(EditText field) {
        android.widget.FrameLayout container = new android.widget.FrameLayout(requireContext());
        int pad = (int) (24 * getResources().getDisplayMetrics().density + 0.5f);
        container.setPadding(pad, 0, pad, 0);
        container.addView(field);
        return container;
    }

    private void editName() {
        EditText field = dialogField(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS | InputType.TYPE_TEXT_VARIATION_PERSON_NAME,
                user.getDisplayName() != null ? user.getDisplayName() : perfil == null ? "" : perfil.nome);
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext()).setTitle("Alterar nome")
                .setView(fieldContainer(field)).setNegativeButton("Cancelar", null).setPositiveButton("Salvar", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = field.getText().toString().trim();
            if (name.isEmpty() || name.length() > 100) { field.setError("Informe um nome de até 100 caracteres."); return; }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            final View view = root;
            UserProfileChangeRequest request = new UserProfileChangeRequest.Builder().setDisplayName(name).build();
            user.updateProfile(request).addOnCompleteListener(task -> {
                if (root != view) return;
                if (!task.isSuccessful()) {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    toast("Não foi possível alterar o nome. Verifique a conexão e tente novamente.");
                    return;
                }
                setName(name);
                if (perfil != null) perfil.nome = name;
                dialog.dismiss();
                // Atualização Firebase bem-sucedida: espelha o nome local sem mexer nas tarifas.
                final PerfilDAO currentDao = dao;
                final String uid = user.getUid();
                executor.execute(() -> {
                    try {
                        currentDao.salvarNome(uid, name);
                        main.post(() -> { if (root == view) toast("Nome atualizado."); });
                    } catch (RuntimeException e) {
                        main.post(() -> { if (root == view) toast("Nome atualizado na conta, mas não foi possível atualizar os dados locais."); });
                    }
                });
            });
        }));
        dialog.show();
    }

    private void editEmail() {
        EditText field = dialogField(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, user.getEmail());
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext()).setTitle("Alterar e-mail")
                .setMessage("Enviaremos um link de confirmação para o novo endereço. O e-mail atual continua válido até a confirmação.")
                .setView(fieldContainer(field)).setNegativeButton("Cancelar", null).setPositiveButton("Enviar link", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String email = field.getText().toString().trim();
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { field.setError("Informe um e-mail válido."); return; }
            if (email.equalsIgnoreCase(user.getEmail())) { field.setError("Informe um e-mail diferente do atual."); return; }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            sendEmailVerification(email, dialog, false);
        }));
        dialog.show();
    }

    private void sendEmailVerification(String email, AlertDialog dialog, boolean reauthenticated) {
        final View view = root;
        user.verifyBeforeUpdateEmail(email).addOnCompleteListener(task -> {
            if (root != view) return;
            if (task.isSuccessful()) {
                dialog.dismiss();
                new MaterialAlertDialogBuilder(requireContext()).setTitle("Confirme o novo e-mail")
                        .setMessage("Enviamos um link para " + email + ". Abra-o para confirmar a alteração. Até lá, use o e-mail atual para entrar.")
                        .setPositiveButton("Entendi", null).show();
            } else if (task.getException() instanceof FirebaseAuthRecentLoginRequiredException && !reauthenticated) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                requestReauthentication(email, dialog);
            } else {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                toast(task.getException() instanceof FirebaseAuthUserCollisionException
                        ? "Este e-mail já está vinculado a outra conta."
                        : "Não foi possível enviar o link. Verifique a conexão e tente novamente.");
            }
        });
    }

    private void requestReauthentication(String email, AlertDialog emailDialog) {
        String currentEmail = user.getEmail();
        if (currentEmail == null || currentEmail.isEmpty()) {
            toast("Saia e entre novamente para confirmar sua identidade."); return;
        }
        EditText password = dialogField(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext()).setTitle("Confirme sua identidade")
                .setMessage("Digite sua senha atual para alterar o e-mail.")
                .setView(fieldContainer(password)).setNegativeButton("Cancelar", null).setPositiveButton("Confirmar", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String secret = password.getText().toString();
            if (secret.isEmpty()) { password.setError("Informe sua senha."); return; }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            final View view = root;
            user.reauthenticate(EmailAuthProvider.getCredential(currentEmail, secret)).addOnCompleteListener(task -> {
                password.setText("");
                if (root != view) return;
                if (task.isSuccessful()) {
                    dialog.dismiss();
                    emailDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                    sendEmailVerification(email, emailDialog, true);
                } else {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    toast("Não foi possível confirmar sua identidade. Confira a senha e a conexão.");
                }
            });
        }));
        dialog.show();
    }

    private void editTariff(boolean water) {
        if (perfil == null) { toast("Aguarde o carregamento do perfil."); return; }
        EditText field = new EditText(requireContext());
        field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        field.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789.,"));
        field.setSingleLine(true);
        Double existing = water ? perfil.tarifaAgua : perfil.tarifaEnergia;
        if (existing != null) field.setText(BigDecimal.valueOf(existing).stripTrailingZeros().toPlainString().replace('.', ','));
        android.widget.FrameLayout wrap = new android.widget.FrameLayout(requireContext());
        int horizontal = (int) (24 * getResources().getDisplayMetrics().density + 0.5f);
        wrap.setPadding(horizontal, 0, horizontal, 0); wrap.addView(field);
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(water ? "Tarifa de água (R$/m³)" : "Tarifa de energia (R$/kWh)")
                .setMessage("A nova tarifa será usada nos próximos registros e nas estimativas do painel. Os custos já gravados nos registros e nos relatórios não serão alterados.")
                .setView(wrap).setNegativeButton("Cancelar", null).setPositiveButton("Salvar", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String raw = field.getText().toString().trim();
            try {
                if (!raw.matches("[0-9]+([.,][0-9]+)?")) throw new NumberFormatException();
                BigDecimal parsed = new BigDecimal(raw.replace(',', '.'));
                double value = parsed.doubleValue();
                if (parsed.signum() <= 0 || !Double.isFinite(value)) throw new NumberFormatException();
                ContentValues changes = new ContentValues(); changes.put(water ? "tarifa_agua" : "tarifa_energia", value);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                update(changes, () -> {
                    if (water) perfil.tarifaAgua = value; else perfil.tarifaEnergia = value;
                    text(water ? R.id.tvTarifaAgua : R.id.tvTarifaEnergia).setText(tariff(value, water ? "m³" : "kWh"));
                    dialog.dismiss(); toast("Tarifa atualizada.");
                }, () -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true));
            } catch (NumberFormatException e) { field.setError("Informe um valor válido maior que zero."); }
        }));
        dialog.show();
    }

    private void update(ContentValues changes, Runnable success, Runnable failure) {
        final View view = root;
        final PerfilDAO currentDao = dao;
        final String uid = user.getUid();
        final String name = perfil != null && perfil.nome != null ? perfil.nome : user.getDisplayName() == null ? "Usuário" : user.getDisplayName();
        executor.execute(() -> {
            try {
                currentDao.atualizar(uid, name, changes);
                main.post(() -> { if (root == view) success.run(); });
            } catch (RuntimeException e) {
                main.post(() -> { if (root == view) { failure.run(); toast("Não foi possível salvar. Tente novamente."); } });
            }
        });
    }

    private void resetPassword() {
        String email = user.getEmail();
        if (email == null || email.isEmpty()) { toast("A conta não possui e-mail para recuperação."); return; }
        new MaterialAlertDialogBuilder(requireContext()).setTitle("Alterar senha")
                .setMessage("Enviar instruções de redefinição para " + email + "?")
                .setNegativeButton("Cancelar", null).setPositiveButton("Enviar", (d, w) ->
                        FirebaseAuth.getInstance().sendPasswordResetEmail(email).addOnCompleteListener(task -> {
                            if (!isAdded()) return;
                            toast(task.isSuccessful() ? "Se a conta permitir, as instruções serão enviadas ao seu e-mail." : "Não foi possível enviar. Verifique sua conexão e tente novamente.");
                        })).show();
    }

    private void confirmLogout() {
        new MaterialAlertDialogBuilder(requireContext()).setTitle("Sair da conta?")
                .setMessage("Você precisará entrar novamente neste aparelho.")
                .setNegativeButton("Cancelar", null).setPositiveButton("Sair", (d, w) -> {
                    Context context = requireContext();
                    FirebaseAuth.getInstance().signOut();
                    OnboardingSession.clear();
                    context.getSharedPreferences(OnboardingStep4Activity.DRAFT_PREFERENCES, Context.MODE_PRIVATE).edit().clear().apply();
                    Intent intent = new Intent(context, MainActivity.class);
                    intent.putExtra(MainActivity.EXTRA_OPEN_LOGIN, true);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                }).show();
    }

    private void setName(String name) {
        String display = name == null || name.trim().isEmpty() ? "Usuário" : name.trim();
        text(R.id.tvNomeCabecalho).setText(display); text(R.id.tvNomePerfil).setText(display);
        text(R.id.tvAvatar).setText(FotoPerfilService.iniciais(display));
    }
    private String tariff(Double value, String unit) { return value == null || value <= 0 ? "Não configurada" : NumberFormat.getCurrencyInstance(locale).format(value) + "/" + unit; }
    private TextView text(int id) { return root.findViewById(id); }
    private void toast(String message) { Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show(); }
    @Override public void onDestroyView() {
        if (user != null) FotoPerfilService.removerObservador(user.getUid(), photoListener);
        root = null;
        if (executor != null) { DatabaseHelper close = helper; executor.execute(close::close); executor.shutdown(); }
        super.onDestroyView();
    }
}
