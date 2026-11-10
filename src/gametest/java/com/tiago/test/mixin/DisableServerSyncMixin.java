package com.tiago.test.mixin;

import com.tiago.network.DimensionSyncPayload;
import com.tiago.test.PeerSharingGameTest;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ensures the peer test cannot accidentally pass using WTA's server packets. */
@Mixin(ServerCommonPacketListenerImpl.class)
public class DisableServerSyncMixin {
	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("HEAD"), cancellable = true)
	private void wtaTest$dropServerSync(Packet<?> packet, ChannelFutureListener listener, CallbackInfo ci) {
		if (PeerSharingGameTest.blockServerSync && packet instanceof ClientboundCustomPayloadPacket custom
				&& custom.payload() instanceof DimensionSyncPayload) ci.cancel();
	}
}
