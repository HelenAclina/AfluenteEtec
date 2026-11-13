package com.example.afluente.data;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.example.afluente.service.AlertaService;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Snapshot coerente, isolado pelo UID; os filtros por data usam o índice de consumo. */
public class DashboardDAO {
    private final DatabaseHelper helper;
    public DashboardDAO(DatabaseHelper helper) { this.helper = helper; }
    public void close() { helper.close(); }

    public static class Snapshot {
        public final double[] water = new double[6], energy = new double[6];
        public final boolean[] hasWater = new boolean[6], hasEnergy = new boolean[6];
        public final String[] months = new String[6];
        public Double waterTariff, energyTariff, waterGoal, energyGoal;
        public int unreadCount;
        public String alertTitle, alertDescription;
    }

    public Snapshot load(String uid) {
        Snapshot result = new Snapshot();
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            AlertaService.verificarMetas(db, uid);
            try (Cursor c = db.rawQuery("SELECT m.tipo_recurso,m.valor_meta FROM metas_consumo m JOIN usuarios u ON u.id=m.usuario_id WHERE u.firebase_uid=?", new String[]{uid})) {
                while (c.moveToNext()) {
                    if ("AGUA".equals(c.getString(0))) result.waterGoal = c.getDouble(1);
                    else if ("ENERGIA".equals(c.getString(0))) result.energyGoal = c.getDouble(1);
                }
            }
            Calendar month = Calendar.getInstance(); month.set(Calendar.DAY_OF_MONTH, 1); month.add(Calendar.MONTH, -5);
            String[] keys = new String[6]; Map<String, Integer> indexes = new HashMap<>();
            for (int i = 0; i < 6; i++) {
                keys[i] = String.format(Locale.ROOT, "%04d-%02d", month.get(Calendar.YEAR), month.get(Calendar.MONTH) + 1);
                indexes.put(keys[i], i);
                result.months[i] = month.getDisplayName(Calendar.MONTH, Calendar.SHORT, new Locale("pt", "BR")).replace(".", "");
                month.add(Calendar.MONTH, 1);
            }
            String sql = "SELECT substr(r.data_registro,1,7),r.tipo_recurso,SUM(r.valor_consumo) FROM registros_consumo r " +
                    "JOIN usuarios u ON u.id=r.usuario_id WHERE u.firebase_uid=? AND r.data_registro>=? AND r.data_registro<? GROUP BY 1,2";
            try (Cursor c = db.rawQuery(sql, new String[]{uid, keys[0] + "-01", keys[5] + "-32"})) {
                while (c.moveToNext()) {
                    Integer index = indexes.get(c.getString(0));
                    if (index == null) continue;
                    if ("AGUA".equals(c.getString(1))) { result.water[index] = c.getDouble(2); result.hasWater[index] = true; }
                    else if ("ENERGIA".equals(c.getString(1))) { result.energy[index] = c.getDouble(2); result.hasEnergy[index] = true; }
                }
            }
            try (Cursor c = db.rawQuery("SELECT c.tarifa_agua,c.tarifa_energia FROM configuracoes_usuario c JOIN usuarios u ON u.id=c.usuario_id WHERE u.firebase_uid=?", new String[]{uid})) {
                if (c.moveToFirst()) { if (!c.isNull(0)) result.waterTariff = c.getDouble(0); if (!c.isNull(1)) result.energyTariff = c.getDouble(1); }
            }
            try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM alertas a JOIN usuarios u ON u.id=a.usuario_id WHERE u.firebase_uid=? AND a.lido=0", new String[]{uid})) {
                if (c.moveToFirst()) result.unreadCount = c.getInt(0);
            }
            if (result.unreadCount > 0) {
                try (Cursor c = db.rawQuery("SELECT a.titulo,a.descricao FROM alertas a JOIN usuarios u ON u.id=a.usuario_id WHERE u.firebase_uid=? AND a.lido=0 ORDER BY a.data_geracao DESC,a.id DESC LIMIT 1", new String[]{uid})) {
                    if (c.moveToFirst()) { result.alertTitle = c.getString(0); result.alertDescription = c.getString(1); }
                }
            }
            db.setTransactionSuccessful();
            return result;
        } finally { db.endTransaction(); }
    }
}
