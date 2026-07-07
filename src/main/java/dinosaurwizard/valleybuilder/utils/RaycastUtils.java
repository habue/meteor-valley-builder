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

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Box;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;

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

    public static BlockHitResult getBestBreakHit(Set<Vec3d> positions) {
        for (Vec3d end : positions) {
            RaycastContext context = new RaycastContext(mc.player.getEyePos(), end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);
            BlockHitResult hit = mc.world.raycast(context);

            if (hit.getBlockPos().equals(BlockPos.ofFloored(end))) {
                return hit;
            }
        }
        return null;
    }

    // Find a spot on an adjacent block that is visible
    public static BlockHitResult getBestPlaceHit(BlockPos blockPos) {
        for (Direction direction : Direction.values()) {

            BlockPos adjacent = blockPos.offset(direction);

            if (mc.world.getBlockState(adjacent).isReplaceable()) continue;

            Set<Vec3d> points = getShapeFacePoints(adjacent, direction.getOpposite());
            BlockHitResult hit = getBestPlaceHit(points, direction.getOpposite());

            if (hit != null) {
                return hit;
            }
        }

        return null;
    }

    public static BlockHitResult getBestPlaceHit(Set<Vec3d> positions, Direction direction) {
        for (Vec3d end : positions) {
            RaycastContext context = new RaycastContext(mc.player.getEyePos(), end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);
            BlockHitResult hit = mc.world.raycast(context);

            if (hit.getBlockPos().equals(BlockPos.ofFloored(end)) && hit.getSide() == direction) {
                return hit;
            }
        }
        return null;
    }

    // Gets a list of all points on a shape's specific face to raycast towards
    private static Set<Vec3d> getShapeFacePoints(BlockPos blockPos, Direction face) {
        Set<Vec3d> points = new LinkedHashSet<>();

        VoxelShape shape = mc.world.getBlockState(blockPos).getOutlineShape(mc.world, blockPos);

        final double epsilon = 0.001;
        final double[] samples = {1.0 / 6.0, 0.5, 5.0 / 6.0};

        for (Box box : shape.getBoundingBoxes()) {
            double minX = box.minX + blockPos.getX();
            double minY = box.minY + blockPos.getY();
            double minZ = box.minZ + blockPos.getZ();
            double maxX = box.maxX + blockPos.getX();
            double maxY = box.maxY + blockPos.getY();
            double maxZ = box.maxZ + blockPos.getZ();

            for (double u : samples) {
                for (double v : samples) {
                    double x  = MathHelper.lerp(u, minX, maxX);
                    double y  = MathHelper.lerp(u, minY, maxY);
                    double z  = MathHelper.lerp(u, minZ, maxZ);

                    double x2 = MathHelper.lerp(v, minX, maxX);
                    double y2 = MathHelper.lerp(v, minY, maxY);
                    double z2 = MathHelper.lerp(v, minZ, maxZ);

                    if (face == null || face == Direction.DOWN)
                        points.add(new Vec3d(x, minY + epsilon, z2));

                    if (face == null || face == Direction.UP)
                        points.add(new Vec3d(x, maxY - epsilon, z2));

                    if (face == null || face == Direction.NORTH)
                        points.add(new Vec3d(x, y2, minZ + epsilon));

                    if (face == null || face == Direction.SOUTH)
                        points.add(new Vec3d(x, y2, maxZ - epsilon));

                    if (face == null || face == Direction.WEST)
                        points.add(new Vec3d(minX + epsilon, y, z2));

                    if (face == null || face == Direction.EAST)
                        points.add(new Vec3d(maxX - epsilon, y, z2));
                }
            }
        }

        return points;
    }
}
