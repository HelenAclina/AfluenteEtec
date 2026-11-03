package com.example.afluente.model;

public class RegistroConsumo {
    public final long id;
    public final String tipo, data, observacao;
    public final double valor;
    public final Double custo;
    public RegistroConsumo(long id, String tipo, double valor, Double custo, String data, String observacao) {
        this.id = id; this.tipo = tipo; this.valor = valor; this.custo = custo; this.data = data; this.observacao = observacao;
    }
}
