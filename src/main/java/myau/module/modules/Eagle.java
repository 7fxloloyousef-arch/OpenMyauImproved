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

    public Eagle() {
        super("Eagle", false);
    }

    // ── Hotbar scan ───────────────────────────────────────────────────────────

    /**
     * Scan all 9 hotbar slots and return true the moment we find
     * at least one ItemBlock with stackSize > 0.
     * Returns instantly on the first match so it is as fast as possible.
     */
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

    // ── Movement safety ───────────────────────────────────────────────────────

    private boolean canMoveSafely() {
        double[] offset = MoveUtil.predictMovement();
        return PlayerUtil.canMove(
                mc.thePlayer.motionX + offset[0],
                mc.thePlayer.motionZ + offset[1]
        );
    }

    // ── Core condition ────────────────────────────────────────────────────────

    /**
     * Every single condition that must be true for eagle to activate.
     * Called fresh every event so there is zero stale state.
     */
    private boolean shouldSneak() {
        // Only sneak on ground
        if (!mc.thePlayer.onGround) return false;

        // Hotbar block check - instant reaction, no grace period
        if (blocksOnly.getValue() && !hotbarHasBlocks()) return false;

        // Don't sneak while walking forward
        if (directionCheck.getValue()
                && mc.thePlayer.movementInput.moveForward > 0.0F) return false;

        // Don't sneak while jumping
        if (jumpCheck.getValue()
                && mc.gameSettings.keyBindJump.isKeyDown()) return false;

        // Don't sneak if not looking down enough
        if (pitchCheck.getValue()
                && mc.thePlayer.rotationPitch < 69.0F) return false;

        // sneakOnly: only eagle while the player is also holding sneak
        if (sneakOnly.getValue()
                && !Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())) return false;

        return true;
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @EventTarget(Priority.LOWEST)
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;

        if (sneakDelay > 0) {
            sneakDelay--;
        }

        // Refresh the sneak delay window each time it expires
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

        if (should && (sneakDelay > 0 || canMoveSafely())) {
            // Eagle is active - force sneak on
            // Also undo the vanilla sneak speed penalty so movement feels normal
            mc.thePlayer.movementInput.sneak = true;
            mc.thePlayer.movementInput.moveForward *= 0.3F;
            mc.thePlayer.movementInput.moveStrafe *= 0.3F;
        } else if (!should) {
            // Condition gone (no blocks, wrong pitch, moved forward, etc.)
            // Only release sneak if WE set it - don't touch it if the
            // player is holding sneak themselves in non-sneakOnly mode
            if (!Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())) {
                mc.thePlayer.movementInput.sneak = false;
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onDisabled() {
        sneakDelay = 0;
        if (mc.thePlayer != null
                && !Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())) {
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
