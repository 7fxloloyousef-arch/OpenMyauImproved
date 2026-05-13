package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.MoveInputEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.util.ItemUtil;
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
    private boolean wasHoldingBlock = false;
    private int noBlockGraceTicks = 0;
    private static final int GRACE_TICKS = 2;

    public final IntProperty minDelay = new IntProperty("min-delay", 2, 0, 10);
    public final IntProperty maxDelay = new IntProperty("max-delay", 3, 0, 10);
    public final BooleanProperty directionCheck = new BooleanProperty("direction-check", true);
    public final BooleanProperty jumpCheck = new BooleanProperty("jump-check", true);
    public final BooleanProperty pitchCheck = new BooleanProperty("pitch-check", true);
    public final BooleanProperty blocksOnly = new BooleanProperty("blocks-only", true);
    public final BooleanProperty sneakOnly = new BooleanProperty("sneaking-only", false);
    public final BooleanProperty safeStop = new BooleanProperty("safe-stop", true);

    private boolean isHoldingBlockWithItems() {
        ItemStack stack = mc.thePlayer.getHeldItem();
        return stack != null
                && stack.getItem() instanceof ItemBlock
                && stack.stackSize > 0;
    }

    private boolean canMoveSafely() {
        double[] offset = MoveUtil.predictMovement();
        return PlayerUtil.canMove(
                mc.thePlayer.motionX + offset[0],
                mc.thePlayer.motionZ + offset[1]
        );
    }

    private boolean hasBlocks() {
        if (!blocksOnly.getValue()) return true;
        boolean holding = isHoldingBlockWithItems();
        // Grace period so sneak doesn't instantly release when blocks run out
        if (wasHoldingBlock && !holding) {
            noBlockGraceTicks = GRACE_TICKS;
        }
        wasHoldingBlock = holding;
        if (!holding && noBlockGraceTicks > 0) {
            return true; // still in grace, act as if we have blocks
        }
        return holding;
    }

    private boolean shouldSneak() {
        if (!mc.thePlayer.onGround) return false;
        if (directionCheck.getValue() && mc.gameSettings.keyBindForward.isKeyDown()) return false;
        if (jumpCheck.getValue() && mc.gameSettings.keyBindJump.isKeyDown()) return false;
        if (pitchCheck.getValue() && mc.thePlayer.rotationPitch < 69.0F) return false;
        if (sneakOnly.getValue() && !Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())) return false;
        if (!hasBlocks()) return false;
        return true;
    }

    public Eagle() {
        super("Eagle", false);
    }

    @EventTarget(Priority.LOWEST)
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;

        // Drain grace ticks
        if (noBlockGraceTicks > 0) {
            noBlockGraceTicks--;
        }

        if (sneakDelay > 0) {
            sneakDelay--;
        }
        if (sneakDelay == 0 && canMoveSafely()) {
            sneakDelay = RandomUtils.nextInt(minDelay.getValue(), maxDelay.getValue() + 1);
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onMoveInput(MoveInputEvent event) {
        if (!isEnabled() || mc.currentScreen != null) return;

        boolean should = shouldSneak();

        // sneakOnly mode - undo vanilla sneak speed penalty while our sneak is active
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
    }

    @Override
    public void onDisabled() {
        sneakDelay = 0;
        noBlockGraceTicks = 0;
        wasHoldingBlock = false;
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
