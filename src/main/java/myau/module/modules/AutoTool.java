package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.TickEvent;
import myau.module.Module;
import myau.util.ItemUtil;
import myau.util.KeyBindUtil;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

public class AutoTool extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private int currentToolSlot = -1;
    private int previousSlot = -1;
    private int tickDelayCounter = 0;

    public final IntProperty switchDelay = new IntProperty("delay", 0, 0, 5);
    public final BooleanProperty switchBack = new BooleanProperty("switch-back", true);
    public final BooleanProperty sneakOnly = new BooleanProperty("sneak-only", true);
    public final BooleanProperty instantSwitch = new BooleanProperty("instant-switch", true);
    public final BooleanProperty instantSwitchBack = new BooleanProperty("instant-switch-back", true);

    public AutoTool() {
        super("AutoTool", false);
    }

    public boolean isKillAura() {
        KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        if (!killAura.isEnabled()) return false;
        return TeamUtil.isEntityLoaded(killAura.getTarget()) && killAura.isAttackAllowed();
    }

    /**
     * Switch to the best tool for the block being looked at.
     * Returns true if a switch happened.
     */
    private boolean switchToTool() {
        int slot = ItemUtil.findInventorySlot(
                mc.thePlayer.inventory.currentItem,
                mc.theWorld.getBlockState(mc.objectMouseOver.getBlockPos()).getBlock()
        );

        if (mc.thePlayer.inventory.currentItem != slot) {
            if (this.previousSlot == -1) {
                this.previousSlot = mc.thePlayer.inventory.currentItem;
            }
            mc.thePlayer.inventory.currentItem = this.currentToolSlot = slot;
            return true;
        }
        return false;
    }

    /**
     * Switch back to the previous slot immediately.
     */
    private void switchBack() {
        if (this.previousSlot != -1) {
            mc.thePlayer.inventory.currentItem = this.previousSlot;
        }
        resetState();
    }

    private void resetState() {
        this.currentToolSlot = -1;
        this.previousSlot = -1;
        this.tickDelayCounter = 0;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) return;

        // If player manually changed slot, cancel the auto tool
        if (this.currentToolSlot != -1 && this.currentToolSlot != mc.thePlayer.inventory.currentItem) {
            resetState();
            return;
        }

        boolean lookingAtBlock = mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK;
        boolean attacking = mc.gameSettings.keyBindAttack.isKeyDown()
                && !mc.thePlayer.isUsingItem()
                && !isKillAura();
        boolean sneakOk = !(Boolean) this.sneakOnly.getValue()
                || KeyBindUtil.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode());

        if (lookingAtBlock && attacking && sneakOk) {
            // Instant switch - bypass the delay entirely on first hit
            if (instantSwitch.getValue()) {
                switchToTool();
            } else {
                // Normal delay-based switch
                if (this.tickDelayCounter >= this.switchDelay.getValue()) {
                    switchToTool();
                }
                this.tickDelayCounter++;
            }
        } else {
            // Not attacking or not looking at block anymore
            if (this.switchBack.getValue() && this.previousSlot != -1) {
                if (instantSwitchBack.getValue()) {
                    // Switch back immediately same tick
                    switchBack();
                } else {
                    // Original behaviour - switch back next tick
                    mc.thePlayer.inventory.currentItem = this.previousSlot;
                    resetState();
                }
            } else {
                resetState();
            }
        }
    }

    @Override
    public void onDisabled() {
        // Switch back to previous slot before disabling
        if (this.switchBack.getValue() && this.previousSlot != -1) {
            mc.thePlayer.inventory.currentItem = this.previousSlot;
        }
        resetState();
    }
}
