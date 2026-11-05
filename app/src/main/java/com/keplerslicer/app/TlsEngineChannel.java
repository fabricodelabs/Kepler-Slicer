package com.keplerslicer.app;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLSession;

/**
 * Write-only TLS client channel over an already connected socket, driven by an SSLEngine created for a given
 * peer host and port. FTPS servers that require session reuse (Bambu Lab) expect the data connection to resume
 * the control session; the engine's session cache is keyed by the host and port passed here, not the socket's.
 */
final class TlsEngineChannel {
    private final SSLEngine engine;
    private final InputStream in;
    private final OutputStream out;
    private ByteBuffer netIn, netOut, appIn;

    TlsEngineChannel(SSLContext context, Socket socket, String peerHost, int peerPort) throws IOException {
        engine = context.createSSLEngine(peerHost, peerPort);
        engine.setUseClientMode(true);
        in = socket.getInputStream(); out = socket.getOutputStream();
        SSLSession s = engine.getSession();
        netIn = ByteBuffer.allocate(s.getPacketBufferSize()); netIn.flip();
        netOut = ByteBuffer.allocate(s.getPacketBufferSize());
        appIn = ByteBuffer.allocate(s.getApplicationBufferSize());
    }

    SSLSession session() { return engine.getSession(); }

    void handshake() throws IOException {
        engine.beginHandshake();
        ByteBuffer empty = ByteBuffer.allocate(0);
        while (true) {
            SSLEngineResult.HandshakeStatus hs = engine.getHandshakeStatus();
            switch (hs) {
                case NEED_WRAP: wrap(empty); break;
                case NEED_UNWRAP: unwrap(); break;
                case NEED_TASK: { Runnable task; while ((task = engine.getDelegatedTask()) != null) task.run(); break; }
                default: return; // FINISHED / NOT_HANDSHAKING (and NEED_UNWRAP_AGAIN never occurs for TLS over TCP)
            }
        }
    }

    void write(byte[] data, int off, int len) throws IOException {
        ByteBuffer src = ByteBuffer.wrap(data, off, len);
        while (src.hasRemaining()) wrap(src);
    }

    /** Sends close_notify; the server then sees the end of the file. */
    void close() throws IOException {
        engine.closeOutbound();
        ByteBuffer empty = ByteBuffer.allocate(0);
        while (!engine.isOutboundDone()) wrap(empty);
        out.flush();
    }

    private void wrap(ByteBuffer src) throws IOException {
        netOut.clear();
        SSLEngineResult r = engine.wrap(src, netOut);
        if (r.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) { netOut = ByteBuffer.allocate(netOut.capacity() * 2); return; }
        if (r.getStatus() == SSLEngineResult.Status.CLOSED && !engine.isOutboundDone()) throw new IOException(L.t("TLS channel closed"));
        netOut.flip(); out.write(netOut.array(), 0, netOut.limit()); out.flush();
    }

    private void unwrap() throws IOException {
        while (true) {
            appIn.clear();
            SSLEngineResult r = engine.unwrap(netIn, appIn);
            switch (r.getStatus()) {
                case OK: return;
                case BUFFER_OVERFLOW: appIn = ByteBuffer.allocate(appIn.capacity() * 2); break;
                case BUFFER_UNDERFLOW: {
                    if (netIn.limit() == netIn.capacity()) { ByteBuffer bigger = ByteBuffer.allocate(netIn.capacity() * 2); bigger.put(netIn); bigger.flip(); netIn = bigger; }
                    netIn.compact();
                    int n = in.read(netIn.array(), netIn.position(), netIn.remaining());
                    if (n < 0) { netIn.flip(); throw new EOFException(L.t("TLS handshake ended early")); }
                    netIn.position(netIn.position() + n); netIn.flip();
                    break;
                }
                case CLOSED: throw new IOException(L.t("TLS channel closed"));
            }
        }
    }
}
