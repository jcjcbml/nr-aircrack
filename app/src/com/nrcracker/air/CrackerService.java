package com.nrcracker.air;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servicio en primer plano que ejecuta aircrack-ng sobre un pcap.
 * Aircrack-ng lee el pcap directamente, usa todos los hilos (-p) y ahora
 * reanuda el trabajo si Android mata el proceso (job.state + words_resume).
 */
public class CrackerService extends Service {
    public static final String ACTION_START = "com.nrcracker.air.START";
    public static final String ACTION_STOP = "com.nrcracker.air.STOP";
    public static final String ACTION_SELFTEST = "com.nrcracker.air.SELFTEST";
    public static final String EXTRA_HASH = "hash";
    public static final String EXTRA_WORDLIST = "wordlist";
    public static final String EXTRA_HASH_URI = "hash_uri";
    public static final String EXTRA_WORD_URI = "wordlist_uri";
    public static final String EXTRA_SSID = "ssid";

    public interface Listener {
        void onState(String status, double speed, long tested, long total, long elapsedMs, String result);
    }

    public static volatile Listener listener;

    private static final String TAG = "NRAirCrack";
    private static final String CHANNEL = "nrcracker_air";
    private static final int NOTIF_ID = 1;
    private static final String JOB_FILE = "job.state";

    private static final Pattern P_STATS = Pattern.compile(
            "(\\d+)/(\\d+)\\s+keys tested\\s*\\(([0-9.]+)\\s+k/s\\)");
    private static final Pattern P_KEY = Pattern.compile(
            "KEY FOUND!\\s*\\[\\s*(.*?)\\s*\\]");

    private static volatile String stStatus = "Inactivo";
    private static volatile double stSpeed = 0;
    private static volatile long stTested = 0;
    private static volatile long stTotal = 0;
    private static volatile long stElapsed = 0;
    private static volatile String stResult = null;

    private final AtomicBoolean stop = new AtomicBoolean(false);
    private final AtomicBoolean finished = new AtomicBoolean(false);
    private Thread thread;
    private volatile Process proc;
    private PowerManager.WakeLock wake;
    private long startMs;
    private long lastNotif;

    public static void emitCurrent() {
        Listener l = listener;
        if (l != null) {
            l.onState(stStatus, stSpeed, stTested, stTotal, stElapsed, stResult);
        }
    }

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stop.set(true);
            kill();
            setState("Detenido", 0, stTested, stTotal, SystemClock.elapsedRealtime() - startMs, stResult);
            finish(false);
            return START_NOT_STICKY;
        }
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        String hash = null;
        String word = null;
        String hashUri = null;
        String wordUri = null;
        String ssid = null;
        if (ACTION_SELFTEST.equals(intent.getAction())) {
            try {
                File dir = getFilesDir();
                File h = new File(dir, "selftest.cap");
                File w = new File(dir, "selftest.txt");
                copyAsset("test/test.cap", h);
                copyAsset("test/words.txt", w);
                hash = h.getAbsolutePath();
                word = w.getAbsolutePath();
                Log.i(TAG, "autotest con pcap interno");
            } catch (Throwable t) {
                Log.e(TAG, "autotest assets", t);
            }
        } else {
            hash = intent.getStringExtra(EXTRA_HASH);
            word = intent.getStringExtra(EXTRA_WORDLIST);
            hashUri = intent.getStringExtra(EXTRA_HASH_URI);
            wordUri = intent.getStringExtra(EXTRA_WORD_URI);
            ssid = intent.getStringExtra(EXTRA_SSID);
        }
        if ((hash == null && hashUri == null) || (word == null && wordUri == null)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (thread != null && thread.isAlive()) {
            Log.i(TAG, "ya hay un crack en curso");
            return START_NOT_STICKY;
        }

        createChannel();
        startForeground(NOTIF_ID, notif("Preparando..."));
        acquireWake();
        stop.set(false);
        finished.set(false);
        stResult = null;
        stStatus = "Preparando...";
        stSpeed = 0;
        stTested = 0;
        stTotal = 0;
        stElapsed = 0;
        startMs = SystemClock.elapsedRealtime();
        lastNotif = 0;
        final String fh = hash;
        final String fw = word;
        final String fhu = hashUri;
        final String fwu = wordUri;
        final String fs = ssid;
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                crack(fh, fw, fhu, fwu, fs);
            }
        }, "nraircrack");
        thread.start();
        return START_REDELIVER_INTENT;
    }

    private void crack(String hashPath, String wordPath, String hashUri, String wordUri, String ssid) {
        File jobFile = null;
        File resumeFile = null;
        try {
            File files = getFilesDir();
            File air = new File(files, "air");
            setState("Preparando aircrack-ng...", 0, 0, 0, 0, null);
            if (!Engine.prepareRuntime(this, air)) {
                setState("Error: runtime aircrack incompleto", 0, 0, 0, 0, null);
                finish(false);
                return;
            }

            String key = jobKey(hashPath, hashUri, wordPath, wordUri, ssid);
            long base = 0;
            long prevTotal = 0;
            jobFile = new File(air, JOB_FILE);
            if (jobFile.isFile()) {
                String[] j = readJob(jobFile);
                if (j != null && key.equals(j[0])) {
                    base = parseLong(j[1]);
                    prevTotal = parseLong(j[2]);
                } else {
                    jobFile.delete();
                }
            }
            File oldLog = new File(air, "air.log");
            if (base > 0) {
                String oldKey = parseKey(Engine.tail(oldLog, 65536));
                if (oldKey != null) {
                    setState("Contrasena encontrada", 0, base, prevTotal, 0, oldKey);
                    jobFile.delete();
                    finish(true);
                    return;
                }
                if (prevTotal > 0 && base >= prevTotal) {
                    setState("Terminado sin resultado", 0, prevTotal, prevTotal, 0, null);
                    jobFile.delete();
                    finish(true);
                    return;
                }
            }

            setState(base > 0 ? "Reanudando desde " + base + "..." : "Copiando archivos...",
                    0, base, prevTotal, 0, null);
            File pcap = new File(air, "input.cap");
            File words = new File(air, "words.txt");
            Uri hu = hashUri != null ? Uri.parse(hashUri) : null;
            Uri wu = wordUri != null ? Uri.parse(wordUri) : null;
            long pcapSize = sizeOf(hashPath, hu);
            long wordSize = sizeOf(wordPath, wu);

            boolean pcapCached = pcapSize > 0 && pcap.isFile() && pcap.length() == pcapSize;
            boolean wordsCached = wordSize > 0 && words.isFile() && words.length() == wordSize;
            long need = 16L << 20;
            if (!pcapCached && pcapSize > 0) {
                need += pcapSize;
            }
            if (!wordsCached && wordSize > 0) {
                need += wordSize;
            }
            if (air.getUsableSpace() > 0 && air.getUsableSpace() < need) {
                throw new Exception("espacio insuficiente: faltan "
                        + ((need - air.getUsableSpace()) >> 20) + " MB");
            }

            if (!pcapCached) {
                if (hu != null) {
                    copyUri(hu, pcap);
                } else {
                    Engine.copy(new File(hashPath), pcap);
                }
            }
            if (!wordsCached) {
                if (wu != null) {
                    copyUri(wu, words);
                } else {
                    Engine.copy(new File(wordPath), words);
                }
            }

            File runWords = words;
            resumeFile = new File(air, "words_resume.txt");
            if (base > 0) {
                long n = Engine.copyFromLine(words, resumeFile, base);
                if (n <= 0) {
                    resumeFile.delete();
                    base = 0;
                } else {
                    runWords = resumeFile;
                }
            } else {
                resumeFile.delete();
            }

            File log = new File(air, "air.log");
            if (base == 0) {
                log.delete();
                prevTotal = 0;
            }
            writeJob(jobFile, key, base, prevTotal);

            setState(base > 0 ? "Reanudando aircrack-ng..." : "Iniciando aircrack-ng...",
                    0, base, prevTotal, 0, null);
            proc = launch(air, pcap, runWords, ssid);
            long start = startMs;
            long lastSave = 0;
            String pass = null;
            long tested = 0;
            long total = 0;
            double kps = 0;
            while (!stop.get() && proc.isAlive()) {
                String t = Engine.tail(log, 8192);
                long[] st = parseStats(t);
                tested = st[0];
                total = st[1];
                kps = parseSpeed(t);
                String k = parseKey(t);
                long elapsed = SystemClock.elapsedRealtime() - start;
                long shownTested = base + tested;
                long shownTotal = base + total;
                if (k != null) {
                    pass = k;
                    setState("Contrasena encontrada", 0, shownTested, shownTotal, elapsed, k);
                    break;
                }
                setState("Crackeando...", kps, shownTested, shownTotal, elapsed, null);
                long now = SystemClock.elapsedRealtime();
                if (now - lastSave >= 2000) {
                    writeJob(jobFile, key, shownTested, shownTotal);
                    lastSave = now;
                }
                Thread.sleep(500);
            }
            if (pass == null) {
                String t = Engine.tail(log, 32768);
                long[] st = parseStats(t);
                tested = st[0];
                total = st[1];
                pass = parseKey(t);
            }
            long elapsed = SystemClock.elapsedRealtime() - start;
            long shownTested = base + tested;
            long shownTotal = base + total;
            if (pass != null) {
                setState("Contrasena encontrada", 0, shownTested, shownTotal, elapsed, pass);
                kill();
                jobFile.delete();
                resumeFile.delete();
                finish(true);
            } else if (total == 0) {
                String err = lastLine(Engine.tail(log, 2000));
                setState("Error: " + err, 0, 0, 0, elapsed, null);
                jobFile.delete();
                resumeFile.delete();
                finish(false);
            } else if (stop.get()) {
                setState("Detenido", 0, shownTested, shownTotal, elapsed, null);
                jobFile.delete();
                resumeFile.delete();
                finish(false);
            } else {
                setState("Terminado sin resultado", 0, shownTested, shownTotal, elapsed, null);
                jobFile.delete();
                resumeFile.delete();
                finish(true);
            }
        } catch (Throwable t) {
            Log.e(TAG, "crack", t);
            setState("Error: " + t.getMessage(), 0, 0, 0, 0, null);
            if (jobFile != null) {
                jobFile.delete();
            }
            finish(false);
        }
    }

    private static String jobKey(String hashPath, String hashUri, String wordPath, String wordUri, String ssid) {
        String h = hashUri != null ? hashUri : String.valueOf(hashPath);
        String w = wordUri != null ? wordUri : String.valueOf(wordPath);
        String s = ssid != null ? ssid.trim() : "";
        return h + "|" + w + "|" + s;
    }

    private static String[] readJob(File f) {
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader(f));
            String k = r.readLine();
            String o = r.readLine();
            String t = r.readLine();
            if (k == null || o == null) {
                return null;
            }
            return new String[]{k, o, t != null ? t : "0"};
        } catch (Throwable t) {
            return null;
        } finally {
            if (r != null) {
                try { r.close(); } catch (Throwable ignored) {}
            }
        }
    }

    private static void writeJob(File f, String key, long offset, long total) {
        File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
        FileWriter w = null;
        try {
            w = new FileWriter(tmp);
            w.write(key + "\n" + offset + "\n" + total + "\n");
            w.close();
            w = null;
            if (!tmp.renameTo(f)) {
                f.delete();
                tmp.renameTo(f);
            }
        } catch (Throwable ignored) {
        } finally {
            if (w != null) {
                try { w.close(); } catch (Throwable ignored) {}
            }
        }
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (Throwable t) {
            return 0;
        }
    }

    private long sizeOf(String path, Uri uri) {
        try {
            if (uri != null) {
                Cursor c = getContentResolver().query(uri,
                        new String[]{OpenableColumns.SIZE}, null, null, null);
                if (c != null) {
                    try {
                        if (c.moveToFirst()) {
                            int i = c.getColumnIndex(OpenableColumns.SIZE);
                            if (i >= 0 && !c.isNull(i)) {
                                return c.getLong(i);
                            }
                        }
                    } finally {
                        c.close();
                    }
                }
            } else if (path != null) {
                return new File(path).length();
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    private Process launch(File air, File pcap, File words, String ssid) throws Exception {
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors());
        List<String> cmd = new ArrayList<String>();
        cmd.add(new File(air, "bin/aircrack-ng").getAbsolutePath());
        cmd.add("-p");
        cmd.add(String.valueOf(threads));
        cmd.add("-w");
        cmd.add(words.getAbsolutePath());
        if (ssid != null && !ssid.trim().isEmpty()) {
            cmd.add("-e");
            cmd.add(ssid.trim());
        }
        cmd.add(pcap.getAbsolutePath());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(air);
        pb.redirectErrorStream(true);
        pb.redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")));
        pb.redirectOutput(ProcessBuilder.Redirect.to(new File(air, "air.log")));
        Map<String, String> env = pb.environment();
        env.put("LD_LIBRARY_PATH", new File(air, "lib").getAbsolutePath());
        env.put("HOME", air.getAbsolutePath());
        Log.i(TAG, "aircrack-ng -p " + threads + " iniciado");
        return pb.start();
    }

    private static long[] parseStats(String s) {
        long tested = 0;
        long total = 0;
        try {
            Matcher m = P_STATS.matcher(s);
            while (m.find()) {
                tested = Long.parseLong(m.group(1));
                total = Long.parseLong(m.group(2));
            }
        } catch (Throwable ignored) {
        }
        return new long[]{tested, total};
    }

    private static double parseSpeed(String s) {
        double v = 0;
        try {
            Matcher m = P_STATS.matcher(s);
            while (m.find()) {
                v = Double.parseDouble(m.group(3));
            }
        } catch (Throwable ignored) {
        }
        return v;
    }

    private static String parseKey(String s) {
        String k = null;
        try {
            Matcher m = P_KEY.matcher(s);
            while (m.find()) {
                k = m.group(1);
            }
        } catch (Throwable ignored) {
        }
        return k;
    }

    private static String lastLine(String s) {
        if (s == null) {
            return "";
        }
        String[] lines = s.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String t = lines[i].trim();
            if (!t.isEmpty()) {
                return t;
            }
        }
        return "";
    }

    private void copyUri(Uri uri, File dst) throws Exception {
        InputStream in = getContentResolver().openInputStream(uri);
        if (in == null) {
            throw new Exception("no se pudo abrir " + uri);
        }
        OutputStream out = new FileOutputStream(dst);
        byte[] buf = new byte[1 << 20];
        int n;
        try {
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        } finally {
            try { out.close(); } catch (Throwable ignored) {}
            try { in.close(); } catch (Throwable ignored) {}
        }
    }

    private void copyAsset(String name, File dst) throws Exception {
        InputStream in = getAssets().open(name);
        OutputStream out = new FileOutputStream(dst);
        byte[] buf = new byte[1 << 16];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        out.close();
        in.close();
    }

    private void setState(String status, double speed, long tested, long total, long elapsed, String result) {
        stStatus = status;
        stSpeed = speed;
        stTested = tested;
        stTotal = total;
        stElapsed = elapsed;
        stResult = result;
        Listener l = listener;
        if (l != null) {
            l.onState(status, speed, tested, total, elapsed, result);
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastNotif >= 1000 || result != null || !"Crackeando...".equals(status)) {
            lastNotif = now;
            try {
                String text = loc(status);
                if (speed > 0) {
                    text = String.format(Locale.US, "%.0f keys/s · %s", speed, loc(status));
                }
                if (result != null) {
                    text = (en() ? "Password: " : "Contrasena: ") + result;
                }
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                nm.notify(NOTIF_ID, notif(text));
            } catch (Throwable ignored) {
            }
        }
    }

    private boolean en() {
        return "en".equals(getSharedPreferences("nr_air", MODE_PRIVATE).getString("lang", "es"));
    }

    private String loc(String s) {
        if (s == null || !en()) {
            return s;
        }
        if (s.startsWith("Preparando aircrack-ng")) return "Preparing aircrack-ng...";
        if (s.startsWith("Preparando")) return "Preparing...";
        if (s.startsWith("Copiando")) return "Copying files...";
        if (s.startsWith("Iniciando")) return "Starting aircrack-ng...";
        if (s.startsWith("Reanudando desde")) return s.replace("Reanudando desde", "Resuming from");
        if (s.startsWith("Reanudando")) return "Resuming aircrack-ng...";
        if (s.startsWith("Crackeando")) return "Cracking...";
        if (s.startsWith("Contrasena")) return "Password found";
        if (s.startsWith("Terminado sin resultado")) return "Finished, no match";
        if (s.startsWith("Detenido")) return "Stopped";
        if (s.startsWith("Inactivo")) return "Idle";
        return s;
    }

    private void kill() {
        Process p = proc;
        if (p != null) {
            try {
                p.destroyForcibly();
            } catch (Throwable ignored) {
            }
        }
    }

    private void finish(boolean keepNotification) {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        kill();
        releaseWake();
        try {
            if (keepNotification) {
                stopForeground(false);
            } else {
                stopForeground(true);
            }
        } catch (Throwable ignored) {
        }
        stopSelf();
    }

    @Override
    public void onDestroy() {
        stop.set(true);
        kill();
        releaseWake();
        super.onDestroy();
    }

    private void acquireWake() {
        if (wake != null && wake.isHeld()) {
            return;
        }
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NRAirCrack");
        wake.setReferenceCounted(false);
        wake.acquire();
    }

    private void releaseWake() {
        try {
            if (wake != null && wake.isHeld()) {
                wake.release();
            }
        } catch (Throwable ignored) {
        }
        wake = null;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "NR AirCrack", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
    }

    private Notification notif(String text) {
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this);
        }
        b.setSmallIcon(R.drawable.ic_stat_air);
        b.setColor(0xFF22D3EE);
        b.setContentTitle("NR AirCrack");
        b.setContentText(text);
        b.setOngoing(true);
        b.setOnlyAlertOnce(true);
        try {
            Intent stopI = new Intent(this, CrackerService.class).setAction(ACTION_STOP);
            PendingIntent pi = PendingIntent.getService(this, 2, stopI,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            b.addAction(android.R.drawable.ic_menu_close_clear_cancel, en() ? "Stop" : "Detener", pi);
        } catch (Throwable ignored) {
        }
        return b.build();
    }
}
