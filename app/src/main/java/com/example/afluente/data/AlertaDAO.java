package com.example.afluente.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.example.afluente.model.Alerta;
import com.example.afluente.service.AlertaService;
import java.util.ArrayList;
import java.util.List;

public class AlertaDAO {
    private final DatabaseHelper helper;
    public AlertaDAO(DatabaseHelper helper) { this.helper = helper; }

    public List<Alerta> buscarAlertasPorUsuario(String uid) {
        List<Alerta> result = new ArrayList<>();
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try { AlertaService.verificarMetas(db, uid); db.setTransactionSuccessful(); } finally { db.endTransaction(); }
        String sql = "SELECT a.id,a.titulo,a.descricao,a.tipo_alerta,a.data_geracao,a.lido " +
                "FROM alertas a JOIN usuarios u ON u.id=a.usuario_id WHERE u.firebase_uid=? " +
                "ORDER BY a.data_geracao DESC,a.id DESC";
        try (Cursor c = helper.getReadableDatabase().rawQuery(sql, new String[]{uid})) {
            while (c.moveToNext()) result.add(new Alerta(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getInt(5) != 0));
        }
        return result;
    }

    public boolean marcarComoLido(long id, String uid) {
        ContentValues update = new ContentValues();
        update.put("lido", 1);
        SQLiteDatabase db = helper.getWritableDatabase();
        return db.update("alertas", update, "id=? AND usuario_id IN (SELECT id FROM usuarios WHERE firebase_uid=?)",
                new String[]{String.valueOf(id), uid}) == 1;
    }
}
