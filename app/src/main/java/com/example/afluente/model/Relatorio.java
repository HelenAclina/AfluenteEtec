package com.example.afluente.model;

/** Identificação comum aos relatórios persistidos localmente. */
public abstract class Relatorio {
    public long id;
    public String uuid, tipoRelatorio, dataGeracao, periodo;
    public long usuarioId;
}
