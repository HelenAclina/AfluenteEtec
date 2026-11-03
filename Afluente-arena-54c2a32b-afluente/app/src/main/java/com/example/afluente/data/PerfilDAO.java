package com.example.afluente.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Dados locais identificados pelo UID autenticado; nunca armazena senha. */
public class PerfilDAO {
    private final DatabaseHelper helper;
    public PerfilDAO(DatabaseHelper helper) { this.helper = helper; }
    public static class Perfil {
        public String nome, fotoUri;
        public Double tarifaAgua, tarifaEnergia;
        public boolean notificacoes, temaEscuro;
    }

    public Perfil carregar(String uid) {
        String sql = "SELECT u.nome,c.tarifa_agua,c.tarifa_energia,c.notificacao_ativa,c.tema_escuro,u.foto_uri FROM usuarios u LEFT JOIN configuracoes_usuario c ON c.usuario_id=u.id WHERE u.firebase_uid=?";
        try (Cursor c = helper.getReadableDatabase().rawQuery(sql, new String[]{uid})) {
            if (!c.moveToFirst()) return null;
            Perfil p = new Perfil();
            p.nome = c.getString(0);
            if (!c.isNull(1)) p.tarifaAgua = c.getDouble(1);
            if (!c.isNull(2)) p.tarifaEnergia = c.getDouble(2);
            p.notificacoes = c.isNull(3) || c.getInt(3) != 0;
            p.temaEscuro = !c.isNull(4) && c.getInt(4) != 0;
            p.fotoUri = c.isNull(5) ? null : c.getString(5);
            return p;
        }
    }

    /** Persiste o caminho relativo privado para o UID; devolve a foto anterior se não estiver em uso. */
    public String atualizarFotoPerfil(String uid, String nome, String fotoUri) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues initial = new ContentValues();
            initial.put("firebase_uid", uid); initial.put("nome", nome == null || nome.trim().isEmpty() ? "Usuário" : nome.trim());
            db.insertWithOnConflict("usuarios", null, initial, SQLiteDatabase.CONFLICT_IGNORE);
            String anterior = null;
            try (Cursor c = db.rawQuery("SELECT foto_uri FROM usuarios WHERE firebase_uid=?", new String[]{uid})) {
                if (!c.moveToFirst()) throw new IllegalStateException("Usuário não encontrado");
                if (!c.isNull(0)) anterior = c.getString(0);
            }
            ContentValues values = new ContentValues(); values.put("foto_uri", fotoUri);
            if (db.update("usuarios", values, "firebase_uid=?", new String[]{uid}) != 1) throw new IllegalStateException("Usuário não encontrado");
            if (anterior != null) {
                try (Cursor c = db.rawQuery("SELECT 1 FROM usuarios WHERE foto_uri=? LIMIT 1", new String[]{anterior})) {
                    if (c.moveToFirst()) anterior = null;
                }
            }
            db.setTransactionSuccessful();
            return anterior;
        } finally { db.endTransaction(); }
    }

    /** Atualiza o nome local sem substituir a conta e suas configurações. */
    public void salvarNome(String uid, String nome) {
        SQLiteDatabase db = helper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("firebase_uid", uid);
        values.put("nome", nome);
        db.beginTransaction();
        try {
            db.insertWithOnConflict("usuarios", null, values, SQLiteDatabase.CONFLICT_IGNORE);
            if (db.update("usuarios", values, "firebase_uid=?", new String[]{uid}) != 1)
                throw new IllegalStateException("Usuário não encontrado");
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    /** Cria somente a configuração local ausente, preservando dados preexistentes. */
    public void atualizar(String uid, String nome, ContentValues changes) {
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues user = new ContentValues(); user.put("firebase_uid", uid); user.put("nome", nome);
            db.insertWithOnConflict("usuarios", null, user, SQLiteDatabase.CONFLICT_IGNORE);
            long id;
            try (Cursor c = db.rawQuery("SELECT id FROM usuarios WHERE firebase_uid=?", new String[]{uid})) {
                if (!c.moveToFirst()) throw new IllegalStateException("Perfil não encontrado");
                id = c.getLong(0);
            }
            ContentValues initial = new ContentValues(); initial.put("usuario_id", id);
            db.insertWithOnConflict("configuracoes_usuario", null, initial, SQLiteDatabase.CONFLICT_IGNORE);
            if (db.update("configuracoes_usuario", changes, "usuario_id=?", new String[]{String.valueOf(id)}) != 1) throw new IllegalStateException("Configuração não encontrada");
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
}
