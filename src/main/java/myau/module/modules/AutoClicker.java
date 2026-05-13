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

    // Track clicks so we can apply block-hit chance per hit
    private int clicksSinceLastBlockHit = 0;
    private int nextBlockHitAt = 0;

    public final IntProperty minCPS = new IntProperty("min-cps", 8, 1, 20);
    public final IntProperty maxCPS = new IntProperty("max-cps", 12, 1, 20);
    public final BooleanProperty blockHit = new BooleanProperty("block-hit", false);
    public final FloatProperty blockHitTicks = new FloatProperty("block-hit-ticks", 1.5F, 1.0F, 20.0F, this.blockHit::getValue);
    // Chance 0-100% that a block hit fires automatically when hitting a player
    public final IntProperty blockHitChance = new IntProperty("block-hit-chance", 50, 1, 100, this.blockHit::getValue);
    // Auto block hit - fires block hit automatically without needing RMB held
    public final BooleanProperty autoBlockHit = new BooleanProperty("auto-block-hit", true, this.blockHit::getValue);
    public final BooleanProperty weaponsOnly = new BooleanProperty("weapons-only", true);
    public final BooleanProperty allowTools = new BooleanProperty("allow-tools", false, this.weaponsOnly::getValue);
    public final BooleanProperty breakBlocks = new BooleanProperty("break-blocks", true);
    public final FloatProperty range = new FloatProperty("range", 3.0F, 3.0F, 8.0F, this.breakBlocks::getValue);
    public final FloatProperty hitBoxVertical = new FloatProperty("hit-box-vertical", 0.1F, 0.0F, 1.0F, this.breakBlocks::getValue);
    public final FloatProperty hitBoxHorizontal = new FloatProperty("hit-box-horizontal", 0.2F, 0.0F, 1.0F, this.breakBlocks::getValue);

    public AutoClicker() {
        super("AutoClicker", false);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private long getNextClickDelay() {
        return 1000L / RandomUtil.nextLong(this.minCPS.getValue(), this.maxCPS.getValue());
    }

    private long getBlockHitDelay() {
        return (long) (50.0F * this.blockHitTicks.getValue());
    }

    private boolean isBreakingBlock() {
        return mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK;
    }

    private boolean canClick() {
        if (!this.weaponsOnly.getValue()
                || ItemUtil.hasRawUnbreakingEnchant()
                || this.allowTools.getValue() && ItemUtil.isHoldingTool()) {
            if (this.breakBlocks.getValue() && this.isBreakingBlock() && !this.hasValidTarget()) {
                GameType gameType = mc.playerController.getCurrentGameType();
                return gameType != GameType.SURVIVAL && gameType != GameType.CREATIVE;
            } else {
                return true;
            }
        } else {
            return false;
        }
    }

    private boolean isValidTarget(EntityPlayer entityPlayer) {
        if (entityPlayer != mc.thePlayer && entityPlayer != mc.thePlayer.ridingEntity) {
            if (entityPlayer == mc.getRenderViewEntity()
                    || entityPlayer == mc.getRenderViewEntity().ridingEntity) {
                return false;
            } else if (entityPlayer.deathTime > 0) {
                return false;
            } else {
                float borderSize = entityPlayer.getCollisionBorderSize();
                return RotationUtil.rayTrace(
                        entityPlayer.getEntityBoundingBox().expand(
                                borderSize + this.hitBoxHorizontal.getValue(),
                                borderSize + this.hitBoxVertical.getValue(),
                                borderSize + this.hitBoxHorizontal.getValue()
                        ),
                        mc.thePlayer.rotationYaw,
                        mc.thePlayer.rotationPitch,
                        this.range.getValue()
                ) != null;
            }
        } else {
            return false;
        }
    }

    private boolean hasValidTarget() {
        return mc.theWorld
                .loadedEntityList
                .stream()
                .filter(e -> e instanceof EntityPlayer)
                .map(e -> (EntityPlayer) e)
                .anyMatch(this::isValidTarget);
    }

    /**
     * Roll the block-hit chance and schedule the next auto block-hit interval.
     * Uses the chance property so e.g. 50% means roughly every 2 hits a block-hit fires.
     */
    private void rollNextBlockHit() {
        // Convert chance % into a hit interval
        // e.g. 100% = every 1 click, 50% = every 2 clicks, 25% = every 4 clicks
        int chance = this.blockHitChance.getValue();
        // Random factor ±1 around the interval so it looks human
        int interval = Math.max(1, (int) Math.round(100.0 / chance));
        int jitter = interval > 1 ? random.nextInt(2) : 0;
        this.nextBlockHitAt = this.clicksSinceLastBlockHit + interval + jitter;
    }

    /**
     * Check if the auto block-hit should fire this click.
     */
    private boolean shouldAutoBlockHit() {
        if (!this.blockHit.getValue()) return false;
        if (!this.autoBlockHit.getValue()) return false;
        if (!ItemUtil.isHoldingSword()) return false;
        if (!this.hasValidTarget()) return false;
        if (this.blockHitDelay > 0L) return false;
        return this.clicksSinceLastBlockHit >= this.nextBlockHitAt;
    }

    /**
     * Fire the block-hit: briefly press RMB so the server sees a sword block,
     * then release it the next tick via blockHitPending.
     */
    private void fireBlockHit() {
        this.blockHitPending = true;
        this.blockHitDelay = this.blockHitDelay + this.getBlockHitDelay();
        KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
        if (!mc.thePlayer.isUsingItem()) {
            KeyBindUtil.pressKeyOnce(mc.gameSettings.keyBindUseItem.getKeyCode());
        }
        this.clicksSinceLastBlockHit = 0;
        rollNextBlockHit();
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.PRE) return;

        // Drain delays every tick regardless of enabled state
        if (this.clickDelay > 0L) {
            this.clickDelay -= 50L;
        }
        if (this.blockHitDelay > 0L) {
            this.blockHitDelay -= 50L;
        }

        if (mc.currentScreen != null) {
            this.clickPending = false;
            this.blockHitPending = false;
            return;
        }

        // Release pending key states from last tick
        if (this.clickPending) {
            this.clickPending = false;
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindAttack.getKeyCode());
        }
        if (this.blockHitPending) {
            this.blockHitPending = false;
            KeyBindUtil.updateKeyState(mc.gameSettings.keyBindUseItem.getKeyCode());
        }

        if (!this.isEnabled()) return;
        if (!this.canClick()) return;
        if (!mc.gameSettings.keyBindAttack.isKeyDown()) return;
        if (mc.thePlayer.isUsingItem()) return;

        // ── Left click spam ───────────────────────────────────────────────────
        while (this.clickDelay <= 0L) {
            this.clickPending = true;
            this.clickDelay += this.getNextClickDelay();
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
            KeyBindUtil.pressKeyOnce(mc.gameSettings.keyBindAttack.getKeyCode());

            // Track how many clicks since last block-hit
            this.clicksSinceLastBlockHit++;

            // Auto block-hit check - fires automatically based on chance %
            if (shouldAutoBlockHit()) {
                fireBlockHit();
                // Skip manual RMB check below since we already fired
                return;
            }
        }

        // ── Manual block-hit (RMB held) with chance gate ──────────────────────
        if (this.blockHit.getValue()
                && !this.autoBlockHit.getValue()
                && this.blockHitDelay <= 0L
                && mc.gameSettings.keyBindUseItem.isKeyDown()
                && ItemUtil.isHoldingSword()) {
            // Only fire if chance roll passes
            if (random.nextInt(100) < this.blockHitChance.getValue()) {
                fireBlockHit();
            }
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onCLick(LeftClickMouseEvent event) {
        if (this.isEnabled() && !event.isCancelled()) {
            if (!this.clickPending) {
                this.clickDelay += this.getNextClickDelay();
            }
        }
    }

    @Override
    public void onEnabled() {
        this.clickDelay = 0L;
        this.blockHitDelay = 0L;
        this.clicksSinceLastBlockHit = 0;
        rollNextBlockHit();
    }

    @Override
    public void onDisabled() {
        this.clicksSinceLastBlockHit = 0;
        this.nextBlockHitAt = 0;
    }

    @Override
    public void verifyValue(String mode) {
        if (this.minCPS.getName().equals(mode)) {
            if (this.minCPS.getValue() > this.maxCPS.getValue()) {
                this.maxCPS.setValue(this.minCPS.getValue());
            }
        } else {
            if (this.maxCPS.getName().equals(mode) && this.minCPS.getValue() > this.maxCPS.getValue()) {
                this.minCPS.setValue(this.maxCPS.getValue());
            }
        }
    }

    @Override
    public String[] getSuffix() {
        return Objects.equals(this.minCPS.getValue(), this.maxCPS.getValue())
                ? new String[]{this.minCPS.getValue().toString()}
                : new String[]{String.format("%d-%d", this.minCPS.getValue(), this.maxCPS.getValue())};
    }
}
