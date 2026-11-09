package com.tiago.network;

import com.tiago.WtaMod;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A complete initial snapshot, or a delta containing changed and removed players. */
public record DimensionSyncPayload(boolean reset, Map<UUID, Identifier> dimensions, List<UUID> removed)
		implements CustomPacketPayload {
	public static final Type<DimensionSyncPayload> TYPE = new Type<>(WtaMod.id("dimensions_v1"));
	private static final int MAX_PLAYERS = 4096;
	public static final StreamCodec<RegistryFriendlyByteBuf, DimensionSyncPayload> CODEC =
			StreamCodec.of(DimensionSyncPayload::write, DimensionSyncPayload::read);

	public DimensionSyncPayload {
		dimensions = Map.copyOf(dimensions);
		removed = List.copyOf(removed);
		if (dimensions.size() > MAX_PLAYERS || removed.size() > MAX_PLAYERS) {
			throw new IllegalArgumentException("Too many players in WTA dimension update");
		}
	}

	private static void write(RegistryFriendlyByteBuf buf, DimensionSyncPayload payload) {
		buf.writeBoolean(payload.reset);
		buf.writeVarInt(payload.dimensions.size());
		payload.dimensions.forEach((id, dimension) -> {
			buf.writeUUID(id);
			buf.writeIdentifier(dimension);
		});
		buf.writeVarInt(payload.removed.size());
		payload.removed.forEach(buf::writeUUID);
	}

	private static DimensionSyncPayload read(RegistryFriendlyByteBuf buf) {
		boolean reset = buf.readBoolean();
		int count = readCount(buf);
		Map<UUID, Identifier> dimensions = new HashMap<>();
		for (int i = 0; i < count; i++) {
			dimensions.put(buf.readUUID(), buf.readIdentifier());
		}
		int removedCount = readCount(buf);
		List<UUID> removed = new ArrayList<>(removedCount);
		for (int i = 0; i < removedCount; i++) {
			removed.add(buf.readUUID());
		}
		return new DimensionSyncPayload(reset, dimensions, removed);
	}

	private static int readCount(RegistryFriendlyByteBuf buf) {
		int count = buf.readVarInt();
		if (count < 0 || count > MAX_PLAYERS) {
			throw new DecoderException("Invalid WTA player count: " + count);
		}
		return count;
	}

	@Override
	public Type<DimensionSyncPayload> type() {
		return TYPE;
	}
}
