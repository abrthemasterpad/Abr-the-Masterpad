package io.causelane.cosmic;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONObject;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived, opt-in local remote-control channel.
 * Messages are AES-GCM encrypted with a random per-session key shown only in the pairing QR.
 * This is a prototype transport: production hardening still needs TLS/server identity pinning,
 * explicit LAN threat review, rate-limit testing, and device verification.
 */
public final class RemoteControlServer extends WebSocketServer {
    public interface Handler { void onCommand(JSONObject command); void onPeerCountChanged(int count); }
    private final byte[] key;
    private final Handler handler;
    private final Map<WebSocket, Long> lastSequence = new ConcurrentHashMap<>();
    private final Map<WebSocket, byte[]> connectionKeys = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public RemoteControlServer(int port, byte[] key, Handler handler) {
        super(new InetSocketAddress("0.0.0.0", port));
        if (key == null || key.length != 32) throw new IllegalArgumentException("Expected 256-bit session key");
        this.key = key.clone();
        this.handler = handler;
        setConnectionLostTimeout(8);
    }

    @Override public void onOpen(WebSocket conn, ClientHandshake handshake) {
        try {
            byte[] challenge = new byte[16];
            random.nextBytes(challenge);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            connectionKeys.put(conn, mac.doFinal(challenge));
            lastSequence.put(conn, -1L);
            JSONObject challengeMessage = new JSONObject();
            challengeMessage.put("challenge", Base64.getEncoder().encodeToString(challenge));
            conn.send(challengeMessage.toString());
        } catch (Exception e) {
            conn.close(1008, "Secure handshake failed");
        }
        handler.onPeerCountChanged(getConnections().size());
    }

    @Override public void onMessage(WebSocket conn, String message) {
        try {
            if (message == null || message.length() > 65536) return;
            JSONObject envelope = new JSONObject(message);
            byte[] iv = Base64.getDecoder().decode(envelope.getString("iv"));
            byte[] ciphertext = Base64.getDecoder().decode(envelope.getString("ct"));
            if (iv.length != 12 || ciphertext.length > 48000) return;
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] sessionKey = connectionKeys.get(conn);
            if (sessionKey == null) return;
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(sessionKey, "AES"), new GCMParameterSpec(128, iv));
            byte[] clear = cipher.doFinal(ciphertext);
            JSONObject command = new JSONObject(new String(clear, StandardCharsets.UTF_8));
            long seq = command.optLong("seq", -1L);
            long previous = lastSequence.getOrDefault(conn, -1L);
            if (seq <= previous || seq < 0) return; // reject duplicate/replayed/out-of-order commands
            lastSequence.put(conn, seq);
            String type = command.optString("type", "");
            if (!isAllowedType(type)) return;
            handler.onCommand(command);
        } catch (Exception ignored) {
            // Invalid/authentication-failed messages are dropped; do not leak secret or payload.
        }
    }

    private boolean isAllowedType(String type) {
        return "pointer_delta".equals(type) || "pointer_tap".equals(type)
                || "key".equals(type) || "text_input".equals(type)
                || "stroke_begin".equals(type) || "stroke_points".equals(type)
                || "stroke_end".equals(type) || "stroke_cancel".equals(type)
                || "voice_text".equals(type) || "ping".equals(type)
                || "disconnect".equals(type) || "camera_frame".equals(type) || "camera_stop".equals(type);
    }

    @Override public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        lastSequence.remove(conn);
        connectionKeys.remove(conn);
        handler.onPeerCountChanged(getConnections().size());
    }
    @Override public void onError(WebSocket conn, Exception ex) { /* surfaced through peer count/diagnostics in later pass */ }
    @Override public void onStart() { }
    public void closeSafely() {
        try { stop(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
