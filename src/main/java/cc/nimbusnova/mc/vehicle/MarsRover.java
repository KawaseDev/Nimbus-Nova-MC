package cc.nimbusnova.mc.vehicle;

import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class MarsRover extends VehicleEntity {
    private static final EntityDataAccessor<Float> DRIVE_SPEED = SynchedEntityData.defineId(MarsRover.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> STEERING = SynchedEntityData.defineId(MarsRover.class, EntityDataSerializers.FLOAT);
    private double speed;
    public float wheelRotation;

    public MarsRover(final EntityType<? extends MarsRover> type, final Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(final SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DRIVE_SPEED, 0.0F);
        builder.define(STEERING, 0.0F);
    }

    @Override
    protected InterpolationHandler createInterpolationHandler() {
        return LinearInterpolationHandler.create(this, 3);
    }

    @Override
    public void tick() {
        super.tick();
        wheelRotation += entityData.get(DRIVE_SPEED) * 3.2F;
        if (level().isClientSide()) {
            getInterpolation().interpolate();
            return;
        }

        // The single seat supplies input; vanilla controlling-passenger remains null to reject client-position authority.
        final Input input = getFirstPassenger() instanceof ServerPlayer driver ? driver.getLastClientInput() : Input.EMPTY;
        final int throttle = (input.forward() ? 1 : 0) - (input.backward() ? 1 : 0);
        final int steering = (input.left() ? -1 : 0) + (input.right() ? 1 : 0);
        speed = nextSpeed(speed, throttle, getFirstPassenger() != null, input.jump());
        if (Math.abs(speed) > 0.005)
            setYRot(getYRot() + steering * (float) Math.clamp(speed / 0.32, -1, 1) * 3.5F);

        final double radians = Math.toRadians(getYRot());
        final double gravity = getGravity();
        final double vertical = onGround() ? -gravity : Math.max(-0.8, getDeltaMovement().y - gravity);
        setDeltaMovement(-Math.sin(radians) * speed, vertical, Math.cos(radians) * speed);
        move(MoverType.SELF, getDeltaMovement());
        if (horizontalCollision)
            speed = 0;

        entityData.set(DRIVE_SPEED, (float) speed);
        entityData.set(STEERING, steering * 0.38F);
    }

    @Override
    protected double getDefaultGravity() {
        return 0.08;
    }

    public static double nextSpeed(final double current, final int throttle, final boolean occupied, final boolean brake) {
        if (!occupied) return 0;
        if (brake) return current * 0.6;
        return Math.clamp(throttle == 0 ? current * 0.86 : current + Math.clamp(throttle, -1, 1) * 0.018, -0.14, 0.32);
    }

    public float steeringAngle() {
        return entityData.get(STEERING);
    }

    @Override
    public InteractionResult interact(final Player player, final InteractionHand hand, final Vec3 location) {
        if (player.isSecondaryUseActive()) return InteractionResult.PASS;
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        if (!getPassengers().isEmpty()) {
            player.sendSystemMessage(Component.literal("The rover seat is occupied."));
            return InteractionResult.FAIL;
        }

        if (!player.startRiding(this)) return InteractionResult.FAIL;
        player.sendSystemMessage(Component.literal("Aster Rover: movement keys steer and drive, jump brakes, sneak exits."));
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    protected boolean canAddPassenger(final Entity passenger) {
        return getPassengers().isEmpty() && passenger instanceof Player;
    }

    @Override
    public Vec3 getPassengerRidingPosition(final Entity passenger) {
        return position().add(0, 0.72, 0);
    }

    @Override
    public boolean isPickable() {
        return isAlive();
    }

    @Override
    public boolean canCollideWith(final Entity other) {
        return !isPassengerOfSameVehicle(other) && other.isPushable();
    }

    @Override
    public float maxUpStep() {
        return 1.0F;
    }

    @Override
    protected Item getDropItem() {
        return Items.IRON_INGOT;
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        /* w */
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        /* w */
    }

    public static void verifyDriveMath() {
        double forward = 0, reverse = 0;
        for (int tick = 0; tick < 200; tick++) {
            forward = nextSpeed(forward, 1, true, false);
            reverse = nextSpeed(reverse, -1, true, false);
        }

        if (forward != 0.32 || reverse != -0.14 || nextSpeed(forward, 1, false, false) != 0
                || nextSpeed(forward, 1, true, true) >= forward || nextSpeed(forward, 0, true, false) >= forward)
            throw new IllegalStateException("Rover speed bounds, empty-seat stop, brake or coasting check failed");
    }
}
