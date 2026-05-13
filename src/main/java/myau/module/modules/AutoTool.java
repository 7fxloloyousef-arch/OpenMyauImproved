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
    // counts up each tick we are mining, switch happens at switchDelay ticks
    private int tickDelayCounter = 0;

    public final IntProperty switchDelay = new IntProperty("delay", 0, 0, 5);
    public final BooleanProperty switchBack = new BooleanProperty("switch-back", true);
    public final BooleanProperty sneakOnly = new BooleanProperty("sneak-only", true);

    public AutoTool() {
        super("AutoTool", false);
    }

    public boolean isKillAura() {
        KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        if (!killAura.isEnabled()) return false;
        return TeamUtil.isEntityLoaded(killAura.getTarget()) && killAura.isAttackAllowed();
    }

    private void resetState() {
        currentToolSlot = -1;
        previousSlot = -1;
        tickDelayCounter = 0;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.getType() != EventType.PRE) return;

        // Player manually scrolled away from the tool we picked - cancel
        if (currentToolSlot != -1 && currentToolSlot != mc.thePlayer.inventory.currentItem) {
            resetState();
            return;
        }

        boolean lookingAtBlock = mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK;
        boolean attacking = mc.gameSettings.keyBindAttack.isKeyDown()
                && !mc.thePlayer.isUsingItem()
                && !isKillAura();
        boolean sneakOk = !(Boolean) sneakOnly.getValue()
                || KeyBindUtil.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode());

        if (lookingAtBlock && attacking && sneakOk) {
            // Increment every tick we are actively mining
            tickDelayCounter++;

            // Only switch once the delay has been met
            // delay=0 means switch on tick 1 (instant), delay=1 means tick 2, etc.
            if (tickDelayCounter > switchDelay.getValue()) {
                int best = ItemUtil.findInventorySlot(
                        mc.thePlayer.inventory.currentItem,
                        mc.theWorld.getBlockState(mc.objectMouseOver.getBlockPos()).getBlock()
                );

                if (mc.thePlayer.inventory.currentItem != best) {
                    if (previousSlot == -1) {
                        previousSlot = mc.thePlayer.inventory.currentItem;
                    }
                    mc.thePlayer.inventory.currentItem = currentToolSlot = best;
                }
            }
        } else {
            // Stopped mining - switch back immediately
            if (switchBack.getValue() && previousSlot != -1) {
                mc.thePlayer.inventory.currentItem = previousSlot;
            }
            resetState();
        }
    }

    @Override
    public void onDisabled() {
        if (switchBack.getValue() && previousSlot != -1) {
            mc.thePlayer.inventory.currentItem = previousSlot;
        }
        resetState();
    }
}
