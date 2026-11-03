package com.example.afluente.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.example.afluente.service.AlertaService;
import java.math.BigDecimal;
import java.util.UUID;

/** Uma meta positiva por usuário e recurso, sem valores padrão inventados. */
public class MetaDAO {
    private final DatabaseHelper helper;
    public MetaDAO(DatabaseHelper helper) { this.helper = helper; }
    public void atualizarAlertas(String uid) {
        SQLiteDatabase db = helper.getWritableDatabase(); db.beginTransaction();
        try { AlertaService.verificarMetas(db, uid); db.setTransactionSuccessful(); } finally { db.endTransaction(); }
    }
    public static class Dados { public Double meta, consumo; }
    public Dados consultar(String uid, String tipo) {
        validarTipo(tipo);
        Dados data = new Dados();
        String mes = AlertaService.mesAtual();
        try (Cursor c = helper.getReadableDatabase().rawQuery("SELECT m.valor_meta,(SELECT SUM(r.valor_consumo) FROM registros_consumo r WHERE r.usuario_id=u.id AND r.tipo_recurso=? AND r.data_registro>=? AND r.data_registro<?) FROM usuarios u LEFT JOIN metas_consumo m ON m.usuario_id=u.id AND m.tipo_recurso=? WHERE u.firebase_uid=?", new String[]{tipo, mes + "-01", mes + "-32", tipo, uid})) {
            if (!c.moveToFirst()) throw new IllegalStateException("Perfil local não encontrado");
            if (!c.isNull(0)) data.meta = c.getDouble(0);
            if (!c.isNull(1)) data.consumo = c.getDouble(1);
        }
        return data;
    }
    public void salvarOuAtualizarMeta(String uid, String tipo, BigDecimal valor) { alterar(uid, tipo, valor, false); }
    public void incrementar(String uid, String tipo, BigDecimal passo) { alterar(uid, tipo, passo, true); }
    private void alterar(String uid, String tipo, BigDecimal valor, boolean incremento) {
        validarTipo(tipo);
        SQLiteDatabase db = helper.getWritableDatabase(); db.beginTransaction();
        try {
            long userId; BigDecimal current = null;
            try (Cursor c = db.rawQuery("SELECT u.id,m.valor_meta FROM usuarios u LEFT JOIN metas_consumo m ON m.usuario_id=u.id AND m.tipo_recurso=? WHERE u.firebase_uid=?", new String[]{tipo, uid})) {
                if (!c.moveToFirst()) throw new IllegalStateException("Perfil local não encontrado");
                userId = c.getLong(0); if (!c.isNull(1)) current = BigDecimal.valueOf(c.getDouble(1));
            }
            BigDecimal novo = incremento ? (current == null ? BigDecimal.ZERO : current).add(valor) : valor;
            if (novo.signum() <= 0 || novo.stripTrailingZeros().scale() > 3 || novo.compareTo(new BigDecimal("1000000000")) > 0 || !Double.isFinite(novo.doubleValue())) throw new IllegalArgumentException("Informe uma meta maior que zero, até 1 bilhão e com até três casas decimais.");
            ContentValues values = new ContentValues(); values.put("valor_meta", novo.doubleValue());
            if (db.update("metas_consumo", values, "usuario_id=? AND tipo_recurso=?", new String[]{String.valueOf(userId), tipo}) == 0) {
                values.put("usuario_id", userId); values.put("tipo_recurso", tipo); values.put("uuid", UUID.randomUUID().toString());
                db.insertOrThrow("metas_consumo", null, values);
            }
            AlertaService.verificarMetas(db, uid);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    private void validarTipo(String tipo) { if (!"AGUA".equals(tipo) && !"ENERGIA".equals(tipo)) throw new IllegalArgumentException("Tipo inválido"); }
}
