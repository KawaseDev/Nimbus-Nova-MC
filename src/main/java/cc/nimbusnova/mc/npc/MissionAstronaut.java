package cc.nimbusnova.mc.npc;

import cc.nimbusnova.mc.ship.ShipController;
import cc.nimbusnova.mc.world.ship.ShipMap;

import cc.nimbusnova.mc.mission.MissionController;
import cc.nimbusnova.mc.mission.MissionState;
import cc.nimbusnova.mc.mission.MissionPreparation;
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

        if (serverPlayer.level().dimension().equals(ShipMap.DIMENSION)) {
            final var ship = ShipController.get(serverPlayer.level().getServer()).mission();
            if (ship != null)
                ServerPlayNetworking.send(serverPlayer, new NpcDialoguePayload(getName().getString(), ship.instruction(),
                        "Keep life support powered while isolating optional loads. Stored energy is finite.",
                        "Oxygen and CO2 are separate readings. A working filter removes CO2; adding oxygen does not."));
            return InteractionResult.SUCCESS_SERVER;
        }

        final MissionController controller = MissionController.get(serverPlayer.level().getServer());
        if (getType() == MissionNpcs.GUIDE && controller.snapshot().phase() == MissionState.Phase.READY) {
            final String stage = controller.progress(serverPlayer).stage();
            controller.interaction(serverPlayer, stage.equals("READY") ? MissionPreparation.TaskKind.LAUNCH
                    : stage.equals("DELIVER") ? MissionPreparation.TaskKind.DELIVER_SAMPLE : MissionPreparation.TaskKind.BRIEFING);
        }

        final MissionState.Snapshot state = controller.snapshot();
        ServerPlayNetworking.send(serverPlayer, new NpcDialoguePayload(getName().getString(),
                state.phase() == MissionState.Phase.READY ? controller.progress(serverPlayer).instruction()
                        + " " + controller.progress(serverPlayer).actionHint() : NpcDialogue.briefing(state, controller.role(serverPlayer.getUUID())),
                NpcDialogue.powerHint(state), NpcDialogue.oxygenHint(state)));
        return InteractionResult.SUCCESS_SERVER;
    }
}
