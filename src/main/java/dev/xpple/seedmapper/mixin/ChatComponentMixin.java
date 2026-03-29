package dev.xpple.seedmapper.mixin;

import dev.xpple.seedmapper.util.SavedSeedChatCatcher;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {

    @Inject(method = "addClientSystemMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"))
    private void seedmapper$captureClientSystem(Component component, CallbackInfo ci) {
        SavedSeedChatCatcher.capture(component);
    }

    @Inject(method = "addServerSystemMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"))
    private void seedmapper$captureServerSystem(Component component, CallbackInfo ci) {
        SavedSeedChatCatcher.capture(component);
    }

    @Inject(method = "addPlayerMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V", at = @At("HEAD"))
    private void seedmapper$capturePlayer(Component component, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
        SavedSeedChatCatcher.capture(component);
    }
}

