package com.nrcracker.air;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Convierte una captura pcap/pcapng (radiotap o 802.11) en lineas hashcat 22000.
 * Portado del parser de NRSuite v1 (WpaHandshakeParser, WpaHandshakeCollector,
 * PcapSsidParser y Wpa22000Exporter) a Java puro, sin Kotlin.
 */
public final class PcapConverter {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final int LINKTYPE_IEEE802_11 = 105;
    private static final int LINKTYPE_RADIOTAP = 127;

    private PcapConverter() {
    }

    static boolean DEBUG = false;

    public static final class Result {
        public final List<String> lines = new ArrayList<String>();
        public final List<String> detectedSsids = new ArrayList<String>();
        public String firstSsid;
        public int handshakes;
        public int ssids;

        public boolean hasHandshakeWithoutSsid() {
            return handshakes > 0 && lines.isEmpty();
        }
    }

    public static Result convert(File file, String manualSsid) throws IOException {
        final Collector collector = new Collector();
        final Map<String, String> ssids = new LinkedHashMap<String, String>();
        InputStream raw = new BufferedInputStream(new FileInputStream(file), 262144);
        raw.mark(4);
        byte[] magic = new byte[4];
        boolean ok = readFully(raw, magic, 4);
        raw.reset();
        if (ok && (magic[0] & 0xff) == 0x0a && (magic[1] & 0xff) == 0x0d
                && (magic[2] & 0xff) == 0x0d && (magic[3] & 0xff) == 0x0a) {
            readPcapng(raw, collector, ssids);
        } else {
            readPcap(raw, collector, ssids);
        }
        raw.close();

        List<Hs> list = collector.finish();
        Result out = new Result();
        out.handshakes = list.size();
        out.ssids = ssids.size();
        out.detectedSsids.addAll(new LinkedHashSet<String>(ssids.values()));
        if (!out.detectedSsids.isEmpty()) {
            out.firstSsid = out.detectedSsids.get(0);
        }
        String manual = manualSsid != null ? manualSsid.trim() : "";
        for (Hs h : list) {
            String ssid = manual;
            if (ssid.isEmpty()) {
                ssid = ssids.get(macString(h.getBssid()));
            }
            if ((ssid == null || ssid.isEmpty()) && out.detectedSsids.size() == 1) {
                ssid = out.detectedSsids.get(0);
            }
            if (ssid == null || ssid.isEmpty()) {
                continue;
            }
            String line = Exporter.buildLine(h, ssid);
            if (line != null) {
                out.lines.add(line);
            } else if (DEBUG) {
                System.out.println("buildLine=null ssid=" + ssid
                        + " ver=" + h.keyDescriptorVersion
                        + " eapol=" + (h.eapolM2 == null ? -1 : h.eapolM2.length)
                        + " mic=" + (h.mic == null ? -1 : h.mic.length)
                        + " anonce=" + (h.aNonce == null ? -1 : h.aNonce.length)
                        + " bssid=" + macString(h.getBssid())
                        + " station=" + macString(h.getStation()));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ pcap

    private static void readPcap(InputStream is, Collector collector, Map<String, String> ssids) throws IOException {
        byte[] gh = new byte[24];
        if (!readFully(is, gh, 24)) {
            return;
        }
        boolean le;
        int m0 = gh[0] & 0xff, m1 = gh[1] & 0xff, m2 = gh[2] & 0xff, m3 = gh[3] & 0xff;
        if (m0 == 0xd4 && m1 == 0xc3 && m2 == 0xb2 && m3 == 0xa1) {
            le = true;
        } else if (m0 == 0xa1 && m1 == 0xb2 && m2 == 0xc3 && m3 == 0xd4) {
            le = false;
        } else if (m0 == 0x4d && m1 == 0x3c && m2 == 0xb2 && m3 == 0xa1) {
            le = true;
        } else if (m0 == 0xa1 && m1 == 0xb2 && m2 == 0x3c && m3 == 0x4d) {
            le = false;
        } else {
            throw new IOException("No es un pcap clasico");
        }
        int linktype = (int) (le ? u32le(gh, 20) : u32be(gh, 20));
        byte[] ph = new byte[16];
        while (readFully(is, ph, 16)) {
            long incl = le ? u32le(ph, 8) : u32be(ph, 8);
            if (incl < 0 || incl > 4 * 1024 * 1024) {
                break;
            }
            byte[] pkt = new byte[(int) incl];
            if (!readFully(is, pkt, pkt.length)) {
                break;
            }
            byte[] frame = adapt(pkt, linktype);
            if (frame != null) {
                feed(frame, collector, ssids);
            }
        }
    }

    private static void readPcapng(InputStream is, Collector collector, Map<String, String> ssids) throws IOException {
        boolean le = true;
        int linktype = LINKTYPE_RADIOTAP;
        byte[] bh = new byte[8];
        while (readFully(is, bh, 8)) {
            long type = u32le(bh, 0);
            long len = u32le(bh, 4);
            if (len < 12 || len > 64L * 1024 * 1024 || (len & 3) != 0) {
                break;
            }
            int blen = (int) len - 12;
            byte[] body = new byte[blen];
            if (!readFully(is, body, blen)) {
                break;
            }
            if (type == 0x0A0D0D0A) {
                if (blen >= 4) {
                    long bom = u32le(body, 0);
                    if (bom == 0x1A2B3C4D) {
                        le = true;
                    } else if (bom == 0x4D3C2B1A) {
                        le = false;
                    }
                }
            } else if (type == 1) {
                if (blen >= 2) {
                    linktype = (int) (le ? u16le(body, 0) : u16be(body, 0));
                }
            } else if (type == 6) {
                if (blen >= 20) {
                    long cap = le ? u32le(body, 12) : u32be(body, 12);
                    if (cap >= 0 && cap <= blen - 20) {
                        byte[] pkt = Arrays.copyOfRange(body, 20, 20 + (int) cap);
                        byte[] frame = adapt(pkt, linktype);
                        if (frame != null) {
                            feed(frame, collector, ssids);
                        }
                    }
                }
            } else if (type == 3) {
                if (blen > 4) {
                    byte[] pkt = Arrays.copyOfRange(body, 4, blen);
                    byte[] frame = adapt(pkt, linktype);
                    if (frame != null) {
                        feed(frame, collector, ssids);
                    }
                }
            }
        }
    }

    private static byte[] adapt(byte[] pkt, int linktype) {
        if (linktype == LINKTYPE_RADIOTAP) {
            return pkt;
        }
        if (linktype == LINKTYPE_IEEE802_11) {
            byte[] f = new byte[pkt.length + 8];
            f[2] = 8;
            System.arraycopy(pkt, 0, f, 8, pkt.length);
            return f;
        }
        return null;
    }

    private static void feed(byte[] frame, Collector collector, Map<String, String> ssids) {
        collector.addFrame(frame);
        String[] d = SsidParser.parse(frame);
        if (d != null && d[1] != null && !d[1].isEmpty()) {
            ssids.put(d[1].toUpperCase(Locale.US), d[0]);
        }
    }

    // -------------------------------------------------------------- handshake

    static final class Hs {
        byte[] m1Bssid;
        byte[] m1Station;
        byte[] aNonce;
        Long m1ReplayCounter;
        byte[] m2Bssid;
        byte[] m2Station;
        byte[] sNonce;
        byte[] eapolM2;
        byte[] mic;
        Long m2ReplayCounter;
        int keyDescriptorVersion;

        byte[] getBssid() {
            if (m1Bssid == null) {
                return null;
            }
            return (m2Bssid == null || Arrays.equals(m1Bssid, m2Bssid)) ? m1Bssid : null;
        }

        byte[] getStation() {
            if (m1Station == null) {
                return null;
            }
            return (m2Station == null || Arrays.equals(m1Station, m2Station)) ? m1Station : null;
        }

        boolean isComplete() {
            return m1Bssid != null && m1Station != null && aNonce != null
                    && m2Bssid != null && m2Station != null && sNonce != null
                    && eapolM2 != null && mic != null
                    && getBssid() != null && getStation() != null && replayCountersMatch();
        }

        private boolean replayCountersMatch() {
            if (m1ReplayCounter == null && m2ReplayCounter == null) {
                return true;
            }
            return m1ReplayCounter != null && m2ReplayCounter != null
                    && m1ReplayCounter.equals(m2ReplayCounter);
        }
    }

    static final class Collector {
        private static final class Key {
            final String bssid;
            final String station;
            final Long replay;

            Key(String bssid, String station, Long replay) {
                this.bssid = bssid;
                this.station = station;
                this.replay = replay;
            }

            @Override
            public boolean equals(Object o) {
                if (!(o instanceof Key)) {
                    return false;
                }
                Key k = (Key) o;
                return eq(bssid, k.bssid) && eq(station, k.station) && eq(replay, k.replay);
            }

            @Override
            public int hashCode() {
                int h = bssid != null ? bssid.hashCode() : 0;
                h = 31 * h + (station != null ? station.hashCode() : 0);
                h = 31 * h + (replay != null ? replay.hashCode() : 0);
                return h;
            }

            private static boolean eq(Object a, Object b) {
                return a == null ? b == null : a.equals(b);
            }
        }

        private final Map<Key, Hs> pendingM1 = new LinkedHashMap<Key, Hs>();
        private final Map<Key, Hs> pendingM2 = new LinkedHashMap<Key, Hs>();
        private final Map<String, Hs> completed = new LinkedHashMap<String, Hs>();

        void addFrame(byte[] frame) {
            Hs h = Parser.parse(frame, new Hs());
            if (h.m1Bssid != null && h.m1Station != null && h.aNonce != null) {
                Key k = new Key(hex(h.m1Bssid), hex(h.m1Station), h.m1ReplayCounter);
                pendingM1.put(k, h);
                Hs m2 = pendingM2.remove(k);
                if (m2 != null) {
                    combine(h, m2);
                }
            }
            if (h.m2Bssid != null && h.m2Station != null && h.sNonce != null
                    && h.eapolM2 != null && h.mic != null) {
                Key k = new Key(hex(h.m2Bssid), hex(h.m2Station), h.m2ReplayCounter);
                pendingM2.put(k, h);
                Hs m1 = pendingM1.get(k);
                if (m1 != null) {
                    combine(m1, h);
                }
            }
        }

        private void combine(Hs m1, Hs m2) {
            Hs h = new Hs();
            h.m1Bssid = copy(m1.m1Bssid);
            h.m1Station = copy(m1.m1Station);
            h.aNonce = copy(m1.aNonce);
            h.m1ReplayCounter = m1.m1ReplayCounter;
            h.m2Bssid = copy(m2.m2Bssid);
            h.m2Station = copy(m2.m2Station);
            h.sNonce = copy(m2.sNonce);
            h.eapolM2 = copy(m2.eapolM2);
            h.mic = copy(m2.mic);
            h.m2ReplayCounter = m2.m2ReplayCounter;
            h.keyDescriptorVersion = m2.keyDescriptorVersion;
            if (!h.isComplete()) {
                return;
            }
            Long replay = h.m1ReplayCounter != null ? h.m1ReplayCounter : h.m2ReplayCounter;
            String key = hex(h.getBssid()) + "|" + hex(h.getStation()) + "|"
                    + (replay != null ? replay : -1) + "|" + hex(h.mic);
            completed.put(key, h);
        }

        List<Hs> finish() {
            return new ArrayList<Hs>(completed.values());
        }
    }

    static final class Parser {
        static Hs parse(byte[] frame, Hs state) {
            if (frame.length < 8) {
                return state;
            }
            int i = u16le(frame, 2);
            if (frame.length < i + 24) {
                return state;
            }
            int b = frame[i] & 0xff;
            if (((b & 12) >> 2) != 2) {
                return state;
            }
            int b2 = frame[i + 1] & 0xff;
            int i3 = (((b2 & 1) != 0) && ((b2 & 2) != 0)) ? 30 : 24;
            int i4 = (b >> 4) & 15;
            if (i4 >= 8 && i4 < 12) {
                i3 += 2;
            }
            int i5 = i3 + i;
            int iMin = Math.min(i5 + 20, frame.length - 1);
            while (true) {
                if (i5 >= iMin) {
                    i5 = -1;
                    break;
                }
                if ((frame[i5] & 0xff) == 0x88 && (frame[i5 + 1] & 0xff) == 0x8e) {
                    break;
                }
                i5++;
            }
            if (i5 < 0) {
                return state;
            }
            int i6 = i5 + 2;
            if (frame.length >= i5 + 101 && (frame[i5 + 3] & 0xff) == 3) {
                int i7 = i5 + 6;
                if ((frame[i7] & 0xff) == 2) {
                    int i8 = ((frame[i5 + 7] & 0xff) << 8) | (frame[i5 + 8] & 0xff);
                    int i9 = i8 & 7;
                    boolean z = (i8 & 8) != 0;
                    boolean z2 = (i8 & 128) != 0;
                    boolean z3 = (i8 & 256) != 0;
                    boolean z4 = (i8 & 512) != 0;
                    boolean z5 = (i8 & 1024) != 0;
                    if (z && !z5) {
                        byte[] addr1 = copy(frame, i + 4, i + 10);
                        byte[] addr2 = copy(frame, i + 10, i + 16);
                        byte[] addr3 = copy(frame, i + 16, i + 22);
                        byte[] nonce = copy(frame, i5 + 19, i5 + 51);
                        byte[] mic = copy(frame, i5 + 83, i5 + 99);
                        if (z2 && !z3) {
                            state.m1Bssid = Arrays.equals(addr2, addr3) ? addr3 : addr2;
                            state.m1Station = addr1;
                            state.aNonce = nonce;
                            state.m1ReplayCounter = readU64Le(frame, i5 + 11);
                            return state;
                        }
                        if (!z2 && z3 && !z4) {
                            state.m2Bssid = Arrays.equals(addr1, addr3) ? addr3 : addr1;
                            state.m2Station = addr2;
                            state.sNonce = nonce;
                            state.mic = mic;
                            state.m2ReplayCounter = readU64Le(frame, i5 + 11);
                            state.keyDescriptorVersion = i9;
                            int eapolLen = ((frame[i5 + 4] & 0xff) << 8) | (frame[i5 + 5] & 0xff);
                            state.eapolM2 = copy(frame, i6, Math.min(i7 + eapolLen, frame.length));
                        }
                    }
                }
            }
            return state;
        }
    }

    static final class SsidParser {
        static String[] parse(byte[] frame) {
            if (frame.length < 32) {
                return null;
            }
            int iU16 = u16le(frame, 2);
            if (iU16 < 8) {
                return null;
            }
            int i = iU16 + 24;
            if (i > frame.length) {
                return null;
            }
            int i2 = frame[iU16] & 0xff;
            if (((i2 >> 2) & 3) != 0) {
                return null;
            }
            int i3 = (i2 >> 4) & 15;
            if (i3 == 0 || i3 == 2) {
                i = iU16 + 28;
            } else if (i3 == 8) {
                i = iU16 + 36;
            } else if (i3 == 5) {
                i = iU16 + 36;
            } else if (i3 != 4) {
                return null;
            }
            if (i + 2 > frame.length) {
                return null;
            }
            String mac = (i3 == 0 || i3 == 2 || i3 == 5 || i3 == 8)
                    ? macString(frame, iU16 + 16) : "";
            while (true) {
                int i4 = i + 2;
                if (i4 > frame.length) {
                    break;
                }
                int i5 = frame[i] & 0xff;
                int i6 = frame[i + 1] & 0xff;
                int i7 = i4 + i6;
                if (i7 > frame.length) {
                    break;
                }
                if (i5 == 0) {
                    if (i6 > 0) {
                        String ssid = new String(frame, i4, i6, UTF8);
                        if (!ssid.trim().isEmpty()) {
                            return new String[]{ssid, mac};
                        }
                    }
                    return null;
                }
                i = i7;
            }
            return null;
        }
    }

    static final class Exporter {
        static String buildLine(Hs h, String ssid) {
            byte[] bssid = h.getBssid();
            byte[] station = h.getStation();
            if (!h.isComplete() || bssid == null || station == null
                    || h.aNonce == null || h.mic == null || h.eapolM2 == null) {
                return null;
            }
            byte[] eapol = Arrays.copyOf(h.eapolM2, h.eapolM2.length);
            byte[] ssidBytes = ssid.getBytes(UTF8);
            if (bssid.length != 6 || station.length != 6 || h.aNonce.length != 32
                    || h.mic.length != 16 || ssidBytes.length == 0 || ssidBytes.length > 32
                    || eapol.length < 97 || eapol.length >= 257
                    || h.keyDescriptorVersion < 1 || h.keyDescriptorVersion >= 4) {
                return null;
            }
            for (int i = 81; i < 97; i++) {
                eapol[i] = 0;
            }
            return "WPA*02*" + hex(h.mic) + "*" + hex(bssid) + "*" + hex(station)
                    + "*" + hex(ssidBytes) + "*" + hex(h.aNonce) + "*" + hex(eapol) + "*00";
        }
    }

    // ---------------------------------------------------------------- utils

    static int u8(byte b) {
        return b & 0xff;
    }

    static int u16le(byte[] b, int o) {
        if (o + 1 >= b.length) {
            return 0;
        }
        return (b[o] & 0xff) | ((b[o + 1] & 0xff) << 8);
    }

    static long u32le(byte[] b, int o) {
        if (o + 3 >= b.length) {
            return 0;
        }
        return ((long) (b[o] & 0xff)) | ((long) (b[o + 1] & 0xff) << 8)
                | ((long) (b[o + 2] & 0xff) << 16) | ((long) (b[o + 3] & 0xff) << 24);
    }

    static long u32be(byte[] b, int o) {
        if (o + 3 >= b.length) {
            return 0;
        }
        return ((long) (b[o] & 0xff) << 24) | ((long) (b[o + 1] & 0xff) << 16)
                | ((long) (b[o + 2] & 0xff) << 8) | (long) (b[o + 3] & 0xff);
    }

    static int u16be(byte[] b, int o) {
        if (o + 1 >= b.length) {
            return 0;
        }
        return ((b[o] & 0xff) << 8) | (b[o + 1] & 0xff);
    }

    static long readU64Le(byte[] b, int o) {
        long v = 0;
        for (int i = 0; i < 8; i++) {
            v |= ((long) (b[o + i] & 0xff)) << (i * 8);
        }
        return v;
    }

    static byte[] copy(byte[] b, int from, int to) {
        if (to > b.length) {
            to = b.length;
        }
        return Arrays.copyOfRange(b, from, to);
    }

    static byte[] copy(byte[] b) {
        return b == null ? null : Arrays.copyOf(b, b.length);
    }

    static String hex(byte[] b) {
        if (b == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) {
            sb.append(String.format(Locale.US, "%02x", x & 0xff));
        }
        return sb.toString();
    }

    static String macString(byte[] b) {
        if (b == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(17);
        for (int i = 0; i < b.length && i < 6; i++) {
            if (i > 0) {
                sb.append(':');
            }
            sb.append(String.format(Locale.US, "%02X", b[i] & 0xff));
        }
        return sb.toString();
    }

    private static String macString(byte[] frame, int offset) {
        if (offset + 6 > frame.length) {
            return "";
        }
        StringBuilder sb = new StringBuilder(17);
        for (int i = 0; i < 6; i++) {
            if (i > 0) {
                sb.append(':');
            }
            sb.append(String.format(Locale.US, "%02X", frame[offset + i] & 0xff));
        }
        return sb.toString();
    }

    static boolean readFully(InputStream in, byte[] buf, int len) throws IOException {
        int off = 0;
        while (off < len) {
            int n = in.read(buf, off, len - off);
            if (n <= 0) {
                return false;
            }
            off += n;
        }
        return true;
    }

    public static void main(String[] args) throws Exception {
        DEBUG = args.length > 1 && args[1].equals("--debug");
        Result r = convert(new File(args[0]), "");
        System.out.println("handshakes=" + r.handshakes + " ssids=" + r.detectedSsids
                + " lines=" + r.lines.size());
        for (String line : r.lines) {
            System.out.println(line);
        }
    }
}
