package io.icker.factions.mixin;

import io.icker.factions.api.events.PlayerEvents;
import io.icker.factions.api.persistents.Faction;
import io.icker.factions.api.persistents.User;
import io.icker.factions.util.Message;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.filter.FilteredMessage;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.registry.RegistryKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayNetworkHandlerMixin {
    @Shadow
    public ServerPlayerEntity player;

    @Inject(method = "onPlayerMove", at = @At("HEAD"))
    public void onPlayerMove(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        PlayerEvents.ON_MOVE.invoker().onMove(player);
    }

    @Redirect(method = "handleDecoratedMessage", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/PlayerManager;broadcast(Lnet/minecraft/server/filter/FilteredMessage;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/registry/RegistryKey;)V"))
    private void replaceChatMessage(PlayerManager instance, FilteredMessage<SignedMessage> message, ServerPlayerEntity sender, RegistryKey<MessageType> typeKey) {
        User member = User.get(sender.getUuid());
        Faction faction = member != null ? member.getFaction() : null;

        boolean factionChat = member.chat == User.ChatMode.FACTION || member.chat == User.ChatMode.FOCUS;

        if (factionChat && faction == null) {
            new Message("You can't send a message to faction chat if you aren't in a faction.")
                .fail()
                .hover("Click to switch to global chat")
                .click("/factions settings chat global")
                .send(sender, false);
        } else {
            instance.broadcast(message.raw(), player -> {
                User targetMember = User.get(player.getUuid());
                Faction target = targetMember != null ? targetMember.getFaction() : null;

                if (member.chat == User.ChatMode.GLOBAL && targetMember.chat != User.ChatMode.FOCUS) {
                    return message.getFilterableFor(sender, player);
                }

                if (factionChat && target != null && target.getName().equals(faction.getName())) {
                    return message.getFilterableFor(sender, player);
                }

                return null;
            }, sender.asMessageSender(), typeKey);
        }
    }

    @Inject(method = "onPlayerInteractEntity", at = @At("HEAD"), cancellable = true)
    public void onPlayerInteractEntity(PlayerInteractEntityC2SPacket packet, CallbackInfo ci) {
        packet.handle(new PlayerInteractEntityC2SPacket.Handler() {
            @Override
            public void interact(Hand hand) {
                if (PlayerEvents.USE_ENTITY.invoker().onUseEntity(player, packet.getEntity(player.getWorld()), player.getWorld()) == ActionResult.FAIL) {
                    ci.cancel();
                }
            }

            @Override
            public void interactAt(Hand hand, Vec3d pos) {
                if (PlayerEvents.USE_ENTITY.invoker().onUseEntity(player, packet.getEntity(player.getWorld()), player.getWorld()) == ActionResult.FAIL) {
                    ci.cancel();
                }
            }

            @Override
            public void attack() {}
        });
    }
}
