package com.trianguloy.urlchecker.modules.companions;

import android.content.Context;
import android.util.AtomicFile;
import android.util.Log;

import com.trianguloy.urlchecker.modules.list.LogModule;
import com.trianguloy.urlchecker.utilities.generics.GenericPref;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Ordered disk logging. No growing log strings or disk work on the UI thread. */
public final class LogStore {
    public static final String FILE = "url-log.txt";
    public static final Object LOCK = new Object();
    private static final int PREVIEW_BYTES = 64 * 1024;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    private LogStore() {}

    public interface Result<T> { void accept(T value, Exception error); }
    private interface Work<T> { T run(Context context) throws Exception; }

    private static <T> void submit(Context context, Work<T> work, Result<T> result) {
        Context app = context.getApplicationContext();
        WORKER.execute(() -> {
            T value = null;
            Exception error = null;
            try {
                synchronized (LOCK) {
                    migrate(app);
                    value = work.run(app);
                }
            } catch (Exception e) {
                Log.e("LogStore", "Log operation failed", e);
                error = e;
            }
            if (result != null) result.accept(value, error);
        });
    }

    /** Export legacy text atomically before removing it from shared preferences. */
    private static void migrate(Context context) throws IOException {
        File base = context.getFileStreamPath(FILE);
        if (base.exists() || new File(base + ".bak").exists()) {
            try (var ignored = new AtomicFile(base).openRead()) {}
        }
        var prefs = GenericPref.getPrefs(context);
        if (!prefs.contains(LogModule.PREF)) return;
        String legacy = prefs.getString(LogModule.PREF, "");
        write(context, legacy);
        // If commit fails, do not append: retrying migration must not erase new entries.
        if (!prefs.edit().remove(LogModule.PREF).commit()) {
            throw new IOException("Unable to finish legacy log migration");
        }
    }

    public static void append(Context context, String line) {
        submit(context, app -> {
            File file = app.getFileStreamPath(FILE);
            try (var out = new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8)) {
                if (file.length() != 0) out.write('\n');
                out.write(line.replace("\n", "%0A"));
            }
            int limit = LogModule.LOG_LIMIT(app).get();
            if (limit > 0) trim(app, limit);
            return null;
        }, null);
    }

    public static final class Preview {
        public final String text;
        public final boolean truncated;
        private final long length;
        private final long modified;

        private Preview(String text, boolean truncated, File file) {
            this.text = text;
            this.truncated = truncated;
            length = file.length();
            modified = file.lastModified();
        }
    }

    public static void preview(Context context, Result<Preview> result) {
        submit(context, app -> {
            File file = app.getFileStreamPath(FILE);
            // Recover an interrupted atomic replacement, if any.
            if (file.exists() || new File(file + ".bak").exists()) {
                try (var ignored = new AtomicFile(file).openRead()) {}
            }
            if (!file.exists()) return new Preview("", false, file);
            try (var in = new RandomAccessFile(file, "r")) {
                long offset = Math.max(0, in.length() - PREVIEW_BYTES);
                in.seek(offset);
                byte[] bytes = new byte[(int) (in.length() - offset)];
                in.readFully(bytes);
                int start = 0;
                // Do not start in the middle of a UTF-8 character.
                while (start < bytes.length && (bytes[start] & 0xc0) == 0x80) start++;
                return new Preview(new String(bytes, start, bytes.length - start, StandardCharsets.UTF_8), offset > 0, file);
            }
        }, result);
    }

    /** Refuse to overwrite unseen or newly appended records when editing. */
    public static void replace(Context context, Preview original, String text, Result<Void> result) {
        submit(context, app -> {
            File file = app.getFileStreamPath(FILE);
            if (original.truncated || file.length() != original.length || file.lastModified() != original.modified) {
                throw new IOException("Log changed; reopen it before editing");
            }
            write(app, text);
            return null;
        }, result);
    }

    public static void clear(Context context, Result<Void> result) {
        submit(context, app -> { write(app, ""); return null; }, result);
    }

    private static void write(Context context, String text) throws IOException {
        AtomicFile file = new AtomicFile(context.getFileStreamPath(FILE));
        FileOutputStream stream = null;
        try {
            stream = file.startWrite();
            var writer = new OutputStreamWriter(stream, StandardCharsets.UTF_8);
            // Stream the string through a fixed buffer; never allocate a whole-log byte[].
            for (int offset = 0; offset < text.length(); offset += 4096) {
                writer.write(text, offset, Math.min(4096, text.length() - offset));
            }
            writer.flush();
            file.finishWrite(stream);
        } catch (IOException e) {
            if (stream != null) file.failWrite(stream);
            throw e;
        }
    }

    /** Retain the configured number of lines using bounded buffers. */
    private static void trim(Context context, int limit) throws IOException {
        File base = context.getFileStreamPath(FILE);
        long start = 0;
        try (var in = new RandomAccessFile(base, "r")) {
            byte[] block = new byte[8192];
            long end = in.length();
            int lines = 0;
            search: while (end > 0) {
                int count = (int) Math.min(block.length, end);
                long offset = end - count;
                in.seek(offset);
                in.readFully(block, 0, count);
                for (int i = count - 1; i >= 0; i--) {
                    if (block[i] == '\n' && ++lines == limit) {
                        start = offset + i + 1;
                        break search;
                    }
                }
                end = offset;
            }
            if (start == 0) return;
            in.seek(start);
            AtomicFile atomic = new AtomicFile(base);
            FileOutputStream out = null;
            try {
                out = atomic.startWrite();
                int count;
                while ((count = in.read(block)) != -1) out.write(block, 0, count);
                atomic.finishWrite(out);
            } catch (IOException e) {
                if (out != null) atomic.failWrite(out);
                throw e;
            }
        }
    }
}
