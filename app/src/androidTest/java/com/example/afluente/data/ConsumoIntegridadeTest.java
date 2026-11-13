package com.example.afluente.data;

import static org.junit.Assert.*;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.afluente.model.RegistroConsumo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Locale;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public class ConsumoIntegridadeTest {
    private Context context;
    private String dbName;
    private DatabaseHelper helper;
    private SQLiteDatabase db;
    private RegistroConsumoDAO dao;
    private String atual, anterior;

    @Before public void setup() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        dbName = "teste_revisao_" + UUID.randomUUID() + ".db";
        helper = new DatabaseHelper(context, dbName); db = helper.getWritableDatabase(); dao = new RegistroConsumoDAO(helper);
        db.execSQL("INSERT INTO usuarios(id,firebase_uid,nome) VALUES(1,'u1','Ana'),(2,'u2','Bia')");
        db.execSQL("INSERT INTO configuracoes_usuario(usuario_id,tarifa_agua,tarifa_energia) VALUES(1,5,0.6),(2,9,2)");
        Calendar month = Calendar.getInstance(); month.set(Calendar.DAY_OF_MONTH, 1);
        atual = String.format(Locale.ROOT, "%04d-%02d-01", month.get(Calendar.YEAR), month.get(Calendar.MONTH) + 1);
        month.add(Calendar.MONTH, -1);
        anterior = String.format(Locale.ROOT, "%04d-%02d-01", month.get(Calendar.YEAR), month.get(Calendar.MONTH) + 1);
    }
    @After public void cleanup() { if (helper != null) helper.close(); if (context != null) context.deleteDatabase(dbName); }
    private long count(String sql, String... args) { try (Cursor c = db.rawQuery(sql, args)) { assertTrue(c.moveToFirst()); return c.getLong(0); } }

    @Test public void custoHistoricoAlertasEIsolamento() {
        dao.inserirRegistro("u1", "AGUA", new BigDecimal("100"), anterior, "");
        dao.inserirRegistro("u1", "AGUA", new BigDecimal("130"), atual, "");
        assertEquals(1, count("SELECT COUNT(*) FROM alertas WHERE usuario_id=1 AND tipo_alerta='AUMENTO_ANORMAL'"));
        assertEquals(0, count("SELECT COUNT(*) FROM alertas WHERE usuario_id=2"));
        new MetaDAO(helper).salvarOuAtualizarMeta("u1", "AGUA", new BigDecimal("120"));
        assertEquals(1, count("SELECT COUNT(*) FROM alertas WHERE usuario_id=1 AND tipo_alerta='META_ULTRAPASSADA'"));
        RegistroConsumo current = dao.listar("u1", "AGUA").get(0);
        assertEquals(650.0, current.custo, 0.001);
        ContentValues tariff = new ContentValues(); tariff.put("tarifa_agua", 10);
        db.update("configuracoes_usuario", tariff, "usuario_id=1", null);
        dao.atualizarRegistro("u1", current.id, "AGUA", new BigDecimal("130"), atual, "observação");
        assertEquals(650.0, dao.buscar("u1", current.id).custo, 0.001);
        assertFalse(dao.excluir("u2", current.id));
        dao.atualizarRegistro("u1", current.id, "AGUA", new BigDecimal("110"), atual, "");
        assertEquals(1100.0, dao.buscar("u1", current.id).custo, 0.001);
        assertEquals(0, count("SELECT COUNT(*) FROM alertas WHERE usuario_id=1 AND tipo_alerta IN ('AUMENTO_ANORMAL','META_ULTRAPASSADA')"));
    }
    @Test public void transacaoRejeitaValoresInvalidosSemEscrever() {
        try { dao.inserirRegistro("u1", "AGUA", new BigDecimal("1e308"), atual, ""); fail(); }
        catch (IllegalArgumentException expected) { }
        try { dao.inserirRegistro("u1", "AGUA", BigDecimal.ONE, "2026-02-30", ""); fail(); }
        catch (IllegalArgumentException expected) { }
        assertEquals(0, count("SELECT COUNT(*) FROM registros_consumo"));
    }
    @Test public void aumentoExigeBasePositivaELimiteDeTrintaPorCento() {
        db.execSQL("INSERT INTO registros_consumo(usuario_id,tipo_recurso,valor_consumo,data_registro) VALUES(1,'AGUA',0,?)", new Object[]{anterior});
        dao.inserirRegistro("u1", "AGUA", new BigDecimal("100"), atual, "");
        assertEquals(0, count("SELECT COUNT(*) FROM alertas WHERE tipo_alerta='AUMENTO_ANORMAL'"));
        db.execSQL("UPDATE registros_consumo SET valor_consumo=100 WHERE data_registro=? AND usuario_id=1", new Object[]{anterior});
        RegistroConsumo record = dao.listar("u1", "AGUA").get(0);
        dao.atualizarRegistro("u1", record.id, "AGUA", new BigDecimal("129.9"), atual, "");
        assertEquals(0, count("SELECT COUNT(*) FROM alertas WHERE tipo_alerta='AUMENTO_ANORMAL'"));
        dao.atualizarRegistro("u1", record.id, "AGUA", new BigDecimal("130"), atual, "");
        assertEquals(1, count("SELECT COUNT(*) FROM alertas WHERE tipo_alerta='AUMENTO_ANORMAL'"));
        dao.atualizarRegistro("u1", record.id, "AGUA", new BigDecimal("140"), atual, "");
        assertEquals(1, count("SELECT COUNT(*) FROM alertas WHERE tipo_alerta='AUMENTO_ANORMAL'"));
        assertTrue(dao.excluir("u1", record.id));
        assertEquals(0, count("SELECT COUNT(*) FROM alertas WHERE tipo_alerta='AUMENTO_ANORMAL'"));
    }
    @Test public void relatorioSalvoAtualizaNaMesmaTransacao() {
        int year = Calendar.getInstance().get(Calendar.YEAR);
        new RelatorioAnualDAO(helper, new RelatorioDAO(helper)).gerar("u1", year);
        dao.inserirRegistro("u1", "AGUA", new BigDecimal("3"), atual, "");
        try (Cursor c = db.rawQuery("SELECT total_agua,total_financeiro FROM relatorios WHERE usuario_id=1 AND periodo=?", new String[]{String.valueOf(year)})) {
            assertTrue(c.moveToFirst()); assertEquals(3.0, c.getDouble(0), 0.001); assertEquals(15.0, c.getDouble(1), 0.001);
        }
    }
    @Test public void tarifaAusentePreservaConsumoSemInventarCusto() {
        db.execSQL("INSERT INTO usuarios(id,firebase_uid,nome) VALUES(3,'u3','Caio')");
        assertFalse(dao.inserirRegistro("u3", "AGUA", BigDecimal.ONE, atual, ""));
        assertNull(dao.listar("u3", "AGUA").get(0).custo);
    }
    @Test public void exclusaoUsuarioFazCascata() {
        dao.inserirRegistro("u1", "AGUA", BigDecimal.ONE, atual, "");
        db.delete("usuarios", "id=1", null);
        assertEquals(0, count("SELECT COUNT(*) FROM registros_consumo WHERE usuario_id=1"));
        assertEquals(0, count("SELECT COUNT(*) FROM configuracoes_usuario WHERE usuario_id=1"));
        assertEquals(1, count("SELECT COUNT(*) FROM usuarios WHERE id=2"));
    }
}
