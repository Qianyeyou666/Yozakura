package gq.yozakura.module.render;

import gq.yozakura.bridge.YozakuraEventBridge;
import gq.yozakura.engine.render.ui.RenderServices;
import gq.yozakura.event.bridge.Render2DEvent;
import gq.yozakura.event.bus.EventTarget;
import gq.yozakura.module.Module;
import gq.yozakura.module.ModuleType;
import gq.yozakura.util.render.RenderUtil;
import gq.yozakura.util.render.ScreenSpaceGlowRenderer;
import gq.yozakura.value.Mode;
import gq.yozakura.value.Numbers;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BedESP extends Module {
    private static final int DEFENSE_RADIUS = 2;
    private static final int DEFENSE_HEIGHT = 2;
    private static final int SCAN_CHUNKS_PER_TICK = 4;
    private static final int RESCAN_DELAY_TICKS = 40;
    private static final float PANEL_ICON_SIZE = 16.0F;
    private static final float PANEL_ICON_GAP = 2.0F;
    private static final float PANEL_PADDING = 4.0F;
    private static final float PANEL_MARGIN = 3.0F;
    private static final float PANEL_OFFSET_Y = 8.0F;
    private static final FloatBuffer MODEL_VIEW = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer PROJECTION = BufferUtils.createFloatBuffer(16);
    private static final IntBuffer VIEWPORT = BufferUtils.createIntBuffer(16);
    private static final FloatBuffer PROJECTED_POINT = BufferUtils.createFloatBuffer(3);

    public enum BedEspMode {
        OUTLINE,
        FILLED,
        BOTH,
        GLOWESP
    }

    private final Mode<BedEspMode> mode = new Mode<BedEspMode>("Mode", "Mode",
            BedEspMode.values(), BedEspMode.BOTH);
    private final Numbers<Double> glowStrength = new Numbers<Double>("Glow Strength", "GlowStrength",
            0.88D, 0.20D, 1.0D, 0.02D);
    private final Numbers<Double> backgroundAlpha = new Numbers<Double>(
            "Background Alpha", "BackgroundAlpha", 224.0D, 0.0D, 255.0D, 1.0D);
    private final Set<BedEspBlockSelector.Position> scannedBeds =
            new HashSet<BedEspBlockSelector.Position>();

    private List<BlockPos> bedBlocks = Collections.emptyList();
    private List<BedDefensePanelEntry> defensePanels = Collections.emptyList();
    private final List<BedDefensePanelOverlayEntry> overlayEntries = new ArrayList<BedDefensePanelOverlayEntry>();
    private List<ChunkCoordIntPair> scanQueue = Collections.emptyList();
    private int scanCursor;
    private int rescanDelay;
    private boolean scanning;
    private World scanWorld;

    public BedESP() {
        super("BedESP", Keyboard.KEY_NONE, ModuleType.Render, "Highlight BedWars bed defenses");
        glowStrength.visibleWhen(() -> mode.getValue() == BedEspMode.GLOWESP);
        addValues(mode, glowStrength, backgroundAlpha);
        Chinese = "床透视";
    }

    @Override
    public void enable() {
        clearScanState();
        if (isInGame()) {
            startScan();
        }
    }

    @Override
    public void disable() {
        clearScanState();
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!isInGame()) {
            clearScanState();
            return;
        }
        if (scanWorld != mc.theWorld) {
            clearScanState();
            startScan();
        }
        if (!scanning) {
            if (rescanDelay > 0) {
                rescanDelay--;
                return;
            }
            startScan();
        }
        scanNextChunks();
    }

    @SubscribeEvent
    public void onWorld(RenderWorldLastEvent event) {
        if (!isInGame() || isClickGuiOpen() || bedBlocks.isEmpty()) {
            overlayEntries.clear();
            return;
        }
        collectOverlayEntries();
        if (mode.getValue() == BedEspMode.GLOWESP) {
            renderGlow(event.partialTicks);
            return;
        }
        renderBedBoxes();
    }

    @EventTarget
    public void onOverlay(Render2DEvent event) {
        renderDefensePanels();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onOverlay(RenderGameOverlayEvent.Text event) {
        if (!YozakuraEventBridge.hasRenderedOverlayThisFrame()) {
            renderDefensePanels();
        }
    }

    private void startScan() {
        scanWorld = mc.theWorld;
        scannedBeds.clear();
        scanQueue = createScanQueue();
        scanCursor = 0;
        scanning = !scanQueue.isEmpty();
        if (!scanning) {
            rebuildHighlightedBlocks();
            rescanDelay = RESCAN_DELAY_TICKS;
        }
    }

    private List<ChunkCoordIntPair> createScanQueue() {
        int centerChunkX = MathHelper.floor_double(mc.thePlayer.posX) >> 4;
        int centerChunkZ = MathHelper.floor_double(mc.thePlayer.posZ) >> 4;
        int radius = Math.max(0, mc.gameSettings.renderDistanceChunks);
        List<ChunkCoordIntPair> chunks = new ArrayList<ChunkCoordIntPair>((radius * 2 + 1) * (radius * 2 + 1));
        for (int distance = 0; distance <= radius; distance++) {
            for (int xOffset = -distance; xOffset <= distance; xOffset++) {
                for (int zOffset = -distance; zOffset <= distance; zOffset++) {
                    if (Math.max(Math.abs(xOffset), Math.abs(zOffset)) != distance) {
                        continue;
                    }
                    chunks.add(new ChunkCoordIntPair(centerChunkX + xOffset, centerChunkZ + zOffset));
                }
            }
        }
        return chunks;
    }

    private void scanNextChunks() {
        IChunkProvider provider = mc.theWorld.getChunkProvider();
        boolean foundBed = false;
        int scannedChunks = 0;
        while (scannedChunks < SCAN_CHUNKS_PER_TICK && scanCursor < scanQueue.size()) {
            ChunkCoordIntPair coordinate = scanQueue.get(scanCursor++);
            scannedChunks++;
            if (!provider.chunkExists(coordinate.chunkXPos, coordinate.chunkZPos)) {
                continue;
            }
            int bedsBefore = scannedBeds.size();
            scanChunk(provider.provideChunk(coordinate.chunkXPos, coordinate.chunkZPos));
            foundBed |= scannedBeds.size() != bedsBefore;
        }
        if (foundBed) {
            rebuildHighlightedBlocks();
        }
        if (scanCursor >= scanQueue.size()) {
            scanning = false;
            rebuildHighlightedBlocks();
            rescanDelay = RESCAN_DELAY_TICKS;
        }
    }

    private void scanChunk(Chunk chunk) {
        if (chunk == null) {
            return;
        }
        ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
        for (ExtendedBlockStorage section : sections) {
            if (section == null || section.isEmpty()) {
                continue;
            }
            int sectionY = section.getYLocation();
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = 0; y < 16; y++) {
                        Block block = section.getBlockByExtId(x, y, z);
                        if (block == Blocks.bed) {
                            scannedBeds.add(new BedEspBlockSelector.Position(
                                    (chunk.xPosition << 4) + x, sectionY + y, (chunk.zPosition << 4) + z));
                        }
                    }
                }
            }
        }
    }

    private void rebuildHighlightedBlocks() {
        final World world = mc.theWorld;
        if (world == null) {
            bedBlocks = Collections.emptyList();
            defensePanels = Collections.emptyList();
            return;
        }
        List<BlockPos> beds = new ArrayList<BlockPos>(scannedBeds.size());
        for (BedEspBlockSelector.Position position : scannedBeds) {
            BlockPos bedPosition = toBlockPos(position);
            if (world.getBlockState(bedPosition).getBlock() == Blocks.bed) {
                beds.add(bedPosition);
            }
        }
        bedBlocks = beds;
        defensePanels = buildDefensePanels(world, beds);
    }

    private List<BedDefensePanelEntry> buildDefensePanels(World world, List<BlockPos> beds) {
        List<BedDefensePanelEntry> panels = new ArrayList<BedDefensePanelEntry>();
        for (BlockPos foot : beds) {
            IBlockState footState = world.getBlockState(foot);
            if (footState.getBlock() != Blocks.bed
                    || footState.getValue(BlockBed.PART) != BlockBed.EnumPartType.FOOT) {
                continue;
            }
            EnumFacing facing = footState.getValue(BlockBed.FACING);
            BlockPos head = foot.offset(facing);
            if (world.getBlockState(head).getBlock() != Blocks.bed) {
                continue;
            }
            Set<BedEspBlockSelector.Position> defenses = BedEspBlockSelector.collect(Arrays.asList(
                    toSelectorPosition(foot), toSelectorPosition(head)), DEFENSE_RADIUS, DEFENSE_HEIGHT,
                    position -> isDefenseBlock(world, position));
            List<ItemStack> icons = createDefenseIcons(world, defenses);
            if (!icons.isEmpty()) {
                panels.add(new BedDefensePanelEntry(foot, head, icons));
            }
        }
        return panels;
    }

    private List<ItemStack> createDefenseIcons(World world, Set<BedEspBlockSelector.Position> defenses) {
        List<BedEspBlockSelector.Position> positions = new ArrayList<BedEspBlockSelector.Position>(defenses);
        Collections.sort(positions, new Comparator<BedEspBlockSelector.Position>() {
            @Override
            public int compare(BedEspBlockSelector.Position left, BedEspBlockSelector.Position right) {
                int x = left.getX() - right.getX();
                if (x != 0) {
                    return x;
                }
                int y = left.getY() - right.getY();
                return y != 0 ? y : left.getZ() - right.getZ();
            }
        });

        List<DefenseIcon> candidates = new ArrayList<DefenseIcon>(positions.size());
        for (BedEspBlockSelector.Position position : positions) {
            IBlockState state = world.getBlockState(toBlockPos(position));
            Block block = state.getBlock();
            Item item = Item.getItemFromBlock(block);
            if (item == null) {
                continue;
            }
            int metadata = block.getMetaFromState(state);
            candidates.add(new DefenseIcon(Block.getIdFromBlock(block) + ":" + metadata,
                    new ItemStack(item, 1, metadata)));
        }

        List<DefenseIcon> unique = BedDefensePanel.uniqueMaterials(candidates);
        List<ItemStack> icons = new ArrayList<ItemStack>(unique.size());
        for (DefenseIcon icon : unique) {
            icons.add(icon.stack);
        }
        return icons;
    }

    private void collectOverlayEntries() {
        overlayEntries.clear();
        if (defensePanels.isEmpty() || !captureProjectionState()) {
            return;
        }
        double viewerX = mc.getRenderManager().viewerPosX;
        double viewerY = mc.getRenderManager().viewerPosY;
        double viewerZ = mc.getRenderManager().viewerPosZ;
        for (BedDefensePanelEntry panel : defensePanels) {
            double anchorX = (panel.foot.getX() + panel.head.getX() + 1.0D) * 0.5D;
            double anchorY = panel.foot.getY() + 1.25D;
            double anchorZ = (panel.foot.getZ() + panel.head.getZ() + 1.0D) * 0.5D;
            float[] projected = project(anchorX - viewerX, anchorY - viewerY, anchorZ - viewerZ);
            if (projected != null && projected[2] >= 0.0F && projected[2] <= 1.0F) {
                double cameraX = viewerX - anchorX;
                double cameraY = viewerY - anchorY;
                double cameraZ = viewerZ - anchorZ;
                float distance = (float) Math.sqrt(cameraX * cameraX + cameraY * cameraY + cameraZ * cameraZ);
                overlayEntries.add(new BedDefensePanelOverlayEntry(projected[0], projected[1],
                        BedDefensePanel.scaleForDistance(distance), panel.icons));
            }
        }
    }

    private boolean captureProjectionState() {
        try {
            MODEL_VIEW.clear();
            PROJECTION.clear();
            VIEWPORT.clear();
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODEL_VIEW);
            GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, PROJECTION);
            GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT);
            MODEL_VIEW.rewind();
            PROJECTION.rewind();
            VIEWPORT.rewind();
            return VIEWPORT.get(2) > 0 && VIEWPORT.get(3) > 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private float[] project(double x, double y, double z) {
        PROJECTED_POINT.clear();
        MODEL_VIEW.rewind();
        PROJECTION.rewind();
        VIEWPORT.rewind();
        if (!GLU.gluProject((float) x, (float) y, (float) z, MODEL_VIEW, PROJECTION, VIEWPORT, PROJECTED_POINT)) {
            return null;
        }
        int scaleFactor = new ScaledResolution(mc).getScaleFactor();
        return new float[]{PROJECTED_POINT.get(0) / scaleFactor,
                (mc.displayHeight - PROJECTED_POINT.get(1)) / scaleFactor, PROJECTED_POINT.get(2)};
    }

    private void renderDefensePanels() {
        if (!isInGame() || isClickGuiOpen() || overlayEntries.isEmpty()) {
            return;
        }
        ScaledResolution resolution = new ScaledResolution(mc);
        for (BedDefensePanelOverlayEntry entry : overlayEntries) {
            renderDefensePanel(entry, resolution);
        }
    }

    private boolean isClickGuiOpen() {
        GuiScreen currentScreen = mc.currentScreen;
        return currentScreen != null
                && currentScreen.getClass().getName().startsWith("gq.yozakura.ui.click.");
    }

    private void renderDefensePanel(BedDefensePanelOverlayEntry entry, ScaledResolution resolution) {
        int iconCount = entry.icons.size();
        int columns = BedDefensePanel.columns(iconCount);
        if (columns == 0) {
            return;
        }
        int rows = BedDefensePanel.rows(iconCount);
        float iconSize = PANEL_ICON_SIZE * entry.scale;
        float iconGap = PANEL_ICON_GAP * entry.scale;
        float padding = PANEL_PADDING * entry.scale;
        float width = padding * 2.0F + columns * iconSize + (columns - 1) * iconGap;
        float height = padding * 2.0F + rows * iconSize + (rows - 1) * iconGap;
        float x = clamp(entry.x - width * 0.5F, PANEL_MARGIN,
                resolution.getScaledWidth() - PANEL_MARGIN - width);
        float y = clamp(entry.y - height - PANEL_OFFSET_Y * entry.scale, PANEL_MARGIN,
                resolution.getScaledHeight() - PANEL_MARGIN - height);
        int surface = withAlpha(ClickGUI.currentPalette().getSurfaceRaised(),
                backgroundAlpha.getValue().intValue());
        int border = withAlpha(ClickGUI.currentPalette().getAccentAlt(), 98);
        float radius = 4.0F * entry.scale;
        HUD.drawNightBloomShadow(x, y, x + width, y + height, radius, 0.72F);
        RenderServices.shapes().roundedBorder(x, y, x + width, y + height, radius,
                Math.max(0.4F, 0.65F * entry.scale), surface, border);
        for (int index = 0; index < iconCount; index++) {
            float iconX = x + padding + (index % columns) * (iconSize + iconGap);
            float iconY = y + padding + (index / columns) * (iconSize + iconGap);
            renderDefenseIcon(entry.icons.get(index), iconX, iconY, entry.scale);
        }
    }

    private void renderDefenseIcon(ItemStack stack, float x, float y, float scale) {
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(Math.round(x), Math.round(y), 0.0F);
            GlStateManager.scale(scale, scale, 1.0F);
            GlStateManager.enableTexture2D();
            GlStateManager.enableAlpha();
            GlStateManager.enableDepth();
            GlStateManager.depthMask(true);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            RenderHelper.enableGUIStandardItemLighting();
            try {
                GlStateManager.enableRescaleNormal();
                mc.getRenderItem().renderItemAndEffectIntoGUI(stack, 0, 0);
            } finally {
                RenderHelper.disableStandardItemLighting();
                GlStateManager.disableRescaleNormal();
                GlStateManager.disableDepth();
            }
        } finally {
            GlStateManager.depthMask(true);
            GlStateManager.popMatrix();
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.enableAlpha();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private boolean isDefenseBlock(World world, BedEspBlockSelector.Position position) {
        Block block = world.getBlockState(toBlockPos(position)).getBlock();
        return block != Blocks.air && block != Blocks.bed;
    }

    private void renderGlow(float partialTicks) {
        ScreenSpaceGlowRenderer renderer = ScreenSpaceGlowRenderer.shared();
        renderer.beginFrame(ClickGUI.currentPalette(), glowStrength.getValue().floatValue());
        try {
            for (BlockPos position : bedBlocks) {
                renderer.collect(position);
            }
            renderer.renderMask(partialTicks);
            renderer.composite();
        } finally {
            if (renderer.isFrameOpen()) {
                renderer.discard();
            }
        }
    }

    private void renderBedBoxes() {
        int color = ClickGUI.currentPalette().getAccentAlt();
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;
        boolean drawOutline = mode.getValue() != BedEspMode.FILLED;
        boolean drawFill = mode.getValue() != BedEspMode.OUTLINE;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LINE_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_LINE_SMOOTH);
            GL11.glLineWidth(1.5F);
            for (BlockPos position : bedBlocks) {
                AxisAlignedBB box = relativeBox(position);
                if (drawFill) {
                    GL11.glColor4f(red, green, blue, 0.25F);
                    RenderUtil.drawBoundingBox(box);
                }
                if (drawOutline) {
                    GL11.glColor4f(red, green, blue, 0.90F);
                    RenderUtil.drawOutlinedBoundingBox(box);
                }
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private AxisAlignedBB relativeBox(BlockPos position) {
        double x = position.getX() - mc.getRenderManager().viewerPosX;
        double y = position.getY() - mc.getRenderManager().viewerPosY;
        double z = position.getZ() - mc.getRenderManager().viewerPosZ;
        return new AxisAlignedBB(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D);
    }

    private static BlockPos toBlockPos(BedEspBlockSelector.Position position) {
        return new BlockPos(position.getX(), position.getY(), position.getZ());
    }

    private static BedEspBlockSelector.Position toSelectorPosition(BlockPos position) {
        return new BedEspBlockSelector.Position(position.getX(), position.getY(), position.getZ());
    }

    private void clearScanState() {
        scannedBeds.clear();
        bedBlocks = Collections.emptyList();
        defensePanels = Collections.emptyList();
        overlayEntries.clear();
        scanQueue = Collections.emptyList();
        scanCursor = 0;
        rescanDelay = 0;
        scanning = false;
        scanWorld = null;
    }

    private static final class BedDefensePanelEntry {
        private final BlockPos foot;
        private final BlockPos head;
        private final List<ItemStack> icons;

        private BedDefensePanelEntry(BlockPos foot, BlockPos head, List<ItemStack> icons) {
            this.foot = foot;
            this.head = head;
            this.icons = icons;
        }
    }

    private static final class BedDefensePanelOverlayEntry {
        private final float x;
        private final float y;
        private final float scale;
        private final List<ItemStack> icons;

        private BedDefensePanelOverlayEntry(float x, float y, float scale, List<ItemStack> icons) {
            this.x = x;
            this.y = y;
            this.scale = scale;
            this.icons = icons;
        }
    }

    private static final class DefenseIcon {
        private final String materialKey;
        private final ItemStack stack;

        private DefenseIcon(String materialKey, ItemStack stack) {
            this.materialKey = materialKey;
            this.stack = stack;
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof DefenseIcon && materialKey.equals(((DefenseIcon) object).materialKey);
        }

        @Override
        public int hashCode() {
            return materialKey.hashCode();
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
