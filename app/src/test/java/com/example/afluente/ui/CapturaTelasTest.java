package com.example.afluente.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.widget.NestedScrollView;
import androidx.test.core.app.ApplicationProvider;
import com.example.afluente.R;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/**
 * Gera imagens PNG de cada layout para revisão visual (pasta app/build/telas).
 * Não verifica nada: serve para enxergar as telas sem emulador.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34)
public class CapturaTelasTest {
    private static final Object[][] TELAS = {
            {"01_boas_vindas", R.layout.activity_main},
            {"02_residencia", R.layout.activity_onboarding_residencia},
            {"03_consumo_estimado", R.layout.activity_onboarding_consumo},
            {"04_objetivo", R.layout.activity_onboarding_objetivo},
            {"05_tarifas", R.layout.activity_onboarding_tarifas},
            {"06_painel", R.layout.fragment_dashboard},
            {"07_registrar", R.layout.activity_registrar_consumo},
            {"08_historico", R.layout.activity_historico},
            {"08b_item_historico", R.layout.item_historico},
            {"09_analise", R.layout.activity_consumption_analysis},
            {"10_comparar", R.layout.activity_comparar_periodos},
            {"11_metas", R.layout.activity_metas_consumo},
            {"12_alertas", R.layout.activity_notificacoes},
            {"12b_item_alerta", R.layout.item_notificacao},
            {"13_relatorios", R.layout.fragment_relatorios},
            {"14_perfil", R.layout.fragment_perfil},
    };

    @Test public void celularPadrao() { capturar("padrao", 411, 891, 1f, false); }
    @Test public void celularPequenoFonteGrande() { capturar("pequeno_fonte_grande", 320, 640, 1.3f, false); }
    @Test public void temaEscuro() { capturar("escuro", 411, 891, 1f, true); }

    private void capturar(String variante, int wDp, int hDp, float fonte, boolean escuro) {
        RuntimeEnvironment.setQualifiers("w" + wDp + "dp-h" + hDp + "dp-xxhdpi-" + (escuro ? "night" : "notnight"));
        RuntimeEnvironment.setFontScale(fonte);
        File pasta = new File("build/telas/" + variante);
        pasta.mkdirs();
        for (Object[] tela : TELAS) {
            try {
                Context base = ApplicationProvider.getApplicationContext();
                Context ctx = new ContextThemeWrapper(base, R.style.Theme_Afluente);
                View view = LayoutInflater.from(ctx).inflate((Integer) tela[1], null, false);
                float d = ctx.getResources().getDisplayMetrics().density;
                int w = (int) (wDp * d), h = (int) (hDp * d);
                boolean item = ((String) tela[0]).contains("item");
                salvar(view, w, item ? -1 : h, new File(pasta, tela[0] + ".png"));
                ViewGroup rolagem = procurarRolagem(view);
                if (rolagem != null && rolagem.getChildCount() > 0) {
                    View conteudo = rolagem.getChildAt(0);
                    rolagem.removeView(conteudo);
                    salvar(conteudo, w, -1, new File(pasta, tela[0] + "_completa.png"));
                }
            } catch (Throwable t) {
                System.out.println("Falha ao capturar " + tela[0] + ": " + t);
            }
        }
    }

    private static ViewGroup procurarRolagem(View v) {
        if (v instanceof ScrollView || v instanceof NestedScrollView) return (ViewGroup) v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                ViewGroup r = procurarRolagem(g.getChildAt(i));
                if (r != null) return r;
            }
        }
        return null;
    }

    private static void salvar(View view, int w, int h, File arquivo) throws Exception {
        int hs = h < 0 ? View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED) : View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY);
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), hs);
        int altura = Math.max(1, Math.min(view.getMeasuredHeight(), 12000));
        view.layout(0, 0, w, altura);
        Bitmap bmp = Bitmap.createBitmap(w, altura, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(Color.WHITE);
        view.draw(canvas);
        try (FileOutputStream out = new FileOutputStream(arquivo)) { bmp.compress(Bitmap.CompressFormat.PNG, 100, out); }
    }
}
