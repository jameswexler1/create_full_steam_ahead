package dev.gustavo.fullsteamahead.registry;

import dev.gustavo.fullsteamahead.FullSteamAhead;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;

/**
 * Damage types for Create: Full Steam Ahead. The {@link DamageType} itself is defined by the
 * datapack at {@code data/full_steam_ahead/damage_type/steam.json}; this only holds the registry
 * key and a helper to build a {@link DamageSource} from the level's registry.
 */
public final class ModDamageTypes {
    public static final ResourceKey<DamageType> STEAM = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(FullSteamAhead.MOD_ID, "steam"));

    /** Scald damage source for steam venting from an open pipe end. */
    public static DamageSource steam(Level level) {
        return new DamageSource(
                level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(STEAM));
    }

    private ModDamageTypes() {
    }
}
