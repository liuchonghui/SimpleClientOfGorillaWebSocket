package android.test.clientsocket;

import android.compact.impl.TaskPayload;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.google.gson.Gson;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;

import tools.android.async2sync.Connection;
import tools.android.async2sync.Packet;
import tools.android.async2sync.PacketCollector;
import tools.android.async2sync.PacketFilter;
import tools.android.async2sync.RandomIdGenerator;

public class WebSocketClientManager {

    private String TAG = "WSCM";
    private static WebSocketClientManager instance;
    private WsClient wsClient;
    private OnMethodCallback methodCallback = new OnMethodCallback();
    private Connection mConnection = new Connection(5000L);

    public static WebSocketClientManager get() {
        if (instance == null) {
            synchronized (WebSocketClientManager.class) {
                if (instance == null) {
                    instance = new WebSocketClientManager();
                }
            }
        }
        return instance;
    }

    interface OnMethodListener {
        void onReceiveMethod(PacketType type, String msg);
    }

    class WsClient extends WebSocketClient {

        public WsClient(URI serverUri) {
            this(serverUri, new Draft_6455());
        }

        public WsClient(URI serverUri, Draft protocolDraft) {
            super(serverUri, protocolDraft);
        }

        OnMethodListener listener;

        public void setOnMethodListener(OnMethodListener l) {
            this.listener = l;
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
            Log.d(TAG, "onOpen|" + handshakedata.getHttpStatusMessage());
            if (listener != null) {
                listener.onReceiveMethod(PacketType.TYPE_OPEN, handshakedata.getHttpStatusMessage());
            }
        }

        @Override
        public void onMessage(String message) {
            Log.d(TAG, "onMessage|" + message);
            if (listener != null) {
                listener.onReceiveMethod(PacketType.TYPE_MSG, message);
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            Log.d(TAG, "onClose|" + reason);
            if (listener != null) {
                listener.onReceiveMethod(PacketType.TYPE_CLOSE, reason);
            }
        }

        @Override
        public void onError(Exception ex) {
            Log.d(TAG, "onError|" + ex.getMessage());
            if (listener != null) {
                listener.onReceiveMethod(PacketType.TYPE_ERROR, ex.getMessage());
            }
        }
    }

    class OnMethodCallback implements OnMethodListener {
        @Override
        public void onReceiveMethod(PacketType type, String msg) {
            Packet<TaskPayload> newPacket = new Packet<TaskPayload>();
            TaskPayload payload = new TaskPayload();
            if (!TextUtils.isEmpty(msg) && msg.startsWith("{") && msg.endsWith("}")) {
                payload = new Gson().fromJson(msg, TaskPayload.class);
            } else {
                payload.type = type.value;
                payload.msg = msg;
            }
            newPacket.setContent(payload);
            mConnection.processPacket(newPacket);
        }
    }

    enum PacketType {
        TYPE_OPEN("open"),
        TYPE_MSG("msg"),
        TYPE_CLOSE("close"),
        TYPE_ERROR("error");

        public String value;

        PacketType(String value) {
            this.value = value;
        }
    }

    public synchronized void init(String uriString) {
        URI uri = null;
        try {
            uri = new URI(uriString);
        } catch (Exception e) {
            Log.d("PPP", "Exception|" + e.getMessage());
        }
        if (uri == null) {
            return;
        }
        wsClient = new WsClient(uri);
        wsClient.setOnMethodListener(methodCallback);
    }

    public synchronized boolean syncConnect() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("cannot run in main thread");
        }
        if (wsClient == null) {
            throw new IllegalStateException("WebSocketClient has not init!");
        }

        TaskPayload payload = new TaskPayload();
        payload.type = PacketType.TYPE_OPEN.value;
        PacketTypeFilter filter = new PacketTypeFilter(PacketType.TYPE_OPEN);
        PacketCollector collector = mConnection.createPacketCollector(filter);

        wsClient.connect();

        final Packet<TaskPayload> packet = collector.nextResult(mConnection.getConnectionTimeOut());
        collector.cancel();
        if (packet != null && packet.getContent() != null) {
            TaskPayload resulPayload = packet.getContent();
            if (PacketType.TYPE_OPEN.value.equals(resulPayload.type)) {
                return true;
            }
        }
        return false;
    }

    public synchronized boolean syncDisconnect() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("cannot run in main thread");
        }
        if (wsClient == null || !wsClient.isOpen()) {
            Log.d(TAG, "isConnecting|" + wsClient.isConnecting() + "|isOpen|" + wsClient.isOpen() + "|isClosed|" + wsClient.isClosed());
            return false;
        }

        TaskPayload payload = new TaskPayload();
        payload.type = PacketType.TYPE_CLOSE.value;
        PacketTypeFilter filter = new PacketTypeFilter(PacketType.TYPE_CLOSE);
        PacketCollector collector = mConnection.createPacketCollector(filter);

        wsClient.close();

        final Packet<TaskPayload> packet = collector.nextResult(mConnection.getConnectionTimeOut());
        collector.cancel();
        if (packet != null && packet.getContent() != null) {
            TaskPayload resulPayload = packet.getContent();
            if (PacketType.TYPE_CLOSE.value.equals(resulPayload.type)) {
                return true;
            }
        }
        return false;
    }

    public synchronized TaskPayload syncSendMsg(String message) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("cannot run in main thread");
        }
        if (wsClient == null || !wsClient.isOpen()) {
            Log.d(TAG, "isConnecting|" + wsClient.isConnecting() + "|isOpen|" + wsClient.isOpen() + "|isClosed|" + wsClient.isClosed());
            return null;
        }

        String identify = RandomIdGenerator.randomId(8);
        TaskPayload payload = new TaskPayload();
        payload.identify = identify;
        payload.type = PacketType.TYPE_MSG.value;
        payload.msg = message;
        PacketFilter filter = new MessageFilter(identify, PacketType.TYPE_MSG);
        PacketCollector collector = mConnection.createPacketCollector(filter);

        wsClient.send(new Gson().toJson(payload));

        final Packet<TaskPayload> packet = collector.nextResult(mConnection.getConnectionTimeOut());
        collector.cancel();
        if (packet != null && packet.getContent() != null) {
            return packet.getContent();
        }
        return null;
    }

    class MessageFilter implements PacketFilter {
        PacketTypeFilter typeFilter;
        PacketIDFilter idFilter;

        MessageFilter(String msgId, PacketType type) {
            idFilter = new PacketIDFilter(msgId);
            typeFilter = new PacketTypeFilter(type);
        }

        @Override
        public boolean accept(Packet packet) {
            return idFilter.accept(packet) && typeFilter.accept(packet);
        }
    }

    class PacketTypeFilter implements PacketFilter {
        PacketType type;

        public PacketTypeFilter(PacketType type) {
            this.type = type;
        }

        @Override
        public boolean accept(Packet packet) {
            if (packet == null) {
                return false;
            }
            if (!(packet.getContent() instanceof TaskPayload)) {
                return false;
            }
            TaskPayload payloadContent = (TaskPayload) packet.getContent();
            if (this.type.value.equals(payloadContent.type)) {
                return true;
            }
            return false;
        }
    }

    class PacketIDFilter implements PacketFilter {

        String packetId;

        public PacketIDFilter(String packetId) {
            this.packetId = packetId;
        }

        @Override
        public boolean accept(Packet packet) {
            if (packet == null) {
                return false;
            }
            if (!(packet.getContent() instanceof TaskPayload)) {
                return false;
            }
            TaskPayload payloadContent = (TaskPayload) packet.getContent();
            if (TextUtils.isEmpty(payloadContent.identify)) {
                return false;
            }
            return payloadContent.identify.equals(this.packetId);
        }
    }
}
