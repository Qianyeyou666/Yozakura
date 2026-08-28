package gq.yozakura.module.world;

import gq.yozakura.bridge.MinecraftAccessor;
import gq.yozakura.event.bridge.HitBlockEvent;
import gq.yozakura.event.bridge.KnockbackEvent;
import gq.yozakura.event.bridge.LeftClickMouseEvent;
import gq.yozakura.event.bridge.LoadWorldEvent;
import gq.yozakura.event.bridge.PacketEvent;
import gq.yozakura.event.bridge.RightClickMouseEvent;
import gq.yozakura.event.bridge.Render2DEvent;
import gq.yozakura.event.bridge.SwapItemEvent;
import gq.yozakura.event.bridge.TickEvent;
import gq.yozakura.event.bridge.UpdateEvent;
import gq.yozakura.event.bus.EventManager;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.event.bus.types.EventType;
import gq.yozakura.event.bus.types.Priority;
import gq.yozakura.manager.VisualRotationState;
import gq.yozakura.engine.font.FontLoaders;
import gq.yozakura.module.ModuleType;
import gq.yozakura.module.runtime.Module;
import gq.yozakura.util.animation.MotionValue;
import gq.yozakura.util.animation.UiClock;
import gq.yozakura.util.module.ItemUtil;
import gq.yozakura.util.module.PacketUtil;
import gq.yozakura.util.render.RenderUtil;
import gq.yozakura.value.Mode;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C07PacketPlayerDigging.Action;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.potion.Potion;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BedNuker extends Module {
    private static final int ROTATION_PRIORITY = 5;
    private static final long WHITELIST_SCAN_DELAY_MS = 1000L;
    private static final float BED_PROGRESS_WIDTH = 112.0F;
    private static final float BED_PROGRESS_HEIGHT = 24.0F;
    private static final float BED_PROGRESS_EPSILON = 0.001F;

    private enum BreakMode {
        NORMAL,
        SWAP
    }

    private enum VelocityMode {
        NONE,
        CANCEL
    }

    private final Mode<BreakMode> mode =
            new Mode<BreakMode>("Mode", "Mode", BreakMode.values(), BreakMode.NORMAL);
    private final Numbers<Double> range =
            new Numbers<Double>("Range", "Range", 4.5D, 3.0D, 6.0D, 0.1D);
    private final Numbers<Double> speed =
            new Numbers<Double>("Speed", "Speed", 0.0D, 0.0D, 100.0D, 1.0D);
    private final Option<Boolean> groundSpeed =
            new Option<Boolean>("Ground Spoof", "GroundSpoof", false);
    private final Mode<VelocityMode> ignoreVelocity =
            new Mode<VelocityMode>("Ignore Velocity", "IgnoreVelocity", VelocityMode.values(), VelocityMode.NONE);
    private final Option<Boolean> surroundings =
            new Option<Boolean>("Surroundings", "Surroundings", true);
    private final Option<Boolean> toolCheck =
            new Option<Boolean>("Tool Check", "ToolCheck", true);
    private final Option<Boolean> whiteList =
            new Option<Boolean>("Whitelist", "Whitelist", true);
    private final Option<Boolean> swing = new Option<Boolean>("Swing", "Swing", true);

    private final List<BlockPos> bedWhitelist = new ArrayList<BlockPos>();
    private BlockPos targetBed;
    private int breakStage;
    private int tickCounter;
    private float breakProgress;
    private boolean targetIsBed;
    private boolean readyToBreak;
    private boolean breaking;
    private int savedSlot = -1;
    private boolean waitingForStart;
    private long whitelistScanAt = -1L;
    private final UiClock bedProgressClock = new UiClock();
    private final MotionValue bedProgressVisibility = new MotionValue(0.0F);
    private final MotionValue bedProgressFill = new MotionValue(0.0F);
    private float displayedBedProgress;
    private boolean bedTaskActive;
    private boolean bedTaskHasProtection;
    private float bedTaskProgressBase;
    private long bedTaskCompletedUntil;
    private BlockPos bedTaskBed;
    private BlockPos selectedBedAnchor;
    private final BedProgressExitRenderer bedProgressExitRenderer = new BedProgressExitRenderer();
    private boolean bedProgressExitRendererRegistered;

    public BedNuker() {
        super("BedNuker", false);
        this.key = Keyboard.KEY_NONE;
        this.category = ModuleType.World;
        this.Chinese = "自动挖床";
        this.Descript = "Break exposed beds or their surrounding protection";
        this.About = this.Descript;
        this.addValues(mode, range, speed, groundSpeed, ignoreVelocity,
                surroundings, toolCheck, whiteList, swing);
    }

    @Override
    public void onEnabled() {
        resetBreaking();
        this.bedProgressClock.reset();
        this.bedProgressVisibility.snapTo(0.0F);
        this.bedProgressFill.snapTo(0.0F);
        this.displayedBedProgress = 0.0F;
        this.resetBedTaskProgress();
        this.unregisterBedProgressExitRenderer();
    }

    @Override
    public void onDisabled() {
        this.bedProgressVisibility.setTarget(0.0F);
        this.bedProgressFill.setTarget(0.0F);
        if (this.isBedProgressHudExiting()) {
            this.registerBedProgressExitRenderer();
        }
        restoreSlot();
        resetBreaking();
        resetBedTaskProgress();
        waitingForStart = false;
        whitelistScanAt = -1L;
        bedWhitelist.clear();
    }

    public boolean isReady() {
        return targetBed != null && readyToBreak;
    }

    public boolean isBreaking() {
        return targetBed != null && breaking;
    }

    public BlockPos getTarget() {
        return targetBed;
    }

    @EventTarget(Priority.HIGH)
    public void onTick(TickEvent event) {
        if (event.getType() != EventType.PRE) {
            return;
        }
        runPendingWhitelistScan();
        if (!canRun()) {
            restoreSlot();
            resetBreaking();
            return;
        }
        if (targetBed != null && (!isBreakable(targetBed) || !canReach(targetBed))) {
            restoreSlot();
            resetBreaking();
        } else if (targetBed != null && !targetIsBed && nearestBed() != null
                && isBed(nearestBed())) {
            restoreSlot();
            resetBreaking();
        }

        if (targetBed != null) {
            breakCurrentTarget();
            if (targetBed != null) {
                return;
            }
        }
        if (mc.thePlayer.capabilities.allowEdit) {
            targetBed = nearestBed();
            breakStage = 0;
            tickCounter = 0;
            breakProgress = 0.0F;
            targetIsBed = targetBed != null && isBed(targetBed);
            if (targetBed != null) {
                this.beginOrContinueBedTask(this.selectedBedAnchor, !this.targetIsBed);
            }
            restoreSlot();
            readyToBreak = targetBed != null;
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE || !isReady() || !isInGame()) {
            return;
        }
        float[] rotations = rotationsTo(targetBed, event.getYaw(), event.getPitch());
        if (!event.trySetRotation(rotations[0], rotations[1], ROTATION_PRIORITY)) {
            return;
        }
        event.setPervRotation(rotations[0], ROTATION_PRIORITY);
        VisualRotationState.publish("BedNuker", rotations[0], rotations[1], ROTATION_PRIORITY);
    }

    private void beginOrContinueBedTask(BlockPos bed, boolean protectionStage) {
        if (bed == null) {
            return;
        }
        if (!bed.equals(this.bedTaskBed)) {
            this.bedTaskBed = bed;
            this.bedTaskActive = true;
            this.bedTaskHasProtection = protectionStage;
            this.bedTaskProgressBase = 0.0F;
            this.bedTaskCompletedUntil = 0L;
            this.bedProgressFill.snapTo(0.0F);
        } else if (protectionStage) {
            this.bedTaskHasProtection = true;
        } else if (this.bedTaskHasProtection) {
            this.bedTaskProgressBase = Math.max(this.bedTaskProgressBase, 0.42F);
        }
    }

    private void resetBedTaskProgress() {
        this.bedTaskActive = false;
        this.bedTaskHasProtection = false;
        this.bedTaskProgressBase = 0.0F;
        this.bedTaskCompletedUntil = 0L;
        this.bedTaskBed = null;
        this.selectedBedAnchor = null;
    }

    private float getBedBreakHudProgress() {
        long now = System.currentTimeMillis();
        if (this.bedTaskCompletedUntil > now) {
            return 1.0F;
        }
        if (this.bedTaskCompletedUntil > 0L) {
            this.resetBedTaskProgress();
            return 0.0F;
        }
        if (!this.bedTaskActive || this.targetBed == null || !this.breaking) {
            return this.bedTaskProgressBase;
        }
        double threshold = BedNukerTargetPolicy.completionThreshold(this.speed.getValue());
        if (threshold <= 0.0D) {
            return this.bedTaskProgressBase;
        }
        float phase = Math.max(0.0F, Math.min(1.0F, (float) (this.breakProgress / threshold)));
        if (!this.targetIsBed) {
            return Math.max(this.bedTaskProgressBase, phase * 0.42F);
        }
        float start = this.bedTaskHasProtection ? 0.42F : 0.0F;
        return Math.max(this.bedTaskProgressBase, start + phase * (1.0F - start));
    }

    private boolean isBedProgressHudExiting() {
        return this.bedProgressVisibility.get() > BED_PROGRESS_EPSILON
                || this.bedProgressVisibility.getTarget() > BED_PROGRESS_EPSILON;
    }

    private void updateBedProgressHud(float progress, float deltaSeconds) {
        boolean visible = this.isEnabled() && (this.bedTaskActive || this.bedTaskCompletedUntil > System.currentTimeMillis());
        if (visible) {
            this.displayedBedProgress = progress;
        }
        this.bedProgressVisibility.setTarget(visible ? 1.0F : 0.0F);
        this.bedProgressFill.setTarget(visible ? progress : 0.0F);
        this.bedProgressVisibility.updateSpring(deltaSeconds, 0.34F);
        this.bedProgressFill.updateSpring(deltaSeconds, 0.24F);
        if (!visible && !this.isBedProgressHudExiting()) {
            this.displayedBedProgress = 0.0F;
            this.bedProgressFill.snapTo(0.0F);
        } else if (visible) {
            this.displayedBedProgress = this.bedProgressFill.get();
        }
    }

    private void renderBedProgressFrame() {
        if (mc.thePlayer == null) {
            return;
        }
        float deltaSeconds = this.bedProgressClock.tick(System.nanoTime());
        float progress = this.isEnabled() ? this.getBedBreakHudProgress() : 0.0F;
        this.updateBedProgressHud(progress, deltaSeconds);
        float visibility = this.bedProgressVisibility.get();
        if (visibility <= BED_PROGRESS_EPSILON) {
            if (!this.isEnabled()) {
                this.unregisterBedProgressExitRenderer();
            }
            return;
        }

        gq.yozakura.engine.font.CFontRenderer font = FontLoaders.productSans(14);
        gq.yozakura.engine.font.CFontRenderer smallFont = FontLoaders.productSans(12);
        ScaledResolution resolution = new ScaledResolution(mc);
        float centerX = resolution.getScaledWidth() * 0.5F;
        float baseY = resolution.getScaledHeight() * 0.5F + 48.0F;
        float y = baseY + (1.0F - visibility) * 10.0F;
        float x = centerX - BED_PROGRESS_WIDTH * 0.5F;
        float panelScale = 0.88F + visibility * 0.12F;
        int percent = Math.round(this.displayedBedProgress * 100.0F);
        String percentText = percent + "%";
        int fill = RenderUtil.applyOpacity(0xF01A1A20, visibility);
        int border = RenderUtil.applyOpacity(0x30FFFFFF, visibility);
        int primary = RenderUtil.applyOpacity(0xFFF4F4F7, visibility);
        int secondary = RenderUtil.applyOpacity(0xFFB7B7C2, visibility);
        int track = RenderUtil.applyOpacity(0xFF303039, visibility);
        int accent = RenderUtil.applyOpacity(0xFFFF6B86, visibility);
        float barX = x + 7.0F;
        float barY = y + 17.0F;
        float barWidth = BED_PROGRESS_WIDTH - 14.0F;
        float progressWidth = barWidth * Math.max(0.0F, Math.min(1.0F, this.displayedBedProgress));

        GlStateManager.pushMatrix();
        try {
            GlStateManager.enableBlend();
            GlStateManager.enableAlpha();
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            RenderUtil.scaleStart(centerX, y + BED_PROGRESS_HEIGHT * 0.5F, panelScale);
            RenderUtil.drawRoundedBorderedRect(x, y, x + BED_PROGRESS_WIDTH,
                    y + BED_PROGRESS_HEIGHT, 5.0F, 0.6F, fill, border);
            font.drawString("Breaking bed", x + 7.0F, y + 4.5F, primary);
            smallFont.drawString(percentText,
                    x + BED_PROGRESS_WIDTH - 7.0F - smallFont.getStringWidth(percentText),
                    y + 5.5F, secondary);
            RenderUtil.drawRoundedRect(barX, barY, barX + barWidth, barY + 3.0F, 1.5F, track);
            if (progressWidth > 0.0F) {
                RenderUtil.drawRoundedRect(barX, barY, barX + progressWidth,
                        barY + 3.0F, 1.5F, accent);
            }
            RenderUtil.scaleEnd();
        } finally {
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.enableAlpha();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.popMatrix();
        }
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (this.isEnabled()) {
            this.renderBedProgressFrame();
        }
    }

    private void registerBedProgressExitRenderer() {
        if (!this.bedProgressExitRendererRegistered) {
            EventManager.register(this.bedProgressExitRenderer);
            this.bedProgressExitRendererRegistered = true;
        }
    }

    private void unregisterBedProgressExitRenderer() {
        if (this.bedProgressExitRendererRegistered) {
            EventManager.unregister(this.bedProgressExitRenderer);
            this.bedProgressExitRendererRegistered = false;
        }
    }

    private final class BedProgressExitRenderer {
        @EventTarget
        public void onRender(Render2DEvent event) {
            if (!BedNuker.this.isEnabled()) {
                BedNuker.this.renderBedProgressFrame();
            }
        }
    }

    @EventTarget(Priority.HIGH)
    public void onKnockback(KnockbackEvent event) {
        if (targetBed != null && ignoreVelocity.getValue() == VelocityMode.CANCEL && event.getY() > 0.0D) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.isCancelled() || event.getType() != EventType.RECEIVE) {
            return;
        }
        if (event.getPacket() instanceof S02PacketChat) {
            String text = ((S02PacketChat) event.getPacket()).getChatComponent().getFormattedText();
            if (text.contains("§e§lProtect your bed and destroy the enemy bed")
                    || text.contains("§e§lDestroy the enemy bed and then eliminate them")) {
                waitingForStart = true;
            }
        } else if (event.getPacket() instanceof S08PacketPlayerPosLook && waitingForStart) {
            waitingForStart = false;
            bedWhitelist.clear();
            scheduleWhitelistScan();
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        restoreSlot();
        waitingForStart = false;
        whitelistScanAt = -1L;
        bedWhitelist.clear();
        resetBreaking();
    }

    @EventTarget
    public void onLeftClick(LeftClickMouseEvent event) {
        if (isReady() || targetBed != null && isLookingAtBlock()) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (isReady()) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onHitBlock(HitBlockEvent event) {
        if (isReady() || targetBed != null && isLookingAtBlock()) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onSwap(SwapItemEvent event) {
        if (savedSlot != -1) {
            event.setCancelled(true);
        }
    }

    private void breakCurrentTarget() {
        IBlockState state = mc.theWorld.getBlockState(targetBed);
        int toolSlot = ItemUtil.findInventorySlot(mc.thePlayer.inventory.currentItem, state.getBlock());
        if (mode.getValue() == BreakMode.NORMAL && savedSlot == -1) {
            saveAndSwitch(toolSlot);
        }
        switch (breakStage) {
            case 0:
                if (!mc.thePlayer.isUsingItem()) {
                    doSwing();
                    PacketUtil.sendPacket(new C07PacketPlayerDigging(
                            Action.START_DESTROY_BLOCK, targetBed, getHitFacing(targetBed)));
                    doSwing();
                    mc.effectRenderer.addBlockHitEffects(targetBed, getHitFacing(targetBed));
                    breakStage = 1;
                }
                break;
            case 1:
                if (mode.getValue() == BreakMode.SWAP) {
                    readyToBreak = false;
                }
                breaking = true;
                tickCounter++;
                breakProgress += getBreakDelta(state, targetBed, toolSlot, mc.thePlayer.onGround);
                float spoofProgress = tickCounter * getBreakDelta(
                        state, targetBed, toolSlot, mc.thePlayer.onGround && Boolean.TRUE.equals(groundSpeed.getValue()));
                mc.effectRenderer.addBlockHitEffects(targetBed, getHitFacing(targetBed));
                double threshold = BedNukerTargetPolicy.completionThreshold(speed.getValue());
                if (breakProgress >= threshold || spoofProgress >= threshold) {
                    if (mode.getValue() == BreakMode.SWAP) {
                        readyToBreak = true;
                        saveAndSwitch(toolSlot);
                        if (mc.thePlayer.isUsingItem()) {
                            mc.thePlayer.inventory.currentItem = (mc.thePlayer.inventory.currentItem + 1) % 9;
                            syncHeldItem();
                        }
                    }
                    breaking = false;
                    PacketUtil.sendPacket(new C07PacketPlayerDigging(
                            Action.STOP_DESTROY_BLOCK, targetBed, getHitFacing(targetBed)));
                    doSwing();
                    IBlockState finishedState = mc.theWorld.getBlockState(targetBed);
                    Block finishedBlock = finishedState.getBlock();
                    if (finishedBlock.getMaterial() != Material.air) {
                        mc.theWorld.playAuxSFX(2001, targetBed, Block.getStateId(finishedState));
                        mc.theWorld.setBlockToAir(targetBed);
                    }
                    if (this.targetIsBed) {
                        this.bedTaskProgressBase = 1.0F;
                        this.bedTaskCompletedUntil = System.currentTimeMillis() + 420L;
                    } else {
                        this.bedTaskProgressBase = Math.max(this.bedTaskProgressBase, 0.42F);
                    }
                    breakStage = 2;
                }
                break;
            default:
                restoreSlot();
                resetBreaking();
                break;
        }
    }

    private BlockPos nearestBed() {
        this.selectedBedAnchor = null;
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        int centerX = MathHelper.floor_double(eye.xCoord);
        int centerY = MathHelper.floor_double(eye.yCoord);
        int centerZ = MathHelper.floor_double(eye.zCoord);
        List<BedNukerTargetPolicy.Position> candidates = new ArrayList<BedNukerTargetPolicy.Position>();
        int radius = 6;
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int y = centerY - radius; y <= centerY + radius; y++) {
                for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                    BlockPos position = new BlockPos(x, y, z);
                    if (isBed(position) && canReach(position)
                            && BedNukerTargetPolicy.isEligibleBed(toPolicy(position),
                            toPolicy(bedWhitelist), Boolean.TRUE.equals(whiteList.getValue()))) {
                        candidates.add(toPolicy(position));
                    }
                }
            }
        }
        BedNukerTargetPolicy.Position nearest = BedNukerTargetPolicy.selectNearest(
                candidates, eye.xCoord, eye.yCoord, eye.zCoord, range.getValue());
        BlockPos bed = fromPolicy(nearest);
        if (bed == null) {
            return null;
        }
        this.selectedBedAnchor = bed;
        if (Boolean.TRUE.equals(surroundings.getValue())) {
            BlockPos surrounding = validateBedPlacement(bed);
            if (surrounding != null) {
                Block block = mc.theWorld.getBlockState(surrounding).getBlock();
                if (!Boolean.TRUE.equals(toolCheck.getValue()) || hasProperTool(block)) {
                    return surrounding;
                }
            }
        }
        return bed;
    }

    private BlockPos validateBedPlacement(BlockPos bedPosition) {
        IBlockState bedState = mc.theWorld.getBlockState(bedPosition);
        if (!(bedState.getBlock() instanceof BlockBed)) {
            return null;
        }
        BlockBed.EnumPartType part = bedState.getValue(BlockBed.PART);
        EnumFacing facing = bedState.getValue(BlockBed.FACING);
        List<BedNukerTargetPolicy.Surrounding> candidates =
                new ArrayList<BedNukerTargetPolicy.Surrounding>();
        for (BlockPos half : Arrays.asList(bedPosition,
                bedPosition.offset(part == BlockBed.EnumPartType.HEAD ? facing.getOpposite() : facing))) {
            for (EnumFacing side : Arrays.asList(EnumFacing.UP, EnumFacing.NORTH,
                    EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST)) {
                BlockPos adjacent = half.offset(side);
                Block block = mc.theWorld.getBlockState(adjacent).getBlock();
                if (isReplaceable(block)) {
                    return null;
                }
                if (!(block instanceof BlockBed)) {
                    candidates.add(new BedNukerTargetPolicy.Surrounding(
                            toPolicy(adjacent), calcBlockStrength(adjacent)));
                }
            }
        }
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        return fromPolicy(BedNukerTargetPolicy.selectSurrounding(
                candidates, eye.xCoord, eye.yCoord, eye.zCoord));
    }

    private float calcBlockStrength(BlockPos position) {
        IBlockState state = mc.theWorld.getBlockState(position);
        int slot = ItemUtil.findInventorySlot(mc.thePlayer.inventory.currentItem, state.getBlock());
        return getBreakDelta(state, position, slot, mc.thePlayer.onGround);
    }

    private float getBreakDelta(IBlockState state, BlockPos position, int slot, boolean onGround) {
        Block block = state.getBlock();
        float hardness = block.getBlockHardness(mc.theWorld, position);
        if (hardness < 0.0F) {
            return 0.0F;
        }
        float divisor = canHarvest(block, slot) ? 30.0F : 100.0F;
        return getDigSpeed(state, slot, onGround) / hardness / divisor;
    }

    private float getDigSpeed(IBlockState state, int slot, boolean onGround) {
        ItemStack item = mc.thePlayer.inventory.getStackInSlot(slot);
        float digSpeed = item == null ? 1.0F : item.getStrVsBlock(state.getBlock());
        if (digSpeed > 1.0F && item != null) {
            int efficiency = EnchantmentHelper.getEnchantmentLevel(Enchantment.efficiency.effectId, item);
            if (efficiency > 0) {
                digSpeed += efficiency * efficiency + 1.0F;
            }
        }
        if (mc.thePlayer.isPotionActive(Potion.digSpeed)) {
            digSpeed *= 1.0F + (mc.thePlayer.getActivePotionEffect(Potion.digSpeed).getAmplifier() + 1) * 0.2F;
        }
        if (mc.thePlayer.isPotionActive(Potion.digSlowdown)) {
            int amplifier = mc.thePlayer.getActivePotionEffect(Potion.digSlowdown).getAmplifier();
            digSpeed *= amplifier == 0 ? 0.3F : amplifier == 1 ? 0.09F : amplifier == 2 ? 0.0027F : 0.00081F;
        }
        if (mc.thePlayer.isInsideOfMaterial(Material.water)
                && !EnchantmentHelper.getAquaAffinityModifier(mc.thePlayer)) {
            digSpeed /= 5.0F;
        }
        if (!onGround) {
            digSpeed /= 5.0F;
        }
        return digSpeed;
    }

    private boolean canHarvest(Block block, int slot) {
        if (block.getMaterial().isToolNotRequired()) {
            return true;
        }
        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
        return stack != null && stack.canHarvestBlock(block);
    }

    private boolean hasProperTool(Block block) {
        Material material = block.getMaterial();
        if (material != Material.iron && material != Material.anvil && material != Material.rock) {
            return true;
        }
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (stack != null && stack.getItem() instanceof ItemPickaxe) {
                return true;
            }
        }
        return false;
    }

    private void scheduleWhitelistScan() {
        whitelistScanAt = System.currentTimeMillis() + WHITELIST_SCAN_DELAY_MS;
    }

    private void runPendingWhitelistScan() {
        if (whitelistScanAt == -1L || System.currentTimeMillis() < whitelistScanAt) {
            return;
        }
        whitelistScanAt = -1L;
        bedWhitelist.clear();
        if (!isInGame()) {
            return;
        }
        int centerX = MathHelper.floor_double(mc.thePlayer.posX);
        int centerY = MathHelper.floor_double(mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        int centerZ = MathHelper.floor_double(mc.thePlayer.posZ);
        for (int x = centerX - 25; x <= centerX + 25; x++) {
            for (int y = centerY - 25; y <= centerY + 25; y++) {
                for (int z = centerZ - 25; z <= centerZ + 25; z++) {
                    BlockPos position = new BlockPos(x, y, z);
                    if (isBed(position)) {
                        bedWhitelist.add(position);
                    }
                }
            }
        }
    }

    private void saveAndSwitch(int slot) {
        if (savedSlot == -1) {
            savedSlot = mc.thePlayer.inventory.currentItem;
        }
        mc.thePlayer.inventory.currentItem = slot;
        syncHeldItem();
    }

    private void restoreSlot() {
        if (savedSlot != -1 && mc.thePlayer != null) {
            mc.thePlayer.inventory.currentItem = savedSlot;
            syncHeldItem();
            savedSlot = -1;
        }
    }

    private void syncHeldItem() {
        if (mc.thePlayer != null && mc.thePlayer.isUsingItem()) {
            mc.thePlayer.stopUsingItem();
        }
        MinecraftAccessor.syncCurrentPlayItem(mc.playerController);
    }

    private void resetBreaking() {
        if (targetBed != null && mc.theWorld != null && mc.thePlayer != null) {
            mc.theWorld.sendBlockBreakProgress(mc.thePlayer.getEntityId(), targetBed, -1);
        }
        targetBed = null;
        breakStage = 0;
        tickCounter = 0;
        breakProgress = 0.0F;
        targetIsBed = false;
        readyToBreak = false;
        breaking = false;
        VisualRotationState.clearSource("BedNuker");
    }

    private void doSwing() {
        if (Boolean.TRUE.equals(swing.getValue())) {
            mc.thePlayer.swingItem();
        } else {
            PacketUtil.sendPacket(new C0APacketAnimation());
        }
    }

    private EnumFacing getHitFacing(BlockPos position) {
        float[] rotations = rotationsTo(position, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        MovingObjectPosition hit = gq.yozakura.util.module.RotationUtil.rayTrace(
                rotations[0], rotations[1], 8.0D, 1.0F);
        return hit == null || hit.sideHit == null ? EnumFacing.UP : hit.sideHit;
    }

    private float[] rotationsTo(BlockPos position, float yaw, float pitch) {
        double x = position.getX() + 0.5D - mc.thePlayer.posX;
        double y = position.getY() + 0.5D - mc.thePlayer.posY - mc.thePlayer.getEyeHeight();
        double z = position.getZ() + 0.5D - mc.thePlayer.posZ;
        return gq.yozakura.util.module.RotationUtil.getRotationsTo(x, y, z, yaw, pitch);
    }

    private boolean canRun() {
        return isEnabled() && isInGame() && mc.currentScreen == null
                && mc.playerController != null && !mc.thePlayer.capabilities.isCreativeMode;
    }

    private boolean canReach(BlockPos position) {
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        return position.distanceSqToCenter(eye.xCoord, eye.yCoord, eye.zCoord)
                <= range.getValue() * range.getValue();
    }

    private boolean isBed(BlockPos position) {
        return position != null && mc.theWorld != null
                && mc.theWorld.getBlockState(position).getBlock() == Blocks.bed;
    }

    private boolean isBreakable(BlockPos position) {
        if (position == null || mc.theWorld == null) {
            return false;
        }
        Block block = mc.theWorld.getBlockState(position).getBlock();
        return block != Blocks.air && block.getBlockHardness(mc.theWorld, position) >= 0.0F;
    }

    private boolean isReplaceable(Block block) {
        return block == null || block == Blocks.air || block.getMaterial().isReplaceable();
    }

    private boolean isLookingAtBlock() {
        return mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK;
    }

    private static BedNukerTargetPolicy.Position toPolicy(BlockPos position) {
        return position == null ? null : new BedNukerTargetPolicy.Position(
                position.getX(), position.getY(), position.getZ());
    }

    private static List<BedNukerTargetPolicy.Position> toPolicy(List<BlockPos> positions) {
        List<BedNukerTargetPolicy.Position> result = new ArrayList<BedNukerTargetPolicy.Position>();
        for (BlockPos position : positions) {
            result.add(toPolicy(position));
        }
        return result;
    }

    private static BlockPos fromPolicy(BedNukerTargetPolicy.Position position) {
        return position == null ? null : new BlockPos(
                position.getX(), position.getY(), position.getZ());
    }

    @Override
    public String[] getSuffix() {
        return new String[]{mode.getModeAsString()};
    }
}
