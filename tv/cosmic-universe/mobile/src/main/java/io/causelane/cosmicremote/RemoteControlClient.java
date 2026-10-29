package io.causelane.cosmicremote;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicLong;

/** Local-only encrypted command transport; no cloud endpoint or account is used. */
public final class RemoteControlClient extends WebSocketClient {
    public interface Listener { void onState(String state); }
    private final byte[] key;
    private volatile byte[] sessionKey;
    private final Listener listener;
    private final AtomicLong sequence = new AtomicLong(0);
    private final SecureRandom random = new SecureRandom();

    public RemoteControlClient(URI uri, byte[] key, Listener listener) {
        super(uri);
        if (key == null || key.length != 32) throw new IllegalArgumentException("Expected 256-bit pairing key");
        this.key = key.clone();
        this.listener = listener;
        setConnectionLostTimeout(8);
    }

    @Override public void onOpen(ServerHandshake handshake) { listener.onState("Connected; securing session…"); }
    @Override public void onMessage(String message) {
        try {
            JSONObject challengeMessage = new JSONObject(message);
            byte[] challenge = Base64.getDecoder().decode(challengeMessage.getString("challenge"));
            if (challenge.length != 16) throw new IllegalArgumentException("Invalid challenge");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            sessionKey = mac.doFinal(challenge);
            listener.onState("Secure local session ready");
        } catch (Exception e) {
            listener.onState("TV secure handshake failed");
            close();
        }
    }
    @Override public void onClose(int code, String reason, boolean remote) { listener.onState("Disconnected"); }
    @Override public void onError(Exception ex) { listener.onState("Connection error: " + ex.getClass().getSimpleName()); }

    public synchronized void sendCommand(JSONObject command) {
        if (!isOpen() || sessionKey == null) return;
        try {
            command.put("v", 1);
            command.put("seq", sequence.incrementAndGet());
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(sessionKey, "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(command.toString().getBytes(StandardCharsets.UTF_8));
            JSONObject envelope = new JSONObject();
            envelope.put("iv", Base64.getEncoder().encodeToString(iv));
            envelope.put("ct", Base64.getEncoder().encodeToString(encrypted));
            send(envelope.toString());
        } catch (Exception e) {
            listener.onState("Could not send command");
        }
    }
}
