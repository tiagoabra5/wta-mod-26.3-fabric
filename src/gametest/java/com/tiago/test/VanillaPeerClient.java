package com.tiago.test;

import com.mojang.authlib.GameProfile;
import com.tiago.client.peer.PeerMessage;
import com.tiago.client.peer.PeerSession;
import net.minecraft.commands.arguments.ArgumentSignatures;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.chat.MessageSignatureCache;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.configuration.*;
import net.minecraft.network.protocol.cookie.ClientboundCookieRequestPacket;
import net.minecraft.network.protocol.cookie.ServerboundCookieResponsePacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.protocol.login.*;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.resources.Identifier;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.SocketAddress;
import java.time.Instant;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/** A headless second client using normal Minecraft login, chat and /msg packets only. */
public final class VanillaPeerClient implements AutoCloseable {
	public static final String NAME = "WtaPeerTest";
	private final Connection connection;
	private final RegistryAccess registries;
	private final ConcurrentLinkedQueue<Packet<?>> incoming = new ConcurrentLinkedQueue<>();
	private final Map<UUID, String> online = new HashMap<>();
	private final MessageSignatureCache signatures = MessageSignatureCache.createDefault();
	private final PeerSession sharing = new PeerSession();
	private GameProfile profile;
	private Identifier dimension;
	private volatile String failure;
	private int chatCount;

	public VanillaPeerClient(SocketAddress address, RegistryAccess registries) {
		this.registries = registries;
		connection = Connection.connectToLocalServer(address);
		connection.initiateServerboundPlayConnection("localhost", 0, listener(ClientLoginPacketListener.class));
		connection.send(new ServerboundHelloPacket(NAME, UUID.randomUUID()));
	}

	private <T extends PacketListener> T listener(Class<T> type) {
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
			if (method.getName().equals("isAcceptingMessages")) return connection.isConnected();
			if (method.getName().equals("onDisconnect")) { failure = String.valueOf(args[0]); return null; }
			if (method.getName().equals("toString")) return "WTA test peer";
			if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
			if (method.getName().equals("equals")) return proxy == args[0];
			if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args == null ? new Object[0] : args);
			if (method.getName().startsWith("handle") && args != null && args.length == 1 && args[0] instanceof Packet<?> packet) {
				incoming.add(packet);
				return null;
			}
			throw new UnsupportedOperationException(method.toString());
		}));
	}

	public void tick() {
		if (failure != null) throw new AssertionError("Headless peer disconnected: " + failure);
		Packet<?> packet;
		while ((packet = incoming.poll()) != null) handle(packet);
		if (profile == null || dimension == null) return;
		sharing.tick(profile.id(), dimension, online, now(), false, new PeerSession.Transport() {
			public void announce(String message) {
				connection.send(new ServerboundChatPacket(message, Instant.now(), 0, Optional.empty(), acknowledgement()));
				chatCount++;
			}
			public void whisper(String name, String message) {
				connection.send(new ServerboundChatCommandSignedPacket("msg " + name + " " + message,
						Instant.now(), 0, ArgumentSignatures.EMPTY, acknowledgement()));
				chatCount++;
			}
		});
	}

	private static LastSeenMessages.Update acknowledgement() { return new LastSeenMessages.Update(0, new BitSet(20), (byte) 0); }
	private static long now() { return System.nanoTime() / 1_000_000; }

	private void handle(Packet<?> packet) {
		if (packet instanceof BundlePacket<?> bundle) {
			for (Packet<?> part : bundle.subPackets()) handle(part);
		} else if (packet instanceof ClientboundLoginFinishedPacket login) {
			profile = login.gameProfile();
			connection.setupInboundProtocol(ConfigurationProtocols.CLIENTBOUND, listener(ClientConfigurationPacketListener.class));
			connection.send(ServerboundLoginAcknowledgedPacket.INSTANCE);
			connection.setupOutboundProtocol(ConfigurationProtocols.SERVERBOUND);
		} else if (packet instanceof ClientboundCustomQueryPacket query) {
			connection.send(new ServerboundCustomQueryAnswerPacket(query.transactionId(), null));
		} else if (packet instanceof ClientboundSelectKnownPacks) {
			connection.send(new ServerboundSelectKnownPacks(List.of()));
		} else if (packet instanceof ClientboundFinishConfigurationPacket) {
			connection.setupInboundProtocol(GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(registries)), listener(ClientGamePacketListener.class));
			connection.send(ServerboundFinishConfigurationPacket.INSTANCE);
			connection.setupOutboundProtocol(GameProtocols.SERVERBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(registries), new GameProtocols.Context() {
				public boolean hasInfiniteMaterials() { return false; }
				public boolean canUseCommandBlocks() { return false; }
			}));
		} else if (packet instanceof ClientboundCookieRequestPacket cookie) {
			connection.send(new ServerboundCookieResponsePacket(cookie.key(), null));
		} else if (packet instanceof ClientboundKeepAlivePacket keepAlive) {
			connection.send(new ServerboundKeepAlivePacket(keepAlive.getId()));
		} else if (packet instanceof ClientboundPingPacket ping) {
			connection.send(new ServerboundPongPacket(ping.getId()));
		} else if (packet instanceof ClientboundLoginPacket login) {
			dimension = login.commonPlayerSpawnInfo().dimension().identifier();
		} else if (packet instanceof ClientboundRespawnPacket respawn) {
			dimension = respawn.commonPlayerSpawnInfo().dimension().identifier();
		} else if (packet instanceof ClientboundPlayerPositionPacket position) {
			var change = position.change();
			connection.send(new ServerboundAcceptTeleportationPacket(position.id(), change.position().x(),
					change.position().y(), change.position().z(), change.yRot(), change.xRot()));
		} else if (packet instanceof ClientboundChunkBatchFinishedPacket) {
			connection.send(new ServerboundChunkBatchReceivedPacket(64));
		} else if (packet instanceof ClientboundPlayerInfoUpdatePacket players) {
			for (var entry : players.entries()) if (entry.profile() != null) online.put(entry.profileId(), entry.profile().name());
		} else if (packet instanceof ClientboundPlayerInfoRemovePacket removed) {
			removed.profileIds().forEach(online::remove);
		} else if (packet instanceof ClientboundPlayerChatPacket chat) {
			var body = chat.body().unpack(signatures).orElseThrow();
			signatures.push(body, chat.signature().orElse(null));
			PeerMessage message = PeerMessage.decode(body.content());
			if (message != null) sharing.receive(chat.sender(), message, now());
		}
	}

	public UUID id() { return profile == null ? null : profile.id(); }
	public Identifier get(UUID player) { return sharing.get(player, now()); }
	public int messagesSent() { return chatCount; }
	public void setSharing(boolean enabled) { sharing.setEnabled(enabled); }
	public void close() { connection.disconnect(Component.literal("WTA peer test complete")); }
}
