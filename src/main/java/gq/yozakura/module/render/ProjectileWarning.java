package gq.yozakura.module.render;

import gq.yozakura.event.bridge.Render2DEvent;
import gq.yozakura.event.bridge.Render3DEvent;
import gq.yozakura.event.bridge.TickEvent;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import gq.yozakura.module.combat.AntiBot;
import gq.yozakura.util.module.TeamUtil;
import gq.yozakura.util.render.RenderUtil;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.block.BlockBed;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityLargeFireball;
import net.minecraft.entity.projectile.EntityWitherSkull;
import net.minecraft.init.Blocks;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.StringUtils;
import net.minecraft.util.Vec3;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ProjectileWarning extends Module {
    private static final int FIREBALL_COLOR = 0xFFFF4D5A;
    private static final double FIREBALL_RAY_DISTANCE = 500.0D;
    private static final double FIREBALL_WARNING_HALF_EXTENT = 2.5D;

    private final Option<Boolean> fireballWarning =
            new Option<Boolean>("Fireball Warning", "FireballWarning", true);
    private final Option<Boolean> bedWarning =
            new Option<Boolean>("Bed Warning", "BedWarning", true);

    private final Numbers<Double> bedThreatRange =
            new Numbers<Double>("Bed Threat Range", "BedThreatRange", 50.0D, 8.0D, 64.0D, 1.0D);
    private final Numbers<Double> bedSearchRange =
            new Numbers<Double>("Bed Search Range", "BedSearchRange", 8.0D, 4.0D, 16.0D, 1.0D);

    private Object trackedWorld;
    private BlockPos ownBed;
    private boolean ownBedDestroyed;
    private boolean middleMouseDown;
    private int lastBedWarsStatus = -1;
    private float progressSmooth;
    private final Set<UUID> teamWhitelist = new HashSet<UUID>();
    private EntityFireball trackedFireball;
    private ProjectileWarningPolicy.Point trackedFireballOrigin;
    private FireballPrediction nearestFireballPrediction;
    private final ArrayList<BlockPos> predictedDestroyedBlocks = new ArrayList<BlockPos>();
    private ProjectileWarningPolicy.BedThreat nearestBedThreat;

    public ProjectileWarning() {
        super("ProjectileWarning", Keyboard.KEY_NONE, ModuleType.Render,
                "Warn about incoming fireballs or enemies near your bed");
        this.addValues(fireballWarning, bedWarning, bedThreatRange, bedSearchRange);
        Chinese = "投掷物预警";
    }

    @Override
    public void enable() {
        resetState();
    }

    @Override
    public void disable() {
        resetState();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event == null || event.getType() != EventType.POST || !isInGame()) {
            return;
        }
        refreshWorldState();
        boolean mouseDown = Mouse.isButtonDown(2);
        if (mouseDown && !middleMouseDown) {
            setBedFromMiddleClick();
        }
        middleMouseDown = mouseDown;

        int status = currentBedWarsStatus();
        if (Boolean.TRUE.equals(bedWarning.getValue()) && status == 2 && lastBedWarsStatus != 2) {
            BlockPos detected = findNearestBed(new BlockPos(mc.thePlayer.posX,
                    mc.thePlayer.posY, mc.thePlayer.posZ));
            if (detected != null) {
                setOwnBed(detected);
            }
        }
        lastBedWarsStatus = status;
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!isInGame() || mc.getRenderManager() == null) {
            resetTransientWarnings();
            return;
        }
        refreshWorldState();
        nearestFireballPrediction = null;
        predictedDestroyedBlocks.clear();
        if (Boolean.TRUE.equals(fireballWarning.getValue())) {
            collectFireballPrediction();
        }
        if (nearestFireballPrediction != null) {
            renderFireballPrediction();
        }
        if (!predictedDestroyedBlocks.isEmpty()) {
            renderPredictedDestroyedBlocks();
        }
        refreshBedThreat();
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!isInGame()) {
            return;
        }
        ScaledResolution resolution = new ScaledResolution(mc);
        float baseY = resolution.getScaledHeight() - 50.0F;
        if (nearestBedThreat != null && Boolean.TRUE.equals(bedWarning.getValue())
                && mc.currentScreen == null) {
            drawBedThreat(resolution, baseY);
            baseY -= 28.0F;
        }
        if (nearestFireballPrediction != null && nearestFireballPrediction.dangerous
                && Boolean.TRUE.equals(fireballWarning.getValue())) {
            drawFireballWarning(resolution, baseY);
        }
    }

    private void collectFireballPrediction() {
        double nearestImpactDistance = Double.POSITIVE_INFINITY;
        FireballPrediction best = null;
        ProjectileWarningPolicy.Point playerPosition = point(
                mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        for (Object object : mc.theWorld.loadedEntityList) {
            if (!(object instanceof EntityFireball) || object instanceof EntityWitherSkull) {
                continue;
            }
            EntityFireball fireball = (EntityFireball) object;
            ProjectileWarningPolicy.Point position = point(
                    fireball.posX, fireball.posY, fireball.posZ);
            ProjectileWarningPolicy.Point motion = point(
                    fireball.motionX, fireball.motionY, fireball.motionZ);
            if (!ProjectileWarningPolicy.hasReferenceFireballMotion(motion)) {
                continue;
            }
            ProjectileWarningPolicy.Point rayEnd = ProjectileWarningPolicy.referenceRayEnd(
                    position, motion, FIREBALL_RAY_DISTANCE);
            MovingObjectPosition collision = mc.theWorld.rayTraceBlocks(
                    toVec(position), toVec(rayEnd));
            if (collision == null || collision.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                    || collision.getBlockPos() == null) {
                continue;
            }
            BlockPos hitBlock = collision.getBlockPos();
            ProjectileWarningPolicy.Point impactCenter = point(
                    hitBlock.getX() + 0.5D, hitBlock.getY() + 0.5D, hitBlock.getZ() + 0.5D);
            double playerDistance = playerPosition.subtract(impactCenter).lengthSquared();
            if (playerDistance < nearestImpactDistance) {
                double impactDistance = position.subtract(point(
                        collision.hitVec.xCoord, collision.hitVec.yCoord,
                        collision.hitVec.zCoord)).length();
                double speed = motion.length();
                best = new FireballPrediction(fireball, collision.hitVec, impactCenter,
                        ProjectileWarningPolicy.referenceEtaSeconds(
                                position.subtract(impactCenter).length(), speed),
                        ProjectileWarningPolicy.referenceDistanceColor(impactDistance),
                        ProjectileWarningPolicy.isInsideReferenceWarningBox(
                                playerPosition, impactCenter, FIREBALL_WARNING_HALF_EXTENT));
                nearestImpactDistance = playerDistance;
            }
        }
        nearestFireballPrediction = best;
        if (best == null) {
            trackedFireball = null;
            trackedFireballOrigin = null;
            return;
        }
        if (best.fireball != trackedFireball) {
            trackedFireball = best.fireball;
            trackedFireballOrigin = point(best.fireball.posX,
                    best.fireball.posY, best.fireball.posZ);
        }
        collectPredictedDestroyedBlocks(best.fireball, best.impact);
    }

    private void collectPredictedDestroyedBlocks(EntityFireball fireball, Vec3 impact) {
        double strength = ProjectileWarningPolicy.inferredExplosionStrength(
                fireball instanceof EntityLargeFireball,
                fireball instanceof EntityLargeFireball ? ((EntityLargeFireball) fireball).explosionPower : 0);
        if (strength <= 0.0D || impact == null) {
            return;
        }
        int radius = Math.max(1, (int) Math.ceil(strength));
        BlockPos center = new BlockPos(impact.xCoord, impact.yCoord, impact.zCoord);
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos position = center.add(x, y, z);
                    IBlockState state = mc.theWorld.getBlockState(position);
                    Block block = state.getBlock();
                    if (block == Blocks.air) {
                        continue;
                    }
                    double distance = Math.sqrt(position.distanceSq(
                            impact.xCoord, impact.yCoord, impact.zCoord));
                    float hardness = block.getBlockHardness(mc.theWorld, position);
                    float resistance = block.getExplosionResistance(fireball);
                    boolean immune = block == Blocks.bedrock || block == Blocks.barrier
                            || block == Blocks.end_portal || block == Blocks.end_portal_frame
                            || block == Blocks.command_block;
                    if (ProjectileWarningPolicy.isPredictedDestroyedBlock(
                            distance, strength, hardness, resistance, immune)
                            && !predictedDestroyedBlocks.contains(position)) {
                        predictedDestroyedBlocks.add(position);
                    }
                }
            }
        }
    }

    private void renderFireballPrediction() {
        RenderManager renderManager = mc.getRenderManager();
        ProjectileWarningPolicy.Point origin = trackedFireballOrigin;
        if (origin != null) {
            RenderUtil.drawLine3D(
                    origin.getX() - renderManager.viewerPosX,
                    origin.getY() - renderManager.viewerPosY,
                    origin.getZ() - renderManager.viewerPosZ,
                    nearestFireballPrediction.impactCenter.getX() - renderManager.viewerPosX,
                    nearestFireballPrediction.impactCenter.getY() - renderManager.viewerPosY,
                    nearestFireballPrediction.impactCenter.getZ() - renderManager.viewerPosZ,
                    0xC0FFFFFF, true);
        }
        double radius = 0.18D;
        ProjectileWarningPolicy.Point impact = nearestFireballPrediction.impactCenter;
        AxisAlignedBB marker = new AxisAlignedBB(
                impact.getX() - renderManager.viewerPosX - radius,
                impact.getY() - renderManager.viewerPosY - radius,
                impact.getZ() - renderManager.viewerPosZ - radius,
                impact.getX() - renderManager.viewerPosX + radius,
                impact.getY() - renderManager.viewerPosY + radius,
                impact.getZ() - renderManager.viewerPosZ + radius);
        RenderUtil.drawBox(marker, nearestFireballPrediction.color & 0x48FFFFFF, true);
        RenderUtil.drawOutlinedBox(marker, nearestFireballPrediction.color, true);
    }

    private void renderPredictedDestroyedBlocks() {
        RenderManager renderManager = mc.getRenderManager();
        for (BlockPos position : predictedDestroyedBlocks) {
            AxisAlignedBB box = new AxisAlignedBB(
                    position.getX() - renderManager.viewerPosX + 0.03D,
                    position.getY() - renderManager.viewerPosY + 0.03D,
                    position.getZ() - renderManager.viewerPosZ + 0.03D,
                    position.getX() - renderManager.viewerPosX + 0.97D,
                    position.getY() - renderManager.viewerPosY + 0.97D,
                    position.getZ() - renderManager.viewerPosZ + 0.97D);
            RenderUtil.drawBox(box, 0x38FF4D2E, true);
            RenderUtil.drawOutlinedBox(box, 0xD0FF6B3D, true);
        }
    }

    private void refreshWorldState() {
        if (trackedWorld == mc.theWorld) {
            return;
        }
        trackedWorld = mc.theWorld;
        ownBed = null;
        ownBedDestroyed = false;
        middleMouseDown = false;
        lastBedWarsStatus = -1;
        progressSmooth = 0.0F;
        teamWhitelist.clear();
        resetTransientWarnings();
    }

    private void refreshBedThreat() {
        nearestBedThreat = null;
        if (!Boolean.TRUE.equals(bedWarning.getValue()) || ownBed == null) {
            progressSmooth = 0.0F;
            return;
        }
        if (mc.theWorld.getBlockState(ownBed).getBlock() != Blocks.bed) {
            ownBed = null;
            ownBedDestroyed = true;
            teamWhitelist.clear();
            progressSmooth = 0.0F;
            return;
        }

        ArrayList<ProjectileWarningPolicy.BedThreat> candidates =
                new ArrayList<ProjectileWarningPolicy.BedThreat>();
        for (Object object : mc.theWorld.playerEntities) {
            if (!(object instanceof EntityPlayer)) {
                continue;
            }
            EntityPlayer player = (EntityPlayer) object;
            boolean eligible = player != mc.thePlayer && !player.isDead && !player.isSpectator()
                    && !AntiBot.isServerBot(player) && !TeamUtil.isBot(player)
                    && !teamWhitelist.contains(player.getUniqueID())
                    && !TeamUtil.isSameTeam(player);
            candidates.add(new ProjectileWarningPolicy.BedThreat(stripColor(player.getName()),
                    distanceToBed(player, ownBed), eligible));
        }
        nearestBedThreat = ProjectileWarningPolicy.selectNearestBedThreat(
                candidates, bedThreatRange.getValue());
        if (nearestBedThreat == null) {
            progressSmooth = 0.0F;
        }
    }

    private void setBedFromMiddleClick() {
        if (mc.objectMouseOver == null || mc.objectMouseOver.getBlockPos() == null) {
            return;
        }
        BlockPos selected = normalizeBedFoot(mc.objectMouseOver.getBlockPos());
        if (selected != null) {
            setOwnBed(selected);
        }
    }

    private void setOwnBed(BlockPos bed) {
        ownBed = bed;
        ownBedDestroyed = false;
        progressSmooth = 0.0F;
        teamWhitelist.clear();
        for (Object object : mc.theWorld.playerEntities) {
            if (object instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer) object;
                if (player != mc.thePlayer && TeamUtil.isSameTeam(player)) {
                    teamWhitelist.add(player.getUniqueID());
                }
            }
        }
    }

    private BlockPos normalizeBedFoot(BlockPos position) {
        IBlockState state = mc.theWorld.getBlockState(position);
        if (state.getBlock() != Blocks.bed) {
            return null;
        }
        if (state.getValue(BlockBed.PART) == BlockBed.EnumPartType.FOOT) {
            return position;
        }
        return position.offset(state.getValue(BlockBed.FACING).getOpposite());
    }

    private BlockPos findNearestBed(BlockPos center) {
        int radius = bedSearchRange.getValue().intValue();
        BlockPos nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos position = center.add(x, y, z);
                    IBlockState state = mc.theWorld.getBlockState(position);
                    if (state.getBlock() != Blocks.bed
                            || state.getValue(BlockBed.PART) != BlockBed.EnumPartType.FOOT) {
                        continue;
                    }
                    double distance = center.distanceSq(position);
                    if (distance < nearestDistance) {
                        nearest = position;
                        nearestDistance = distance;
                    }
                }
            }
        }
        return nearest;
    }

    private int currentBedWarsStatus() {
        ScoreObjective objective = mc.theWorld.getScoreboard().getObjectiveInDisplaySlot(1);
        if (objective == null) {
            return -1;
        }
        return ProjectileWarningPolicy.bedWarsStatus(
                stripHypixelCodes(objective.getDisplayName()), sidebarLines(objective));
    }

    private List<String> sidebarLines(ScoreObjective objective) {
        ArrayList<String> lines = new ArrayList<String>();
        Collection<Score> scores = mc.theWorld.getScoreboard().getSortedScores(objective);
        ArrayList<Score> filtered = new ArrayList<Score>();
        for (Score score : scores) {
            if (score != null && score.getPlayerName() != null
                    && !score.getPlayerName().startsWith("#")) {
                filtered.add(score);
            }
        }
        int start = Math.max(0, filtered.size() - 15);
        for (int index = start; index < filtered.size(); index++) {
            Score score = filtered.get(index);
            ScorePlayerTeam team = mc.theWorld.getScoreboard().getPlayersTeam(score.getPlayerName());
            lines.add(stripHypixelCodes(ScorePlayerTeam.formatPlayerName(team, score.getPlayerName())));
        }
        return lines;
    }

    private String stripHypixelCodes(String input) {
        String stripped = StringUtils.stripControlCodes(input == null ? "" : input);
        StringBuilder ascii = new StringBuilder();
        for (int index = 0; index < stripped.length(); index++) {
            char character = stripped.charAt(index);
            if (character >= 0x20 && character <= 0x7E) {
                ascii.append(character);
            }
        }
        return ascii.toString();
    }

    private String stripColor(String input) {
        String stripped = EnumChatFormatting.getTextWithoutFormattingCodes(input);
        return stripped == null ? "" : stripped;
    }

    private void drawBedThreat(ScaledResolution resolution, float ignoredY) {
        float width = 100.0F;
        float height = 6.0F;
        float x = resolution.getScaledWidth() * 0.5F - width * 0.5F;
        float y = ProjectileWarningPolicy.bedWarningY(resolution.getScaledHeight());
        double distance = nearestBedThreat.getDistance();
        float target = (float) ProjectileWarningPolicy.bedAlarmProgress(
                distance, bedThreatRange.getValue());
        progressSmooth += (target - progressSmooth) * 0.15F;

        int color;
        if (progressSmooth < 0.33F) {
            color = 0xFFFF3C3C;
        } else if (progressSmooth < 0.66F) {
            color = 0xFFFFC828;
        } else {
            color = 0xFF50DC50;
        }
        RenderUtil.drawRect(x - 2.0F, y - 2.0F, x + width + 2.0F, y + height + 2.0F, 0x28000000);
        RenderUtil.drawRect(x, y, x + width, y + height, 0x80000000);
        if (progressSmooth > 0.0F) {
            RenderUtil.drawRect(x, y, x + width * progressSmooth, y + height, color);
        }

        int blocks = Math.max(0, (int) Math.round(distance));
        String text = nearestBedThreat.getName().isEmpty()
                ? blocks + "m" : nearestBedThreat.getName() + " " + blocks + "m";
        int textX = Math.round(x + (width - mc.fontRendererObj.getStringWidth(text)) * 0.5F);
        mc.fontRendererObj.drawStringWithShadow(text,
                textX, y - mc.fontRendererObj.FONT_HEIGHT - 2.0F, 0xFFFFFFFF);
    }

    private void drawFireballWarning(ScaledResolution resolution, float y) {
        double seconds = nearestFireballPrediction.etaSeconds;
        String text = String.format(java.util.Locale.ROOT, "火焰弹来袭 %.1fs", seconds);
        float width = Math.max(112.0F, mc.fontRendererObj.getStringWidth(text) + 18.0F);
        float x = (resolution.getScaledWidth() - width) * 0.5F;
        RenderUtil.drawRect(x, y, x + width, y + 19.0F, 0xD8181012);
        RenderUtil.drawRect(x, y, x + 2.0F, y + 19.0F, FIREBALL_COLOR);
        mc.fontRendererObj.drawStringWithShadow(text,
                x + (width - mc.fontRendererObj.getStringWidth(text)) * 0.5F, y + 5.0F, 0xFFFFD8D8);
    }

    private double distanceToBed(EntityPlayer player, BlockPos bed) {
        double x = player.posX - (bed.getX() + 0.5D);
        double y = player.posY - (bed.getY() + 0.5D);
        double z = player.posZ - (bed.getZ() + 0.5D);
        return Math.sqrt(x * x + y * y + z * z);
    }

    private void resetState() {
        trackedWorld = null;
        ownBed = null;
        ownBedDestroyed = false;
        middleMouseDown = false;
        lastBedWarsStatus = -1;
        progressSmooth = 0.0F;
        teamWhitelist.clear();
        resetTransientWarnings();
    }

    private void resetTransientWarnings() {
        trackedFireball = null;
        trackedFireballOrigin = null;
        nearestFireballPrediction = null;
        predictedDestroyedBlocks.clear();
        nearestBedThreat = null;
    }

    private static ProjectileWarningPolicy.Point point(double x, double y, double z) {
        return new ProjectileWarningPolicy.Point(x, y, z);
    }

    private static Vec3 toVec(ProjectileWarningPolicy.Point point) {
        return new Vec3(point.getX(), point.getY(), point.getZ());
    }

    private static final class FireballPrediction {
        private final EntityFireball fireball;
        private final Vec3 impact;
        private final ProjectileWarningPolicy.Point impactCenter;
        private final double etaSeconds;
        private final int color;
        private final boolean dangerous;

        private FireballPrediction(EntityFireball fireball, Vec3 impact,
                                   ProjectileWarningPolicy.Point impactCenter,
                                   double etaSeconds, int color, boolean dangerous) {
            this.fireball = fireball;
            this.impact = impact;
            this.impactCenter = impactCenter;
            this.etaSeconds = etaSeconds;
            this.color = color;
            this.dangerous = dangerous;
        }
    }
}
