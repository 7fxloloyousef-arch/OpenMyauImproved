package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.MoveInputEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.util.MoveUtil;
import myau.util.PlayerUtil;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import org.apache.commons.lang3.RandomUtils;
import org.lwjgl.input.Keyboard;

import java.util.Objects;

public class Eagle extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private int sneakDelay = 0;

    public final IntProperty minDelay = new IntProperty("min-delay", 2, 0, 10);
    public final IntProperty maxDelay = new IntProperty("max-delay", 3, 0, 10);
    public final BooleanProperty directionCheck = new BooleanProperty("direction-check", true);
    public final BooleanProperty jumpCheck = new BooleanProperty("jump-check", true);
    public final BooleanProperty pitchCheck = new BooleanProperty("pitch-check", true);
    public final BooleanProperty blocksOnly = new BooleanProperty("blocks-only", true);
    public final BooleanProperty sneakOnly = new BooleanProperty("sneaking-only", false);

    /**
     * Count every block item stack across the entire hotbar (slots 0-8).
     * Returns the total number of block items found.
     */
    private int countHotbarBlocks() {
        int count = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemBlock && stack.stackSize > 0) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns true if the player has at least 1 block anywhere in their hotbar.
     */
    private boolean hotbarHasBlocks() {
        return countHotbarBlocks() > 0;
    }

    private boolean canMoveSafely() {
        double[] offset = MoveUtil.predictMovement();
        return PlayerUtil.canMove(
                mc.thePlayer.motionX + offset[0],
                mc.thePlayer.motionZ + offset[1]
        );
    }

    /**
     * Core check - should we be sneaking right now?
     * All conditions must pass. If blocksOnly is on, hotbar is scanned
     * every call so the moment blocks hit 0 this returns false immediately.
     */
    private boolean shouldSneak() {
        // Must be on ground
        if (!mc.thePlayer.onGround) return false;

        // Direction check
        if (directionCheck.getValue() && mc.gameSettings.keyBindForward.isKeyDown()) return false;

        // Jump check
        if (jumpCheck.getValue() && mc.gameSettings.keyBindJump.isKeyDown()) return false;

        // Pitch check
        if (pitchCheck.getValue() && mc.thePlayer.rotationPitch < 69.0F) return false;

        // Sneak-only check
        if (sneakOnly.getValue()
                && !Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())) return false;

        // Hotbar block check - scans every call so it reacts instantly
        if (blocksOnly.getValue() && !hotbarHasBlocks()) return false;

        return true;
    }

    public Eagle() {
        super("Eagle", false);
    }

    @EventTarget(Priority.LOWEST)
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;

        if (sneakDelay > 0) {
            sneakDelay--;
        }

        if (sneakDelay == 0 && canMoveSafely()) {
            sneakDelay = RandomUtils.nextInt(
                    minDelay.getValue(),
                    maxDelay.getValue() + 1
            );
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onMoveInput(MoveInputEvent event) {
        if (!isEnabled() || mc.currentScreen != null) return;

        boolean should = shouldSneak();

        // sneakOnly mode - undo the vanilla sneak movement speed penalty
        // while our sneak is active so the player moves at normal speed
        if (sneakOnly.getValue()
                && Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())
                && should) {
            mc.thePlayer.movementInput.sneak = false;
            mc.thePlayer.movementInput.moveForward /= 0.3F;
            mc.thePlayer.movementInput.moveStrafe /= 0.3F;
        }

        if (!mc.thePlayer.movementInput.sneak && should
                && (sneakDelay > 0 || canMoveSafely())) {
            mc.thePlayer.movementInput.sneak = true;
            mc.thePlayer.movementInput.moveStrafe *= 0.3F;
            mc.thePlayer.movementInput.moveForward *= 0.3F;
        }

        // Key moment - if shouldSneak() is false (no blocks, wrong pitch, etc.)
        // force sneak off immediately so the player stops eagle instantly
        if (!should) {
            mc.thePlayer.movementInput.sneak = false;
        }
    }

    @Override
    public void onDisabled() {
        sneakDelay = 0;
        if (mc.thePlayer != null) {
            mc.thePlayer.movementInput.sneak = false;
        }
    }

    @Override
    public void verifyValue(String name) {
        switch (name) {
            case "min-delay":
                if (minDelay.getValue() > maxDelay.getValue()) {
                    maxDelay.setValue(minDelay.getValue());
                }
                break;
            case "max-delay":
                if (minDelay.getValue() > maxDelay.getValue()) {
                    minDelay.setValue(maxDelay.getValue());
                }
                break;
        }
    }

    @Override
    public String[] getSuffix() {
        return Objects.equals(minDelay.getValue(), maxDelay.getValue())
                ? new String[]{minDelay.getValue().toString()}
                : new String[]{String.format("%d-%d", minDelay.getValue(), maxDelay.getValue())};
    }
}
