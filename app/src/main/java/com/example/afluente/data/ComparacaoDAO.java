package com.example.afluente.data;

import android.database.Cursor;
import java.util.ArrayList;
import java.util.List;

/** Consultas de comparação isoladas por UID e por mês ISO (aaaa-MM). */
public class ComparacaoDAO {
    private final DatabaseHelper helper;
    public ComparacaoDAO(DatabaseHelper helper) { this.helper = helper; }
    public static class Periodo {
        public Double agua, energia, custo;
        public boolean temRegistros, custoIncompleto;
    }

    public List<String> listarMeses(String uid) {
        List<String> result = new ArrayList<>();
        String sql = "SELECT DISTINCT substr(r.data_registro,1,7) AS mes FROM registros_consumo r " +
                "JOIN usuarios u ON u.id=r.usuario_id WHERE u.firebase_uid=? " +
                "AND strftime('%Y-%m',r.data_registro)=substr(r.data_registro,1,7) ORDER BY mes DESC";
        try (Cursor c = helper.getReadableDatabase().rawQuery(sql, new String[]{uid})) {
            while (c.moveToNext()) result.add(c.getString(0));
        }
        return result;
    }

    public Periodo consultar(String uid, String mes) {
        if (mes == null || !mes.matches("[0-9]{4}-(0[1-9]|1[0-2])")) throw new IllegalArgumentException("Mês inválido");
        Periodo result = new Periodo();
        double totalCusto = 0;
        String sql = "SELECT r.tipo_recurso,SUM(r.valor_consumo),SUM(r.valor_financeiro),COUNT(*),COUNT(r.valor_financeiro) " +
                "FROM registros_consumo r JOIN usuarios u ON u.id=r.usuario_id " +
                "WHERE u.firebase_uid=? AND r.data_registro>=? AND r.data_registro<? GROUP BY r.tipo_recurso";
        try (Cursor c = helper.getReadableDatabase().rawQuery(sql, new String[]{uid, mes + "-01", mes + "-32"})) {
            while (c.moveToNext()) {
                if ("AGUA".equals(c.getString(0))) result.agua = c.getDouble(1);
                else if ("ENERGIA".equals(c.getString(0))) result.energia = c.getDouble(1);
                result.temRegistros = true;
                if (!c.isNull(2)) totalCusto += c.getDouble(2);
                if (c.getLong(3) != c.getLong(4)) result.custoIncompleto = true;
            }
        }
        if (result.temRegistros && !result.custoIncompleto) result.custo = totalCusto;
        return result;
    }
}
