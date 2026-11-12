package com.example.afluente.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import org.junit.Test;

/** Testes locais (sem celular) das regras de validação e de cálculo de custo. */
public class ConsumoServiceTest {
    private static String dia(int deslocamento) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, deslocamento);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(c.getTime());
    }

    private static void invalido(String tipo, String consumo, String data) {
        try {
            ConsumoService.validar("uid", tipo, consumo == null ? null : new BigDecimal(consumo), data);
            fail("Deveria rejeitar: " + tipo + " " + consumo + " " + data);
        } catch (IllegalArgumentException esperado) { /* ok */ }
    }

    @Test public void aceitaLeituraValidaDeHoje() {
        ConsumoService.validar("uid", "AGUA", new BigDecimal("12.5"), dia(0));
        ConsumoService.validar("uid", "ENERGIA", new BigDecimal("180"), dia(-30));
    }

    @Test public void rejeitaConsumoZeroNegativoOuAcimaDoLimite() {
        invalido("AGUA", "0", dia(0));
        invalido("AGUA", "-1", dia(0));
        invalido("AGUA", null, dia(0));
        invalido("AGUA", "1000000.01", dia(0));
        ConsumoService.validar("uid", "AGUA", new BigDecimal("1000000"), dia(0));
    }

    @Test public void rejeitaTipoEDatasInvalidas() {
        invalido("GAS", "1", dia(0));
        invalido("AGUA", "1", "2026-02-30");
        invalido("AGUA", "1", "12/11/2026");
        invalido("AGUA", "1", dia(1));
    }

    @Test public void identificaDataFutura() {
        assertTrue(ConsumoService.dataFutura(dia(1)));
        assertFalse(ConsumoService.dataFutura(dia(0)));
        assertFalse(ConsumoService.dataFutura(dia(-1)));
    }

    @Test public void calculaCustoComArredondamentoParaCentavos() {
        assertEquals(50.0, ConsumoService.calcularCusto(new BigDecimal("10"), 5.0), 0.0);
        assertEquals(0.89, ConsumoService.calcularCusto(new BigDecimal("1"), 0.894), 0.0);
        assertEquals(0.9, ConsumoService.calcularCusto(new BigDecimal("1"), 0.895), 0.0);
    }

    @Test public void semTarifaNaoHaCusto() {
        assertNull(ConsumoService.calcularCusto(BigDecimal.ONE, null));
        assertNull(ConsumoService.calcularCusto(BigDecimal.ONE, 0.0));
        assertNull(ConsumoService.calcularCusto(BigDecimal.ONE, -2.0));
    }
}
