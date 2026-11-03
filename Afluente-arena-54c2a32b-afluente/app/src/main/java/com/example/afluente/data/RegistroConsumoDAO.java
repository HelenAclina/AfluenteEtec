package com.example.afluente.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.afluente.model.RegistroConsumo;
import com.example.afluente.service.AlertaService;
import com.example.afluente.service.ConsumoService;
import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;
import java.util.UUID;

/** Insere o consumo e o custo com a tarifa vigente na mesma transação. */
public class RegistroConsumoDAO {
    private final DatabaseHelper helper;
    public RegistroConsumoDAO(DatabaseHelper helper) { this.helper = helper; }

    /** Retorna false quando a tarifa não está configurada; o custo fica NULL. */
    public boolean inserirRegistro(String uid, String tipo, BigDecimal consumo, String data, String observacao) {
        ConsumoService.validar(uid, tipo, consumo, data);
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            long usuarioId;
            Double tarifa = null;
            try (Cursor c = db.rawQuery("SELECT u.id,c.tarifa_agua,c.tarifa_energia FROM usuarios u LEFT JOIN configuracoes_usuario c ON c.usuario_id=u.id WHERE u.firebase_uid=?", new String[]{uid})) {
                if (!c.moveToFirst()) throw new IllegalStateException("Perfil local não encontrado");
                usuarioId = c.getLong(0);
                int column = "AGUA".equals(tipo) ? 1 : 2;
                if (!c.isNull(column)) {
                    tarifa = c.getDouble(column);
                }
            }
            ContentValues values = new ContentValues();
            values.put("uuid", UUID.randomUUID().toString());
            values.put("usuario_id", usuarioId);
            values.put("tipo_recurso", tipo);
            values.put("valor_consumo", consumo.doubleValue());
            values.put("unidade", "AGUA".equals(tipo) ? "m³" : "kWh");
            values.put("data_registro", data);
            if (observacao == null || observacao.isEmpty()) values.putNull("observacao");
            else values.put("observacao", observacao);
            Double custo = ConsumoService.calcularCusto(consumo, tarifa);
            if (custo == null) values.putNull("valor_financeiro"); else values.put("valor_financeiro", custo);
            db.insertOrThrow("registros_consumo", null, values);
            atualizarRelatorios(db, uid, data);
            AlertaService.verificarMetas(db, uid);
            db.setTransactionSuccessful();
            return custo != null;
        } finally { db.endTransaction(); }
    }
    // Mantém os relatórios anuais já gerados coerentes com os lançamentos, na mesma transação.
    private void atualizarRelatorios(SQLiteDatabase db, String uid, String... datas) {
        new RelatorioAnualDAO(helper, new RelatorioDAO(helper)).atualizarExistentes(db, uid, datas);
    }

    public List<RegistroConsumo> listar(String uid, String tipo) {
        if (tipo != null && !"AGUA".equals(tipo) && !"ENERGIA".equals(tipo)) throw new IllegalArgumentException("Tipo inválido");
        String sql = "SELECT r.id,r.tipo_recurso,r.valor_consumo,r.valor_financeiro,r.data_registro,r.observacao " +
                "FROM registros_consumo r JOIN usuarios u ON u.id=r.usuario_id WHERE u.firebase_uid=?" +
                (tipo == null ? "" : " AND r.tipo_recurso=?") + " ORDER BY r.data_registro DESC,r.id DESC";
        String[] args = tipo == null ? new String[]{uid} : new String[]{uid, tipo};
        List<RegistroConsumo> result = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().rawQuery(sql, args)) {
            while (c.moveToNext()) result.add(fromCursor(c));
        }
        return result;
    }

    public RegistroConsumo buscar(String uid, long id) {
        String sql = "SELECT r.id,r.tipo_recurso,r.valor_consumo,r.valor_financeiro,r.data_registro,r.observacao " +
                "FROM registros_consumo r JOIN usuarios u ON u.id=r.usuario_id WHERE u.firebase_uid=? AND r.id=?";
        try (Cursor c = helper.getReadableDatabase().rawQuery(sql, new String[]{uid, String.valueOf(id)})) {
            return c.moveToFirst() ? fromCursor(c) : null;
        }
    }

    private RegistroConsumo fromCursor(Cursor c) {
        return new RegistroConsumo(c.getLong(0), c.getString(1), c.getDouble(2),
                c.isNull(3) ? null : c.getDouble(3), c.getString(4), c.getString(5));
    }

    public boolean excluir(String uid, long id) {
        SQLiteDatabase db = helper.getWritableDatabase(); db.beginTransaction();
        try {
            String oldDate = null;
            try (Cursor c = db.rawQuery("SELECT r.data_registro FROM registros_consumo r JOIN usuarios u ON u.id=r.usuario_id WHERE r.id=? AND u.firebase_uid=?", new String[]{String.valueOf(id), uid})) {
                if (c.moveToFirst()) oldDate = c.getString(0);
            }
            boolean removed = db.delete("registros_consumo", "id=? AND usuario_id IN (SELECT id FROM usuarios WHERE firebase_uid=?)", new String[]{String.valueOf(id), uid}) == 1;
            if (removed) { atualizarRelatorios(db, uid, oldDate); AlertaService.verificarMetas(db, uid); }
            db.setTransactionSuccessful(); return removed;
        } finally { db.endTransaction(); }
    }

    /** Mantém o custo original se somente data/observação forem alteradas. */
    public boolean atualizarRegistro(String uid, long id, String tipo, BigDecimal consumo, String data, String observacao) {
        ConsumoService.validar(uid, tipo, consumo, data);
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            String sql = "SELECT r.tipo_recurso,r.valor_consumo,r.valor_financeiro,c.tarifa_agua,c.tarifa_energia,r.data_registro FROM registros_consumo r " +
                    "JOIN usuarios u ON u.id=r.usuario_id LEFT JOIN configuracoes_usuario c ON c.usuario_id=u.id WHERE r.id=? AND u.firebase_uid=?";
            Double custo;
            String oldDate;
            try (Cursor c = db.rawQuery(sql, new String[]{String.valueOf(id), uid})) {
                if (!c.moveToFirst()) throw new IllegalStateException("Registro não encontrado");
                oldDate = c.getString(5);
                if (tipo.equals(c.getString(0)) && consumo.compareTo(BigDecimal.valueOf(c.getDouble(1))) == 0) {
                    custo = c.isNull(2) ? null : c.getDouble(2);
                } else {
                    int col = "AGUA".equals(tipo) ? 3 : 4;
                    custo = ConsumoService.calcularCusto(consumo, c.isNull(col) ? null : c.getDouble(col));
                }
            }
            ContentValues values = new ContentValues();
            values.put("tipo_recurso", tipo); values.put("valor_consumo", consumo.doubleValue());
            values.put("unidade", "AGUA".equals(tipo) ? "m³" : "kWh"); values.put("data_registro", data);
            if (observacao == null || observacao.isEmpty()) values.putNull("observacao"); else values.put("observacao", observacao);
            if (custo == null) values.putNull("valor_financeiro"); else values.put("valor_financeiro", custo);
            int rows = db.update("registros_consumo", values, "id=? AND usuario_id IN (SELECT id FROM usuarios WHERE firebase_uid=?)", new String[]{String.valueOf(id), uid});
            if (rows != 1) throw new IllegalStateException("Registro não encontrado");
            atualizarRelatorios(db, uid, oldDate, data);
            AlertaService.verificarMetas(db, uid);
            db.setTransactionSuccessful();
            return custo != null;
        } finally { db.endTransaction(); }
    }

}
