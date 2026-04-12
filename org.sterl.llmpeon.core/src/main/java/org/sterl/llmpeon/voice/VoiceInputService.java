package org.sterl.llmpeon.voice;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

public class VoiceInputService implements AutoCloseable {

    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(VoiceInputService.class.getName());

    // mac native 44100
    private static final AudioFormat FORMAT = new AudioFormat(16000, 16, 1, true, false);
    // 15 seconds of 16 kHz, 16-bit, mono — flush and dispatch when reached
    private static final int MAX_BUFFER_BYTES = 16_000 * 2 * 15;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final List<CompletableFuture<String>> pendingChunks = new CopyOnWriteArrayList<>();

    private TargetDataLine line;
    private ByteArrayOutputStream buffer;
    private Thread readerThread;
    private VoiceConfig voiceConfig;

    public void startRecording(VoiceConfig voice) throws LineUnavailableException {
        this.voiceConfig = voice;
        String mixerName = voice.mixer();

        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        if (mixerName != null && !mixerName.isBlank()) {
            for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
                if (mi.getName().equals(mixerName)) {
                    line = (TargetDataLine) AudioSystem.getMixer(mi).getLine(info);
                    break;
                }
            }
        }
        if (line == null) {
            line = (TargetDataLine) AudioSystem.getLine(info); // fallback to system default
        }
        line.open(FORMAT);
        line.start();

        buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        readerThread = Thread.ofVirtual().start(() -> {
            while (line != null && line.isOpen()) {
                int read = line.read(chunk, 0, chunk.length);
                if (read > 0) {
                    buffer.write(chunk, 0, read);
                    if (buffer.size() >= MAX_BUFFER_BYTES) {
                        dispatchChunk(buffer.toByteArray());
                        buffer.reset();
                    }
                }
            }
        });
    }

    public String stopAndTranscribe() throws Exception {
        if (line != null) {
            line.stop();
            line.close();
            line = null;
        }
        if (readerThread != null) {
            readerThread.join(2000);
            readerThread = null;
        }

        byte[] remaining = buffer.toByteArray();
        buffer = null;

        if (remaining.length > 0 && !isSilent(remaining)) {
            dispatchChunk(remaining);
        }

        if (pendingChunks.isEmpty()) {
            throw new RuntimeException(
                "No audio captured — check microphone permissions: " +
                "System Settings → Privacy & Security → Microphone → enable Eclipse/java");
        }

        StringBuilder sb = new StringBuilder();
        for (var f : pendingChunks) {
            String part = f.get();
            if (part != null && !part.isBlank()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(part.strip());
            }
        }
        pendingChunks.clear();

        if (sb.isEmpty()) {
            throw new RuntimeException(
                "No audio captured — check microphone permissions: " +
                "System Settings → Privacy & Security → Microphone → enable Eclipse/java");
        }
        return sb.toString();
    }

    @Override
    public void close() {
        if (line != null) {
            line.stop();
            line.close();
            line = null;
        }
        pendingChunks.forEach(f -> f.cancel(true));
        pendingChunks.clear();
    }

    private void dispatchChunk(byte[] pcm) {
        if (isSilent(pcm)) return;
        byte[] wav = toWav(pcm);
        VoiceConfig cfg = this.voiceConfig;
        pendingChunks.add(CompletableFuture.supplyAsync(() -> {
            try { return transcribe(wav, cfg); }
            catch (Exception e) { throw new RuntimeException(e); }
        }));
    }

    private String transcribe(byte[] wav, VoiceConfig voice) throws Exception {
        String boundary = UUID.randomUUID().toString().replace("-", "");
        String url = voice.baseUrl() + voice.endpoint();

        byte[] body = buildMultipartBody(boundary, wav, voice);

        HttpRequest.Builder req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));

        String apiKey = voice.apiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            req.header("Authorization", "Bearer " + apiKey);
        }

        LOG.info("transcribe " + wav.length + " bytes");
        HttpResponse<String> response = httpClient
                .send(req.build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("Transcription request failed (" + response.statusCode() + "): " + response.body());
        }

        return parseText(response.body());
    }

    private byte[] buildMultipartBody(String boundary, byte[] wav, VoiceConfig voice) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String crlf = "\r\n";

        // file part
        out.write(("--" + boundary + crlf).getBytes());
        out.write(("Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"" + crlf).getBytes());
        out.write(("Content-Type: audio/wav" + crlf).getBytes());
        out.write(crlf.getBytes());
        out.write(wav);
        out.write(crlf.getBytes());

        // model part
        out.write(("--" + boundary + crlf).getBytes());
        out.write(("Content-Disposition: form-data; name=\"model\"" + crlf).getBytes());
        out.write(crlf.getBytes());
        out.write(voice.model().getBytes());
        out.write(crlf.getBytes());

        // optional language part
        if (voice.language() != null && !voice.language().isBlank()) {
            out.write(("--" + boundary + crlf).getBytes());
            out.write(("Content-Disposition: form-data; name=\"language\"" + crlf).getBytes());
            out.write(crlf.getBytes());
            out.write(voice.language().getBytes());
            out.write(crlf.getBytes());
        }

        // response format
        out.write(("--" + boundary + crlf).getBytes());
        out.write(("Content-Disposition: form-data; name=\"response_format\"" + crlf).getBytes());
        out.write(crlf.getBytes());
        out.write("json".getBytes());
        out.write(crlf.getBytes());

        out.write(("--" + boundary + "--" + crlf).getBytes());
        return out.toByteArray();
    }

    /** Minimal JSON extraction of {"text":"..."} without adding a JSON dependency. */
    private String parseText(String json) {
        int idx = json.indexOf("\"text\"");
        if (idx < 0) throw new RuntimeException("Unexpected transcription response: " + json);
        int colon = json.indexOf(':', idx);
        int start = json.indexOf('"', colon + 1) + 1;
        int end   = json.indexOf('"', start);
        return json.substring(start, end);
    }

    /** Returns true if all PCM bytes are zero — indicates denied mic permission on macOS. */
    private boolean isSilent(byte[] pcm) {
        for (byte b : pcm) {
            if (b != 0) return false;
        }
        return true;
    }

    /** Wraps raw 16-bit PCM bytes in a standard WAV container. */
    private byte[] toWav(byte[] pcm) {
        int sampleRate    = (int) FORMAT.getSampleRate();
        int channels      = FORMAT.getChannels();
        int bitsPerSample = FORMAT.getSampleSizeInBits();
        int byteRate      = sampleRate * channels * bitsPerSample / 8;
        int blockAlign    = channels * bitsPerSample / 8;
        int dataSize      = pcm.length;
        int chunkSize     = 36 + dataSize;

        ByteBuffer buf = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        buf.put("RIFF".getBytes());
        buf.putInt(chunkSize);
        buf.put("WAVE".getBytes());
        buf.put("fmt ".getBytes());
        buf.putInt(16);              // subchunk1 size
        buf.putShort((short) 1);     // PCM
        buf.putShort((short) channels);
        buf.putInt(sampleRate);
        buf.putInt(byteRate);
        buf.putShort((short) blockAlign);
        buf.putShort((short) bitsPerSample);
        buf.put("data".getBytes());
        buf.putInt(dataSize);
        buf.put(pcm);
        return buf.array();
    }
}
