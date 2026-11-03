package com.example.afluente.model;

/** Registro local, sempre carregado para o usuário autenticado. */
public class Alerta {
    public final long id;
    public final String titulo, descricao, tipo, data;
    public boolean lido;
    public Alerta(long id, String titulo, String descricao, String tipo, String data, boolean lido) {
        this.id = id; this.titulo = titulo; this.descricao = descricao;
        this.tipo = tipo; this.data = data; this.lido = lido;
    }
}
