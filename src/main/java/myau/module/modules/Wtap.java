package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.event.types.Priority;
import myau.events.MoveInputEvent;
import myau.events.PacketEvent;
import myau.module.Module;
import myau.util.TimerUtil;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.potion.Potion;

public class Wtap extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final TimerUtil timer = new TimerUtil();
    private boolean active = false;
    private boolean stopForward = false;
    private long delayTicks = 0L;
    private long durationTicks = 0L;

    public final FloatProperty delay = new FloatProperty("delay", 5.5F, 0.0F, 10.0F);
    public final FloatProperty duration = new FloatProperty("duration", 1.5F, 1.0F, 5.0F);
    public final FloatProperty cooldown = new FloatProperty("cooldown", 500.0F, 100.0F, 1000.0F);
    public final BooleanProperty onlyOnGround = new BooleanProperty("only-on-ground", false);

    private boolean canTrigger() {
        return !(mc.thePlayer.movementInput.moveForward < 0.8F)
                && !mc.thePlayer.isCollidedHorizontally
                && (!((float) mc.thePlayer.getFoodStats().getFoodLevel() <= 6.0F)
                || mc.thePlayer.capabilities.allowFlying)
                && (mc.thePlayer.isSprinting()
                || !mc.thePlayer.isUsingItem()
                && !mc.thePlayer.isPotionActive(Potion.blindness)
                && mc.gameSettings.keyBindSprint.isKeyDown())
                && (!onlyOnGround.getValue() || mc.thePlayer.onGround);
    }

    private void resetState() {
        active = false;
        stopForward = false;
        delayTicks = 0L;
        durationTicks = 0L;
    }

    public Wtap() {
        super("WTap", false);
    }

    @EventTarget(Priority.LOWEST)
    public void onMoveInput(MoveInputEvent event) {
        if (!isEnabled()) return;

        if (this.active) {
            if (!this.stopForward && !this.canTrigger()) {
                resetState();
                return;
            }

            if (this.delayTicks > 0L) {
                this.delayTicks -= 50L;
                return;
            }

            if (this.durationTicks > 0L) {
                this.durationTicks -= 50L;
                this.stopForward = true;
                mc.thePlayer.movementInput.moveForward = 0.0F;
            }

            if (this.durationTicks <= 0L) {
                resetState();
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled()) return;
        if (event.isCancelled()) return;
        if (event.getType() != EventType.SEND) return;
        if (!(event.getPacket() instanceof C02PacketUseEntity)) return;

        C02PacketUseEntity packet = (C02PacketUseEntity) event.getPacket();

        if (packet.getAction() == Action.ATTACK
                && !this.active
                && this.timer.hasTimeElapsed((long) ((float) cooldown.getValue()))
                && mc.thePlayer.isSprinting()) {
            this.timer.reset();
            this.active = true;
            this.stopForward = false;
            this.delayTicks += (long) (50.0F * this.delay.getValue());
            this.durationTicks += (long) (50.0F * this.duration.getValue());
        }
    }

    @Override
    public void onDisabled() {
        resetState();
    }
}
