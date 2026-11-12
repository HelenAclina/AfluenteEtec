package com.example.afluente.service;

import android.os.Handler;
import android.os.Looper;
import com.example.afluente.data.RelatorioDAO;
import com.example.afluente.data.RelatorioAnualDAO;
import java.util.Calendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RelatorioService implements AutoCloseable {
    public interface Callback { void onSuccess(RelatorioDAO.Resultado data); void onError(); }
    private final RelatorioDAO dao;
    private final RelatorioAnualDAO anualDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean closed;
    private int sequence;
    public RelatorioService(RelatorioDAO dao, RelatorioAnualDAO anualDao) { this.dao = dao; this.anualDao = anualDao; }
    public void carregar(String uid, int months, Callback callback) {
        final int request = ++sequence;
        executor.execute(() -> {
            try {
                RelatorioDAO.Resultado result = months == 12
                        ? anualDao.gerar(uid, Calendar.getInstance().get(Calendar.YEAR)) : dao.consultar(uid, months);
                main.post(() -> { if (!closed && request == sequence) callback.onSuccess(result); });
            } catch (RuntimeException ex) {
                main.post(() -> { if (!closed && request == sequence) callback.onError(); });
            }
        });
    }
    @Override public void close() { closed = true; executor.execute(dao::close); executor.shutdown(); }
}
