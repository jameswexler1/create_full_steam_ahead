package dev.gustavo.fullsteamahead.content.cylinder;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class CylinderRingShapes {
    private static final double SHIFT = 16.0D;
    private static final VoxelShape[] UP_SHAPES = new VoxelShape[CylinderSection.values().length];
    private static final VoxelShape[] DOWN_SHAPES = new VoxelShape[CylinderSection.values().length];
    private static final VoxelShape[] SHARED_Z_UP_SHAPES = new VoxelShape[CylinderSection.values().length];
    private static final VoxelShape[] SHARED_Z_DOWN_SHAPES = new VoxelShape[CylinderSection.values().length];
    private static final VoxelShape[] SHARED_X_UP_SHAPES = new VoxelShape[CylinderSection.values().length];
    private static final VoxelShape[] SHARED_X_DOWN_SHAPES = new VoxelShape[CylinderSection.values().length];
    private static final RingBox[] ASSEMBLED_BOXES = {
            new RingBox(0, 0, -9, 16, 31, 0),
            new RingBox(0, 31, -8, 16, 32, 0),
            new RingBox(18, 0, -2, 25, 31, 0),
            new RingBox(16, 0, -9, 18, 31, 0),
            new RingBox(22, 0, -4, 24, 31, -2),
            new RingBox(20, 0, -6, 22, 31, -4),
            new RingBox(18, 0, -8, 20, 31, -6),
            new RingBox(16, 31, -8, 18, 32, 0),
            new RingBox(18, 31, -2, 24, 32, 0),
            new RingBox(18, 31, -4, 22, 32, -2),
            new RingBox(18, 31, -6, 20, 32, -4),
            new RingBox(18, 0, -6, 20, 1, -4),
            new RingBox(18, 0, -4, 22, 1, -2),
            new RingBox(-9, 0, 0, 0, 31, 16),
            new RingBox(-8, 31, 0, 0, 32, 16),
            new RingBox(-2, 0, -9, 0, 31, -2),
            new RingBox(-9, 0, -2, 0, 31, 0),
            new RingBox(-4, 0, -8, -2, 31, -6),
            new RingBox(-6, 0, -6, -4, 31, -4),
            new RingBox(-8, 0, -4, -6, 31, -2),
            new RingBox(-8, 31, -2, 0, 32, 0),
            new RingBox(-2, 31, -8, 0, 32, -2),
            new RingBox(-4, 31, -6, -2, 32, -2),
            new RingBox(-6, 31, -4, -4, 32, -2),
            new RingBox(-6, 0, -4, -4, 1, -2),
            new RingBox(-4, 0, -6, -2, 1, -2),
            new RingBox(0, 0, 16, 16, 31, 25),
            new RingBox(0, 31, 16, 16, 32, 24),
            new RingBox(-9, 0, 16, -2, 31, 18),
            new RingBox(-2, 0, 16, 0, 31, 25),
            new RingBox(-8, 0, 18, -6, 31, 20),
            new RingBox(-6, 0, 20, -4, 31, 22),
            new RingBox(-4, 0, 22, -2, 31, 24),
            new RingBox(-2, 31, 16, 0, 32, 24),
            new RingBox(-8, 31, 16, -2, 32, 18),
            new RingBox(-6, 31, 18, -2, 32, 20),
            new RingBox(-4, 31, 20, -2, 32, 22),
            new RingBox(-4, 0, 20, -2, 1, 22),
            new RingBox(-6, 0, 18, -2, 1, 20),
            new RingBox(16, 0, 0, 25, 31, 16),
            new RingBox(16, 31, 0, 24, 32, 16),
            new RingBox(16, 0, 18, 18, 31, 25),
            new RingBox(16, 0, 16, 25, 31, 18),
            new RingBox(18, 0, 22, 20, 31, 24),
            new RingBox(20, 0, 20, 22, 31, 22),
            new RingBox(22, 0, 18, 24, 31, 20),
            new RingBox(16, 31, 16, 24, 32, 18),
            new RingBox(16, 31, 18, 18, 32, 24),
            new RingBox(18, 31, 18, 20, 32, 22),
            new RingBox(20, 31, 18, 22, 32, 20),
            new RingBox(20, 0, 18, 22, 1, 20),
            new RingBox(18, 0, 18, 20, 1, 22),
            new RingBox(18, 1, -6, 20, 31, -2),
            new RingBox(-6, 1, -4, -2, 31, -2),
            new RingBox(-4, 1, 18, -2, 31, 22),
            new RingBox(18, 1, 18, 22, 31, 20),
            new RingBox(20, 1, -4, 22, 31, -2),
            new RingBox(-4, 1, -6, -2, 31, -4),
            new RingBox(-6, 1, 18, -4, 31, 20),
            new RingBox(18, 1, 20, 20, 31, 22)
    };
    private static final RingBox[] SHARED_WALL_BOXES = {
            new RingBox(0, 0, -2, 8, 31, 0),
            new RingBox(6, 0, -4, 8, 31, -2),
            new RingBox(4, 0, -6, 6, 31, -4),
            new RingBox(2, 0, -8, 4, 31, -6),
            new RingBox(0, 31, -8, 2, 32, 0),
            new RingBox(2, 31, -2, 8, 32, 0),
            new RingBox(2, 31, -4, 6, 32, -2),
            new RingBox(2, 31, -6, 4, 32, -4),
            new RingBox(2, 0, -6, 4, 1, -4),
            new RingBox(2, 0, -4, 6, 1, -2),
            new RingBox(0, 0, 0, 8, 31, 16),
            new RingBox(0, 31, 0, 8, 32, 16),
            new RingBox(0, 0, 18, 2, 31, 25),
            new RingBox(0, 0, 16, 8, 31, 18),
            new RingBox(2, 0, 22, 4, 31, 24),
            new RingBox(4, 0, 20, 6, 31, 22),
            new RingBox(6, 0, 18, 8, 31, 20),
            new RingBox(0, 31, 16, 8, 32, 18),
            new RingBox(0, 31, 18, 2, 32, 24),
            new RingBox(2, 31, 18, 4, 32, 22),
            new RingBox(4, 31, 18, 6, 32, 20),
            new RingBox(4, 0, 18, 6, 1, 20),
            new RingBox(2, 0, 18, 4, 1, 22),
            new RingBox(2, 1, -5.9, 4, 31, -1.9),
            new RingBox(2, 1, 18, 6, 31, 20),
            new RingBox(4, 1, -4, 6, 31, -2),
            new RingBox(2, 1, 20, 4, 31, 22),
            new RingBox(8, 31, 0, 16, 32, 16),
            new RingBox(8, 0, -2, 16, 31, 0),
            new RingBox(8, 0, -4, 10, 31, -2),
            new RingBox(10, 0, -6, 12, 31, -4),
            new RingBox(12, 0, -8, 14, 31, -6),
            new RingBox(14, 31, -8, 16, 32, 0),
            new RingBox(8, 31, -2, 14, 32, 0),
            new RingBox(10, 31, -4, 14, 32, -2),
            new RingBox(12, 31, -6, 14, 32, -4),
            new RingBox(12, 0, -6, 14, 1, -4),
            new RingBox(10, 0, -4, 14, 1, -2),
            new RingBox(8, 0, 0, 16, 31, 16),
            new RingBox(14, 0, 18, 16, 31, 25),
            new RingBox(8, 0, 16, 16, 31, 18),
            new RingBox(12, 0, 22, 14, 31, 24),
            new RingBox(10, 0, 20, 12, 31, 22),
            new RingBox(8, 0, 18, 10, 31, 20),
            new RingBox(8, 31, 16, 16, 32, 18),
            new RingBox(14, 31, 18, 16, 32, 24),
            new RingBox(12, 31, 18, 14, 32, 22),
            new RingBox(10, 31, 18, 12, 32, 20),
            new RingBox(10, 0, 18, 12, 1, 20),
            new RingBox(12, 0, 18, 14, 1, 22),
            new RingBox(12, 1, -5.9, 14, 31, -1.9),
            new RingBox(10, 1, 18, 14, 31, 20),
            new RingBox(10, 1, -4, 12, 31, -2),
            new RingBox(12, 1, 20, 14, 31, 22),
            new RingBox(14, 0, -9, 16, 31, -2),
            new RingBox(0, 0, -9, 2, 31, -2)
    };

    static {
        for (CylinderSection section : CylinderSection.values()) {
            UP_SHAPES[section.ordinal()] = createShape(section, Direction.UP);
            DOWN_SHAPES[section.ordinal()] = createShape(section, Direction.DOWN);
            SHARED_Z_UP_SHAPES[section.ordinal()] =
                    createSharedShape(section, Direction.UP, CylinderSharedWall.STRIP_Z);
            SHARED_Z_DOWN_SHAPES[section.ordinal()] =
                    createSharedShape(section, Direction.DOWN, CylinderSharedWall.STRIP_Z);
            SHARED_X_UP_SHAPES[section.ordinal()] =
                    createSharedShape(section, Direction.UP, CylinderSharedWall.STRIP_X);
            SHARED_X_DOWN_SHAPES[section.ordinal()] =
                    createSharedShape(section, Direction.DOWN, CylinderSharedWall.STRIP_X);
        }
    }

    public static VoxelShape forSection(CylinderSection section) {
        return forSection(section, Direction.UP);
    }

    public static VoxelShape forSection(CylinderSection section, Direction facing) {
        return forSection(section, facing, CylinderSharedWall.NONE);
    }

    public static VoxelShape forSection(CylinderSection section, Direction facing, CylinderSharedWall sharedWall) {
        if (section == null || section == CylinderSection.NONE) {
            return Shapes.block();
        }
        if (sharedWall == CylinderSharedWall.STRIP_Z) {
            return facing == Direction.DOWN
                    ? SHARED_Z_DOWN_SHAPES[section.ordinal()]
                    : SHARED_Z_UP_SHAPES[section.ordinal()];
        }
        if (sharedWall == CylinderSharedWall.STRIP_X) {
            return facing == Direction.DOWN
                    ? SHARED_X_DOWN_SHAPES[section.ordinal()]
                    : SHARED_X_UP_SHAPES[section.ordinal()];
        }
        return facing == Direction.DOWN ? DOWN_SHAPES[section.ordinal()] : UP_SHAPES[section.ordinal()];
    }

    private static VoxelShape createShape(CylinderSection section, Direction facing) {
        if (section == CylinderSection.NONE) {
            return Shapes.block();
        }

        double cellMinX = section.xOffset() * 16.0D;
        double cellMinY = section.yOffset() * 16.0D;
        double cellMinZ = section.zOffset() * 16.0D;
        double cellMaxX = cellMinX + 16.0D;
        double cellMaxY = cellMinY + 16.0D;
        double cellMaxZ = cellMinZ + 16.0D;

        VoxelShape shape = Shapes.empty();
        for (RingBox source : ASSEMBLED_BOXES) {
            RingBox oriented = facing == Direction.DOWN ? source.mirrorY(32.0D) : source;
            RingBox shifted = oriented.shift(SHIFT, 0, SHIFT);
            double minX = Math.max(shifted.minX, cellMinX);
            double minY = Math.max(shifted.minY, cellMinY);
            double minZ = Math.max(shifted.minZ, cellMinZ);
            double maxX = Math.min(shifted.maxX, cellMaxX);
            double maxY = Math.min(shifted.maxY, cellMaxY);
            double maxZ = Math.min(shifted.maxZ, cellMaxZ);

            if (minX >= maxX || minY >= maxY || minZ >= maxZ) {
                continue;
            }

            shape = Shapes.or(shape, Block.box(
                    minX - cellMinX,
                    minY - cellMinY,
                    minZ - cellMinZ,
                    maxX - cellMinX,
                    maxY - cellMinY,
                    maxZ - cellMinZ
            ));
        }
        return shape.optimize();
    }

    private static VoxelShape createSharedShape(
            CylinderSection section,
            Direction facing,
            CylinderSharedWall sharedWall
    ) {
        if (section == CylinderSection.NONE || sharedWall == CylinderSharedWall.NONE) {
            return createShape(section, facing);
        }

        int stripSegment = sharedWall == CylinderSharedWall.STRIP_Z
                ? section.zOffset()
                : 2 - section.xOffset();
        double cellMinY = section.yOffset() * 16.0D;
        double cellMaxY = cellMinY + 16.0D;
        double cellMinZ = (stripSegment - 1) * 16.0D;
        double cellMaxZ = cellMinZ + 16.0D;

        VoxelShape shape = Shapes.empty();
        for (RingBox source : SHARED_WALL_BOXES) {
            RingBox oriented = facing == Direction.DOWN ? source.mirrorY(32.0D) : source;
            double minX = Math.max(oriented.minX, 0);
            double minY = Math.max(oriented.minY, cellMinY);
            double minZ = Math.max(oriented.minZ, cellMinZ);
            double maxX = Math.min(oriented.maxX, 16);
            double maxY = Math.min(oriented.maxY, cellMaxY);
            double maxZ = Math.min(oriented.maxZ, cellMaxZ);

            if (minX >= maxX || minY >= maxY || minZ >= maxZ) {
                continue;
            }

            RingBox local = new RingBox(
                    minX,
                    minY - cellMinY,
                    minZ - cellMinZ,
                    maxX,
                    maxY - cellMinY,
                    maxZ - cellMinZ
            );
            if (sharedWall == CylinderSharedWall.STRIP_X) {
                local = local.rotateY90();
            }

            shape = Shapes.or(shape, Block.box(
                    local.minX,
                    local.minY,
                    local.minZ,
                    local.maxX,
                    local.maxY,
                    local.maxZ
            ));
        }
        return shape.optimize();
    }

    private record RingBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        RingBox shift(double x, double y, double z) {
            return new RingBox(minX + x, minY + y, minZ + z, maxX + x, maxY + y, maxZ + z);
        }

        RingBox mirrorY(double height) {
            return new RingBox(minX, height - maxY, minZ, maxX, height - minY, maxZ);
        }

        RingBox rotateY90() {
            return new RingBox(16 - maxZ, minY, minX, 16 - minZ, maxY, maxX);
        }
    }

    private CylinderRingShapes() {
    }
}
