package cc.nimbusnova.mc.npc;

import cc.nimbusnova.mc.mission.MissionController;
import cc.nimbusnova.mc.mission.MissionState;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class MissionAstronaut extends PathfinderMob {
    public MissionAstronaut(final EntityType<? extends MissionAstronaut> type, final Level level) {
        super(type, level);
        setPersistenceRequired();
        setPermanentlyInvulnerable(true);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new LookAtPlayerGoal(this, Player.class, 8, 1));
        goalSelector.addGoal(1, new RandomLookAroundGoal(this));
    }

    @Override
    protected InteractionResult mobInteract(final Player player, final InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        if (distanceToSqr(player) > 36 || !ServerPlayNetworking.canSend(serverPlayer, NpcDialoguePayload.TYPE))
            return InteractionResult.PASS;

        final MissionState.Snapshot state = MissionController.get(serverPlayer.level().getServer()).snapshot();
        ServerPlayNetworking.send(serverPlayer, new NpcDialoguePayload(getName().getString(),
                NpcDialogue.briefing(state), NpcDialogue.powerHint(state), NpcDialogue.oxygenHint(state)));
        return InteractionResult.SUCCESS_SERVER;
    }
}
