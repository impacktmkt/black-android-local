package com.racas.black;

import java.io.*;
import java.net.*;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.regex.*;

/** Downloads only into app-owned storage; a partial file never becomes visible to the engine. */
final class GameDownload {
    static final long EXPECTED_BYTES = 2222194688L;
    static final String EXPECTED_SHA256 = "7fede48e38662c3702fc90b70b97afcafc6745f2795ce730b79c69c7ba9b2995";
    interface Progress { void update(String stage, long done, long total); }
    interface Connections { HttpURLConnection open(URL url) throws IOException; }
    static final class Cancellation {
        volatile boolean cancelled;
        volatile HttpURLConnection connection;
        void cancel() { cancelled = true; HttpURLConnection c = connection; if (c != null) c.disconnect(); }
        void check() throws InterruptedIOException { if (cancelled) throw new InterruptedIOException("Preparação pausada. Toque em Continuar para retomar."); }
    }
    private final long expected;
    private final String sha256;
    private final Connections connections;
    GameDownload() { this(EXPECTED_BYTES, EXPECTED_SHA256, url -> (HttpURLConnection) url.openConnection()); }
    // Injection is used only by host tests; production always uses the normal HTTPS connection.
    GameDownload(long size, String hash, Connections factory) { expected = size; sha256 = hash; connections = factory; }

    File prepare(String address, File dir, Cancellation cancel, Progress progress) throws Exception {
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Não foi possível criar a pasta do jogo.");
        File target = new File(dir, "BLACK.iso");
        if (target.exists()) {
            // Never overwrite an existing image, even when validation fails.
            validate(target, cancel, progress);
            return target;
        }
        SourcePart[] sources = sources(address);
        File part = new File(dir, "BLACK.download.part");
        long offset = part.isFile() ? part.length() : 0;
        if (offset > expected) { if (!part.delete()) throw new IOException("Não foi possível reiniciar o download."); offset = 0; }
        if (dir.getUsableSpace() < expected - offset + (64L << 20))
            throw new IOException("Espaço insuficiente. Libere pelo menos " + String.format(Locale.forLanguageTag("pt-BR"), "%.2f GB", (expected - offset + (64L << 20)) / 1e9) + " e tente novamente.");
        long base = 0;
        for (SourcePart segment : sources) {
            cancel.check();
            long present = part.isFile() ? part.length() : 0;
            if (present < base + segment.size)
                download(segment.url, part, base, segment.size, Math.max(0, present - base), cancel, progress);
            base += segment.size;
        }
        try { validate(part, cancel, progress); }
        catch (InterruptedIOException e) { throw e; }
        catch (IOException e) { part.delete(); throw e; }
        cancel.check();
        // No REPLACE_EXISTING: preserve any image another operation may have created.
        Files.move(part.toPath(), target.toPath());
        return target;
    }

    static URL source(String address) throws IOException {
        if (address == null || address.trim().isEmpty()) throw new IOException("O download ainda não foi configurado pelo responsável pelo app.");
        URL u;
        try { u = new URL(address.trim()); } catch (MalformedURLException e) { throw new IOException("O endereço de download é inválido."); }
        if (!"https".equalsIgnoreCase(u.getProtocol()) || u.getHost().isEmpty() || u.getUserInfo() != null)
            throw new IOException("É necessário um endereço HTTPS direto, sem usuário ou senha no link.");
        return u;
    }

    private static final class SourcePart {
        final URL url; final long size;
        SourcePart(URL url, long size) { this.url = url; this.size = size; }
    }
    private SourcePart[] sources(String address) throws IOException {
        if (address == null || address.trim().isEmpty()) { source(address); }
        String[] lines = address.trim().split("\\r?\\n");
        SourcePart[] parts = new SourcePart[lines.length]; long sum = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim(); int separator = line.lastIndexOf('|');
            String link = separator < 0 ? line : line.substring(0, separator);
            long size;
            try { size = separator < 0 && lines.length == 1 ? expected : Long.parseLong(line.substring(separator + 1)); }
            catch (Exception e) { throw new IOException("A configuração dos dados do jogo é inválida."); }
            if (size <= 0 || size > expected || sum > expected - size) throw new IOException("O tamanho das partes do jogo é inválido.");
            parts[i] = new SourcePart(source(link), size); sum += size;
        }
        if (sum != expected) throw new IOException("As partes do jogo não correspondem ao tamanho esperado.");
        return parts;
    }

    private HttpURLConnection connect(URL url, long offset, Cancellation cancel) throws Exception {
        for (int redirects = 0; redirects <= 5; redirects++) {
            cancel.check();
            HttpURLConnection c = connections.open(url); cancel.connection = c;
            c.setInstanceFollowRedirects(false); c.setConnectTimeout(15000); c.setReadTimeout(20000);
            c.setRequestProperty("Accept-Encoding", "identity");
            c.setRequestProperty("User-Agent", "BLACK-Local/1.5.0");
            if (offset > 0) c.setRequestProperty("Range", "bytes=" + offset + "-");
            int code = c.getResponseCode();
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String next = c.getHeaderField("Location"); c.disconnect();
                if (next == null) throw new IOException("O servidor retornou um redirecionamento sem destino.");
                url = source(new URL(url, next).toExternalForm());
                continue;
            }
            return c;
        }
        throw new IOException("O endereço fez redirecionamentos demais. Tente novamente mais tarde.");
    }

    private void download(URL url, File part, long base, long segmentSize, long offset, Cancellation cancel, Progress progress) throws Exception {
        HttpURLConnection c = null;
        try {
            c = connect(url, offset, cancel);
            int code = c.getResponseCode();
            if (code != 200 && code != 206) throw new IOException(code == 401 || code == 403
                    ? "O link não permite o download ou expirou. Solicite um link válido ao responsável pelo app."
                    : "Não foi possível baixar os dados (HTTP " + code + "). Confira sua conexão e tente novamente.");
            if (code == 200) offset = 0; // server ignores Range: restart, never append a second full image
            if (code == 206) {
                String range = c.getHeaderField("Content-Range");
                Matcher m = Pattern.compile("bytes (\\d+)-(\\d+)/(\\d+)").matcher(range == null ? "" : range);
                if (!m.matches() || Long.parseLong(m.group(1)) != offset || Long.parseLong(m.group(3)) != segmentSize
                        || Long.parseLong(m.group(2)) != segmentSize - 1)
                    throw new IOException("O servidor retornou uma parte incompatível do arquivo. Tente novamente.");
            }
            long size = c.getContentLengthLong();
            if (size >= 0 && size != segmentSize - offset) throw new IOException("O link não retornou o arquivo completo esperado. Avise o responsável pelo app.");
            try (InputStream in = c.getInputStream(); RandomAccessFile out = new RandomAccessFile(part, "rw")) {
                out.setLength(base + offset); out.seek(base + offset);
                byte[] buffer = new byte[1 << 20]; long total = offset; long shown = System.nanoTime();
                progress.update("Baixando o jogo", base + total, expected);
                int n;
                while ((n = in.read(buffer)) != -1) {
                    cancel.check();
                    if (total + n > segmentSize) throw new IOException("O download tem tamanho diferente do esperado.");
                    out.write(buffer, 0, n); total += n;
                    if (System.nanoTime() - shown > 250_000_000L) { progress.update("Baixando o jogo", base + total, expected); shown = System.nanoTime(); }
                }
                out.getFD().sync(); cancel.check();
                if (total != segmentSize) throw new IOException("A conexão foi interrompida. Toque em Continuar para retomar o download.");
                progress.update("Baixando o jogo", base + total, expected);
            }
        } catch (IOException e) { cancel.check(); throw e; }
        finally { if (c != null) c.disconnect(); cancel.connection = null; }
    }

    private void validate(File file, Cancellation cancel, Progress progress) throws Exception {
        if (file.length() != expected) throw new IOException("O arquivo tem tamanho diferente do esperado. Nenhuma imagem existente foi substituída.");
        try (FileInputStream in = new FileInputStream(file); FileChannel channel = in.getChannel()) {
            if (!Boolean.TRUE.equals(GameImporter.isXboxImage(channel))) throw new IOException("Os dados baixados não são uma imagem Xbox válida.");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[4 << 20]; long done = 0; long shown = System.nanoTime();
            progress.update("Verificando os dados", 0, expected);
            int n;
            while ((n = in.read(buffer)) != -1) {
                cancel.check(); digest.update(buffer, 0, n); done += n;
                if (System.nanoTime() - shown > 250_000_000L) { progress.update("Verificando os dados", done, expected); shown = System.nanoTime(); }
            }
            StringBuilder actual = new StringBuilder(); for (byte b : digest.digest()) actual.append(String.format(Locale.ROOT, "%02x", b & 255));
            if (!sha256.equals(actual.toString())) throw new IOException("Os dados falharam na verificação de integridade. Tente baixar novamente.");
            progress.update("Verificando os dados", expected, expected);
        }
    }
}
