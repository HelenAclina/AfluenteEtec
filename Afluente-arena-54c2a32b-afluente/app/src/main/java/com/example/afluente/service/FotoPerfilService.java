package com.example.afluente.service;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.example.afluente.data.DatabaseHelper;
import com.example.afluente.data.PerfilDAO;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Cópia privada e decodificação limitada. Operações de escrita sobrevivem à recriação da tela. */
public final class FotoPerfilService {
    public interface Listener { void aoConcluir(boolean sucesso); }
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    // Observadores e busy são acessados exclusivamente na thread principal.
    private static final Map<String, List<Listener>> LISTENERS = new HashMap<>();
    private static final Set<String> BUSY = new HashSet<>();
    private static final long MAX_BYTES = 25L * 1024 * 1024;
    private FotoPerfilService() { }

    public static String iniciais(String nome) {
        String display = nome == null || nome.trim().isEmpty() ? "Usuário" : nome.trim();
        String[] parts = display.split("\\s+");
        String first = parts[0].substring(0, parts[0].offsetByCodePoints(0, 1));
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, parts[parts.length - 1].offsetByCodePoints(0, 1)) : "";
        return (first + last).toUpperCase(new java.util.Locale("pt", "BR"));
    }

    public static void observar(String uid, Listener listener) {
        List<Listener> list = LISTENERS.get(uid);
        if (list == null) { list = new ArrayList<>(); LISTENERS.put(uid, list); }
        list.add(listener);
    }
    public static void removerObservador(String uid, Listener listener) {
        List<Listener> list = LISTENERS.get(uid);
        if (list != null) { list.remove(listener); if (list.isEmpty()) LISTENERS.remove(uid); }
    }
    public static boolean ocupado(String uid) { return BUSY.contains(uid); }

    public static void salvar(Context context, String uid, String nome, Uri source) {
        if (BUSY.contains(uid)) return;
        BUSY.add(uid);
        Context app = context.getApplicationContext();
        WRITER.execute(() -> {
            boolean success = false;
            File temp = null, destination = null;
            DatabaseHelper helper = null;
            try {
                temp = File.createTempFile("foto-", ".tmp", app.getCacheDir());
                try (InputStream input = app.getContentResolver().openInputStream(source);
                     FileOutputStream output = new FileOutputStream(temp)) {
                    if (input == null) throw new IOException("Imagem indisponível");
                    byte[] buffer = new byte[16384]; long total = 0; int count;
                    while ((count = input.read(buffer)) != -1) {
                        total += count;
                        if (total > MAX_BYTES) throw new IOException("Imagem muito grande");
                        output.write(buffer, 0, count);
                    }
                }
                Bitmap bitmap = decode(temp, 1024);
                if (bitmap == null) throw new IOException("Formato de imagem inválido");
                Bitmap oriented = bitmap;
                try {
                    Matrix transform = orientation(temp);
                    if (!transform.isIdentity()) oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), transform, true);
                    File dir = new File(app.getFilesDir(), "profile_photos");
                    if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Diretório indisponível");
                    destination = File.createTempFile("avatar-", ".png", dir);
                    try (FileOutputStream output = new FileOutputStream(destination)) {
                        if (!oriented.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new IOException("Falha ao salvar imagem");
                        output.getFD().sync();
                    }
                } finally {
                    if (oriented != bitmap) oriented.recycle();
                    bitmap.recycle();
                }
                FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
                if (current == null || !uid.equals(current.getUid())) throw new IOException("Conta alterada");
                helper = new DatabaseHelper(app);
                String relativePath = "profile_photos/" + destination.getName();
                String previous = new PerfilDAO(helper).atualizarFotoPerfil(uid, nome, relativePath);
                success = true;
                File old = privatePhoto(app, previous);
                if (old != null && !old.equals(destination)) old.delete();
            } catch (Exception | OutOfMemoryError ignored) {
                // A cópia incompleta não deve substituir a foto já salva.
            } finally {
                if (helper != null) helper.close();
                if (temp != null) temp.delete();
                if (!success && destination != null) destination.delete();
            }
            final boolean result = success;
            MAIN.post(() -> {
                BUSY.remove(uid);
                List<Listener> listeners = LISTENERS.get(uid);
                if (listeners != null) for (Listener l : new ArrayList<>(listeners)) l.aoConcluir(result);
            });
        });
    }

    /** Retorna null para caminho inválido, foto ausente ou formato não suportado. */
    public static Bitmap carregar(Context context, String path, int targetSize) {
        File file = privatePhoto(context, path);
        if (file == null || !file.isFile()) return null;
        try { return decode(file, targetSize); }
        catch (RuntimeException | OutOfMemoryError e) { return null; }
    }
    private static File privatePhoto(Context context, String path) {
        if (path == null || path.isEmpty()) return null;
        try {
            File dir = new File(context.getFilesDir(), "profile_photos").getCanonicalFile();
            File file = new File(context.getFilesDir(), path).getCanonicalFile();
            if (!dir.equals(file.getParentFile()) || !file.getName().startsWith("avatar-") || !file.getName().endsWith(".png")) return null;
            return file;
        } catch (IOException e) { return null; }
    }
    private static Bitmap decode(File file, int target) {
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (options.outWidth <= 0 || options.outHeight <= 0 || target <= 0) return null;
        int sample = 1; int dimension = Math.max(options.outWidth, options.outHeight);
        while ((long) dimension / sample > target && sample < (1 << 29)) sample *= 2;
        BitmapFactory.Options decode = new BitmapFactory.Options(); decode.inSampleSize = sample;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), decode);
    }
    private static Matrix orientation(File image) {
        Matrix matrix = new Matrix();
        try {
            ExifInterface exif = new ExifInterface(image.getAbsolutePath());
            switch (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: matrix.setScale(-1, 1); break;
                case ExifInterface.ORIENTATION_ROTATE_180: matrix.setRotate(180); break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL: matrix.setScale(1, -1); break;
                case ExifInterface.ORIENTATION_TRANSPOSE: matrix.setRotate(90); matrix.postScale(-1, 1); break;
                case ExifInterface.ORIENTATION_ROTATE_90: matrix.setRotate(90); break;
                case ExifInterface.ORIENTATION_TRANSVERSE: matrix.setRotate(-90); matrix.postScale(-1, 1); break;
                case ExifInterface.ORIENTATION_ROTATE_270: matrix.setRotate(-90); break;
                default: break;
            }
        } catch (IOException | RuntimeException ignored) { /* Alguns formatos não possuem EXIF. */ }
        return matrix;
    }
}
