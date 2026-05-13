package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.TickEvent;
import myau.mixin.IAccessorMinecraft;
import myau.module.Module;
import myau.util.BlockUtil;
import myau.util.RotationUtil;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import net.minecraft.block.Block;
import net.minecraft.block.BlockObsidian;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class FastPlace extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final DecimalFormat df = new DecimalFormat("0.0#", new DecimalFormatSymbols(Locale.US));
    private long delayMS = 0L;

    public final FloatProperty delay = new FloatProperty("delay", 1.0F, 1.0F, 3.0F);
    public final BooleanProperty blocksOnly = new BooleanProperty("blocks-only", true);
    public final BooleanProperty placeFix = new BooleanProperty("place-fix", true);
    public final BooleanProperty skipObsidian = new BooleanProperty("skip-obsidian", true);
    public final BooleanProperty skipEndStone = new BooleanProperty("skip-endstone", true);
    public final BooleanProperty skipPlanks = new BooleanProperty("skip-planks", true);
    public final BooleanProperty skipInteractable = new BooleanProperty("skip-interactable", true);

    private final Set<Block> extraSkippedBlocks = new HashSet<>(Arrays.asList(
            Blocks.bedrock,
            Blocks.anvil,
            Blocks.enchanting_table,
            Blocks.chest,
            Blocks.trapped_chest,
            Blocks.ender_chest,
            Blocks.furnace,
            Blocks.crafting_table,
            Blocks.dispenser,
            Blocks.dropper,
            Blocks.hopper,
            Blocks.beacon
    ));

    public void addSkippedBlock(Block block) {
        if (block != null) {
            extraSkippedBlocks.add(block);
        }
    }

    public void removeSkippedBlock(Block block) {
        extraSkippedBlocks.remove(block);
    }

    private boolean isSkippedBlock(Block block) {
        if (block == null) return false;

        if (skipObsidian.getValue() && block instanceof BlockObsidian) {
            return true;
        }
        if (skipEndStone.getValue() && block == Blocks.end_stone) {
            return true;
        }
        if (skipPlanks.getValue() && block == Blocks.planks) {
            return true;
        }
        if (skipInteractable.getValue() && BlockUtil.isInteractable(block)) {
            return true;
        }
        if (extraSkippedBlocks.contains(block)) {
            return true;
        }

        return false;
    }

    private boolean canPlace() {
        ItemStack stack = mc.thePlayer.getHeldItem();

        if (stack == null) {
            return !(Boolean) this.blocksOnly.getValue();
        }

        Item item = stack.getItem();

        if (item instanceof ItemFishingRod) {
            return false;
        }

        if (item instanceof ItemBlock) {
            Block block = ((ItemBlock) item).getBlock();

            if (isSkippedBlock(block)) {
                return false;
            }

            if (!(Boolean) this.placeFix.getValue()) {
                return true;
            }

            MovingObjectPosition mop = RotationUtil.rayTrace(
                    mc.thePlayer.rotationYaw,
                    mc.thePlayer.rotationPitch,
                    mc.playerController.getBlockReachDistance(),
                    1.0F
            );

            return mop != null
                    && mop.typeOfHit == MovingObjectType.BLOCK
                    && ((ItemBlock) item).canPlaceBlockOnSide(
                    mc.theWorld,
                    mop.getBlockPos(),
                    mop.sideHit,
                    mc.thePlayer,
                    stack
            );
        }

        return !(Boolean) this.blocksOnly.getValue();
    }

    public FastPlace() {
        super("FastPlace", false);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) return;

        int rightClickDelayTimer = ((IAccessorMinecraft) mc).getRightClickDelayTimer();

        if (rightClickDelayTimer == 4) {
            this.delayMS += (long) (50.0F * this.delay.getValue());
        }

        if (this.delayMS > 0L) {
            this.delayMS -= 50L;
        }

        if (this.delayMS <= 0L && rightClickDelayTimer > 1 && this.canPlace()) {
            ((IAccessorMinecraft) mc).setRightClickDelayTimer(0);
        }
    }

    @Override
    public void onDisabled() {
        this.delayMS = 0L;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{df.format(this.delay.getValue())};
    }
}
