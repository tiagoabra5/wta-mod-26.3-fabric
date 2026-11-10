package com.tiago.client.peer;

import net.minecraft.resources.Identifier;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Client-only discovery/state machine. No sockets, external services or server mod required. */
public final class PeerSession {
	public static final long EXPIRY_MS = 180_000;
	private static final long SEND_GAP_MS = 1_500;
	private static final long DISCOVERY_GAP_MS = 15_000;
	private static final int MAX_PEERS = 64;
	private final Map<UUID, Peer> peers = new LinkedHashMap<>();
	private final Map<UUID, PeerMessage.Kind> pending = new LinkedHashMap<>();
	private UUID session = UUID.randomUUID();
	private UUID self;
	private Identifier dimension;
	private Map<UUID, String> online = Map.of();
	private boolean enabled = true;
	private boolean announced;
	private long readyAt = -1;
	private long lastSend = -SEND_GAP_MS;
	private long lastDiscovery = -DISCOVERY_GAP_MS;
	private long sequence;

	public interface Transport {
		void announce(String message);
		void whisper(String playerName, String message);
	}

	private static final class Peer {
		UUID session;
		long sequence;
		Identifier dimension;
		long seenAt;
		long stateAt = -EXPIRY_MS;
		long sentAt = -EXPIRY_MS;
		long repliedAt = -DISCOVERY_GAP_MS;
		Peer(PeerMessage message, long now) {
			session = message.session();
			sequence = message.sequence();
			dimension = message.dimension();
			seenAt = now;
			if (message.dimension() != null) stateAt = now;
		}
	}

	public void tick(UUID self, Identifier dimension, Map<UUID, String> online, long now,
			boolean serverSync, Transport transport) {
		this.self = self;
		this.online = Map.copyOf(online);
		if (readyAt < 0) readyAt = now + 2_000;
		boolean changed = !dimension.equals(this.dimension);
		this.dimension = dimension;
		peers.entrySet().removeIf(entry -> !online.containsKey(entry.getKey()) || now - entry.getValue().seenAt >= EXPIRY_MS);
		pending.entrySet().removeIf(entry -> !online.containsKey(entry.getKey())
				|| (entry.getValue() == PeerMessage.Kind.STATE && !peers.containsKey(entry.getKey())));
		if (enabled && !serverSync) {
			if (!announced && now >= readyAt && online.size() > 1 && now - lastSend >= SEND_GAP_MS
					&& now - lastDiscovery >= DISCOVERY_GAP_MS) {
				transport.announce(message(PeerMessage.Kind.HELLO));
				announced = true;
				lastSend = lastDiscovery = now;
			}
			long heartbeat = Math.max(30_000, peers.size() * SEND_GAP_MS + 5_000);
			peers.forEach((id, peer) -> {
				if (changed || now - peer.sentAt >= heartbeat) pending.put(id, PeerMessage.Kind.STATE);
			});
		}
		if (serverSync) pending.clear();
		if (now - lastSend < SEND_GAP_MS || pending.isEmpty()) return;
		Iterator<Map.Entry<UUID, PeerMessage.Kind>> iterator = pending.entrySet().iterator();
		Map.Entry<UUID, PeerMessage.Kind> next = iterator.next();
		UUID id = next.getKey();
		PeerMessage.Kind kind = next.getValue();
		iterator.remove();
		String name = online.get(id);
		if (name == null || !name.matches("[A-Za-z0-9_]{1,16}")) return;
		transport.whisper(name, message(kind));
		lastSend = now;
		Peer peer = peers.get(id);
		if (peer != null) peer.sentAt = now;
	}

	public void receive(UUID sender, PeerMessage message, long now) {
		if (!enabled || sender.equals(self) || !online.containsKey(sender)) return;
		Peer peer = peers.get(sender);
		boolean newSession = peer == null || !peer.session.equals(message.session());
		if (!newSession && message.sequence() <= peer.sequence) return;
		if (message.kind() == PeerMessage.Kind.LEAVE) {
			if (!newSession) { peers.remove(sender); pending.remove(sender); }
			return;
		}
		if (peer == null && peers.size() >= MAX_PEERS) return;
		if (newSession) {
			long lastReply = peer == null ? -DISCOVERY_GAP_MS : peer.repliedAt;
			peer = new Peer(message, now);
			peer.repliedAt = lastReply;
			peers.put(sender, peer);
		} else {
			peer.sequence = message.sequence();
			peer.seenAt = now;
			if (message.dimension() != null) { peer.dimension = message.dimension(); peer.stateAt = now; }
		}
		if ((message.kind() == PeerMessage.Kind.HELLO || newSession) && now - peer.repliedAt >= DISCOVERY_GAP_MS) {
			pending.put(sender, PeerMessage.Kind.STATE);
			peer.repliedAt = now;
		}
	}

	private String message(PeerMessage.Kind kind) {
		return new PeerMessage(kind, session, sequence++, kind == PeerMessage.Kind.STATE ? dimension : null).encode();
	}

	public Identifier get(UUID id, long now) {
		Peer peer = peers.get(id);
		return enabled && peer != null && now - peer.stateAt < EXPIRY_MS ? peer.dimension : null;
	}

	public int peerCount() { return peers.size(); }
	public boolean isEnabled() { return enabled; }
	public void discover() { announced = false; }

	public void setEnabled(boolean enabled) {
		if (this.enabled == enabled) return;
		this.enabled = enabled;
		pending.clear();
		if (!enabled) {
			peers.keySet().forEach(id -> pending.put(id, PeerMessage.Kind.LEAVE));
			peers.clear();
		} else {
			reset();
		}
	}

	public void reset() {
		peers.clear();
		pending.clear();
		online = Map.of();
		self = null;
		dimension = null;
		session = UUID.randomUUID();
		sequence = 0;
		announced = false;
		readyAt = -1;
		lastSend = -SEND_GAP_MS;
		lastDiscovery = -DISCOVERY_GAP_MS;
	}
}
