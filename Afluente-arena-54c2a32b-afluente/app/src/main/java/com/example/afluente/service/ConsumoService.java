package com.example.afluente.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Locale;

/** Valida entradas e centraliza o cálculo monetário, sem criar tarifa fictícia. */
public final class ConsumoService {
    private ConsumoService() { }
    public static void validar(String uid, String tipo, BigDecimal consumo, String data) {
        if (uid == null || uid.trim().isEmpty() || (!"AGUA".equals(tipo) && !"ENERGIA".equals(tipo)) ||
                consumo == null || consumo.signum() <= 0 || !Double.isFinite(consumo.doubleValue()) || consumo.doubleValue() <= 0)
            throw new IllegalArgumentException("Consumo inválido");
        if (data == null || !data.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException("Data inválida");
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT); format.setLenient(false);
        ParsePosition pos = new ParsePosition(0);
        if (format.parse(data, pos) == null || pos.getIndex() != data.length()) throw new IllegalArgumentException("Data inválida");
    }
    /** Null indica tarifa não configurada ou inválida. */
    public static Double calcularCusto(BigDecimal consumo, Double tarifa) {
        if (tarifa == null || !Double.isFinite(tarifa) || tarifa <= 0) return null;
        double result = consumo.multiply(BigDecimal.valueOf(tarifa)).setScale(2, RoundingMode.HALF_UP).doubleValue();
        if (!Double.isFinite(result)) throw new IllegalArgumentException("Valor financeiro inválido");
        return result;
    }
}
