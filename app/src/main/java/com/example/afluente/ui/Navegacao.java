package com.example.afluente.ui;

import android.app.Activity;
import android.content.Intent;
import com.example.afluente.R;

/**
 * Navegação da barra inferior. O painel é sempre a base da pilha de telas:
 * trocar de aba substitui a tela atual em vez de empilhar, e "Início" volta ao painel
 * fechando tudo o que estiver acima dele.
 */
public final class Navegacao {
    private Navegacao() { }

    /** Abre o destino do item tocado. Retorna true apenas se o item já corresponde à tela atual. */
    public static boolean abrir(Activity atual, int itemId) {
        Class<?> destino;
        if (itemId == R.id.nav_inicio) destino = DashboardActivity.class;
        else if (itemId == R.id.nav_consumo) destino = ConsumptionAnalysisActivity.class;
        else if (itemId == R.id.nav_relatorios) destino = RelatoriosActivity.class;
        else if (itemId == R.id.nav_perfil) destino = PerfilActivity.class;
        else return false;
        if (destino.isInstance(atual)) return true;
        Intent intent = new Intent(atual, destino);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        atual.startActivity(intent);
        // Telas secundárias saem da pilha: o voltar do sistema sempre leva ao painel.
        if (!(atual instanceof DashboardActivity)) atual.finish();
        atual.overridePendingTransition(0, 0);
        return false;
    }
}
