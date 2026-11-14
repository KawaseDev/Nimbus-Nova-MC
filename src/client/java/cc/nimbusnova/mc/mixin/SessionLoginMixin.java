package cc.nimbusnova.mc.mixin;

import cc.nimbusnova.mc.client.SessionIdentity;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(targets = "net.minecraft.client.gui.screens.ConnectScreen$1")
abstract class SessionLoginMixin {
    @Shadow @Final private ServerData val$server;
    @ModifyArgs(method = "run", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/login/ServerboundHelloPacket;<init>(Ljava/lang/String;Ljava/util/UUID;)V"))
    private void nimbusNova$sessionName(Args args) {
        String name = SessionIdentity.takeLogin(val$server);
        if (name == null) return;
        args.set(0, name);
        args.set(1, UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)));
    }
}
