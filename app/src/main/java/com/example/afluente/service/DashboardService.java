package com.example.afluente.service;

import com.example.afluente.data.DashboardDAO;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.os.Handler;
import android.os.Looper;

/** Carrega SQLite fora da thread de interface. */
public class DashboardService implements AutoCloseable {
    public interface Callback { void onSuccess(DashboardDAO.Snapshot data); void onError(); }
    private final DashboardDAO dao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean closed;
    private int sequence;
    public DashboardService(DashboardDAO dao) { this.dao = dao; }
    public void load(String uid, Callback callback) {
        if (closed) return;
        final int request = ++sequence;
        executor.execute(() -> {
            try {
                DashboardDAO.Snapshot data = dao.load(uid);
                main.post(() -> { if (!closed && request == sequence) callback.onSuccess(data); });
            } catch (RuntimeException ex) {
                main.post(() -> { if (!closed && request == sequence) callback.onError(); });
            }
        });
    }
    @Override public void close() { if (closed) return; closed = true; executor.execute(dao::close); executor.shutdown(); }
}
