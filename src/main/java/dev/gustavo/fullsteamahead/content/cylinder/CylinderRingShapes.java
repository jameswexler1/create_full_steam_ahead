package dev.gustavo.fullsteamahead.content.cylinder;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class CylinderRingShapes {
    private static final double SHIFT = 16.0D;
    private static final VoxelShape[] SHAPES = new VoxelShape[CylinderSection.values().length];
    private static final RingBox[] ASSEMBLED_BOXES = {
            new RingBox(16, 0, -2, 25, 31, 18),
            new RingBox(22, 0, 18, 24, 31, 20),
            new RingBox(20, 0, 20, 22, 31, 22),
            new RingBox(18, 0, 22, 20, 31, 24),
            new RingBox(-1, 0, 16, 18, 31, 25),
            new RingBox(-1, 0, -10, 18, 31, 0),
            new RingBox(22, 0, -4, 24, 31, -2),
            new RingBox(20, 0, -6, 22, 31, -4),
            new RingBox(18, 0, -8, 20, 31, -6),
            new RingBox(-3, 0, -8, -1, 31, -6),
            new RingBox(-5, 0, -6, -3, 31, -4),
            new RingBox(-7, 0, -4, -5, 31, -2),
            new RingBox(-9, 0, -2, 0, 31, 18),
            new RingBox(-3, 0, 22, -1, 31, 24),
            new RingBox(-5, 0, 20, -3, 31, 22),
            new RingBox(-7, 0, 18, -5, 31, 20),
            new RingBox(-1, 31, -8, 18, 32, 0),
            new RingBox(-1, 31, 16, 18, 32, 24),
            new RingBox(18, 31, -2, 24, 32, 18),
            new RingBox(-7, 31, -2, -1, 32, 18),
            new RingBox(-1, 31, 0, 1, 32, 16),
            new RingBox(16, 31, 0, 18, 32, 16),
            new RingBox(18, 31, 18, 22, 32, 20),
            new RingBox(18, 31, -4, 22, 32, -2),
            new RingBox(-5, 31, 18, -1, 32, 20),
            new RingBox(-5, 31, -4, -1, 32, -2),
            new RingBox(-3, 31, -6, -1, 32, -4),
            new RingBox(-3, 31, 20, -1, 32, 22),
            new RingBox(18, 31, 20, 20, 32, 22),
            new RingBox(18, 31, -6, 20, 32, -4),
            new RingBox(18, 0, 18, 22, 1, 20),
            new RingBox(18, 0, 20, 20, 1, 22),
            new RingBox(-3, 0, 20, -1, 1, 22),
            new RingBox(-5, 0, 18, -1, 1, 20),
            new RingBox(-5, 0, -4, -1, 1, -2),
            new RingBox(-3, 0, -6, -1, 1, -4),
            new RingBox(18, 0, -6, 20, 1, -4),
            new RingBox(18, 0, -4, 22, 1, -2)
    };

    static {
        for (CylinderSection section : CylinderSection.values()) {
            SHAPES[section.ordinal()] = createShape(section);
        }
    }

    public static VoxelShape forSection(CylinderSection section) {
        if (section == null || section == CylinderSection.NONE) {
            return Shapes.block();
        }
        return SHAPES[section.ordinal()];
    }

    private static VoxelShape createShape(CylinderSection section) {
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
            RingBox shifted = source.shift(SHIFT, 0, SHIFT);
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

    private record RingBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        RingBox shift(double x, double y, double z) {
            return new RingBox(minX + x, minY + y, minZ + z, maxX + x, maxY + y, maxZ + z);
        }
    }

    private CylinderRingShapes() {
    }
}
