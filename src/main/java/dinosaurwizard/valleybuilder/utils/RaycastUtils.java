/*
 * RaycastUtils by dinosaurwizard
 *
 * These utilities are for servers that have strict placement/breaking requirements.
 * It raycasts to points on a block to determine if it is actually 'touchable' by a player. 
 * If it is 'touchable' then information can be accessed to help modules place and break blocks 'legitimently'.
 *
 * In practice, this means having players rotate towards visible sections of a block, then breaking/placing that face of the block.
 */

package dinosaurwizard.valleybuilder.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.ClipContext;

import java.util.Set;
import java.util.LinkedHashSet;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class RaycastUtils {
    private RaycastUtils() {}

    // Find a spot on a block that is visible
    public static BlockHitResult getBestBreakHit(BlockPos blockPos) {
        BlockHitResult hit = getBestBreakHit(getShapeFacePoints(blockPos, null));
        return hit;
    }

    public static BlockHitResult getBestBreakHit(Set<Vec3> positions) {
        for (Vec3 end : positions) {
            ClipContext context = new ClipContext(mc.player.getEyePosition(), end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player);
            BlockHitResult hit = mc.level.clip(context);

            if (hit.getBlockPos().equals(BlockPos.containing(end))) {
                return hit;
            }
        }
        return null;
    }

    // Find a spot on an adjacent block that is visible
    public static BlockHitResult getBestPlaceHit(BlockPos blockPos) {
        for (Direction direction : Direction.values()) {

            BlockPos adjacent = blockPos.relative(direction);

            if (mc.level.getBlockState(adjacent).canBeReplaced()) continue;

            Set<Vec3> points = getShapeFacePoints(adjacent, direction.getOpposite());
            BlockHitResult hit = getBestPlaceHit(points, direction.getOpposite());

            if (hit != null) {
                return hit;
            }
        }

        return null;
    }

    public static BlockHitResult getBestPlaceHit(Set<Vec3> positions, Direction direction) {
        for (Vec3 end : positions) {
            ClipContext context = new ClipContext(mc.player.getEyePosition(), end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player);
            BlockHitResult hit = mc.level.clip(context);

            if (hit.getBlockPos().equals(BlockPos.containing(end)) && hit.getDirection() == direction) {
                return hit;
            }
        }
        return null;
    }

    // Gets a list of all points on a shape's specific face to raycast towards
    private static Set<Vec3> getShapeFacePoints(BlockPos blockPos, Direction face) {
        Set<Vec3> points = new LinkedHashSet<>();

        VoxelShape shape = mc.level.getBlockState(blockPos).getShape(mc.level, blockPos);

        final double epsilon = 0.001;
        final double[] samples = {1.0 / 6.0, 0.5, 5.0 / 6.0};

        for (AABB box : shape.toAabbs()) {
            double minX = box.minX + blockPos.getX();
            double minY = box.minY + blockPos.getY();
            double minZ = box.minZ + blockPos.getZ();
            double maxX = box.maxX + blockPos.getX();
            double maxY = box.maxY + blockPos.getY();
            double maxZ = box.maxZ + blockPos.getZ();

            for (double u : samples) {
                for (double v : samples) {
                    double x  = Mth.lerp(u, minX, maxX);
                    double y  = Mth.lerp(u, minY, maxY);
                    double z  = Mth.lerp(u, minZ, maxZ);

                    double x2 = Mth.lerp(v, minX, maxX);
                    double y2 = Mth.lerp(v, minY, maxY);
                    double z2 = Mth.lerp(v, minZ, maxZ);

                    if (face == null || face == Direction.DOWN)
                        points.add(new Vec3(x, minY + epsilon, z2));

                    if (face == null || face == Direction.UP)
                        points.add(new Vec3(x, maxY - epsilon, z2));

                    if (face == null || face == Direction.NORTH)
                        points.add(new Vec3(x, y2, minZ + epsilon));

                    if (face == null || face == Direction.SOUTH)
                        points.add(new Vec3(x, y2, maxZ - epsilon));

                    if (face == null || face == Direction.WEST)
                        points.add(new Vec3(minX + epsilon, y, z2));

                    if (face == null || face == Direction.EAST)
                        points.add(new Vec3(maxX - epsilon, y, z2));
                }
            }
        }

        return points;
    }
}
