package cc.nimbusnova.mc.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** Built-in Minecraft UI cues so every minigame answers an input with sound as well as a picture. */
public final class NovaSounds {
    private NovaSounds() {
        /* w */
    }

    public static void tick(final float pitch) {
        play(SoundEvents.NOTE_BLOCK_HAT.value(), pitch, 0.35F);
    }

    public static void step(final float pitch) {
        play(SoundEvents.NOTE_BLOCK_BIT.value(), pitch, 0.4F);
    }

    public static void good() {
        play(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.6F, 0.55F);
    }

    public static void bad() {
        play(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7F, 0.6F);
    }

    public static void success() {
        play(SoundEvents.PLAYER_LEVELUP, 1.25F, 0.5F);
        play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.4F, 0.9F);
    }

    private static void play(final SoundEvent sound, final float pitch, final float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }
}
