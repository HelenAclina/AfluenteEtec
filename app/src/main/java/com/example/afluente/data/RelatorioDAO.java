package com.example.afluente.data;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.Calendar;
import java.util.Locale;

/** Agrega lançamentos mensais do usuário, sem substituir custos históricos pela tarifa atual. */
public class RelatorioDAO {
    private final DatabaseHelper helper;
    public RelatorioDAO(DatabaseHelper helper) { this.helper = helper; }
    public void close() { helper.close(); }

    public static class Mes {
        public String periodo, rotulo;
        public double agua, energia, custo;
        public boolean temAgua, temEnergia, temRegistros, custoIncompleto;
    }
    public static class Resultado {
        public final Mes[] meses;
        public Resultado(int count) { meses = new Mes[count]; }
    }

    public Resultado consultar(String uid, int quantidadeMeses) {
        if (quantidadeMeses != 6 && quantidadeMeses != 12) throw new IllegalArgumentException("Período inválido");
        Calendar calendar = Calendar.getInstance();
        if (quantidadeMeses == 12) return consultarAno(uid, calendar.get(Calendar.YEAR), helper.getReadableDatabase());
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.add(Calendar.MONTH, 1 - quantidadeMeses);
        return consultarDesde(uid, quantidadeMeses, calendar, helper.getReadableDatabase());
    }

    /** Consulta os 12 meses de um ano civil, inclusive meses futuros que tenham lançamentos. */
    Resultado consultarAno(String uid, int ano, SQLiteDatabase db) {
        if (ano < 1 || ano > 9999) throw new IllegalArgumentException("Ano inválido");
        Calendar calendar = Calendar.getInstance();
        calendar.clear(); calendar.set(ano, Calendar.JANUARY, 1);
        return consultarDesde(uid, 12, calendar, db);
    }

    private Resultado consultarDesde(String uid, int quantidadeMeses, Calendar calendar, SQLiteDatabase db) {
        Resultado result = new Resultado(quantidadeMeses);
        Locale pt = new Locale("pt", "BR");
        for (int i = 0; i < quantidadeMeses; i++) {
            Mes mes = new Mes();
            mes.periodo = String.format(Locale.ROOT, "%04d-%02d", calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1);
            mes.rotulo = calendar.getDisplayName(Calendar.MONTH, Calendar.SHORT, pt).replace(".", "");
            result.meses[i] = mes;
            calendar.add(Calendar.MONTH, 1);
        }
        String sql = "SELECT substr(r.data_registro,1,7),r.tipo_recurso,SUM(r.valor_consumo),SUM(r.valor_financeiro),COUNT(*),COUNT(r.valor_financeiro) " +
                "FROM registros_consumo r JOIN usuarios u ON u.id=r.usuario_id WHERE u.firebase_uid=? AND r.data_registro>=? AND r.data_registro<? GROUP BY 1,2 ORDER BY 1";
        try (Cursor c = db.rawQuery(sql, new String[]{uid, result.meses[0].periodo + "-01", result.meses[quantidadeMeses - 1].periodo + "-32"})) {
            while (c.moveToNext()) {
                String key = c.getString(0), type = c.getString(1);
                for (Mes mes : result.meses) {
                    if (!mes.periodo.equals(key)) continue;
                    if ("AGUA".equals(type)) { mes.agua = c.getDouble(2); mes.temAgua = true; }
                    else if ("ENERGIA".equals(type)) { mes.energia = c.getDouble(2); mes.temEnergia = true; }
                    mes.temRegistros = true;
                    if (!c.isNull(3)) mes.custo += c.getDouble(3);
                    if (c.getLong(4) != c.getLong(5)) mes.custoIncompleto = true;
                    break;
                }
            }
        }
        return result;
    }
}
