package com.nrcracker.air;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;

/** Utilidades de extraccion del runtime aircrack-ng y lectura de logs. */
final class Engine {
    private static final String TAG = "NRAirCrack";

    private Engine() {
    }

    static void log(String s) {
        Log.i(TAG, s);
    }

    static boolean prepareRuntime(Context ctx, File dir) {
        File marker = new File(dir, ".air_ready_v4");
        if (marker.exists() && runtimeOk(dir)) {
            return true;
        }
        try {
            deleteRecursive(dir);
            dir.mkdirs();
            copyAssetTree(ctx.getAssets(), "air", dir);
            new File(dir, "lib").mkdirs();
            makeExecutable(new File(dir, "bin"));
            marker.createNewFile();
            log("runtime aircrack extraido en " + dir);
            return runtimeOk(dir);
        } catch (Throwable t) {
            log("extraccion runtime fallo: " + t);
            return false;
        }
    }

    private static final String[] REQUIRED = {
            "bin/aircrack-ng",
            "lib/libaircrack-osdep-1.7.0.so",
            "lib/libaircrack-ce-wpa.so",
            "lib/libaircrack-ce-wpa-arm-neon.so",
            "lib/libcrypto.so.3",
            "lib/libssl.so.3",
            "lib/libsqlite3.so",
            "lib/libc++_shared.so"
    };

    private static boolean runtimeOk(File dir) {
        for (String p : REQUIRED) {
            File f = new File(dir, p);
            if (!f.isFile() || f.length() == 0) {
                log("runtime incompleto: falta " + p);
                return false;
            }
        }
        return true;
    }

    static void deleteRecursive(File f) {
        if (f == null || !f.exists()) {
            return;
        }
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) {
                deleteRecursive(k);
            }
        }
        f.delete();
    }

    private static void copyAssetTree(AssetManager am, String path, File dest) throws Exception {
        String[] list = am.list(path);
        if (list == null || list.length == 0) {
            InputStream in = am.open(path);
            OutputStream out = new FileOutputStream(dest);
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.close();
            in.close();
        } else {
            dest.mkdirs();
            for (String name : list) {
                copyAssetTree(am, path + "/" + name, new File(dest, name));
            }
        }
    }

    static void makeExecutable(File root) {
        try {
            Runtime.getRuntime().exec(new String[]{"chmod", "-R", "755", root.getAbsolutePath()}).waitFor();
        } catch (Throwable ignored) {
        }
        File[] kids = root.listFiles();
        if (kids != null) {
            for (File f : kids) {
                if (f.isDirectory()) {
                    makeExecutable(f);
                } else if (f.getName().equals("aircrack-ng") || f.getName().endsWith(".so")) {
                    f.setExecutable(true, false);
                    f.setReadable(true, false);
                }
            }
        }
    }

    static void copy(File src, File dst) throws IOException {
        FileInputStream in = new FileInputStream(src);
        FileOutputStream out = new FileOutputStream(dst);
        try {
            java.nio.channels.FileChannel ic = in.getChannel();
            java.nio.channels.FileChannel oc = out.getChannel();
            long pos = 0;
            long size = ic.size();
            while (pos < size) {
                long n = ic.transferTo(pos, size - pos, oc);
                if (n <= 0) {
                    break;
                }
                pos += n;
            }
        } finally {
            try { out.close(); } catch (Throwable ignored) {}
            try { in.close(); } catch (Throwable ignored) {}
        }
    }

    /**
     * Copia src a dst saltando las primeras 'skip' lineas. Devuelve los bytes
     * escritos (0 si no queda nada). Se usa para reanudar un crack.
     */
    static long copyFromLine(File src, File dst, long skip) throws IOException {
        InputStream in = new java.io.BufferedInputStream(new FileInputStream(src), 1 << 20);
        OutputStream out = new java.io.BufferedOutputStream(new FileOutputStream(dst), 1 << 20);
        long written = 0;
        long seen = 0;
        boolean writing = skip <= 0;
        byte[] buf = new byte[1 << 20];
        try {
            int n;
            while ((n = in.read(buf)) > 0) {
                int off = 0;
                if (!writing) {
                    for (int i = 0; i < n; i++) {
                        if (buf[i] == '\n') {
                            seen++;
                            if (seen >= skip) {
                                off = i + 1;
                                writing = true;
                                break;
                            }
                        }
                    }
                    if (!writing) {
                        continue;
                    }
                }
                out.write(buf, off, n - off);
                written += n - off;
            }
        } finally {
            try { out.close(); } catch (Throwable ignored) {}
            try { in.close(); } catch (Throwable ignored) {}
        }
        return written;
    }

    static String tail(File f, int max) {
        try {
            if (!f.exists()) {
                return "";
            }
            RandomAccessFile raf = new RandomAccessFile(f, "r");
            long len = raf.length();
            long start = Math.max(0, len - max);
            raf.seek(start);
            byte[] buf = new byte[(int) (len - start)];
            raf.readFully(buf);
            raf.close();
            return new String(buf);
        } catch (Throwable t) {
            return "";
        }
    }

    static String firstLine(File f) {
        try {
            if (!f.exists()) {
                return null;
            }
            BufferedReader r = new BufferedReader(new FileReader(f));
            String line = r.readLine();
            r.close();
            if (line == null) {
                return null;
            }
            line = line.trim();
            return line.isEmpty() ? null : line;
        } catch (Throwable t) {
            return null;
        }
    }
}
