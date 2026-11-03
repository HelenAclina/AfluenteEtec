package com.example.afluente.model;

/** Totais nulos representam ausência de dados ou, no gasto, custo incompleto. */
public class RelatorioAnual extends Relatorio {
    public Double totalAgua, totalEnergia, totalFinanceiro;
    // JSON por recurso; os picos também guardam o mês de referência.
    public String maiorConsumo, menorConsumo, mediaAnual;
    public RelatorioAnual() { tipoRelatorio = "ANUAL"; }
}
