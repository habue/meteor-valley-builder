/*
 * Valley Builder Module by dinosaurwizard
 */

package dinosaurwizard.valleybuilder.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.meteorclient.utils.world.TickRate;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.*;
import net.minecraft.item.ItemStack;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;

public class ValleyBuilder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgInventory = settings.createGroup("Inventory");
    private final SettingGroup sgPause = settings.createGroup("Pause");

    private final Setting<Integer> placeDelay = sgGeneral.add(new IntSetting.Builder()
        .name("place-delay")
        .description("The tick delay between placing blocks.")
        .defaultValue(1)
        .range(0, 10)
        .build()
    );

    private final Setting<Double> placeRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("place-range")
        .description("How far away from the player you can place a block.")
        .defaultValue(4.5)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<Double> wallsRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("walls-range")
        .description("How far away from the player you can place a block behind walls.")
        .defaultValue(4.5)
        .min(0)
        .sliderMax(6)
        .build()
    );

    private final Setting<Integer> blocksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("blocks-per-tick")
        .description("How many blocks to place in one tick.")
        .defaultValue(1)
        .min(1)
        .build()
    );

    private final Setting<List<Block>> placeBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("place-blocks")
        .description("Blocks to place.")
        .build()
    );

    private final Setting<List<Block>> ignoreBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("ignore-blocks")
        .description("Blocks that will not be placed onto.")
        .build()
    );

    private final Setting<Boolean> randomPattern = sgGeneral.add(new BoolSetting.Builder()
        .name("random-pattern")
        .description("If using multiple types of blocks, they will be all be used in a random pattern.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> smartPlacement = sgGeneral.add(new BoolSetting.Builder()
        .name("smart-placement")
        .description("Only attempts places where the blocks can go.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotates towards the blocks being placed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SortMode> sortMode = sgGeneral.add(new EnumSetting.Builder<SortMode>()
        .name("sort-mode")
        .description("The blocks you want to place first.")
        .defaultValue(SortMode.None)
        .build()
    );

    // Inventory

    private final Setting<Boolean> autoSwitch = sgInventory.add(new BoolSetting.Builder()
        .name("auto-switch")
        .description("Switches to placeable blocks in your hotbar automatically.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swapBack = sgInventory.add(new BoolSetting.Builder()
        .name("swap-back")
        .description("Switches to your previous slot after placing blocks.")
        .defaultValue(true)
        .visible(autoSwitch::get)
        .build()
    );

    // Pause

    private final Setting<Boolean> pauseOnUse = sgPause.add(new BoolSetting.Builder()
        .name("pause-on-use")
        .description("Pauses while using an item.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseOnMine = sgPause.add(new BoolSetting.Builder()
        .name("pause-on-mine")
        .description("Pauses while mining blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseOnKillAura = sgPause.add(new BoolSetting.Builder()
        .name("pause-on-kill-aura")
        .description("Pauses while using kill aura.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseOnLag = sgPause.add(new BoolSetting.Builder()
        .name("pause-on-lag")
        .description("Pauses when the server stops responding.")
        .defaultValue(true)
        .build()
    );

    private int timer;
    private int cacheTimer;
    private final List<BlockPos> placePositions = new ArrayList<>();
    private final List<BlockPos> placedPositions = new ArrayList<>();

    public ValleyBuilder() {
        super(Categories.World, "valley-builder", "Cover ground with specified blocks.");
    }

    @Override
    public void onActivate() {
        placePositions.clear();
        placedPositions.clear();
        cacheTimer = 0;
    }

    @Override
    public void onDeactivate() {
        placePositions.clear();
        placedPositions.clear();
        cacheTimer = 0;
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (shouldPause()) return;

        // Clear cached placed positions once every two seconds
        if (cacheTimer++ > 40) {
            placedPositions.clear();
            cacheTimer = 0;
        }

        // Delay
        if (timer++ < placeDelay.get()) return;

        // Find place locations
        BlockIterator.register((int) Math.ceil(placeRange.get()), (int) Math.ceil(placeRange.get()), (blockPos, blockState) -> {
            // Spot must be air or some other replaceable block
            if (!blockState.isReplaceable()) return;

            // Make certain we dont replace blocks we want to keep. Only relevant for things like grass/ferns/seagrass
            if (!blockState.isAir()) {
                if (placeBlocks.get().contains(blockState.getBlock())) return;

                if (ignoreBlocks.get().contains(blockState.getBlock())) return;
            }

            // Dont place in positions we have already attempted
            // The reason this exists is to mediate certain servers that dont like it when you try to place blocks but fail (they kick you)
            if (placedPositions.contains(blockPos)) return;

            // Spot must have a block nearby to place onto
            if (!hasUncoveredFace(blockPos)) return;

            // Spot must be have no entities overlapping and is below world height
            if (!BlockUtils.canPlace(blockPos)) return;

            // Must be within range
            if (isOutOfRange(blockPos)) return;

            placePositions.add(new BlockPos(blockPos));
        });
    }

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        if (placePositions.isEmpty()) return;

        // Find slot
        FindItemResult result = getItemToUse();
        if (!result.found()) return;

        Hand hand = result.getHand();
        ItemStack stack = mc.player.getInventory().getStack(result.slot());
        Block block = Block.getBlockFromItem(stack.getItem());

        if (!autoSwitch.get() && !mc.player.isHolding(itemStack -> Block.getBlockFromItem(itemStack.getItem()) == block)) return;

        // Sort blocks
        if (sortMode.get() != SortMode.None) {
            double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
            placePositions.sort(Comparator.comparingDouble(value -> Utils.squaredDistance(x, y, z, value.getX() + 0.5, value.getY() + 0.5, value.getZ() + 0.5) * (sortMode.get() == SortMode.Closest ? 1 : -1)));
        }

        // Place blocks!
        int placedCount = 0;
        for (BlockPos placePosition : placePositions) {
            if (placedCount >= blocksPerTick.get()) break;

            if (smartPlacement.get() && !canPlaceAt(placePosition, block, stack, hand)) continue;

            if (BlockUtils.place(placePosition, result, rotate.get(), -50, true, true, swapBack.get())) {

                placedPositions.add(placePosition);
                placedCount++;
            }
        }

        placePositions.clear();
        timer = 0;
    }

    public FindItemResult getItemToUse() {
        // Random pattern
        if (randomPattern.get()) {
            List<FindItemResult> results = new ArrayList<>();

            for (Block block : placeBlocks.get() ) {
                FindItemResult result = InvUtils.findInHotbar(itemStack -> block == Block.getBlockFromItem(itemStack.getItem()));
                if (!result.found()) continue;

                results.add(result);
            }

            if (results.isEmpty()) return new FindItemResult(-1, 0);

            return results.get(mc.world.random.nextInt(results.size()));
        }

        // Normal pattern
        FindItemResult result = InvUtils.findInHotbar(itemStack -> placeBlocks.get().contains(Block.getBlockFromItem(itemStack.getItem())));
        return result;
    }

    // Checks if a block-face is uncovered by our specified blocks
    private boolean hasUncoveredFace(BlockPos blockPos) {
        for (Direction direction : Direction.values()) {
            BlockPos offsetPos = blockPos.offset(direction);
            BlockState state = mc.world.getBlockState(offsetPos);
            if (state.isReplaceable()) continue;

            if (ignoreBlocks.get().contains(state.getBlock())) continue;

            if (!placeBlocks.get().contains(state.getBlock())) return true;
        }
        return false;
    }

    // Determine range and raycasting
    private boolean isOutOfRange(BlockPos blockPos) {
        Vec3d pos = blockPos.toCenterPos();
        if (!PlayerUtils.isWithin(pos, placeRange.get())) return true;

        RaycastContext raycastContext = new RaycastContext(mc.player.getEyePos(), pos, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);
        BlockHitResult result = mc.world.raycast(raycastContext);
        if (result == null || !result.getBlockPos().equals(blockPos))
            return !PlayerUtils.isWithin(pos, wallsRange.get());

        return false;
    }

    // Placement check that uses a block's specific requirements, not totally accurate
    private boolean canPlaceAt(BlockPos pos, Block block, ItemStack stack, Hand hand) {
        for (Direction direction : Direction.values()) {
            BlockHitResult hit = new BlockHitResult(pos.toCenterPos(), direction, pos, false);
            ItemPlacementContext context = new ItemPlacementContext(mc.player, hand, stack, hit);

            BlockState state = block.getPlacementState(context);
            if (state == null) continue;

            if (!state.canPlaceAt(mc.world, pos)) continue;

            return true;
        }

        return false;
    }

    private boolean shouldPause() {
        if (pauseOnUse.get() && mc.player.isUsingItem()) return true;

        if (pauseOnMine.get() && mc.interactionManager.isBreakingBlock()) return true;

        if (pauseOnKillAura.get() && Modules.get().get(KillAura.class).attacking) return true;

        if (pauseOnLag.get() && TickRate.INSTANCE.getTimeSinceLastTick() >= 1.5f) return true;

        return false;
    }

    public enum SortMode {
        None,
        Closest,
        Furthest
    }
}
