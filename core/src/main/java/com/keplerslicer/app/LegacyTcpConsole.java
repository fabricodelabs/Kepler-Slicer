package com.keplerslicer.app;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Bounded command console used by the official MKS and Flashforge hosts. */
final class LegacyTcpConsole implements Closeable {
    private final Socket socket;
    private final InputStream input;
    private final OutputStream output;
    private final NetworkHttp.Cancellation cancel;

    LegacyTcpConsole(String host, int port, NetworkHttp.Cancellation cancellation) throws IOException {
        if (host == null || host.isEmpty() || port < 1 || port > 65535) throw new IOException(L.t("Неверный адрес TCP принтера."));
        cancel = cancellation;
        cancel.check();
        Socket newSocket = new Socket();
        cancel.setCloser(newSocket);
        try {
            newSocket.connect(new InetSocketAddress(host, port), 5000);
            newSocket.setSoTimeout(500);
            socket = newSocket; input = socket.getInputStream(); output = socket.getOutputStream();
        } catch (IOException | RuntimeException error) {
            cancel.clearCloser(newSocket); try { newSocket.close(); } catch (IOException ignored) { }
            throw error;
        }
    }

    /** Send one command and wait for an exact "ok" line, like upstream TCPConsole. */
    void command(String command) throws IOException {
        if (command == null || command.isEmpty() || command.length() > 1024 || command.indexOf('\r') >= 0 || command.indexOf('\n') >= 0)
            throw new IOException(L.t("Недопустимая команда принтера."));
        cancel.check(); output.write((command + "\r\n").getBytes(StandardCharsets.US_ASCII)); output.flush();
        long deadline = System.nanoTime() + 10_000_000_000L;
        ByteArrayOutputStream line = new ByteArrayOutputStream(); int total = 0;
        while (System.nanoTime() < deadline) {
            cancel.check();
            int b;
            try { b = input.read(); } catch (SocketTimeoutException retry) { continue; }
            if (b < 0) throw new EOFException(L.t("Принтер закрыл соединение до подтверждения."));
            if (++total > 65536) throw new IOException(L.t("Слишком длинный ответ TCP принтера."));
            if (b == '\n') {
                String text = new String(line.toByteArray(), StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
                if (text.equals("ok")) return;
                line.reset();
            } else {
                if (line.size() >= 4096) throw new IOException(L.t("Слишком длинная строка TCP принтера."));
                line.write(b);
            }
        }
        throw new SocketTimeoutException(L.t("Принтер не подтвердил команду."));
    }

    /** Stream exactly the advertised file length without starting a print. */
    void file(File source, long length) throws IOException {
        if (source == null || !source.isFile() || length <= 0 || source.length() != length) throw new IOException(L.t("Файл задания недоступен или изменился."));
        try (InputStream in = new FileInputStream(source)) {
            byte[] bytes = new byte[8192]; long remaining = length;
            while (remaining > 0) {
                cancel.check(); int n = in.read(bytes, 0, (int)Math.min(bytes.length, remaining));
                if (n < 0) throw new EOFException(L.t("Файл изменился во время передачи."));
                if (n == 0) continue;
                output.write(bytes, 0, n); remaining -= n; cancel.progress(length - remaining, length);
            }
            output.flush(); cancel.check();
        }
    }

    @Override public void close() throws IOException {
        cancel.clearCloser(socket); socket.close();
    }
}
