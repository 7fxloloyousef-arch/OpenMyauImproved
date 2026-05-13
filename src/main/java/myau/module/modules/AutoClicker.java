package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.LeftClickMouseEvent;
import myau.events.TickEvent;
import myau.module.Module;
import myau.util.*;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.property.properties.IntProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.WorldSettings.GameType;

import java.util.Objects;
import java.util.Random;

public class AutoClicker extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Random random = new Random();

    private boolean clickPending = false;
    private long clickDelay = 0L;
    private boolean blockHitPending = false;
    private long blockHitDelay = 0L;

    // counts clicks, fires block hit when it reaches clicksNeeded
    private int clickCounter = 0;
    private int clicksNeeded = 0;

    public final IntProperty minCPS = new IntProperty("min-cps", 8, 1, 20);
    public final IntProperty maxCPS = new IntProperty("max-cps", 12, 1, 20);

    public final BooleanProperty blockHit = new BooleanProperty("block-hit", false);
    public final FloatProperty blockHitTicks = new FloatProperty(
            "block-hit-ticks", 1.5F, 1.0F, 20.0F, this.blockHit::getValue);
    // 100 = every click gets a block hit, 1 = very rarely fires
    public final IntProperty blockHitChance = new IntProperty(
            "block-hit-chance", 50, 1, 100, this.blockHit::getValue);

    public final BooleanProperty weaponsOnly = new BooleanProperty("weapons-only", true);
    public final BooleanProperty allowTools = new BooleanProperty(
            "allow-tools", false, this.weaponsOnly::getValue);
    public final BooleanProperty breakBlocks = new BooleanProperty("break-blocks", true);
    public final FloatProperty range = new FloatProperty(
            "range", 3.0F, 3.0F, 8.0F, this.breakBlocks::getValue);
    public final FloatProperty hitBoxVertical = new FloatProperty(
            "hit-box-vertical", 0.1F, 0.0F, 1.0F, this.breakBlocks::getValue);
    public final FloatProperty hitBoxHorizontal = new FloatProperty(
            "hit-box-horizontal", 0.2F, 0.0F, 1.0F, this.breakBlocks::getValue);

    public AutoClicker() {
        super("AutoClicker", false);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private long getNextClickDelay() {
        return 1000L / RandomUtil.nextLong(minCPS.getValue(), maxCPS.getValue());
    }

    private long getBlockHitDelay() {
        return (long) (50.0F * blockHitTicks.getValue());
    }

    private boolean isBreakingBlock() {
        return mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK;
    }

    private boolean canClick() {
        if (!weaponsOnly.getValue()
                || ItemUtil.hasRawUnbreakingEnchant()
                || allowTools.getValue() && ItemUtil.isHoldingTool()) {
            if (breakBlocks.getValue() && isBreakingBlock() && !hasValidTarget()) {
                GameType gt = mc.playerController.getCurrentGameType();
                return gt != GameType.SURVIVAL && gt != GameType.CREATIVE;
            }
            return true;
        }
        return false;
    }

    private boolean isValidTarget(EntityPlayer p) {
        if (p == mc.thePlayer || p == mc.thePlayer.ridingEntity) return false;
        if (p == mc.getRenderViewEntity()
                || p == mc.getRenderViewEntity().ridingEntity) return false;
        if (p.deathTime > 0) return false;
        float b = p.getCollisionBorderSize();
        return RotationUtil.rayTrace(
                p.getEntityBoundingBox().expand(
                        b + hitBoxHorizontal.getValue(),
                        b + hitBoxVertical.getValue(),
                        b + hitBoxHorizontal.getValue()),
                mc.thePlayer.rotationYaw,
                mc.thePlayer.rotationPitch,
                range.getValue()
        ) != null;
    }

    private boolean hasValidTarget() {
        return mc.theWorld.loadedEntityList.stream()
                .filter(e -> e instanceof EntityPlayer)
                .map(e -> (EntityPlayer) e)
                .anyMatch(this::isValidTarget);
    }

    /**
     * Simple random roll against the chance property.
     * chance=100 always true, chance=1 almost never true.
     */
    private boolean rollChance() {
        return random.nextInt(100) < blockHitChance.getValue();
    }

    /**
     * Set how many clicks until we attempt the next block hit.
     * Higher chance = fewer clicks needed between hits.
     */
    private void resetClickCounter() {
        // interval: chance 100 -> 1 click, chance 50 -> 2 clicks, chance 1 -> 100 clicks
        int interval = Math.max(1, (int) Math.round(100.0 / blockHitChance.getValue()));
        // small random jitter so it looks human
        int jitter = interval > 1 ? random.nextInt(interval) : 0;
        clicksNeeded = interval + jitter;
        clickCounter = 0;
    }

    /**
     * Fire the block hit using exact same bypass as original.
     */
    private void fireBlockHit() {
        if (mc.thePlayer.isUsingItem()) return;
        blockHitPending = true;
        blockHitDelay += getBlockHitDelay();
        KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
        KeyBindUtil.pressKeyOnce(mc.gameSettings.keyBindUseItem.getKeyCode());
        resetClickCounter();
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.PRE) return;

        // Always drain delays every tick
        if (clickDelay > 0L) clickDelay -= 50L;
        if (blockHitDelay > 0L) blockHitDelay -= 50L;

        if (mc.currentScreen != null) {
            clickPending = false;
            blockHitPending = false;
            return;
        }

        // Release key states that were pressed last tick
        if (clickPending) {
            clickPending = false;
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindAttack.getKeyCode());
        }
        if (blockHitPending) {
            blockHitPending = false;
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
        }

        if (!isEnabled() || !canClick()) return;
        if (!mc.gameSettings.keyBindAttack.isKeyDown()) return;
        if (mc.thePlayer.isUsingItem()) return;

        // ── Left click ────────────────────────────────────────────────────────
        while (clickDelay <= 0L) {
            clickPending = true;
            clickDelay += getNextClickDelay();
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
            KeyBindUtil.pressKeyOnce(mc.gameSettings.keyBindAttack.getKeyCode());

            // ── Block hit logic ───────────────────────────────────────────────
            if (blockHit.getValue()
                    && ItemUtil.isHoldingSword()
                    && hasValidTarget()
                    && blockHitDelay <= 0L) {

                clickCounter++;

                // Only try when we have reached the needed click count
                if (clickCounter >= clicksNeeded) {
                    // Roll the actual chance - so even at the interval
                    // there is still a random gate so it never feels robotic
                    if (rollChance()) {
                        fireBlockHit();
                    } else {
                        // Failed the roll, wait another interval
                        resetClickCounter();
                    }
                    break;
                }
            }
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onCLick(LeftClickMouseEvent event) {
        if (isEnabled() && !event.isCancelled() && !clickPending) {
            clickDelay += getNextClickDelay();
        }
    }

    @Override
    public void onEnabled() {
        clickDelay = 0L;
        blockHitDelay = 0L;
        resetClickCounter();
    }

    @Override
    public void onDisabled() {
        clickCounter = 0;
        clicksNeeded = 0;
    }

    @Override
    public void verifyValue(String mode) {
        if (minCPS.getName().equals(mode)) {
            if (minCPS.getValue() > maxCPS.getValue()) maxCPS.setValue(minCPS.getValue());
        } else if (maxCPS.getName().equals(mode)) {
            if (minCPS.getValue() > maxCPS.getValue()) minCPS.setValue(maxCPS.getValue());
        }
    }

    @Override
    public String[] getSuffix() {
        return Objects.equals(minCPS.getValue(), maxCPS.getValue())
                ? new String[]{minCPS.getValue().toString()}
                : new String[]{String.format("%d-%d", minCPS.getValue(), maxCPS.getValue())};
    }
}
