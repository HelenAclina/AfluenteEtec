package com.example.afluente.ui;

import android.app.Activity;
import android.view.View;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Com targetSdk 35 ou superior, o Android 15+ desenha o app por baixo das barras do sistema.
 * Este utilitário aplica as margens das barras (e do teclado) ao conteúdo de cada tela.
 * Em versões anteriores, as margens recebidas são zero e nada muda.
 */
public final class EdgeInsets {
    private EdgeInsets() { }

    public static void aplicar(Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        if (content == null) return;
        ViewCompat.setOnApplyWindowInsetsListener(content, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(content);
    }
}
