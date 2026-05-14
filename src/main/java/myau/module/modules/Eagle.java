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

    private int sneakTicks = 0;
    private boolean wasEdge = false;

    public final IntProperty minDelay = new IntProperty("min-delay", 2, 0, 10);
    public final IntProperty maxDelay = new IntProperty("max-delay", 3, 0, 10);
    public final BooleanProperty directionCheck = new BooleanProperty("direction-check", true);
    public final BooleanProperty jumpCheck = new BooleanProperty("jump-check", true);
    public final BooleanProperty pitchCheck = new BooleanProperty("pitch-check", true);
    public final BooleanProperty blocksOnly = new BooleanProperty("blocks-only", true);
    public final BooleanProperty sneakOnly = new BooleanProperty("sneaking-only", false);

    public Eagle() {
        super("Eagle", false);
    }

    private boolean hotbarHasBlocks() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemBlock && stack.stackSize > 0) {
                return true;
            }
        }
        return false;
    }

    private boolean isOnEdge() {
        double[] offset = MoveUtil.predictMovement();
        return !PlayerUtil.canMove(
                mc.thePlayer.motionX + offset[0],
                mc.thePlayer.motionZ + offset[1]
        );
    }

    private boolean passesChecks() {
        if (!mc.thePlayer.onGround) {
            return false;
        }
        if (mc.currentScreen != null) {
            return false;
        }
        if (directionCheck.getValue() && mc.gameSettings.keyBindForward.isKeyDown()) {
            return false;
        }
        if (jumpCheck.getValue() && mc.gameSettings.keyBindJump.isKeyDown()) {
            return false;
        }
        if (pitchCheck.getValue() && mc.thePlayer.rotationPitch < 69.0F) {
            return false;
        }
        if (sneakOnly.getValue() && !Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())) {
            return false;
        }
        if (blocksOnly.getValue() && !hotbarHasBlocks()) {
            return false;
        }
        return true;
    }

    private int nextDelay() {
        int min = minDelay.getValue();
        int max = maxDelay.getValue();
        return min == max ? min : RandomUtils.nextInt(min, max + 1);
    }

    @EventTarget(Priority.LOWEST)
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        boolean onEdge = isOnEdge();

        if (onEdge && !wasEdge) {
            // just reached the edge — start sneak timer
            sneakTicks = nextDelay();
        } else if (!onEdge) {
            // safe ground — reset
            sneakTicks = 0;
        } else if (onEdge && sneakTicks > 0) {
            // counting down
            sneakTicks--;
        }

        wasEdge = onEdge;
    }

    @EventTarget(Priority.LOWEST)
    public void onMoveInput(MoveInputEvent event) {
        if (!isEnabled() || mc.thePlayer == null) {
            return;
        }

        if (!passesChecks()) {
            return;
        }

        boolean shouldSneak = isOnEdge() && sneakTicks == 0;

        // when sneaking-only mode is on, the player is already holding sneak,
        // so vanilla already applied the 0.3 slowdown — undo it first
        if (sneakOnly.getValue()
                && Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())) {
            mc.thePlayer.movementInput.sneak = false;
            mc.thePlayer.movementInput.moveForward /= 0.3F;
            mc.thePlayer.movementInput.moveStrafe /= 0.3F;
        }

        if (shouldSneak) {
            mc.thePlayer.movementInput.sneak = true;
            mc.thePlayer.movementInput.moveForward *= 0.3F;
            mc.thePlayer.movementInput.moveStrafe *= 0.3F;
        }
    }

    @Override
    public void onDisabled() {
        sneakTicks = 0;
        wasEdge = false;
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
