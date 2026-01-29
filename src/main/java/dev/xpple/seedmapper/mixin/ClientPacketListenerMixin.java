package dev.xpple.seedmapper.mixin;

import dev.xpple.seedmapper.config.Configs;
import dev.xpple.seedmapper.command.commands.DatapackImportCommand;
import dev.xpple.seedmapper.render.RenderManager;
import dev.xpple.seedmapper.seedmap.SeedMapMinimapManager;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @Inject(method = "handleLogin", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V", shift = At.Shift.AFTER))
    private void onHandleLogin(ClientboundLoginPacket packet, CallbackInfo ci) {
        RenderManager.clear();

        SeedMapMinimapManager.hide();
    }

    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void seedmapper$applySavedSeed(ClientboundLoginPacket packet, CallbackInfo ci) {
        Configs.loadSavedSeedForCurrentServer();
        Configs.loadWorldBorderForCurrentServer();
        if (Configs.DatapackAutoload) {
            String url = Configs.getSavedDatapackUrlForCurrentServer();
            if (url != null && !url.isBlank()) {
                DatapackImportCommand.importUrlForCurrentServer(url);
            }
        }
    }

    @Inject(method = "handleRespawn", at = @At("HEAD"))
    private void onHandleRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
        RenderManager.clear();

        SeedMapMinimapManager.refreshIfOpen();
    }
}
