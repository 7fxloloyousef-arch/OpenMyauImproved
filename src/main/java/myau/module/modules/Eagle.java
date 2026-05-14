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
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import org.apache.commons.lang3.RandomUtils;
import org.lwjgl.input.Keyboard;

import java.util.Objects;

public class Eagle extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();

    private int sneakDelay = 0;
    private boolean pendingInventoryClose = false;
    private boolean wasEdge = false;
    private long lastEdgeTime = 0L;

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

    private boolean shouldSneak() {
        if (!mc.thePlayer.onGround) {
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

    private boolean shouldActivate() {
        return shouldSneak() && (sneakDelay > 0 || isOnEdge());
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

        // close the inventory we opened last tick (sends the sneak packet then closes)
        if (pendingInventoryClose) {
            mc.thePlayer.closeScreen();
            pendingInventoryClose = false;
        }

        boolean onEdge = isOnEdge();

        // manage the delay timer
        if (sneakDelay > 0) {
            sneakDelay--;
        }

        if (onEdge && !wasEdge) {
            // just arrived at an edge — start a fresh delay
            sneakDelay = nextDelay();
            lastEdgeTime = System.currentTimeMillis();
        } else if (!onEdge && wasEdge) {
            // moved away from edge — reset
            sneakDelay = 0;
        } else if (onEdge && sneakDelay == 0) {
            // still on edge and delay expired — renew
            sneakDelay = nextDelay();
        }

        wasEdge = onEdge;

        // open inventory briefly so the server receives the sneak state change
        if (shouldActivate()) {
            mc.displayGuiScreen(new GuiInventory(mc.thePlayer));
            pendingInventoryClose = true;
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onMoveInput(MoveInputEvent event) {
        if (!isEnabled() || mc.thePlayer == null) {
            return;
        }

        if (mc.currentScreen != null) {
            return;
        }

        // when sneaking-only is on and the player is holding sneak, undo the vanilla
        // sneak slowdown first so we can reapply it ourselves with proper timing
        if (sneakOnly.getValue()
                && Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())
                && shouldSneak()) {
            mc.thePlayer.movementInput.sneak = false;
            mc.thePlayer.movementInput.moveForward /= 0.3F;
            mc.thePlayer.movementInput.moveStrafe /= 0.3F;
        }

        if (shouldActivate()) {
            if (!mc.thePlayer.movementInput.sneak) {
                mc.thePlayer.movementInput.sneak = true;
                mc.thePlayer.movementInput.moveForward *= 0.3F;
                mc.thePlayer.movementInput.moveStrafe *= 0.3F;
            }
        } else {
            // make sure sneak is released when conditions aren't met
            if (blocksOnly.getValue() && !hotbarHasBlocks()) {
                mc.thePlayer.movementInput.sneak = false;
            }
        }
    }

    @Override
    public void onDisabled() {
        sneakDelay = 0;
        wasEdge = false;
        lastEdgeTime = 0L;

        if (pendingInventoryClose) {
            if (mc.thePlayer != null) {
                mc.thePlayer.closeScreen();
            }
            pendingInventoryClose = false;
        }

        if (mc.currentScreen instanceof GuiInventory && mc.thePlayer != null) {
            mc.thePlayer.closeScreen();
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
