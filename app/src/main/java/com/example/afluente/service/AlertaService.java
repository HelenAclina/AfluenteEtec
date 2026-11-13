package com.example.afluente.service;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Calendar;
import java.util.Locale;

/** Mantém alertas locais ativos de meta e aumento do mês atual. Chamado dentro de transação. */
public final class AlertaService {
    private AlertaService() { }
    // Evita classificar erros mínimos da soma em ponto flutuante como excesso real.
    public static boolean excedeu(double consumo, double meta) {
        return consumo > meta && consumo - meta > Math.max(1e-12, 16 * Math.ulp(Math.max(Math.abs(consumo), Math.abs(meta))));
    }
    public static String mesAtual() { return new SimpleDateFormat("yyyy-MM", Locale.ROOT).format(new Date()); }
    public static void verificarMetas(SQLiteDatabase db, String uid) {
        String month = mesAtual();
        for (String type : new String[]{"AGUA", "ENERGIA"}) {
            String key = "meta-excesso:" + type;
            long userId = -1;
            Double goal = null, consumed = null;
            try (Cursor c = db.rawQuery("SELECT u.id,m.valor_meta,(SELECT SUM(r.valor_consumo) FROM registros_consumo r WHERE r.usuario_id=u.id AND r.tipo_recurso=? AND r.data_registro>=? AND r.data_registro<?) FROM usuarios u LEFT JOIN metas_consumo m ON m.usuario_id=u.id AND m.tipo_recurso=? WHERE u.firebase_uid=?", new String[]{type, month + "-01", month + "-32", type, uid})) {
                if (c.moveToFirst()) { userId = c.getLong(0); if (!c.isNull(1)) goal = c.getDouble(1); if (!c.isNull(2)) consumed = c.getDouble(2); }
            }
            if (userId < 0) continue;
            String[] keyArgs = {String.valueOf(userId), key};
            if (goal == null || consumed == null || !excedeu(consumed, goal)) {
                db.delete("alertas", "usuario_id=? AND uuid=?", keyArgs);
                continue;
            }
            // Um alerta por recurso; um novo mês inicia um novo alerta não lido.
            db.delete("alertas", "usuario_id=? AND uuid=? AND substr(data_geracao,1,7)<>?", new String[]{String.valueOf(userId), key, month});
            NumberFormat fmt = NumberFormat.getNumberInstance(new Locale("pt", "BR")); fmt.setMaximumFractionDigits(3);
            String name = "AGUA".equals(type) ? "Água" : "Energia";
            String unit = "AGUA".equals(type) ? " m³" : " kWh";
            ContentValues values = new ContentValues();
            values.put("tipo_alerta", "META_ULTRAPASSADA");
            values.put("titulo", "Meta de " + name.toLowerCase(new Locale("pt", "BR")) + " ultrapassada");
            values.put("descricao", "O consumo do mês excedeu a meta em " + fmt.format(consumed - goal) + unit + ".");
            int rows = db.update("alertas", values, "usuario_id=? AND uuid=?", keyArgs);
            if (rows == 0) {
                values.put("usuario_id", userId); values.put("uuid", key); values.put("tipo_alerta", "META_ULTRAPASSADA");
                values.put("lido", 0); values.put("data_geracao", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date()));
                db.insertOrThrow("alertas", null, values);
            }
        }
        verificarAumento(db, uid, month);
    }

    /** UC19: pelo menos 30% acima do mês civil anterior, com base anterior positiva. */
    private static void verificarAumento(SQLiteDatabase db, String uid, String month) {
        Calendar previous = Calendar.getInstance();
        previous.set(Calendar.DAY_OF_MONTH, 1);
        previous.set(Calendar.YEAR, Integer.parseInt(month.substring(0, 4)));
        previous.set(Calendar.MONTH, Integer.parseInt(month.substring(5, 7)) - 1);
        previous.add(Calendar.MONTH, -1);
        String prior = String.format(Locale.ROOT, "%04d-%02d", previous.get(Calendar.YEAR), previous.get(Calendar.MONTH) + 1);
        for (String type : new String[]{"AGUA", "ENERGIA"}) {
            long userId = -1; Double current = null, baseline = null;
            String sql = "SELECT u.id,SUM(CASE WHEN r.data_registro>=? THEN r.valor_consumo END)," +
                    "SUM(CASE WHEN r.data_registro<? THEN r.valor_consumo END) FROM usuarios u " +
                    "LEFT JOIN registros_consumo r ON r.usuario_id=u.id AND r.tipo_recurso=? AND r.data_registro>=? AND r.data_registro<? " +
                    "WHERE u.firebase_uid=? GROUP BY u.id";
            try (Cursor c = db.rawQuery(sql, new String[]{month + "-01", month + "-01", type, prior + "-01", month + "-32", uid})) {
                if (c.moveToFirst()) { userId = c.getLong(0); if (!c.isNull(1)) current = c.getDouble(1); if (!c.isNull(2)) baseline = c.getDouble(2); }
            }
            if (userId < 0) continue;
            String key = "aumento-anormal:" + type;
            String[] args = {String.valueOf(userId), key};
            double ratio = current == null || baseline == null || baseline <= 0 ? 0 : current / baseline;
            if (ratio < 1.3 - 1e-12 || Double.isNaN(ratio)) {
                db.delete("alertas", "usuario_id=? AND uuid=?", args);
                continue;
            }
            db.delete("alertas", "usuario_id=? AND uuid=? AND substr(data_geracao,1,7)<>?", new String[]{String.valueOf(userId), key, month});
            String resource = "AGUA".equals(type) ? "água" : "energia";
            double percentage = (ratio - 1) * 100;
            String amount = Double.isFinite(percentage) ? String.format(new Locale("pt", "BR"), "%.1f%%", percentage) : "mais de 30%";
            ContentValues values = new ContentValues();
            values.put("titulo", "Aumento de consumo de " + resource);
            values.put("descricao", "O consumo deste mês aumentou " + amount + " em relação ao mês anterior.");
            values.put("tipo_alerta", "AUMENTO_ANORMAL");
            if (db.update("alertas", values, "usuario_id=? AND uuid=?", args) == 0) {
                values.put("usuario_id", userId); values.put("uuid", key); values.put("lido", 0);
                values.put("data_geracao", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date()));
                db.insertOrThrow("alertas", null, values);
            }
        }
    }
}
