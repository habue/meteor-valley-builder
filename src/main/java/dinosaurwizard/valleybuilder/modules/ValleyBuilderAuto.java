/*
 * Valley Builder Auto Module by dinosaurwizard
 */

package dinosaurwizard.valleybuilder.modules;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.schematic.*;
import baritone.api.selection.ISelection;
import baritone.api.utils.BetterBlockPos;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.Container;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ValleyBuilderAuto extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgResupply = settings.createGroup("Resupply");

    private final Setting<List<Block>> placeBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("place-blocks")
        .description("Blocks to place.")
        .onChanged(value -> recalculate())
        .build()
    );

    private final Setting<List<Block>> removeBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("remove-blocks")
        .description("Blocks that will be removed.")
        .onChanged(value -> recalculate())
        .build()
    );

    private final Setting<List<Block>> ignoreBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("ignore-blocks")
        .description("Blocks that will not be placed onto.")
        .onChanged(value -> recalculate())
        .build()
    );

    private final Setting<Boolean> ignoreIsolated = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-isolated")
        .description("Ignore placing on isolated areas (e.g corners that get covered up).")
        .defaultValue(true)
        .onChanged(value -> recalculate())
        .build()
    );

    private final Setting<Boolean> ignoreCaves = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-caves")
        .description("Ignore building inside caves.")
        .defaultValue(true)
        .onChanged(value -> recalculate())
        .build()
    );

    private final Setting<Pattern> pattern = sgGeneral.add(new EnumSetting.Builder<Pattern>()
        .name("pattern")
        .description("The block pattern you want to place.")
        .defaultValue(Pattern.None)
        .onChanged(value -> recalculate())
        .build()
    );

    // Resupply

    private final Setting<Boolean> resupply = sgResupply.add(new BoolSetting.Builder()
        .name("resupply")
        .description("When out of materials- resupply from a designated container.")
        .defaultValue(true)
        .onChanged(value -> recalculate())
        .build()
    );

    private final Setting<BlockPos> resupplyPosition = sgResupply.add(new BlockPosSetting.Builder()
        .name("resupply-position")
        .description("The position of the resupply container.")
        .visible(resupply::get)
        .build()
    );

    private final IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();

    private final Set<Block> placeSet = new ObjectOpenHashSet<>();
    private final Set<Block> removeSet = new ObjectOpenHashSet<>();
    private final Set<Block> ignoreSet = new ObjectOpenHashSet<>();

    private State currentState;

    private boolean restartBuildingProcess = false;
    private final Long2ObjectMap<ChunkSchematic> loadedSchematics = new Long2ObjectOpenHashMap<>();
    private final List<SelectionArea> selections = new ArrayList<>();

    private int awaitingInventoryTimer = 0;
    private int inventoryMoveTimer = 0;
    private int lastInventorySlot = -1;

    public ValleyBuilderAuto() {
        super(Categories.World, "valley-builder-auto", "Cover the world with specified blocks using baritone.");
    }

    @Override
    public void onActivate() {
        // Start up schematic building
        recalculate();
    }

    @Override
    public void onDeactivate() {
        // Stop building
        baritone.getPathingBehavior().cancelEverything();
    }

    private void recalculate() {
        // Convert our block lists into hash sets for faster lookups
        placeSet.clear();
        placeSet.addAll(placeBlocks.get());
        removeSet.clear();
        removeSet.addAll(removeBlocks.get());
        ignoreSet.clear();
        ignoreSet.addAll(ignoreBlocks.get());

        // Reset values
        restartBuildingProcess = false;
        loadedSchematics.clear();
        selections.clear();
        awaitingInventoryTimer = 0;
        inventoryMoveTimer = 0;
        lastInventorySlot = -1;

        // Use baritone's selection system since its quite sophisticated
        ISelection[] baritoneSelections = baritone.getSelectionManager().getSelections();
        for (ISelection selection : baritoneSelections) {
            selections.add(new SelectionArea(selection.min(), selection.size()));
        }

        // Reassess the world
        currentState = State.Processing;
    }

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        // Toggle the module off if building stops for any outside reason 
        // Such as if the building was finished or the process was stopped via chat commands
        if (currentState == State.Building && !baritone.getBuilderProcess().isActive()) {
            toggle();
            return;
        }

        // Automatically pause if eating
        if (mc.player.isUsingItem()) {
            baritone.getBuilderProcess().pause();
        } else if (baritone.getBuilderProcess().isPaused()) {
            baritone.getBuilderProcess().resume();
        }

        if (currentState == State.Building || currentState == State.Processing) {
            processBuilding();
        }

        if (resupply.get()) {
            processResupply();
        }
    }

    // Checks if a block-face is uncovered by our specified blocks
    private boolean hasBlockToPlaceOn(BlockPos blockPos) {
        BlockPos.MutableBlockPos offsetPos = new BlockPos.MutableBlockPos();
        for (Direction direction : Direction.values()) {
            offsetPos.set(blockPos.relative(direction));

            BlockState blockState = mc.level.getBlockState(offsetPos);
            if (blockState.canBeReplaced()) continue;

            Block block = blockState.getBlock();
            if (removeSet.contains(block)) continue;
            if (ignoreSet.contains(block)) continue;
            if (placeSet.contains(block)) continue;

            return true;
        }

        return false;
    }

    // Checks if the block is a valid spot for valley builder to place onto
    private boolean isPlacePosition(BlockPos blockPos, BlockState blockState) {
        // Spot must be on/near the surface
        if (ignoreCaves.get() && mc.level.getBrightness(LightLayer.SKY, blockPos) < 13) return false; 

        Block block = blockState.getBlock();
        if (!removeSet.contains(block)) {
            // Spot must be air or some other replaceable block
            if (!blockState.canBeReplaced()) return false;

            // Make certain we dont replace blocks we want to keep. Only relevant for things like grass/ferns/seagrass
            if (!blockState.isAir()) {
                if (placeSet.contains(block)) return false;

                if (ignoreSet.contains(block)) return false;
            }
        }

        // Spot must have a block nearby to place onto
        if (!hasBlockToPlaceOn(blockPos)) return false;

        return true;
    }

    // Checks if this block would totally covered by everything around it
    private boolean isIsolated(BlockPos blockPos) {
        BlockPos.MutableBlockPos offsetPos = new BlockPos.MutableBlockPos();
        for (Direction direction : Direction.values()) {
            offsetPos.set(blockPos.relative(direction));

            BlockState blockState = mc.level.getBlockState(offsetPos);
            if (blockState.canBeReplaced() && !isPlacePosition(offsetPos, blockState)) return false;
        }
        return true;
    }

    public class SelectionArea {
        private final BetterBlockPos min;
        private final Vec3i size;

        public SelectionArea(BetterBlockPos min, Vec3i size) {
            this.min = min;
            this.size = size;
        }
    }

    public class ChunkSchematic extends AbstractSchematic implements IStaticSchematic {
        protected BlockState[][][] states;
        private int originX;
        private int originY;
        private int originZ;
        public boolean loaded;
        public boolean completed;

        public ChunkSchematic(BlockState[][][] states, boolean loaded, boolean completed) {
            this.states = states;
            this.loaded = loaded;
            this.completed = completed;
        }

        public void setOrigin(int x, int y, int z) {
            this.originX = x;
            this.originY = y;
            this.originZ = z;
        }

        public void setSize(int x, int y, int z) {
            boolean empty = x == 0 || y == 0 || z == 0;
            this.x = empty ? 0 : x;
            this.z = empty ? 0 : y;
            this.y = empty ? 0 : z;
        }

        @Override
        public boolean inSchematic(int x, int y, int z, BlockState currentState) {
            if (x < 0 || x >= this.x || y < 0 || y >= this.y || z < 0 || z >= this.z) return false;

            if (this.states == null || !this.loaded) return true;

            return this.states[x][z][y] != null;
        }

        @Override
        public BlockState desiredState(int x, int y, int z, BlockState current, List<BlockState> approxPlaceable) {
            if (this.states == null || this.states[x][z][y] == null) return current;

            return this.states[x][z][y];
        }

        @Override
        public BlockState getDirect(int x, int y, int z) {
            return this.states[x][z][y];
        }

        @Override
        public BlockState[] getColumn(int x, int z) {
            return this.states[x][z];
        }
    }

    // Setup a schematic that makes a valley, and tell our player to build it
    private void processBuilding() {
        if (placeBlocks.get().isEmpty()) {
            error("No place blocks configured.");
            toggle();
            return;
        }

        if (selections.isEmpty()) {
            error("No selections found. Use command #sel to set selections.");
            toggle();
            return;
        }

        /*
            Dynamic schematic updating system
            The following code was devised in order for schematics to work reliably when dealing with unloaded chunks

            Each chunk within every selection holds its own schematic.
            Many of these schematics will be made in unloaded chunks and therefor have no data. So, when their chunks are loaded-
            We update those specific schematics with the blockstates of that specific chunk.
            Baritone is good and automatically sends our player towards unloaded areas to try to 'complete' the schematic, so this works.
        */

        // Determine schematic origin
        BetterBlockPos origin = selections.get(0).min;

        for (SelectionArea selection : selections) {
            BetterBlockPos min = selection.min;
            origin = new BetterBlockPos(Math.min(origin.x, min.x), Math.min(origin.y, min.y), Math.min(origin.z, min.z));
        }

        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();

        int playerChunkX = mc.player.chunkPosition().x;
        int playerChunkZ = mc.player.chunkPosition().z;
        int localX = mc.player.getBlockX() & 15;
        int localZ = mc.player.getBlockZ() & 15;
        int minPlayerChunkX = playerChunkX + (localX < 8 ? -1 : 0);
        int minPlayerChunkZ = playerChunkZ + (localZ < 8 ? -1 : 0);

        int chunksProcessed = 0;
        for (int selectionIndex = 0; selectionIndex < selections.size(); selectionIndex++) {
            SelectionArea selection = selections.get(selectionIndex);
            BetterBlockPos min = selection.min;
            Vec3i size = selection.size;

            int minX = min.getX();
            int minY = min.getY();
            int minZ = min.getZ();

            int maxX = minX + size.getX() - 1;
            int maxY = minY + size.getY() - 1;
            int maxZ = minZ + size.getZ() - 1;

            int minChunkX = minX >> 4;
            int maxChunkX = maxX >> 4;
            int minChunkZ = minZ >> 4;
            int maxChunkZ = maxZ >> 4;

            // Iterate through every overlapping chunk on our selections
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {

                    boolean chunkLoaded = mc.level.getChunkSource().hasChunk(chunkX, chunkZ);

                    // Chunk key is a combo of chunk pos and selection index- this allows for multiple selections to use the same chunk
                    long chunkKey = (ChunkPos.asLong(chunkX, chunkZ) << 16) | selectionIndex;

                    ChunkSchematic schematic = loadedSchematics.get(chunkKey);

                    if (schematic != null) {
                        // If the schematic is completed, don't process it
                        if (schematic.completed) continue;

                        // If the schematic was made in an unloaded chunk but now is in a loaded chunk, reassess its blockstates
                        if (!schematic.loaded && !chunkLoaded) continue;

                        boolean isNearbyChunk = chunkX >= minPlayerChunkX && chunkX <= minPlayerChunkX + 1 &&
                                                chunkZ >= minPlayerChunkZ && chunkZ <= minPlayerChunkZ + 1;
                        // If the schematic has been loaded in, disregard unless its nearby
                        if (schematic.loaded && !isNearbyChunk) continue;
                    }

                    int startX = Math.max(minX, chunkX << 4);
                    int endX   = Math.min(maxX, (chunkX << 4) + 15);
                    int startZ = Math.max(minZ, chunkZ << 4);
                    int endZ   = Math.min(maxZ, (chunkZ << 4) + 15);

                    int chunkWidth = endX - startX + 1;
                    int chunkHeight = size.getY();
                    int chunkLength = endZ - startZ + 1;

                    boolean completed = chunkLoaded;
                    BlockState[][][] blockStates = null;

                    // Process blockstates
                    if (chunkLoaded) {
                        blockStates = new BlockState[chunkWidth][chunkLength][chunkHeight];

                        for (int x = 0; x < chunkWidth; x++) {
                            for (int y = 0; y < chunkHeight; y++) {
                                for (int z = 0; z < chunkLength; z++) {
                                    blockPos.set(startX + x, minY + y, startZ + z);

                                    BlockState blockState = mc.level.getBlockState(blockPos);

                                    if (isPlacePosition(blockPos, blockState)) {
                                        if (ignoreIsolated.get() && isIsolated(blockPos)) continue;
                                        // Place blocks
                                        completed = false;
                                        blockStates[x][z][y] = getBlockToPlaceAt(blockPos).defaultBlockState();
                                    } else if (removeSet.contains(blockState.getBlock())) {
                                        // Remove blocks
                                        blockStates[x][z][y] = Blocks.AIR.defaultBlockState();
                                    }
                                }
                            }
                        }
                    }

                    // Loaded schematics only update if it was completed
                    if (schematic != null && schematic.loaded && !completed) continue;

                    schematic = new ChunkSchematic(completed ? null : blockStates, chunkLoaded, completed);
                    schematic.setOrigin(startX - origin.x, minY - origin.y, startZ - origin.z);
                    schematic.setSize(chunkWidth, chunkLength, chunkHeight);

                    loadedSchematics.put(chunkKey, schematic);

                    // Begin building next tick if there is no more chunks to process
                    restartBuildingProcess = true;

                    // Only process so many loaded chunks a tick to mitigate lag spikes
                    if (chunkLoaded && chunksProcessed++ >= 4) {
                        return;
                    }
                }
            }
        }

        if (restartBuildingProcess) {
            restartBuildingProcess = false;

            currentState = State.Building;

            // Combine all our chunk schematics into a composite
            CompositeSchematic composite = new CompositeSchematic(0, 0, 0);
            for (ChunkSchematic schematic : loadedSchematics.values()) {
                if (schematic.completed) continue;

                composite.put(schematic, schematic.originX, schematic.originY, schematic.originZ);
            }

            baritone.getPathingBehavior().cancelEverything();
            baritone.getBuilderProcess().build("Valley Builder", composite, origin);
        }
    }

    // Finds what block valley builder should place at a world block position
    private Block getBlockToPlaceAt(BlockPos blockPos) {
        List<Block> blocks = placeBlocks.get();
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

    public enum State {
        Processing,
        Building,
        Resupplying,
        MovingItems
    }


    /// RESUPPLY

    private void processResupply() {
        // If we ran out of resources, stop building and go resupply
        if (currentState == State.Building && !hasAnyPlaceBlocks()) {
            setResupplying();
        }

        // Reached container block goal, open our container
        if (currentState == State.Resupplying && !baritone.getCustomGoalProcess().isActive()) {
            onResupplyContainerGoalReached();
        }

        // Move items out of container into our own inventory
        if (currentState == State.MovingItems && !moveItemsOutOfContainer()) {
            // Wait a bit before exiting because of latentcy to access the inventory
            if (awaitingInventoryTimer++ >= 40) {
                awaitingInventoryTimer = 0;
                lastInventorySlot = -1;

                mc.player.closeContainer();

                // Return to building after we finished moving items
                if (hasAnyPlaceBlocks()) {
                    restartBuildingProcess = true;
                    currentState = State.Processing;
                // Resupply failed, cancel everything
                } else {
                    error("Failed to resupply.");
                    toggle();
                    return;
                }
            }
        }
    }

    // Begin resupplying by pathing to our resupply container
    private void setResupplying() {
        // Resupply position must be configured
        if (resupplyPosition.get().equals(BlockPos.ZERO)) {
            error("No resupply position configured.");
            toggle();
            return;
        }

        currentState = State.Resupplying;

        baritone.getPathingBehavior().cancelEverything();
        baritone.getCustomGoalProcess().setGoalAndPath(new GoalGetToBlock(resupplyPosition.get()));
    }

    // When we reach our resupply container, open it and begin moving items
    private void onResupplyContainerGoalReached() {
        if (!(mc.level.getBlockEntity(resupplyPosition.get()) instanceof Container)) {
            error("No container at configured resupply position.");
            toggle();
            return;
        }

        Vec3 center = resupplyPosition.get().getCenter();
        if (mc.player.getEyePosition().distanceTo(center) > mc.player.blockInteractionRange()) {
            error("Failed to reach resupply position.");
            toggle();
            return;
        }

        // Interact with container to open its inventory
        Rotations.rotate(Rotations.getYaw(center), Rotations.getPitch(center), () -> {
            BlockHitResult hit = new BlockHitResult(center, BlockUtils.getDirection(resupplyPosition.get()), resupplyPosition.get(), true);
            BlockUtils.interact(hit, InteractionHand.MAIN_HAND, true);
        });

        currentState = State.MovingItems;
    }

    // Move an item out of a container. Returns true if it moved/can move an item
    private boolean moveItemsOutOfContainer() {
        // Container screen hasn't appeared yet
        if (!(mc.screen instanceof AbstractContainerScreen<?>)) return false;

        // Our own inventory is full
        if (mc.player.getInventory().getFreeSlot() == -1) return false;

        AbstractContainerMenu handler = mc.player.containerMenu;

        int containerSlots = handler.slots.size() - 36;
        int startSlot = Math.min(lastInventorySlot + 1, containerSlots);

        for (int slot = startSlot; slot < containerSlots; slot++) {
            Slot containerSlot = handler.getSlot(slot);

            if (!containerSlot.hasItem()) continue;

            ItemStack stack = containerSlot.getItem();
            if (!(stack.getItem() instanceof BlockItem blockItem)) continue;

            if (!placeSet.contains(blockItem.getBlock())) continue;

            if (inventoryMoveTimer++ >= BaritoneAPI.getSettings().ticksBetweenInventoryMoves.value) {
                inventoryMoveTimer = 0;
                lastInventorySlot = slot;
                InvUtils.shiftClick().slotId(slot);
            }
            return true;
        }

        // No usable items left in container
        return false;
    }

    // Checks if our player has a block in our inventory that is placeable
    private boolean hasAnyPlaceBlocks() {
        for (Block block : placeBlocks.get() ) {

            FindItemResult result = InvUtils.find(itemStack -> block == Block.byItem(itemStack.getItem()));
            if (result.found()) return true;
        }
        return false;
    }
}
