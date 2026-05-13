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

    // Track if we were holding a block last tick
    private boolean wasHoldingBlock = false;
    // Grace period ticks after losing blocks so sneak releases smoothly
    private int noBlockGrace = 0;
    private static final int NO_BLOCK_GRACE_TICKS = 3;

    public final IntProperty minDelay = new IntProperty("min-delay", 2, 0, 10);
    public final IntProperty maxDelay = new IntProperty("max-delay", 3, 0, 10);
    public final BooleanProperty directionCheck = new BooleanProperty("direction-check", true);
    public final BooleanProperty jumpCheck = new BooleanProperty("jump-check", true);
    public final BooleanProperty pitchCheck = new BooleanProperty("pitch-check", true);
    public final BooleanProperty blocksOnly = new BooleanProperty("blocks-only", true);
    public final BooleanProperty sneakOnly = new BooleanProperty("sneaking-only", false);
    public final BooleanProperty safeStop = new BooleanProperty("safe-stop", true);

    /**
     * Returns true if the player is currently holding a block item with at least 1 count.
     */
    private boolean isHoldingBlockWithItems() {
        ItemStack stack = mc.thePlayer.getHeldItem();
        return stack != null
                && stack.getItem() instanceof ItemBlock
                && stack.stackSize > 0;
    }

    private boolean canMoveSafely() {
        double[] offset = MoveUtil.predictMovement();
        return PlayerUtil.canMove(mc.thePlayer.motionX + offset[0], mc.thePlayer.motionZ + offset[1]);
    }

    private boolean shouldSneak() {
        if (this.directionCheck.getValue() && mc.gameSettings.keyBindForward.isKeyDown()) {
            return false;
        }
        if (this.jumpCheck.getValue() && mc.gameSettings.keyBindJump.isKeyDown()) {
            return false;
        }
        if (this.pitchCheck.getValue() && mc.thePlayer.rotationPitch < 69.0F) {
            return false;
        }
        if (sneakOnly.getValue() && !Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())) {
            return false;
        }

        // blocksOnly check - use our safe-stop aware block check
        if (this.blocksOnly.getValue()) {
            if (!isHoldingBlockWithItems()) {
                // If safe-stop is on and we just ran out of blocks,
                // allow the grace period to expire before stopping sneak
                // so the player doesn't fall mid-edge
                if (safeStop.getValue() && noBlockGrace > 0) {
                    return mc.thePlayer.onGround;
                }
                return false;
            }
        }

        return mc.thePlayer.onGround;
    }

    public Eagle() {
        super("Eagle", false);
    }

    @EventTarget(Priority.LOWEST)
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) return;

        // ── Safe-stop block tracking ──────────────────────────────────────────
        if (safeStop.getValue() && blocksOnly.getValue()) {
            boolean holdingNow = isHoldingBlockWithItems();

            if (wasHoldingBlock && !holdingNow) {
                // Just ran out of blocks - start grace period
                noBlockGrace = NO_BLOCK_GRACE_TICKS;
            } else if (holdingNow) {
                // Has blocks, reset grace
                noBlockGrace = 0;
            }

            if (noBlockGrace > 0) {
                noBlockGrace--;
            }

            wasHoldingBlock = holdingNow;
        }

        // ── Sneak delay tick ──────────────────────────────────────────────────
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

    @EventTarget(Priority.LOWEST)
    public void onMoveInput(MoveInputEvent event) {
        if (!this.isEnabled() || mc.currentScreen != null) return;

        // Handle sneakOnly mode - release sneak movement penalty if conditions met
        if (sneakOnly.getValue()
                && Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())
                && shouldSneak()) {
            mc.thePlayer.movementInput.sneak = false;
            mc.thePlayer.movementInput.moveForward /= 0.3F;
            mc.thePlayer.movementInput.moveStrafe /= 0.3F;
        }

        // Apply sneak if needed
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
        this.noBlockGrace = 0;
        this.wasHoldingBlock = false;
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
                break;
        }
    }

    @Override
    public String[] getSuffix() {
        return Objects.equals(this.minDelay.getValue(), this.maxDelay.getValue())
                ? new String[]{this.minDelay.getValue().toString()}
                : new String[]{String.format("%d-%d", this.minDelay.getValue(), this.maxDelay.getValue())};
    }
}
