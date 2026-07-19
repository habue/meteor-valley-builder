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
import meteordevelopment.meteorclient.systems.modules.player.AutoEat;
import meteordevelopment.meteorclient.systems.modules.player.AutoGap;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
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
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.util.PlayerInput;
import dinosaurwizard.valleybuilder.utils.RaycastUtils;

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

    private final Setting<Integer> placeRetryDelay = sgGeneral.add(new IntSetting.Builder()
        .name("place-retry-delay")
        .description("Delay in ticks to retry placing a block if it failed the first time.")
        .defaultValue(20)
        .min(0)
        .sliderMax(200)
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

    private final Setting<Pattern> pattern = sgGeneral.add(new EnumSetting.Builder<Pattern>()
        .name("pattern")
        .description("The block pattern you want to use.")
        .defaultValue(Pattern.None)
        .build()
    );

    private final Setting<Boolean> forcePattern = sgGeneral.add(new BoolSetting.Builder()
        .name("force-pattern")
        .description("Forces the chosen pattern to use all configured place blocks.")
        .defaultValue(false)
        .visible(() -> pattern.get() != Pattern.None)
        .build()
    );

    private final Setting<Boolean> smartPlacement = sgGeneral.add(new BoolSetting.Builder()
        .name("smart-placement")
        .description("Only attempts places where the blocks can go.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> sneakPlacement = sgGeneral.add(new BoolSetting.Builder()
        .name("sneak-placement")
        .description("Sends sneaking packets when placing blocks.")
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

    private final Setting<Boolean> allowInventory = sgInventory.add(new BoolSetting.Builder()
        .name("allow-inventory")
        .description("Allows blocks to be moved into your hotbar from your inventory.")
        .defaultValue(true)
        .visible(autoSwitch::get)
        .build()
    );

    private final Setting<Boolean> stationaryMove = sgInventory.add(new BoolSetting.Builder()
        .name("stationary-move")
        .description("Only allows blocks to be moved from your inventory if you are standing still. Required for certain anti-cheats.")
        .defaultValue(false)
        .visible(() -> autoSwitch.get() && allowInventory.get())
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
        super(Categories.World, "valley-builder", "Cover the world with specified blocks.");
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

        // Clear cached placed positions once every so often
        if (cacheTimer++ > placeRetryDelay.get()) {
            placedPositions.clear();
            cacheTimer = 0;
        }

        // Delay
        if (timer++ < placeDelay.get()) return;

        // Find place locations
        BlockIterator.register((int) Math.ceil(placeRange.get()), (int) Math.ceil(placeRange.get()), (blockPos, blockState) -> {
            if (isPlacePosition(blockPos, blockState)) {
                placePositions.add(new BlockPos(blockPos));
            }
        });
    }

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        if (placePositions.isEmpty()) return;

        // Sort blocks
        if (sortMode.get() != SortMode.None) {
            double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
            placePositions.sort(Comparator.comparingDouble(value -> Utils.squaredDistance(x, y, z, value.getX() + 0.5, value.getY() + 0.5, value.getZ() + 0.5) * (sortMode.get() == SortMode.Closest ? 1 : -1)));
        }

        // Place blocks!
        int placedCount = 0;
        for (BlockPos placePosition : placePositions) {
            if (placedCount >= blocksPerTick.get()) break;

            // Find items
            FindItemResult result = getItemToUse(placePosition);
            if (!result.found()) continue;

            // Move items into hotbar if allowed
            if (!result.isHotbar() && autoSwitch.get() && allowInventory.get()) {
                if (!stationaryMove.get() || mc.player.getVelocity().multiply(1, 0, 1).length() < 0.00001) {
                    int slotToUse = getSwapSlotToUse();
                    InvUtils.quickSwap().fromId(slotToUse).to(result.slot());
                    result = new FindItemResult(slotToUse, result.count());
                }
            }

            if (!result.isHotbar()) continue;

            if (!autoSwitch.get() && mc.player.getInventory().getSelectedSlot() != result.slot()) continue;

            if (place(placePosition, result)) {
                placedPositions.add(placePosition);
                placedCount++;
            }
        }

        placePositions.clear();
        timer = 0;
    }

    // Checks if the block is a valid spot for valley builder to place onto
    private boolean isPlacePosition(BlockPos blockPos, BlockState blockState) {
        // Spot must be air or some other replaceable block
        if (!blockState.isReplaceable()) return false;

        // Make certain we dont replace blocks we want to keep. Only relevant for things like grass/ferns/seagrass
        if (!blockState.isAir()) {
            if (placeBlocks.get().contains(blockState.getBlock())) return false;

            if (ignoreBlocks.get().contains(blockState.getBlock())) return false;
        }

        // Dont place in positions we have already attempted
        // The reason this exists is to mediate certain servers that dont like it when you try to place blocks but fail (they kick you)
        if (placedPositions.contains(blockPos)) return false;

        // Spot must have a block nearby to place onto
        if (!hasBlockToPlaceOn(blockPos)) return false;

        // Spot must be have no entities overlapping and is below world height
        if (!BlockUtils.canPlace(blockPos)) return false;

        // Must be within range
        if (isOutOfRange(blockPos)) return false;

        return true;
    }

    // Checks if a block-face is uncovered by our specified blocks
    private boolean hasBlockToPlaceOn(BlockPos blockPos) {
        BlockPos.Mutable offsetPos = new BlockPos.Mutable();
        for (Direction direction : Direction.values()) {
            offsetPos.set(blockPos.offset(direction));

            BlockState blockState = mc.world.getBlockState(offsetPos);
            if (blockState.isReplaceable()) continue;

            Block block = blockState.getBlock();

            if (ignoreBlocks.get().contains(block)) continue;

            if (!placeBlocks.get().contains(block)) return true;
        }
        return false;
    }

    // Determine range and raycasting
    private boolean isOutOfRange(BlockPos blockPos) {
        Vec3d pos = blockPos.toCenterPos();
        if (!PlayerUtils.isWithin(pos, placeRange.get())) return true;

        BlockHitResult hit = RaycastUtils.getBestPlaceHit(blockPos);
        if (hit == null)
            return !PlayerUtils.isWithin(pos, wallsRange.get());

        return false;
    }

    // Placement check that uses a block's specific requirements, not totally accurate
    private boolean canPlaceAt(BlockPos pos, Block block, ItemStack stack, Hand hand) {
        for (Direction direction : Direction.values()) {
            BlockHitResult hit = new BlockHitResult(pos.toCenterPos(), direction, pos, false);
            ItemPlacementContext context = new ItemPlacementContext(mc.player, hand, stack, hit);

            BlockState blockState = block.getPlacementState(context);
            if (blockState == null) continue;

            if (!blockState.canPlaceAt(mc.world, pos)) continue;

            return true;
        }

        return false;
    }

    // Custom placement that is far better than meteor's standard
    private boolean place(BlockPos blockPos, FindItemResult result) {
        if (result.slot() < 0 || result.slot() > 8) return false;

        Hand hand = null;

        if (result.isHotbar()) {
            hand = Hand.MAIN_HAND;
        } else if (result.isOffhand()) {
            hand = Hand.OFF_HAND;
        }

        if (hand == null) return false;

        ItemStack stack = mc.player.getInventory().getStack(result.slot());
        Block block = Block.getBlockFromItem(stack.getItem());

        if (smartPlacement.get() && !canPlaceAt(blockPos, block, stack, hand)) return false;

        if (!BlockUtils.canPlaceBlock(blockPos, true, block)) return false;

        // Find a visible spot on the block to place on
        BlockHitResult hit = RaycastUtils.getBestPlaceHit(blockPos);

        // If no visible spot, do our best anyways
        if (hit == null) {
            Vec3d center = blockPos.toCenterPos();

            BlockPos neighbour;
            Direction side = BlockUtils.getPlaceSide(blockPos);

            if (side == null) {
                side = Direction.UP;
                neighbour = blockPos;
            } else {
                neighbour = blockPos.offset(side);
                center = center.add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
            }

            hit = new BlockHitResult(center, side.getOpposite(), neighbour, false);
        }

        if (rotate.get()) {
            BlockHitResult finalHit = hit;
            Hand finalHand = hand;
            Vec3d rotatePos = hit.getPos();
            Rotations.rotate(Rotations.getYaw(rotatePos), Rotations.getPitch(rotatePos), () -> {
                place(finalHit, result, finalHand);
            });
        } else {
            place(hit, result, hand);
        }

        return true;
    }

    private void place(BlockHitResult hit, FindItemResult result, Hand hand) {
        // Send our inputs with sneaking injected
        boolean isSneaking = mc.player.isSneaking();
        PlayerInput old = mc.player.input.playerInput;
        if (sneakPlacement.get() && !isSneaking) {
           PlayerInput sneak = new PlayerInput(old.forward(), old.backward(), old.left(), old.right(), old.jump(), true, old.sprint());
           mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(sneak));
        }

        InvUtils.swap(result.slot(), swapBack.get());

        if (mc.interactionManager.interactBlock(mc.player, hand, hit).isAccepted()) {
            mc.player.swingHand(hand);
        }

        if (swapBack.get()) InvUtils.swapBack();

        // Go back to our old inputs
        if (sneakPlacement.get() && !isSneaking) {
           mc.getNetworkHandler().sendPacket(new PlayerInputC2SPacket(old));
        }
    }

    private boolean shouldPause() {
        if (pauseOnUse.get()) {
            if (Modules.get().get(AutoEat.class).eating || Modules.get().get(AutoGap.class).isEating()) return true;

            if (mc.player.isUsingItem()) return true;
        }

        if (pauseOnMine.get() && mc.interactionManager.isBreakingBlock()) return true;

        if (pauseOnKillAura.get() && Modules.get().get(KillAura.class).attacking) return true;

        if (pauseOnLag.get() && TickRate.INSTANCE.getTimeSinceLastTick() >= 1.5f) return true;

        return false;
    }

    // Find a good slot to swap items into
    private int getSwapSlotToUse() {
        FindItemResult empty = InvUtils.findEmpty();
        if (empty.found() && empty.isHotbar()) return empty.slot();

        return mc.player.getInventory().getSelectedSlot();
    }

    // Find the next block to place
    private FindItemResult getItemToUse(BlockPos blockPos) {
        List<Block> blocks = new ArrayList<>();

        // If forcing the pattern, use configured place blocks even if we dont have them in our hotbar
        if (forcePattern.get() && pattern.get() != Pattern.None) {
            blocks = placeBlocks.get();
        // Otherwise just use whatever blocks are available
        } else {
            for (Block block : placeBlocks.get() ) {
                FindItemResult result = InvUtils.find(itemStack -> block == Block.getBlockFromItem(itemStack.getItem()));
                if (!result.found()) continue;

                blocks.add(block);
            }
        }

        if (blocks.isEmpty()) return new FindItemResult(-1, 0);

        Block block = getBlockToPlaceAt(blockPos, blocks);

        // If block is in main hand, use it first
        if (block == Block.getBlockFromItem(mc.player.getMainHandStack().getItem())) {
            return new FindItemResult(mc.player.getInventory().getSelectedSlot(), mc.player.getMainHandStack().getCount());
        }

        // Otherwise find it in the rest of the inventory
        FindItemResult result = InvUtils.find(itemStack -> block == Block.getBlockFromItem(itemStack.getItem()));
        return result;
    }

    // Finds what block valley builder should place at a world block position
    private Block getBlockToPlaceAt(BlockPos blockPos, List<Block> blocks) {
        switch (pattern.get()) {
            // Randomized
            case Random: {
                long hash = 0x9E3779B97F4A7C15L;
                hash ^= blockPos.getX() * 0xBF58476D1CE4E5B9L;
                hash ^= blockPos.getY() * 0x94D049BB133111EBL;
                hash ^= blockPos.getZ() * 0xD6E8FEB86659FD93L;
                hash ^= hash >>> 30;
                hash *= 0xBF58476D1CE4E5B9L;
                hash ^= hash >>> 27;
                hash *= 0x94D049BB133111EBL;
                hash ^= hash >>> 31;
                int index = Math.floorMod(hash, blocks.size());
                return blocks.get(index);
            }
            // Diagonal stripes
            case Diagonal: {
                int index = Math.floorMod(blockPos.getX() + blockPos.getY() + blockPos.getZ(), blocks.size());
                return blocks.get(index);
            }
            // Straight stripes along the X axis
            case StripesX: {
                int index = Math.floorMod(blockPos.getX(), blocks.size());
                return blocks.get(index);
            }
            // Straight stripes along the Z axis
            case StripesZ: {
                int index = Math.floorMod(blockPos.getZ(), blocks.size());
                return blocks.get(index);
            }
            // Straight stripes along the Y axis
            case StripesY: {
                int index = Math.floorMod(blockPos.getY(), blocks.size());
                return blocks.get(index);
            }
        }

        return blocks.getFirst();
    }

    public enum Pattern {
        None,
        Random,
        Diagonal,
        StripesX,
        StripesZ,
        StripesY
    }

    public enum SortMode {
        None,
        Closest,
        Furthest
    }
}
