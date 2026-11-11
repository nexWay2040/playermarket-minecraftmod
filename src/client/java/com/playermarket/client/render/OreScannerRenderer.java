package com.playermarket.client.render;

import com.playermarket.client.ClientMarketState;
import com.playermarket.client.hud.MarketHudOverlay;
import com.playermarket.market.PlayerMarketAccount;
import com.playermarket.market.UpgradeType;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * Non-cheaty subtle sonar pulse highlighter for ORE_SCANNER upgrade (radius 5 / 8 / 12 blocks).
 */
public final class OreScannerRenderer {

    private record OreTarget(BlockPos pos, int rgb, int priority) {}

    private static volatile List<OreTarget> cachedOres = List.of();
    private static int cachedRadius = 0;
    private static int tickCounter = 0;

    private OreScannerRenderer() {
    }

    public static void register() {
        LevelRenderEvents.BEFORE_GIZMOS.register(context -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null || MarketHudOverlay.isEndingCinematicPlaying()) {
                return;
            }
            PlayerMarketAccount account = ClientMarketState.getAccount();
            int lvl = account.getUpgradeLevel(UpgradeType.ORE_SCANNER);
            if (lvl <= 0 || account.isPermanentImpaired() || account.isMarketCardBlocked()) {
                return;
            }
            List<OreTarget> targets = cachedOres;
            int radius = cachedRadius;
            if (targets.isEmpty() || radius <= 0) {
                return;
            }

            // 3.6-second expanding sonar pulse wave + subtle persistent shimmer when standing very close (<= 3.5 blocks)
            long cycleMs = 3600L;
            double cyclePos = (System.currentTimeMillis() % cycleMs) / (double) cycleMs;
            double waveDist = cyclePos * (radius + 2.0);

            double px = mc.player.getX();
            double py = mc.player.getEyeY();
            double pz = mc.player.getZ();

            try (Gizmos.TemporaryCollection ignored = context.levelRenderer().collectPerFrameRenderThreadGizmos()) {
                for (OreTarget ore : targets) {
                    double dx = (ore.pos.getX() + 0.5) - px;
                    double dy = (ore.pos.getY() + 0.5) - py;
                    double dz = (ore.pos.getZ() + 0.5) - pz;
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (dist > radius + 0.5) continue;

                    double distFade = Math.max(0.25, 1.0 - (dist / (radius + 1.5)));
                    double waveDelta = Math.abs(dist - waveDist);
                    double pulse = waveDelta < 2.6 ? (1.0 - waveDelta / 2.6) : 0.0;
                    double baseGlow = dist <= 3.5 ? 0.30 : 0.08;
                    double intensity = Math.min(1.0, (baseGlow + pulse * 0.72) * distFade);
                    if (intensity < 0.08) continue;

                    int strokeAlpha = Math.clamp((int) Math.round(intensity * 0xB8), 0x14, 0xC8);
                    int fillAlpha = Math.clamp((int) Math.round(intensity * 0x26), 0x06, 0x30);
                    int strokeColor = (strokeAlpha << 24) | ore.rgb;
                    int fillColor = (fillAlpha << 24) | ore.rgb;

                    float width = ore.priority >= 3 ? 2.1F : 1.5F;
                    Gizmos.cuboid(ore.pos, 0.004F, GizmoStyle.strokeAndFill(strokeColor, width, fillColor))
                            .setAlwaysOnTop();
                }
            }
        });
    }

    public static void tickScan(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            cachedOres = List.of();
            cachedRadius = 0;
            return;
        }
        PlayerMarketAccount account = ClientMarketState.getAccount();
        int lvl = account.getUpgradeLevel(UpgradeType.ORE_SCANNER);
        if (lvl <= 0 || account.isPermanentImpaired() || account.isMarketCardBlocked()) {
            cachedOres = List.of();
            cachedRadius = 0;
            return;
        }

        // Scan every 6 client ticks (~0.3s) to keep client performance effortless
        if ((tickCounter++ % 6) != 0) {
            return;
        }

        int radius = (int) Math.round(UpgradeType.ORE_SCANNER.getValueForLevel(lvl));
        int radiusSq = radius * radius;
        BlockPos center = mc.player.blockPosition();
        List<OreTarget> found = new ArrayList<>();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radiusSq) continue;
                    BlockPos p = center.offset(dx, dy, dz);
                    Block b = mc.level.getBlockState(p).getBlock();
                    OreTarget match = classifyOre(p, b, lvl);
                    if (match != null) {
                        found.add(match);
                        if (found.size() >= 64) {
                            cachedRadius = radius;
                            cachedOres = found;
                            return;
                        }
                    }
                }
            }
        }
        cachedRadius = radius;
        cachedOres = found;
    }

    private static OreTarget classifyOre(BlockPos pos, Block b, int scannerLvl) {
        if (b == Blocks.DIAMOND_ORE || b == Blocks.DEEPSLATE_DIAMOND_ORE) {
            return new OreTarget(pos, 0x38BDF8, 4); // Cyan
        }
        if (b == Blocks.ANCIENT_DEBRIS) {
            return new OreTarget(pos, 0xC084FC, 4); // Purple
        }
        if (b == Blocks.EMERALD_ORE || b == Blocks.DEEPSLATE_EMERALD_ORE) {
            return new OreTarget(pos, 0x34D399, 3); // Emerald green
        }
        if (b == Blocks.GOLD_ORE || b == Blocks.DEEPSLATE_GOLD_ORE || b == Blocks.NETHER_GOLD_ORE) {
            return new OreTarget(pos, 0xFBBF24, 3); // Amber gold
        }
        if (scannerLvl >= 2) {
            if (b == Blocks.IRON_ORE || b == Blocks.DEEPSLATE_IRON_ORE) {
                return new OreTarget(pos, 0xE2E8F0, 2); // Crisp silver
            }
            if (b == Blocks.REDSTONE_ORE || b == Blocks.DEEPSLATE_REDSTONE_ORE) {
                return new OreTarget(pos, 0xF87171, 2); // Crimson
            }
            if (b == Blocks.LAPIS_ORE || b == Blocks.DEEPSLATE_LAPIS_ORE) {
                return new OreTarget(pos, 0x60A5FA, 2); // Lapis blue
            }
        }
        return null;
    }
}
