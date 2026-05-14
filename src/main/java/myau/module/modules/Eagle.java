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

    private boolean sequenceDone = false;   // true = fired once, wait for blocks
    private boolean didLookBack = false;    // true = look back already done
    private long sneakStartTime = 0L;      // when sneak started
    private boolean sneakingForBlocks = false; // true = currently in sneak phase
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
        didLookBack = false;
        sneakStartTime = 0L;
        sneakingForBlocks = false;
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

        // Blocks came back - reset so next time blocks run out it fires again
        if (!noBlocks) {
            resetSequence();
        }

        if (sneakOnly.getValue()
                && Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())
                && shouldSneak()) {
            mc.thePlayer.movementInput.sneak = false;
            mc.thePlayer.movementInput.moveForward /= 0.3F;
            mc.thePlayer.movementInput.moveStrafe /= 0.3F;
        }

        if (noBlocks) {
            // Sequence already fully done - do absolutely nothing until blocks return
            if (sequenceDone) {
                mc.thePlayer.movementInput.sneak = false;
                return;
            }

            // Start sneak phase once
            if (!sneakingForBlocks) {
                sneakingForBlocks = true;
                sneakStartTime = System.currentTimeMillis();
            }

            long elapsed = System.currentTimeMillis() - sneakStartTime;

            if (elapsed < SNEAK_DURATION_MS) {
                // Still in 0.7s sneak window
                mc.thePlayer.movementInput.sneak = true;
                mc.thePlayer.movementInput.moveStrafe *= 0.3F;
                mc.thePlayer.movementInput.moveForward *= 0.3F;
            } else {
                // 0.7s done - release sneak
                mc.thePlayer.movementInput.sneak = false;

                // Look back exactly once
                if (!didLookBack) {
                    mc.thePlayer.rotationYaw += 180.0F;
                    mc.thePlayer.rotationYawHead = mc.thePlayer.rotationYaw;
                    didLookBack = true;
                }

                // Lock sequence - nothing more until blocks return
                sequenceDone = true;
            }
            return;
        }

        // Normal eagle with blocks
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
