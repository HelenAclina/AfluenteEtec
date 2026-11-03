package com.example.afluente.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.example.afluente.model.RelatorioAnual;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

/** Gera e atualiza um resumo por usuário e ano na mesma transação da consulta. */
public class RelatorioAnualDAO {
    private final DatabaseHelper helper;
    private final RelatorioDAO consumoDao;
    public RelatorioAnualDAO(DatabaseHelper helper, RelatorioDAO consumoDao) { this.helper = helper; this.consumoDao = consumoDao; }

    public RelatorioDAO.Resultado gerar(String uid, int ano) {
        SQLiteDatabase db = helper.getWritableDatabase(); db.beginTransaction();
        try {
            RelatorioDAO.Resultado data = consumoDao.consultarAno(uid, ano, db);
            long userId = -1;
            try (Cursor c = db.rawQuery("SELECT id FROM usuarios WHERE firebase_uid=?", new String[]{uid})) {
                if (c.moveToFirst()) userId = c.getLong(0);
            }
            if (userId >= 0) salvar(db, userId, ano, data);
            db.setTransactionSuccessful();
            return data;
        } finally { db.endTransaction(); }
    }

    /** Recalcula snapshots existentes após uma alteração de consumo; usa a transação do chamador. */
    public void atualizarExistentes(SQLiteDatabase db, String uid, String... datas) {
        java.util.Set<Integer> afetados = new java.util.HashSet<>();
        for (String data : datas) {
            if (data == null || !data.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) continue;
            int ano = Integer.parseInt(data.substring(0, 4));
            if (ano >= 1 && ano <= 9999) afetados.add(ano);
        }
        if (afetados.isEmpty()) return;
        List<Integer> anos = new ArrayList<>();
        long userId = -1;
        try (Cursor c = db.rawQuery("SELECT u.id,r.periodo FROM relatorios r JOIN usuarios u ON u.id=r.usuario_id WHERE u.firebase_uid=? AND r.tipo_relatorio='ANUAL'", new String[]{uid})) {
            while (c.moveToNext()) {
                userId = c.getLong(0);
                try {
                    int ano = Integer.parseInt(c.getString(1));
                    if (afetados.contains(ano)) anos.add(ano);
                } catch (NumberFormatException ignored) { /* Ignora período legado inválido. */ }
            }
        }
        for (int ano : anos) salvar(db, userId, ano, consumoDao.consultarAno(uid, ano, db));
    }

    private void salvar(SQLiteDatabase db, long userId, int ano, RelatorioDAO.Resultado data) {
        double water = 0, energy = 0, cost = 0;
        int waterCount = 0, energyCount = 0;
        boolean anyRecords = false, incomplete = false;
        RelatorioDAO.Mes waterMax = null, waterMin = null, energyMax = null, energyMin = null;
        for (RelatorioDAO.Mes m : data.meses) {
            if (m.temAgua) {
                water += m.agua; waterCount++;
                if (waterMax == null || m.agua > waterMax.agua) waterMax = m;
                if (waterMin == null || m.agua < waterMin.agua) waterMin = m;
            }
            if (m.temEnergia) {
                energy += m.energia; energyCount++;
                if (energyMax == null || m.energia > energyMax.energia) energyMax = m;
                if (energyMin == null || m.energia < energyMin.energia) energyMin = m;
            }
            if (m.temRegistros) { anyRecords = true; incomplete |= m.custoIncompleto; cost += m.custo; }
        }
        RelatorioAnual relatorio = new RelatorioAnual();
        relatorio.usuarioId = userId;
        relatorio.periodo = String.format(Locale.ROOT, "%04d", ano);
        relatorio.dataGeracao = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date());
        relatorio.totalAgua = waterCount == 0 ? null : water;
        relatorio.totalEnergia = energyCount == 0 ? null : energy;
        relatorio.totalFinanceiro = !anyRecords || incomplete ? null : cost;
        try {
            JSONObject max = new JSONObject(), min = new JSONObject(), average = new JSONObject();
            putPeak(max, "agua", waterMax, true); putPeak(max, "energia", energyMax, false);
            putPeak(min, "agua", waterMin, true); putPeak(min, "energia", energyMin, false);
            if (waterCount > 0) average.put("agua", water / waterCount);
            if (energyCount > 0) average.put("energia", energy / energyCount);
            relatorio.maiorConsumo = max.toString(); relatorio.menorConsumo = min.toString();
            relatorio.mediaAnual = average.toString();
        } catch (JSONException e) { throw new IllegalStateException("Resumo anual inválido", e); }
        String[] args = {String.valueOf(relatorio.usuarioId), relatorio.tipoRelatorio, relatorio.periodo};
        try (Cursor c = db.rawQuery("SELECT id,uuid FROM relatorios WHERE usuario_id=? AND tipo_relatorio=? AND periodo=?", args)) {
            if (c.moveToFirst()) { relatorio.id = c.getLong(0); relatorio.uuid = c.getString(1); }
            else relatorio.uuid = UUID.randomUUID().toString();
        }
        ContentValues values = new ContentValues();
        values.put("data_geracao", relatorio.dataGeracao);
        if (relatorio.totalAgua == null) values.putNull("total_agua"); else values.put("total_agua", relatorio.totalAgua);
        if (relatorio.totalEnergia == null) values.putNull("total_energia"); else values.put("total_energia", relatorio.totalEnergia);
        if (relatorio.totalFinanceiro == null) values.putNull("total_financeiro"); else values.put("total_financeiro", relatorio.totalFinanceiro);
        values.put("maior_consumo", relatorio.maiorConsumo); values.put("menor_consumo", relatorio.menorConsumo);
        values.put("media_anual", relatorio.mediaAnual);
        if (relatorio.id != 0) db.update("relatorios", values, "id=?", new String[]{String.valueOf(relatorio.id)});
        else {
            values.put("uuid", relatorio.uuid); values.put("usuario_id", relatorio.usuarioId);
            values.put("tipo_relatorio", relatorio.tipoRelatorio); values.put("periodo", relatorio.periodo);
            relatorio.id = db.insertOrThrow("relatorios", null, values);
        }
    }
    private void putPeak(JSONObject out, String key, RelatorioDAO.Mes month, boolean water) throws JSONException {
        if (month == null) return;
        JSONObject peak = new JSONObject(); peak.put("valor", water ? month.agua : month.energia); peak.put("periodo", month.periodo);
        out.put(key, peak);
    }
}
