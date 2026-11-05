package com.keplerslicer.app;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** Minimal RFC 6455 client for plain ws:// text messages on the local network (Creality, Elegoo SDCP). */
final class MiniWebSocket implements Closeable {
    private final Socket socket;
    private final DataInputStream in;
    private final OutputStream out;
    private final SecureRandom random = new SecureRandom();
    private final NetworkHttp.Cancellation cancel;

    MiniWebSocket(String host, int port, String path, int timeoutMs, NetworkHttp.Cancellation cancel) throws IOException {
        if (path == null || !path.startsWith("/") || path.contains("\r") || path.contains("\n")) throw new IllegalArgumentException("path");
        this.cancel = cancel; socket = new Socket();
        cancel.setCloser(socket);
        boolean ok = false;
        try {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            in = new DataInputStream(socket.getInputStream()); out = socket.getOutputStream();
            byte[] nonce = new byte[16]; random.nextBytes(nonce);
            String key = Base64.getEncoder().encodeToString(nonce);
            String request = "GET " + path + " HTTP/1.1\r\nHost: " + host + ":" + port + "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: " + key + "\r\nSec-WebSocket-Version: 13\r\n\r\n";
            out.write(request.getBytes(StandardCharsets.US_ASCII)); out.flush();
            String status = line(); if (!status.startsWith("HTTP/1.1 101")) throw new IOException(L.t("WebSocket upgrade refused: ") + status);
            String accept = null; for (String h; !(h = line()).isEmpty(); ) if (h.toLowerCase(java.util.Locale.ROOT).startsWith("sec-websocket-accept:")) accept = h.substring(21).trim();
            String expected = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));
            if (!expected.equals(accept)) throw new IOException(L.t("WebSocket handshake mismatch"));
            ok = true;
        } catch (java.security.NoSuchAlgorithmException e) { throw new IOException(e); }
        finally { if (!ok) { cancel.clearCloser(socket); try { socket.close(); } catch (IOException ignored) { } } }
    }

    private String line() throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream(); int c;
        while ((c = in.read()) != -1) { if (c == '\n') break; if (c != '\r') b.write(c); if (b.size() > 8192) throw new IOException("Header too long"); }
        if (c == -1 && b.size() == 0) throw new IOException("Connection closed");
        return b.toString("ISO-8859-1");
    }

    void send(String text) throws IOException {
        byte[] p = text.getBytes(StandardCharsets.UTF_8); byte[] mask = new byte[4]; random.nextBytes(mask);
        ByteArrayOutputStream f = new ByteArrayOutputStream(); f.write(0x81);
        if (p.length < 126) f.write(0x80 | p.length); else if (p.length < 65536) { f.write(0x80 | 126); f.write(p.length >> 8); f.write(p.length & 255); } else throw new IOException("Message too long");
        f.write(mask, 0, 4); for (int i = 0; i < p.length; i++) f.write(p[i] ^ mask[i & 3]);
        out.write(f.toByteArray()); out.flush();
    }

    /** Next text message, or null on timeout. Answers pings, ignores binary frames. */
    String receive() throws IOException {
        ByteArrayOutputStream message = new ByteArrayOutputStream();
        while (true) {
            int b0, b1;
            try { b0 = in.readUnsignedByte(); } catch (SocketTimeoutException t) { return null; }
            b1 = in.readUnsignedByte();
            long len = b1 & 127; if (len == 126) len = in.readUnsignedShort(); else if (len == 127) len = in.readLong();
            if (len < 0 || len > 4 * 1024 * 1024) throw new IOException(L.t("WebSocket frame too large"));
            byte[] mask = (b1 & 0x80) != 0 ? new byte[4] : null; if (mask != null) in.readFully(mask);
            byte[] data = new byte[(int) len]; in.readFully(data); if (mask != null) for (int i = 0; i < data.length; i++) data[i] ^= mask[i & 3];
            int op = b0 & 15;
            if (op == 8) throw new IOException(L.t("WebSocket closed by the printer"));
            if (op == 9) { sendControl(0x8A, data); continue; }
            if (op == 10) continue;
            if (op == 1 || op == 0) { message.write(data); if ((b0 & 0x80) != 0) return message.toString("UTF-8"); }
        }
    }
    private void sendControl(int header, byte[] data) throws IOException {
        byte[] mask = new byte[4]; random.nextBytes(mask); ByteArrayOutputStream f = new ByteArrayOutputStream(); f.write(header); f.write(0x80 | Math.min(125, data.length)); f.write(mask, 0, 4);
        for (int i = 0; i < Math.min(125, data.length); i++) f.write(data[i] ^ mask[i & 3]); out.write(f.toByteArray()); out.flush();
    }

    @Override public void close() { try { sendControl(0x88, new byte[0]); } catch (IOException ignored) { } try { socket.close(); } catch (IOException ignored) { } cancel.clearCloser(socket); }
}
