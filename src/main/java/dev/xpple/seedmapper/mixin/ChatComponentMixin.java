package dev.xpple.seedmapper.mixin;

import dev.xpple.seedmapper.util.SavedSeedChatCatcher;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"))
    private void seedmapper$captureSimple(Component component, CallbackInfo ci) {
        SavedSeedChatCatcher.capture(component);
    }

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V", at = @At("HEAD"))
    private void seedmapper$captureSigned(Component component, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
        SavedSeedChatCatcher.capture(component);
    }
}
