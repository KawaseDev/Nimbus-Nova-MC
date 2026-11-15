package cc.nimbusnova.mc.vehicle;

import cc.nimbusnova.mc.content.ModContent;
import cc.nimbusnova.mc.mission.MissionController;
import cc.nimbusnova.mc.world.MarsTrainingMap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class MarsRover extends VehicleEntity {
    private static final EntityDataAccessor<Float> DRIVE_SPEED = SynchedEntityData.defineId(MarsRover.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> STEERING = SynchedEntityData.defineId(MarsRover.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ENERGY = SynchedEntityData.defineId(MarsRover.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> CARGO = SynchedEntityData.defineId(MarsRover.class, EntityDataSerializers.INT);
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
        builder.define(ENERGY, 100.0F);
        builder.define(CARGO, 0);
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
        speed = nextSpeed(speed, throttle, getFirstPassenger() != null && battery() > 0, input.jump());
        speed = Math.clamp(speed, -0.14 * (1 - cargoCount() * 0.06), 0.32 * (1 - cargoCount() * 0.06));
        if (Math.abs(speed) > 0.005)
            setYRot(getYRot() + steering * (float) Math.clamp(speed / 0.32, -1, 1) * 3.5F);

        final double radians = Math.toRadians(getYRot());
        final double gravity = getGravity();
        final double vertical = onGround() ? -gravity : Math.max(-0.8, getDeltaMovement().y - gravity);
        setDeltaMovement(-Math.sin(radians) * speed, vertical, Math.cos(radians) * speed);
        final Vec3 before = position();
        move(MoverType.SELF, getDeltaMovement());
        entityData.set(ENERGY, nextEnergy(battery(), Math.hypot(getX() - before.x, getZ() - before.z), cargoCount()));
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

    private static float nextEnergy(final float current, final double distance, final int cargo) {
        return (float) Math.clamp(current - Math.max(0, distance) * (0.08 + Math.clamp(cargo, 0, 3) * 0.02), 0, 100);
    }

    public float battery() {
        return entityData.get(ENERGY);
    }

    public int cargoCount() {
        return entityData.get(CARGO);
    }

    public float steeringAngle() {
        return entityData.get(STEERING);
    }

    @Override
    public InteractionResult interact(final Player player, final InteractionHand hand, final Vec3 location) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level().isClientSide()) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer) || !player.isAlive() || player.isSpectator() || player.distanceToSqr(this) > 36) return InteractionResult.FAIL;
        if (player.isSecondaryUseActive()) {
            if (player.getMainHandItem().is(ModContent.ENGINEERING_TOOL)) {
                if (battery() >= 100) {
                    player.sendSystemMessage(Component.literal("Rover battery is full."));
                    return InteractionResult.SUCCESS_SERVER;
                }
                if (!level().dimension().equals(MarsTrainingMap.DIMENSION)
                        || distanceToSqr(28.5, 66, 25.5) > 64) {
                    player.sendSystemMessage(Component.literal("Park at the rover workshop to connect charging power."));
                    return InteractionResult.FAIL;
                }
                if (!MissionController.get(serverPlayer.level().getServer()).chargeVehicle(serverPlayer)) return InteractionResult.FAIL;
                entityData.set(ENERGY, Math.min(100, battery() + 25));
                player.sendSystemMessage(Component.literal("Rover charging transfer complete: up to +25 battery."));
                return InteractionResult.SUCCESS_SERVER;
            }
            if (cargoCount() == 0) {
                player.sendSystemMessage(Component.literal("Cargo bay is empty. Hold a Mars sample and use the rover to secure it."));
                return InteractionResult.SUCCESS_SERVER;
            }
            final ItemStack samples = new ItemStack(ModContent.MARS_SAMPLE, cargoCount());
            player.getInventory().add(samples);
            if (!samples.isEmpty() && !level().addFreshEntity(new ItemEntity(level(), getX(), getY() + 1, getZ(), samples.copy()))) {
                entityData.set(CARGO, samples.getCount());
                player.sendSystemMessage(Component.literal("Free inventory space before unloading the remaining samples."));
                return InteractionResult.FAIL;
            }
            entityData.set(CARGO, 0);
            player.sendSystemMessage(Component.literal("Sample cargo unloaded. Take it to the analyzer."));
            return InteractionResult.SUCCESS_SERVER;
        }
        if (player.getMainHandItem().is(ModContent.MARS_SAMPLE)) {
            // shortcut: the rack stores identical training samples; use item slots before adding distinct specimen data.
            if (!ItemStack.isSameItemSameComponents(player.getMainHandItem(), new ItemStack(ModContent.MARS_SAMPLE))) {
                player.sendSystemMessage(Component.literal("Keep named or customized specimens in your inventory; this rack carries standard training samples."));
                return InteractionResult.FAIL;
            }
            if (cargoCount() >= 3) {
                player.sendSystemMessage(Component.literal("The three-sample cargo bay is full."));
                return InteractionResult.FAIL;
            }
            player.getMainHandItem().shrink(1);
            entityData.set(CARGO, cargoCount() + 1);
            player.sendSystemMessage(Component.literal("Sample secured. Cargo mass increases driving energy and reduces speed."));
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!getPassengers().isEmpty()) {
            player.sendSystemMessage(Component.literal("The rover seat is occupied."));
            return InteractionResult.FAIL;
        }

        if (!player.startRiding(this)) return InteractionResult.FAIL;
        player.sendSystemMessage(Component.literal("Aster Rover: movement keys drive, jump brakes, sneak exits. Sneak-use to unload; sneak-use the engineering tool at the workshop to charge."));
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
    public void destroy(final ServerLevel level, final Item item) {
        if (!isAlive()) return;
        if (cargoCount() > 0) {
            if (!level.addFreshEntity(new ItemEntity(level, getX(), getY() + 1, getZ(), new ItemStack(ModContent.MARS_SAMPLE, cargoCount())))) return;
            entityData.set(CARGO, 0);
        }
        super.destroy(level, item);
    }

    @Override
    protected Item getDropItem() {
        return Items.IRON_INGOT;
    }

    @Override
    protected void readAdditionalSaveData(final ValueInput input) {
        final float stored = input.getFloatOr("RoverEnergy", 100);
        entityData.set(ENERGY, Float.isFinite(stored) ? Math.clamp(stored, 0, 100) : 0);
        entityData.set(CARGO, Math.clamp(input.getIntOr("RoverSamples", 0), 0, 3));
    }

    @Override
    protected void addAdditionalSaveData(final ValueOutput output) {
        output.putFloat("RoverEnergy", battery());
        output.putInt("RoverSamples", cargoCount());
    }

    public static void verifyDriveMath() {
        if (nextEnergy(100, 0, 3) != 100 || nextEnergy(0, 10, 0) != 0
                || nextEnergy(100, 1000, 3) != 0 || nextEnergy(100, 1, 3) >= nextEnergy(100, 1, 0))
            throw new IllegalStateException("Rover charge must be bounded and cargo must increase driving demand");
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
