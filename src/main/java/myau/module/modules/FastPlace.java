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
import myau.property.properties.StringListProperty;
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
    public final BooleanProperty skipCustom = new BooleanProperty("skip-custom", false);

    /**
     * A set of blocks that will be skipped when skipCustom is enabled.
     * You can add or remove blocks from this set dynamically.
     */
    private final Set<Block> customSkippedBlocks = new HashSet<>(Arrays.asList(
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

    /**
     * Add a block to the custom skip list at runtime.
     *
     * @param block The block to add
     */
    public void addCustomSkip(Block block) {
        if (block != null) {
            customSkippedBlocks.add(block);
        }
    }

    /**
     * Remove a block from the custom skip list at runtime.
     *
     * @param block The block to remove
     */
    public void removeCustomSkip(Block block) {
        customSkippedBlocks.remove(block);
    }

    /**
     * Check whether a block should be skipped based on the current settings.
     *
     * @param block The block to check
     * @return true if the block should be skipped, false otherwise
     */
    private boolean isSkippedBlock(Block block) {
        if (block == null) return false;

        // Skip obsidian
        if (skipObsidian.getValue() && block instanceof BlockObsidian) {
            return true;
        }

        // Skip end stone
        if (skipEndStone.getValue() && block == Blocks.end_stone) {
            return true;
        }

        // Skip all plank variants (oak, spruce, birch, jungle, acacia, dark oak)
        if (skipPlanks.getValue() && block == Blocks.planks) {
            return true;
        }

        // Skip interactable blocks (chests, furnaces, etc.)
        if (skipInteractable.getValue() && BlockUtil.isInteractable(block)) {
            return true;
        }

        // Skip custom user-defined blocks
        if (skipCustom.getValue() && customSkippedBlocks.contains(block)) {
            return true;
        }

        return false;
    }

    /**
     * Determine if placing is allowed based on the held item and current settings.
     *
     * @return true if placing is allowed, false otherwise
     */
    private boolean canPlace() {
        ItemStack stack = mc.thePlayer.getHeldItem();

        if (stack == null) {
            return !(Boolean) this.blocksOnly.getValue();
        }

        Item item = stack.getItem();

        // Never fast-place with a fishing rod
        if (item instanceof ItemFishingRod) {
            return false;
        }

        if (item instanceof ItemBlock) {
            Block block = ((ItemBlock) item).getBlock();

            // Check all skip conditions
            if (isSkippedBlock(block)) {
                return false;
            }

            // If place-fix is disabled, allow placing
            if (!(Boolean) this.placeFix.getValue()) {
                return true;
            }

            // Raytrace to make sure we are actually looking at a valid block face
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

        // If blocks-only is off, allow fast-placing with non-block items
        return !(Boolean) this.blocksOnly.getValue();
    }

    public FastPlace() {
        super("FastPlace", false);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) return;

        int rightClickDelayTimer = ((IAccessorMinecraft) mc).getRightClickDelayTimer();

        // Accumulate delay when the timer resets to 4
        if (rightClickDelayTimer == 4) {
            this.delayMS += (long) (50.0F * this.delay.getValue());
        }

        // Drain the delay buffer each tick
        if (this.delayMS > 0L) {
            this.delayMS -= 50L;
        }

        // Reset the right-click timer if the delay has been satisfied
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
