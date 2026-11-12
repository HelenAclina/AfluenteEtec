package com.example.afluente.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Salva o perfil local de forma atômica, sem apagar registros de consumo existentes. */
public class ConfiguracaoUsuarioDAO {
    private final DatabaseHelper helper;
    public ConfiguracaoUsuarioDAO(DatabaseHelper helper) { this.helper = helper; }

    public void save(String uid, String name, int residents, String residenceType,
                     String waterEstimate, String energyEstimate, String objectives,
                     Double waterTariff, Double energyTariff) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues user = new ContentValues();
            user.put("firebase_uid", uid);
            user.put("nome", name);
            db.insertWithOnConflict("usuarios", null, user, SQLiteDatabase.CONFLICT_IGNORE);
            db.update("usuarios", user, "firebase_uid=?", new String[]{uid});
            long userId;
            try (Cursor cursor = db.rawQuery("SELECT id FROM usuarios WHERE firebase_uid=?", new String[]{uid})) {
                if (!cursor.moveToFirst()) throw new IllegalStateException("Usuário local não encontrado");
                userId = cursor.getLong(0);
            }
            ContentValues config = new ContentValues();
            config.put("usuario_id", userId);
            if (waterTariff == null) config.putNull("tarifa_agua"); else config.put("tarifa_agua", waterTariff);
            if (energyTariff == null) config.putNull("tarifa_energia"); else config.put("tarifa_energia", energyTariff);
            config.put("residentes", residents);
            config.put("tipo_residencia", residenceType);
            putEstimate(config, "estimativa_agua", waterEstimate);
            putEstimate(config, "estimativa_energia", energyEstimate);
            config.put("objetivos", objectives);
            int updated = db.update("configuracoes_usuario", config, "usuario_id=?", new String[]{String.valueOf(userId)});
            if (updated == 0) db.insertOrThrow("configuracoes_usuario", null, config);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    /**
     * Cria o perfil local mínimo de uma conta que já existe no Firebase, sem sobrescrever dados.
     * Necessário quando o usuário entra em um aparelho novo ou depois de reinstalar o aplicativo.
     */
    public void garantirPerfil(String uid, String name) {
        if (uid == null || uid.trim().isEmpty()) throw new IllegalArgumentException("UID inválido");
        String nome = name == null || name.trim().isEmpty() ? "Usuário" : name.trim();
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues user = new ContentValues();
            user.put("firebase_uid", uid);
            user.put("nome", nome);
            db.insertWithOnConflict("usuarios", null, user, SQLiteDatabase.CONFLICT_IGNORE);
            long userId;
            try (Cursor cursor = db.rawQuery("SELECT id FROM usuarios WHERE firebase_uid=?", new String[]{uid})) {
                if (!cursor.moveToFirst()) throw new IllegalStateException("Usuário local não encontrado");
                userId = cursor.getLong(0);
            }
            ContentValues config = new ContentValues();
            config.put("usuario_id", userId);
            db.insertWithOnConflict("configuracoes_usuario", null, config, SQLiteDatabase.CONFLICT_IGNORE);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    private void putEstimate(ContentValues values, String key, String text) {
        if (text == null || text.isEmpty()) values.putNull(key);
        else values.put(key, Double.parseDouble(text.replace(',', '.')));
    }
}
