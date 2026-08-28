package gq.yozakura.util.render;

import gq.yozakura.engine.render.GLStateManager;
import gq.yozakura.engine.render.ui.VisualPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.BlockPos;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A non-nested world-glow transaction: beginFrame, collect, renderMask, composite.
 * The mask capture temporarily enables RenderManager's native outline mode so
 * {@link EntityLivingBase} name tags and render layers cannot enter the mask.
 */
public final class ScreenSpaceGlowRenderer {
    private static final String OUTLINE_SHADER_RESOURCE =
            "/assets/minecraft/yozakura/shaders/world_glow_outline.frag";
    private static final String BLUR_SHADER_RESOURCE =
            "/assets/minecraft/yozakura/shaders/world_glow_blur.frag";
    private static final String COMPOSITE_SHADER_RESOURCE =
            "/assets/minecraft/yozakura/shaders/world_glow_composite.frag";
    private static final int GLOW_ATTRIB_MASK = GL11.GL_ENABLE_BIT
            | GL11.GL_COLOR_BUFFER_BIT
            | GL11.GL_CURRENT_BIT
            | GL11.GL_DEPTH_BUFFER_BIT
            | GL11.GL_HINT_BIT
            | GL11.GL_LINE_BIT
            | GL11.GL_SCISSOR_BIT
            | GL11.GL_STENCIL_BUFFER_BIT
            | GL11.GL_TEXTURE_BIT
            | GL11.GL_VIEWPORT_BIT;
    private static final int MASK_TEXTURE_UNIT = OpenGlHelper.defaultTexUnit;
    private static final int BLUR_TEXTURE_UNIT = OpenGlHelper.defaultTexUnit + 1;
    private static final FloatBuffer WEIGHT_BUFFER =
            BufferUtils.createFloatBuffer(ScreenSpaceGlowPlan.MAX_BLUR_RADIUS + 1);
    // LWJGL validates glGetInteger buffers against its maximum return width,
    // even when querying the four-component GL_VIEWPORT state.
    private static final IntBuffer VIEWPORT_BUFFER = BufferUtils.createIntBuffer(16);
    private static final String[] RENDER_OUTLINES_FIELD_NAMES =
            new String[]{"renderOutlines", "field_178639_r", "r"};
    private static final String VERTEX_SHADER =
            "#version 120\n"
                    + "void main() {\n"
                    + "    gl_TexCoord[0] = gl_MultiTexCoord0;\n"
                    + "    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;\n"
                    + "}\n";

    private static final ScreenSpaceGlowRenderer SHARED = new ScreenSpaceGlowRenderer();
    private static volatile Field renderOutlinesField;

    private final List<EntityLivingBase> entities = new ArrayList<EntityLivingBase>();
    private final List<BlockPos> blocks = new ArrayList<BlockPos>();
    private final Map<Integer, float[]> kernels = new HashMap<Integer, float[]>();
    private ScreenSpaceGlowPlan.Quality quality = ScreenSpaceGlowPlan.Quality.MEDIUM;
    private ScreenSpaceGlowColors colors;
    private float compositeStrength = 1.0f;
    private boolean fillCore;
    private boolean frameOpen;
    private boolean maskRendered;
    private Framebuffer maskFramebuffer;
    private Framebuffer outlineHorizontalFramebuffer;
    private Framebuffer outlineFramebuffer;
    private Framebuffer blurHorizontalFramebuffer;
    private Framebuffer blurFramebuffer;
    private Program outlineProgram;
    private Program blurProgram;
    private Program compositeProgram;

    public static ScreenSpaceGlowRenderer shared() {
        return SHARED;
    }

    public void beginFrame(VisualPalette palette) {
        beginFrame(palette, 1.0f);
    }

    public void beginFrame(VisualPalette palette, float strength) {
        beginFrame(palette, strength, false);
    }

    /** Starts a glow frame, optionally tinting the collected mask inside the entity body. */
    public void beginFrame(VisualPalette palette, float strength, boolean fillCore) {
        if (frameOpen) {
            throw new IllegalStateException("A world glow frame is already open");
        }
        colors = ScreenSpaceGlowColors.from(palette);
        compositeStrength = ScreenSpaceGlowPlan.clampStrength(strength);
        this.fillCore = fillCore;
        entities.clear();
        blocks.clear();
        frameOpen = true;
        maskRendered = false;
    }

    public boolean isFrameOpen() {
        return frameOpen;
    }

    public void collect(EntityLivingBase entity) {
        requireOpenFrame();
        if (entity == null) {
            throw new IllegalArgumentException("entity must not be null");
        }
        entities.add(entity);
    }

    public void collect(BlockPos blockPos) {
        requireOpenFrame();
        if (blockPos == null) {
            throw new IllegalArgumentException("blockPos must not be null");
        }
        blocks.add(blockPos);
    }

    public void renderMask(float partialTicks) {
        requireOpenFrame();
        if (maskRendered) {
            throw new IllegalStateException("World glow mask was already rendered for this frame");
        }
        ScreenSpaceGlowPlan plan = currentPlan();
        if (plan.getMaskPassCount() == 0) {
            maskRendered = true;
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        RenderManager renderManager = minecraft.getRenderManager();
        if (renderManager == null) {
            throw new IllegalStateException("World glow requires an active RenderManager");
        }

        RenderState state = null;
        boolean attribPushed = false;
        boolean matricesPushed = false;
        try {
            ensureSupported();
            state = RenderState.capture();
            GL11.glPushAttrib(GLOW_ATTRIB_MASK);
            attribPushed = true;
            pushMatrices();
            matricesPushed = true;

            ensureTargets(minecraft, plan);
            clearMask();
            configureMaskState();
            renderEntityMasks(renderManager, clampPartialTicks(partialTicks));
            renderBlockMasks(renderManager);
            maskRendered = true;
        } finally {
            restoreTransaction(state, attribPushed, matricesPushed);
        }
    }

    public void composite() {
        requireOpenFrame();
        try {
            ScreenSpaceGlowPlan plan = currentPlan();
            if (plan.getMaskPassCount() != 0 && !maskRendered) {
                throw new IllegalStateException("renderMask must run before world glow composite");
            }
            if (plan.getPostProcessPassCount() == 0) {
                return;
            }

            RenderState state = null;
            boolean attribPushed = false;
            boolean matricesPushed = false;
            try {
                ensureSupported();
                state = RenderState.capture();
                GL11.glPushAttrib(GLOW_ATTRIB_MASK);
                attribPushed = true;
                pushMatrices();
                matricesPushed = true;

                requireTargets();
                ensurePrograms();
                // Identity matrices are required by both blur and outline
                // passes and are not mutated by renderBlur/renderComposite
                // (only by renderComposite's viewport call, which we re-do
                // below). Set them once here so renderBlurPair does not need to
                // re-issue loadIdentityMatrices on its second invocation.
                loadIdentityMatrices();
                renderOutline(plan);
                renderBlurPair(plan.getOuterBlurRadius());
                renderComposite(state, false);
                renderBlurPair(plan.getCoreBlurRadius());
                renderComposite(state, true);
            } finally {
                restoreTransaction(state, attribPushed, matricesPushed);
            }
        } finally {
            closeFrame();
        }
    }

    public void discard() {
        requireOpenFrame();
        closeFrame();
    }

    public ScreenSpaceGlowPlan.Quality getQuality() {
        return quality;
    }

    public void setQuality(ScreenSpaceGlowPlan.Quality quality) {
        if (quality == null) {
            throw new IllegalArgumentException("quality must not be null");
        }
        if (frameOpen) {
            throw new IllegalStateException("World glow quality cannot change during a frame");
        }
        this.quality = quality;
    }

    private ScreenSpaceGlowPlan currentPlan() {
        return ScreenSpaceGlowPlan.forBatch(entities.size(), blocks.size(), quality);
    }

    private void requireOpenFrame() {
        if (!frameOpen) {
            throw new IllegalStateException("World glow work requires beginFrame(VisualPalette)");
        }
    }

    private void closeFrame() {
        entities.clear();
        blocks.clear();
        colors = null;
        fillCore = false;
        maskRendered = false;
        frameOpen = false;
    }

    private void ensureSupported() {
        boolean supported;
        try {
            supported = OpenGlHelper.isFramebufferEnabled()
                    && GLContext.getCapabilities() != null
                    && GLContext.getCapabilities().OpenGL20;
        } catch (Throwable throwable) {
            throw new IllegalStateException("Unable to inspect OpenGL world-glow support", throwable);
        }
        if (!supported) {
            throw new IllegalStateException("World glow requires framebuffer support and OpenGL 2.0 shaders");
        }
    }

    private void ensureTargets(Minecraft minecraft, ScreenSpaceGlowPlan plan) {
        int width = plan.scaleDimension(Math.max(1, minecraft.displayWidth));
        int height = plan.scaleDimension(Math.max(1, minecraft.displayHeight));
        if (matches(maskFramebuffer, width, height)
                && matches(outlineHorizontalFramebuffer, width, height)
                && matches(outlineFramebuffer, width, height)
                && matches(blurHorizontalFramebuffer, width, height)
                && matches(blurFramebuffer, width, height)) {
            return;
        }

        deleteTargets();
        maskFramebuffer = createTarget(width, height, true);
        outlineHorizontalFramebuffer = createTarget(width, height, false);
        outlineFramebuffer = createTarget(width, height, false);
        blurHorizontalFramebuffer = createTarget(width, height, false);
        blurFramebuffer = createTarget(width, height, false);
    }

    private static boolean matches(Framebuffer framebuffer, int width, int height) {
        return framebuffer != null && framebuffer.framebufferWidth == width
                && framebuffer.framebufferHeight == height;
    }

    private static Framebuffer createTarget(int width, int height, boolean withDepth) {
        Framebuffer framebuffer = new Framebuffer(width, height, withDepth);
        framebuffer.setFramebufferColor(0.0f, 0.0f, 0.0f, 0.0f);
        framebuffer.setFramebufferFilter(GL11.GL_LINEAR);
        setActiveTexture(MASK_TEXTURE_UNIT);
        bindTexture(framebuffer.framebufferTexture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        return framebuffer;
    }

    private void deleteTargets() {
        deleteTarget(maskFramebuffer);
        deleteTarget(outlineHorizontalFramebuffer);
        deleteTarget(outlineFramebuffer);
        deleteTarget(blurHorizontalFramebuffer);
        deleteTarget(blurFramebuffer);
        maskFramebuffer = null;
        outlineHorizontalFramebuffer = null;
        outlineFramebuffer = null;
        blurHorizontalFramebuffer = null;
        blurFramebuffer = null;
    }

    private static void deleteTarget(Framebuffer framebuffer) {
        if (framebuffer != null) {
            framebuffer.deleteFramebuffer();
        }
    }

    private void requireTargets() {
        if (maskFramebuffer == null || outlineHorizontalFramebuffer == null || outlineFramebuffer == null
                || blurHorizontalFramebuffer == null || blurFramebuffer == null) {
            throw new IllegalStateException("World glow mask targets are missing after mask capture");
        }
    }

    private void clearMask() {
        maskFramebuffer.bindFramebuffer(true);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glColorMask(true, true, true, true);
        GL11.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GL11.glClearDepth(1.0d);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    }

    private static void clearAndBind(Framebuffer framebuffer) {
        framebuffer.bindFramebuffer(true);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glColorMask(true, true, true, true);
        GL11.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
    }

    private static void configureMaskState() {
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDepthMask(true);
        GlStateManager.disableAlpha();
        GlStateManager.enableDepth();
        GlStateManager.disableCull();
        GlStateManager.disableLighting();
        GlStateManager.disableBlend();
        GlStateManager.depthMask(true);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static void configurePassState(boolean composite) {
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDepthMask(false);
        if (composite) {
            GL11.glEnable(GL11.GL_BLEND);
            OpenGlHelper.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        } else {
            GL11.glDisable(GL11.GL_BLEND);
        }
        GlStateManager.disableAlpha();
        GlStateManager.disableDepth();
        GlStateManager.disableCull();
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        if (composite) {
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        } else {
            GlStateManager.disableBlend();
        }
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void renderEntityMasks(RenderManager renderManager, float partialTicks) {
        if (entities.isEmpty()) {
            return;
        }
        boolean previousOutlines = readRenderOutlines(renderManager);
        renderManager.setRenderOutlines(true);
        try {
            for (EntityLivingBase entity : entities) {
                // The final true suppresses debug hitboxes when F3+B is enabled.
                renderManager.renderEntityStatic(entity, partialTicks, true);
            }
        } finally {
            renderManager.setRenderOutlines(previousOutlines);
            restoreRendererOutlineFlags(renderManager, previousOutlines);
        }
    }

    @SuppressWarnings("rawtypes")
    private void restoreRendererOutlineFlags(RenderManager renderManager, boolean renderOutlines) {
        for (EntityLivingBase entity : entities) {
            Render renderer = renderManager.getEntityRenderObject(entity);
            if (renderer instanceof RendererLivingEntity) {
                ((RendererLivingEntity) renderer).setRenderOutlines(renderOutlines);
            }
        }
    }

    private static boolean readRenderOutlines(RenderManager renderManager) {
        try {
            return resolveRenderOutlinesField(renderManager.getClass()).getBoolean(renderManager);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to preserve RenderManager outline state", exception);
        }
    }

    private static Field resolveRenderOutlinesField(Class<?> managerType) {
        Field cached = renderOutlinesField;
        if (cached != null && cached.getDeclaringClass().isAssignableFrom(managerType)) {
            return cached;
        }
        Class<?> type = managerType;
        while (type != null && type != Object.class) {
            for (String name : RENDER_OUTLINES_FIELD_NAMES) {
                try {
                    Field candidate = type.getDeclaredField(name);
                    if (candidate.getType() != Boolean.TYPE) {
                        continue;
                    }
                    if (!candidate.isAccessible()) {
                        candidate.setAccessible(true);
                    }
                    renderOutlinesField = candidate;
                    return candidate;
                } catch (NoSuchFieldException ignored) {
                    // Keep looking through the MCP, SRG, and Lunar-obfuscated field names.
                } catch (SecurityException exception) {
                    throw new IllegalStateException("Unable to access RenderManager outline state", exception);
                }
            }
            type = type.getSuperclass();
        }
        throw new IllegalStateException("Unable to locate RenderManager outline state");
    }

    private void renderBlockMasks(RenderManager renderManager) {
        if (blocks.isEmpty()) {
            return;
        }
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GlStateManager.disableTexture2D();
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        for (BlockPos position : blocks) {
            double minX = position.getX() - renderManager.viewerPosX;
            double minY = position.getY() - renderManager.viewerPosY;
            double minZ = position.getZ() - renderManager.viewerPosZ;
            drawSolidAabb(minX, minY, minZ, minX + 1.0d, minY + 1.0d, minZ + 1.0d);
        }
    }

    private static void drawSolidAabb(double minX, double minY, double minZ,
                                      double maxX, double maxY, double maxZ) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3d(minX, minY, minZ);
        GL11.glVertex3d(maxX, minY, minZ);
        GL11.glVertex3d(maxX, maxY, minZ);
        GL11.glVertex3d(minX, maxY, minZ);

        GL11.glVertex3d(maxX, minY, maxZ);
        GL11.glVertex3d(minX, minY, maxZ);
        GL11.glVertex3d(minX, maxY, maxZ);
        GL11.glVertex3d(maxX, maxY, maxZ);

        GL11.glVertex3d(minX, minY, maxZ);
        GL11.glVertex3d(minX, minY, minZ);
        GL11.glVertex3d(minX, maxY, minZ);
        GL11.glVertex3d(minX, maxY, maxZ);

        GL11.glVertex3d(maxX, minY, minZ);
        GL11.glVertex3d(maxX, minY, maxZ);
        GL11.glVertex3d(maxX, maxY, maxZ);
        GL11.glVertex3d(maxX, maxY, minZ);

        GL11.glVertex3d(minX, maxY, minZ);
        GL11.glVertex3d(maxX, maxY, minZ);
        GL11.glVertex3d(maxX, maxY, maxZ);
        GL11.glVertex3d(minX, maxY, maxZ);

        GL11.glVertex3d(minX, minY, maxZ);
        GL11.glVertex3d(maxX, minY, maxZ);
        GL11.glVertex3d(maxX, minY, minZ);
        GL11.glVertex3d(minX, minY, minZ);
        GL11.glEnd();
    }

    private void renderOutline(ScreenSpaceGlowPlan plan) {
        // Identity matrices are set up once by composite(); configurePassState
        // and outlineProgram.use are still set here because renderOutline is
        // the first post-process step and configures the no-blend pass state.
        configurePassState(false);
        outlineProgram.use();
        renderOutlinePass(maskFramebuffer, outlineHorizontalFramebuffer, plan.getOutlineRadius(), 1.0f, 0.0f);
        renderOutlinePass(outlineHorizontalFramebuffer, outlineFramebuffer, plan.getOutlineRadius(), 0.0f, 1.0f);
    }

    private void renderOutlinePass(Framebuffer source, Framebuffer target, int radius,
                                   float directionX, float directionY) {
        clearAndBind(target);
        // loadIdentityMatrices / configurePassState(false) / outlineProgram.use
        // are set up once per renderOutline call; pass only swaps source
        // texture and direction uniforms.
        setActiveTexture(MASK_TEXTURE_UNIT);
        bindTexture(source.framebufferTexture);
        outlineProgram.set1i("maskTexture", 0);
        outlineProgram.set2f("texelSize", 1.0f / source.framebufferWidth,
                1.0f / source.framebufferHeight);
        outlineProgram.set2f("direction", directionX, directionY);
        outlineProgram.set1i("radius", radius);
        drawFullscreenQuad();
    }

    private void renderBlurPair(int radius) {
        // Identity matrices are set up once by composite(). configurePassState
        // and blurProgram.use are still set here because renderComposite (run
        // between the two blur pairs) switches to the composite program and
        // enables blend; we must restore no-blend state and rebind blurProgram
        // before the second pair runs.
        configurePassState(false);
        blurProgram.use();
        renderBlur(outlineFramebuffer, blurHorizontalFramebuffer, radius, 1.0f, 0.0f);
        renderBlur(blurHorizontalFramebuffer, blurFramebuffer, radius, 0.0f, 1.0f);
    }

    private void renderBlur(Framebuffer source, Framebuffer target, int radius,
                            float directionX, float directionY) {
        clearAndBind(target);
        // loadIdentityMatrices / configurePassState(false) / blurProgram.use
        // are set up once per renderBlurPair call; pass only swaps source
        // texture, direction and kernel uniforms.
        setActiveTexture(MASK_TEXTURE_UNIT);
        bindTexture(source.framebufferTexture);
        blurProgram.set1i("sourceTexture", 0);
        blurProgram.set2f("texelSize", 1.0f / source.framebufferWidth,
                1.0f / source.framebufferHeight);
        blurProgram.set2f("direction", directionX, directionY);
        blurProgram.set1i("radius", radius);
        blurProgram.setWeights(kernel(radius));
        drawFullscreenQuad();
    }

    private void renderComposite(RenderState state, boolean coreLayer) {
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, state.framebuffer);
        GL11.glViewport(state.viewportX, state.viewportY, state.viewportWidth, state.viewportHeight);
        configurePassState(true);
        // Identity matrices were loaded by renderBlurPair and renderBlur does
        // not mutate them; no need to reload here.
        compositeProgram.use();
        setActiveTexture(MASK_TEXTURE_UNIT);
        bindTexture(blurFramebuffer.framebufferTexture);
        compositeProgram.set1i("blurTexture", 0);
        setActiveTexture(BLUR_TEXTURE_UNIT);
        bindTexture(maskFramebuffer.framebufferTexture);
        compositeProgram.set1i("maskTexture", 1);
        compositeProgram.set4f("coreColor", red(colors.getCoreColor()), green(colors.getCoreColor()),
                blue(colors.getCoreColor()), alpha(colors.getCoreColor()));
        compositeProgram.set4f("outerColor", red(colors.getOuterColor()), green(colors.getOuterColor()),
                blue(colors.getOuterColor()), alpha(colors.getOuterColor()));
        compositeProgram.set1f("coreLayer", coreLayer ? 1.0f : 0.0f);
        compositeProgram.set1f("fillCore", fillCore ? 1.0f : 0.0f);
        compositeProgram.set1f("strength", compositeStrength * (coreLayer ? 1.0f : 0.82f));
        setActiveTexture(MASK_TEXTURE_UNIT);
        drawFullscreenQuad();
    }

    private float[] kernel(int radius) {
        Integer key = Integer.valueOf(radius);
        float[] cached = kernels.get(key);
        if (cached == null) {
            cached = createGaussianKernel(radius);
            kernels.put(key, cached);
        }
        return cached;
    }

    private static float[] createGaussianKernel(int radius) {
        int clampedRadius = ScreenSpaceGlowPlan.clampBlurRadius(radius);
        float[] weights = new float[clampedRadius + 1];
        double sigma = Math.max(1.0d, clampedRadius / 2.0d);
        double denominator = 2.0d * sigma * sigma;
        double total = 0.0d;
        for (int offset = 0; offset <= clampedRadius; offset++) {
            double weight = Math.exp(-(offset * offset) / denominator);
            weights[offset] = (float) weight;
            total += offset == 0 ? weight : weight * 2.0d;
        }
        for (int offset = 0; offset <= clampedRadius; offset++) {
            weights[offset] = (float) (weights[offset] / total);
        }
        return weights;
    }

    private void ensurePrograms() {
        if (outlineProgram == null) {
            outlineProgram = createProgram(OUTLINE_SHADER_RESOURCE);
        }
        if (blurProgram == null) {
            blurProgram = createProgram(BLUR_SHADER_RESOURCE);
        }
        if (compositeProgram == null) {
            compositeProgram = createProgram(COMPOSITE_SHADER_RESOURCE);
        }
    }

    private static Program createProgram(String fragmentResource) {
        int vertexShader = 0;
        int fragmentShader = 0;
        int program = 0;
        try {
            vertexShader = compileShader(GL20.GL_VERTEX_SHADER, VERTEX_SHADER, "world glow vertex shader");
            fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER, readResource(fragmentResource), fragmentResource);
            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vertexShader);
            GL20.glAttachShader(program, fragmentShader);
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                throw new IllegalStateException("Unable to link " + fragmentResource + ": "
                        + GL20.glGetProgramInfoLog(program, 32768));
            }
            return new Program(program);
        } catch (RuntimeException exception) {
            if (program != 0) {
                GL20.glDeleteProgram(program);
            }
            throw exception;
        } finally {
            if (vertexShader != 0) {
                GL20.glDeleteShader(vertexShader);
            }
            if (fragmentShader != 0) {
                GL20.glDeleteShader(fragmentShader);
            }
        }
    }

    private static int compileShader(int type, String source, String label) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader, 32768);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("Unable to compile " + label + ": " + log);
        }
        return shader;
    }

    private static String readResource(String resource) {
        InputStream stream = ScreenSpaceGlowRenderer.class.getResourceAsStream(resource);
        if (stream == null) {
            throw new IllegalStateException("Missing world glow shader resource " + resource);
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), "UTF-8");
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read world glow shader resource " + resource, exception);
        } finally {
            try {
                stream.close();
            } catch (IOException ignored) {
                // The shader was already read or a prior error is being reported.
            }
        }
    }

    private static void drawFullscreenQuad() {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0f, 0.0f);
        GL11.glVertex2f(-1.0f, -1.0f);
        GL11.glTexCoord2f(0.0f, 1.0f);
        GL11.glVertex2f(-1.0f, 1.0f);
        GL11.glTexCoord2f(1.0f, 1.0f);
        GL11.glVertex2f(1.0f, 1.0f);
        GL11.glTexCoord2f(1.0f, 0.0f);
        GL11.glVertex2f(1.0f, -1.0f);
        GL11.glEnd();
    }

    private static void pushMatrices() {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        setActiveTexture(MASK_TEXTURE_UNIT);
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPushMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
    }

    private static void loadIdentityMatrices() {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        setActiveTexture(MASK_TEXTURE_UNIT);
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
    }

    private static void popMatrices(int matrixMode) {
        setActiveTexture(MASK_TEXTURE_UNIT);
        GL11.glMatrixMode(GL11.GL_TEXTURE);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(matrixMode);
    }

    private static void restoreTransaction(RenderState state, boolean attribPushed, boolean matricesPushed) {
        try {
            if (matricesPushed) {
                popMatrices(state == null ? GL11.GL_MODELVIEW : state.matrixMode);
            }
        } finally {
            try {
                if (attribPushed) {
                    GL11.glPopAttrib();
                }
            } finally {
                try {
                    if (state != null) {
                        state.restore();
                    }
                } finally {
                    GLStateManager.syncToCurrent();
                    GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
                }
            }
        }
    }

    private static void setActiveTexture(int textureUnit) {
        OpenGlHelper.setActiveTexture(textureUnit);
        GlStateManager.setActiveTexture(textureUnit);
        GL13.glActiveTexture(textureUnit);
    }

    private static void bindTexture(int texture) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GlStateManager.bindTexture(texture);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    }

    private static float clampPartialTicks(float partialTicks) {
        if (Float.isNaN(partialTicks) || Float.isInfinite(partialTicks)) {
            throw new IllegalArgumentException("partialTicks must be finite");
        }
        return Math.max(0.0f, Math.min(1.0f, partialTicks));
    }

    private static float alpha(int color) {
        return (color >>> 24 & 255) / 255.0f;
    }

    private static float red(int color) {
        return (color >>> 16 & 255) / 255.0f;
    }

    private static float green(int color) {
        return (color >>> 8 & 255) / 255.0f;
    }

    private static float blue(int color) {
        return (color & 255) / 255.0f;
    }

    private static final class RenderState {
        private final int framebuffer;
        private final int viewportX;
        private final int viewportY;
        private final int viewportWidth;
        private final int viewportHeight;
        private final int program;
        private final int activeTexture;
        private final int texture0;
        private final int texture1;
        private final int matrixMode;

        private RenderState(int framebuffer, int viewportX, int viewportY, int viewportWidth,
                            int viewportHeight, int program, int activeTexture, int texture0,
                            int texture1, int matrixMode) {
            this.framebuffer = framebuffer;
            this.viewportX = viewportX;
            this.viewportY = viewportY;
            this.viewportWidth = viewportWidth;
            this.viewportHeight = viewportHeight;
            this.program = program;
            this.activeTexture = activeTexture;
            this.texture0 = texture0;
            this.texture1 = texture1;
            this.matrixMode = matrixMode;
        }

        private static RenderState capture() {
            int framebuffer = GL11.glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT);
            VIEWPORT_BUFFER.clear();
            GL11.glGetInteger(GL11.GL_VIEWPORT, VIEWPORT_BUFFER);
            int viewportX = VIEWPORT_BUFFER.get(0);
            int viewportY = VIEWPORT_BUFFER.get(1);
            int viewportWidth = VIEWPORT_BUFFER.get(2);
            int viewportHeight = VIEWPORT_BUFFER.get(3);
            int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
            int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            int texture0 = textureBinding(MASK_TEXTURE_UNIT);
            int texture1 = textureBinding(BLUR_TEXTURE_UNIT);
            setActiveTexture(activeTexture);
            return new RenderState(framebuffer, viewportX, viewportY, viewportWidth, viewportHeight,
                    program, activeTexture, texture0, texture1, matrixMode);
        }

        private static int textureBinding(int textureUnit) {
            setActiveTexture(textureUnit);
            return GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        }

        private void restore() {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, framebuffer);
            GL11.glViewport(viewportX, viewportY, viewportWidth, viewportHeight);
            GL20.glUseProgram(program);
            setActiveTexture(MASK_TEXTURE_UNIT);
            bindTexture(texture0);
            setActiveTexture(BLUR_TEXTURE_UNIT);
            bindTexture(texture1);
            setActiveTexture(activeTexture);
            GL11.glMatrixMode(matrixMode);
        }
    }

    private static final class Program {
        private final int id;
        private final Map<String, Integer> uniforms = new HashMap<String, Integer>();

        private Program(int id) {
            this.id = id;
        }

        private void use() {
            GL20.glUseProgram(id);
        }

        private int uniform(String name) {
            Integer cached = uniforms.get(name);
            if (cached != null) {
                return cached.intValue();
            }
            int location = GL20.glGetUniformLocation(id, name);
            if (location < 0) {
                throw new IllegalStateException("World glow shader is missing uniform " + name);
            }
            uniforms.put(name, Integer.valueOf(location));
            return location;
        }

        private void set1i(String name, int value) {
            GL20.glUniform1i(uniform(name), value);
        }

        private void set1f(String name, float value) {
            GL20.glUniform1f(uniform(name), value);
        }

        private void set2f(String name, float first, float second) {
            GL20.glUniform2f(uniform(name), first, second);
        }

        private void set4f(String name, float first, float second, float third, float fourth) {
            GL20.glUniform4f(uniform(name), first, second, third, fourth);
        }

        private void setWeights(float[] weights) {
            WEIGHT_BUFFER.clear();
            for (int index = 0; index <= ScreenSpaceGlowPlan.MAX_BLUR_RADIUS; index++) {
                WEIGHT_BUFFER.put(index < weights.length ? weights[index] : 0.0f);
            }
            WEIGHT_BUFFER.flip();
            GL20.glUniform1(uniform("weights"), WEIGHT_BUFFER);
        }
    }
}
