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

    private boolean sequenceDone = false;
    private boolean sneakingForBlocks = false;
    private long sneakStartTime = 0L;
    private static final long SNEAK_DURATION_MS = 700L;

    public final IntProperty minDelay = new IntProperty("min-delay", 2, 0, 10);
    public final IntProperty maxDelay = new IntProperty("max-delay", 3, 0, 10);
    public final BooleanProperty directionCheck = new BooleanProperty("direction-check", true);
    public final BooleanProperty jumpCheck = new BooleanProperty("jump-check", true);
    public final BooleanProperty pitchCheck = new BooleanProperty("pitch-check", true);
    public final BooleanProperty blocksOnly = new BooleanProperty("blocks-only", true);
    public final BooleanProperty sneakOnly = new BooleanProperty("sneaking-only", false);

    private boolean hotbarHasBlocks() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null
                    && stack.getItem() instanceof ItemBlock
                    && stack.stackSize > 0) {
                return true;
            }
        }
        return false;
    }

    private boolean canMoveSafely() {
        double[] offset = MoveUtil.predictMovement();
        return PlayerUtil.canMove(mc.thePlayer.motionX + offset[0], mc.thePlayer.motionZ + offset[1]);
    }

    private boolean shouldSneak() {
        if (this.directionCheck.getValue() && mc.gameSettings.keyBindForward.isKeyDown()) {
            return false;
        } else if (this.jumpCheck.getValue() && mc.gameSettings.keyBindJump.isKeyDown()) {
            return false;
        } else if (this.pitchCheck.getValue() && mc.thePlayer.rotationPitch < 69.0F) {
            return false;
        } else if (sneakOnly.getValue() && !Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())) {
            return false;
        } else {
            return mc.thePlayer.onGround;
        }
    }

    private void resetSequence() {
        sequenceDone = false;
        sneakingForBlocks = false;
        sneakStartTime = 0L;
    }

    public Eagle() {
        super("Eagle", false);
    }

    @EventTarget(Priority.LOWEST)
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            if (this.sneakDelay > 0) {
                this.sneakDelay--;
            }
            if (this.sneakDelay == 0 && this.canMoveSafely()) {
                this.sneakDelay = RandomUtils.nextInt(
                        this.minDelay.getValue(),
                        this.maxDelay.getValue() + 1
                );
            }
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onMoveInput(MoveInputEvent event) {
        if (!this.isEnabled() || mc.currentScreen != null) return;

        boolean noBlocks = this.blocksOnly.getValue() && !hotbarHasBlocks();

        // Blocks came back - reset so sequence can fire again next time
        if (!noBlocks) {
            resetSequence();
        }

        if (noBlocks) {
            // Sequence fully done - do nothing until blocks return
            if (sequenceDone) {
                mc.thePlayer.movementInput.sneak = false;
                return;
            }

            // Start sneak timer once
            if (!sneakingForBlocks) {
                sneakingForBlocks = true;
                sneakStartTime = System.currentTimeMillis();
            }

            long elapsed = System.currentTimeMillis() - sneakStartTime;

            if (elapsed < SNEAK_DURATION_MS) {
                // Sneak for 0.7s - use same bypass as original
                mc.thePlayer.movementInput.sneak = true;
                mc.thePlayer.movementInput.moveStrafe *= 0.3F;
                mc.thePlayer.movementInput.moveForward *= 0.3F;
            } else {
                // 0.7s done - stop and lock
                mc.thePlayer.movementInput.sneak = false;
                sequenceDone = true;
            }
            return;
        }

        // Normal eagle when we have blocks - exact original logic
        if (sneakOnly.getValue()
                && Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())
                && shouldSneak()) {
            mc.thePlayer.movementInput.sneak = false;
            mc.thePlayer.movementInput.moveForward /= 0.3F;
            mc.thePlayer.movementInput.moveStrafe /= 0.3F;
        }

        if (!mc.thePlayer.movementInput.sneak) {
            if (this.shouldSneak() && (this.sneakDelay > 0 || this.canMoveSafely())) {
                mc.thePlayer.movementInput.sneak = true;
                mc.thePlayer.movementInput.moveStrafe *= 0.3F;
                mc.thePlayer.movementInput.moveForward *= 0.3F;
            }
        }
    }

    @Override
    public void onDisabled() {
        this.sneakDelay = 0;
        resetSequence();
    }

    @Override
    public void verifyValue(String name) {
        switch (name) {
            case "min-delay":
                if (this.minDelay.getValue() > this.maxDelay.getValue()) {
                    this.maxDelay.setValue(this.minDelay.getValue());
                }
                break;
            case "max-delay":
                if (this.minDelay.getValue() > this.maxDelay.getValue()) {
                    this.minDelay.setValue(this.maxDelay.getValue());
                }
        }
    }

    @Override
    public String[] getSuffix() {
        return Objects.equals(this.minDelay.getValue(), this.maxDelay.getValue())
                ? new String[]{this.minDelay.getValue().toString()}
                : new String[]{String.format("%d-%d", this.minDelay.getValue(), this.maxDelay.getValue())};
    }
}
