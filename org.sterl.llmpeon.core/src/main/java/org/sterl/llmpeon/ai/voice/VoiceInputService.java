package org.sterl.llmpeon.ai.voice;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;


public class VoiceInputService implements AutoCloseable {

    private static final AudioFormat FORMAT = new AudioFormat(16000, 16, 1, true, false);

    private TargetDataLine line;
    private ByteArrayOutputStream buffer;
    private Thread readerThread;

    public void startRecording() throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        line = (TargetDataLine) AudioSystem.getLine(info);
        line.open(FORMAT);
        line.start();

        buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        readerThread = Thread.ofVirtual().start(() -> {
            while (line != null && line.isOpen()) {
                int read = line.read(chunk, 0, chunk.length);
                if (read > 0) buffer.write(chunk, 0, read);
            }
        });
    }

    public String stopAndTranscribe(VoiceConfig voice) throws Exception {
        if (line != null) {
            line.stop();
            line.close();
            line = null;
        }
        if (readerThread != null) {
            readerThread.join(2000);
            readerThread = null;
        }

        byte[] pcm = buffer.toByteArray();
        buffer = null;
        byte[] wav = toWav(pcm);

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

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(req.build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("Transcription request failed (" + response.statusCode() + "): " + response.body());
        }

        return parseText(response.body());
    }

    @Override
    public void close() {
        if (line != null) {
            line.stop();
            line.close();
            line = null;
        }
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
