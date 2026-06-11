package gq.vapulite.Vapu.modules.render;

import gq.vapulite.Vapu.ModuleType;
import gq.vapulite.Vapu.modules.Module;
import gq.vapulite.Vapu.modules.combat.AntiBot;
import gq.vapulite.Vapu.modules.combat.Backtrack;
import gq.vapulite.Vapu.modules.combat.KillAura;
import gq.vapulite.Vapu.value.Mode;
import gq.vapulite.Vapu.value.Numbers;
import gq.vapulite.Vapu.value.Option;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAmbientCreature;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityWaterMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

public class TargetESP extends Module {
    public enum EspMode {
        VAPE,
        RINGS,
        CAPSULE,
        COSMIC,
        AURORA,
        SAKURA
    }

    private final Mode<EspMode> mode = new Mode<EspMode>("Mode", "Mode", EspMode.values(), EspMode.VAPE);
    private final Numbers<Double> alpha = new Numbers<Double>("Alpha", "Alpha", 155.0, 35.0, 220.0, 5.0);
    private final Numbers<Double> radius = new Numbers<Double>("Radius", "Radius", 1.0, 0.65, 1.65, 0.05);
    private final Numbers<Double> height = new Numbers<Double>("Height", "Height", 1.0, 0.65, 1.45, 0.05);
    private final Numbers<Double> pulseSpeed = new Numbers<Double>("Pulse Speed", "PulseSpeed", 1.0, 0.25, 2.8, 0.05);
    private final Numbers<Double> lineWidth = new Numbers<Double>("Line Width", "LineWidth", 1.8, 0.6, 4.0, 0.1);
    private final Option<Boolean> shader = new Option<Boolean>("Shader", "Shader", true);
    private final Option<Boolean> auroraBloom = new Option<Boolean>("Aurora Bloom", "AuroraBloom", false);
    private final Option<Boolean> throughWalls = new Option<Boolean>("Through Walls", "ThroughWalls", true);
    private final Option<Boolean> auraTarget = new Option<Boolean>("Aura Target", "AuraTarget", true);
    private final Option<Boolean> crosshairTarget = new Option<Boolean>("Crosshair", "Crosshair", true);
    private final Option<Boolean> backtrackTarget = new Option<Boolean>("Backtrack", "Backtrack", true);
    private final Option<Boolean> players = new Option<Boolean>("Players", "Players", true);
    private final Option<Boolean> mobs = new Option<Boolean>("Mobs", "Mobs", true);
    private final Option<Boolean> animals = new Option<Boolean>("Animals", "Animals", false);

    private EntityLivingBase displayTarget;
    private float visibility;
    private long lastFrameMS = System.currentTimeMillis();

    public TargetESP() {
        super("TargetESP", Keyboard.KEY_NONE, ModuleType.Render, "Draw a shader based marker around the current target");
        auroraBloom.visibleWhen(() -> mode.getValue() == EspMode.AURORA);
        this.addValues(mode, alpha, radius, height, pulseSpeed, lineWidth, shader, auroraBloom, throughWalls, auraTarget,
                crosshairTarget, backtrackTarget, players, mobs, animals);
        Chinese = "目标ESP";
    }

    @Override
    public void enable() {
        displayTarget = null;
        visibility = 0.0f;
        lastFrameMS = System.currentTimeMillis();
    }

    @Override
    public void disable() {
        displayTarget = null;
        visibility = 0.0f;
    }

    @SubscribeEvent
    public void onWorld(RenderWorldLastEvent event) {
        if (!isInGame()) {
            displayTarget = null;
            visibility = 0.0f;
            return;
        }

        long now = System.currentTimeMillis();
        float factor = animationFactor(now);
        EntityLivingBase resolved = resolveTarget();
        if (resolved != null) {
            displayTarget = resolved;
        }
        float wanted = resolved == null ? 0.0f : 1.0f;
        visibility += (wanted - visibility) * factor;
        if (displayTarget == null || visibility <= 0.025f || !isValidDisplayTarget(displayTarget)) {
            return;
        }
        drawTarget(displayTarget, event.partialTicks, visibility);
    }

    private EntityLivingBase resolveTarget() {
        if (Boolean.TRUE.equals(auraTarget.getValue())) {
            EntityLivingBase aura = asTarget(KillAura.target);
            if (aura != null) {
                return aura;
            }
        }
        if (Boolean.TRUE.equals(crosshairTarget.getValue())
                && mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
            EntityLivingBase direct = asTarget(mc.objectMouseOver.entityHit);
            if (direct != null) {
                return direct;
            }
        }
        if (Boolean.TRUE.equals(backtrackTarget.getValue())) {
            EntityLivingBase backtrack = asTarget(Backtrack.getAimedTarget());
            if (backtrack != null) {
                return backtrack;
            }
        }
        return null;
    }

    private EntityLivingBase asTarget(Entity entity) {
        if (!(entity instanceof EntityLivingBase) || entity == mc.thePlayer) {
            return null;
        }
        EntityLivingBase living = (EntityLivingBase) entity;
        return isValidDisplayTarget(living) ? living : null;
    }

    private boolean isValidDisplayTarget(EntityLivingBase target) {
        if (target == null || target.isDead || target.deathTime > 0 || target.getHealth() <= 0.0f) {
            return false;
        }
        if (target instanceof EntityPlayer) {
            return Boolean.TRUE.equals(players.getValue()) && !AntiBot.isServerBot(target);
        }
        if (target instanceof EntityAnimal || target instanceof EntityWaterMob || target instanceof EntityAmbientCreature) {
            return Boolean.TRUE.equals(animals.getValue());
        }
        if (target instanceof EntityMob || target instanceof EntitySlime || target instanceof IMob) {
            return Boolean.TRUE.equals(mobs.getValue());
        }
        return Boolean.TRUE.equals(mobs.getValue());
    }

    private void drawTarget(EntityLivingBase target, float partialTicks, float fade) {
        double x = target.lastTickPosX + (target.posX - target.lastTickPosX) * partialTicks - mc.getRenderManager().viewerPosX;
        double y = target.lastTickPosY + (target.posY - target.lastTickPosY) * partialTicks - mc.getRenderManager().viewerPosY;
        double z = target.lastTickPosZ + (target.posZ - target.lastTickPosZ) * partialTicks - mc.getRenderManager().viewerPosZ;
        float baseRadius = Math.max(0.36f, target.width * 0.72f) * radius.getValue().floatValue();
        float bodyHeight = Math.max(1.0f, target.height) * height.getValue().floatValue();
        float time = (System.currentTimeMillis() % 200000L) / 1000.0f * pulseSpeed.getValue().floatValue();
        float hurtPulse = target.hurtTime > 0 ? 1.0f : 0.0f;
        float alphaScale = fade * alpha.getValue().floatValue() / 255.0f;
        EspMode current = mode.getValue();
        int primary = current == EspMode.AURORA ? 0xFF49D6FF
                : current == EspMode.SAKURA ? 0xFFFF9FCA
                : target.hurtTime > 0 ? 0xFFFF6270
                : current == EspMode.COSMIC ? rainbowColor(time * 0.08f, 0.0f) : 0xFF79C9FF;
        int secondary = current == EspMode.COSMIC ? rainbowColor(time * 0.08f, 0.38f)
                : current == EspMode.SAKURA ? 0xFFFFFFFF
                : current == EspMode.AURORA ? 0xFFFFFFFF : healthColor(target);

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LINE_BIT);
        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);
        if (Boolean.TRUE.equals(throughWalls.getValue())) {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }
        boolean usingShader = Boolean.TRUE.equals(shader.getValue())
                && TargetShader.begin(primary, secondary, alphaScale, time,
                current == EspMode.AURORA || current == EspMode.SAKURA ? 0.0f : hurtPulse);
        try {
            if (current == EspMode.COSMIC) {
                drawCosmic(target, baseRadius, bodyHeight, alphaScale, time);
            } else if (current == EspMode.AURORA) {
                drawAurora(target, baseRadius, bodyHeight, alphaScale, time);
            } else if (current == EspMode.SAKURA) {
                drawSakuraPetals(baseRadius, bodyHeight, alphaScale, time);
            } else if (current == EspMode.VAPE || current == EspMode.CAPSULE) {
                drawCapsule(baseRadius, bodyHeight, alphaScale, time);
                drawVerticalMarkers(baseRadius, bodyHeight, alphaScale, time);
            }
            if (current == EspMode.VAPE || current == EspMode.RINGS) {
                drawRing(0.04f, baseRadius + 0.08f, 0.045f, 72, alphaScale * 0.92f, time);
                drawRing(bodyHeight * 0.52f, baseRadius + 0.03f, 0.025f, 72, alphaScale * 0.48f, time + 0.7f);
                drawRing(bodyHeight + 0.04f, baseRadius + 0.06f, 0.035f, 72, alphaScale * 0.70f, time + 1.4f);
                drawHealthArc(target, bodyHeight + 0.12f, baseRadius + 0.13f, 0.032f, alphaScale);
            }
        } finally {
            if (usingShader) {
                TargetShader.end();
            }
            GL11.glDepthMask(true);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    private void drawCosmic(EntityLivingBase target, float radius, float height, float alpha, float time) {
        float breath = 1.0f + 0.07f * (float) Math.sin(time * 2.35f);
        float orbitRadius = radius * (1.24f + 0.05f * (float) Math.sin(time * 1.6f));
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        drawCosmicGround(radius, alpha, time);
        drawCosmicCapsule(radius * breath, height, alpha * 0.48f, time);
        drawHelixRibbon(radius * 1.04f, height, alpha * 0.72f, time, 0.0f, false);
        drawHelixRibbon(radius * 1.04f, height, alpha * 0.58f, time, (float) Math.PI, true);
        drawTiltedRing(height * 0.36f, orbitRadius, 0.026f, 92, alpha * 0.80f, time, 64.0f, time * 38.0f, 0.10f);
        drawTiltedRing(height * 0.58f, orbitRadius * 0.98f, 0.022f, 92, alpha * 0.72f, time + 0.9f, -58.0f,
                -time * 42.0f, 0.45f);
        drawTiltedRing(height * 0.80f, orbitRadius * 0.90f, 0.018f, 84, alpha * 0.58f, time + 1.8f, 18.0f,
                time * 56.0f, 0.72f);
        drawEnergySpikes(radius, height, alpha * 0.72f, time);
        drawOrbitingStars(orbitRadius * 1.04f, height, alpha, time);
        drawCosmicChains(radius * 1.72f, height, alpha * 0.42f, time);
        drawScanBeam(radius * 1.15f, height, alpha * 0.36f, time);
        drawHealthArc(target, height + 0.16f, radius * 1.32f, 0.040f, alpha * 0.95f);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    private void drawAurora(EntityLivingBase target, float radius, float height, float alpha, float time) {
        boolean bloom = Boolean.TRUE.equals(auroraBloom.getValue());
        float bloomSize = bloom ? 1.82f : 1.38f;
        float bloomAlpha = bloom ? 2.15f : 1.45f;
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        drawAuroraCore(radius * bloomSize, height, alpha * 0.18f * bloomAlpha, time);
        drawAuroraOrbs(radius, height, Math.min(1.0f, alpha * bloomAlpha), time, bloom);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    private void drawSakuraPetals(float radius, float height, float alpha, float time) {
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        drawSakuraGroundGlow(radius, alpha, time);
        int petals = 12;
        float orbit = radius * 1.34f;
        for (int i = 0; i < petals; i++) {
            float phase = (float) (Math.PI * 2.0D * i / petals);
            int color = i % 3 == 0 ? 0xFFFF74B2 : i % 3 == 1 ? 0xFFFF9DCA : 0xFFFFC1DC;
            float size = 0.082f + 0.030f * (float) Math.sin(time * 2.6f + i * 0.9f);
            drawSakuraPetalTrail(orbit, height, alpha, time, phase, color, size);
            float[] p = sakuraPetalPosition(orbit, height, time, phase, 0.0f, i);
            float spin = time * 132.0f + i * 37.0f;
            drawSakuraFlower(p[0], p[1], p[2], size, spin, color, Math.min(1.0f, alpha * 1.35f), false);
        }
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    private void drawSakuraGroundGlow(float radius, float alpha, float time) {
        int segments = 80;
        float r = radius * (1.48f + 0.05f * (float) Math.sin(time * 1.8f));
        GL11.glBegin(GL11.GL_QUAD_STRIP);
        for (int i = 0; i <= segments; i++) {
            double angle = Math.PI * 2.0D * i / segments;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            float u = i / (float) segments;
            float wave = 0.72f + 0.28f * (float) Math.sin(time * 3.8f + u * Math.PI * 2.0D);
            setColor(0xFFFF77B5, alpha * 0.16f * wave);
            GL11.glTexCoord2f(u, 0.0f);
            GL11.glVertex3d(cos * (r - 0.10f), 0.035D, sin * (r - 0.10f));
            setColor(0xFFFFB9D7, alpha * 0.30f * wave);
            GL11.glTexCoord2f(u, 1.0f);
            GL11.glVertex3d(cos * (r + 0.10f), 0.035D, sin * (r + 0.10f));
        }
        GL11.glEnd();
    }

    private void drawSakuraPetalTrail(float orbit, float height, float alpha, float time, float phase, int color, float size) {
        int samples = 9;
        for (int i = samples; i >= 1; i--) {
            float trail = i / (float) samples;
            float[] p = sakuraPetalPosition(orbit, height, time, phase, trail, i);
            float fade = 1.0f - trail;
            fade *= fade;
            drawSakuraFlower(p[0], p[1], p[2], size * (0.52f + 0.38f * fade),
                    time * 118.0f + phase * 57.0f - trail * 95.0f,
                    color, alpha * fade * 0.42f, true);
        }

        GL11.glLineWidth(Math.max(0.8f, lineWidth.getValue().floatValue() * 0.30f));
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int i = samples; i >= 0; i--) {
            float trail = i / (float) samples;
            float[] p = sakuraPetalPosition(orbit, height, time, phase, trail, i);
            float fade = 1.0f - trail;
            fade *= fade;
            setColor(0xFFFF7DB8, alpha * fade * 0.32f);
            GL11.glTexCoord2f(fade, 1.0f - trail);
            GL11.glVertex3d(p[0], p[1], p[2]);
        }
        GL11.glEnd();
    }

    private float[] sakuraPetalPosition(float orbit, float height, float time, float phase, float trail, int index) {
        float localTime = time - trail * 0.52f;
        double angle = localTime * 1.25D + phase + Math.sin(localTime * 0.8f + phase) * 0.20D;
        float vertical = (localTime * 0.19f + phase * 0.159f + index * 0.037f) % 1.0f;
        if (vertical < 0.0f) {
            vertical += 1.0f;
        }
        float y = 0.08f + vertical * Math.max(0.65f, height + 0.18f);
        float drift = 0.10f * (float) Math.sin(localTime * 2.2f + phase * 1.7f);
        float r = orbit * (0.84f + 0.22f * (float) Math.sin(localTime * 1.4f + phase)) + drift;
        return new float[]{(float) Math.cos(angle) * r, y, (float) Math.sin(angle) * r};
    }

    private void drawSakuraFlower(float x, float y, float z, float size, float spin, int color, float alpha, boolean trail) {
        if (alpha <= 0.002f || size <= 0.002f) {
            return;
        }
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, z);
        GL11.glRotatef(-mc.getRenderManager().playerViewY, 0.0f, 1.0f, 0.0f);
        GL11.glRotatef(mc.getRenderManager().playerViewX, 1.0f, 0.0f, 0.0f);
        GL11.glRotatef(spin, 0.0f, 0.0f, 1.0f);
        if (!trail) {
            drawBillboardGlow(size * 3.25f, 0xFFFF6FAE, alpha * 0.34f, 20);
            drawBillboardGlow(size * 1.75f, 0xFFFFC0DC, alpha * 0.28f, 18);
        }
        for (int i = 0; i < 5; i++) {
            GL11.glPushMatrix();
            GL11.glRotatef(i * 72.0f, 0.0f, 0.0f, 1.0f);
            GL11.glTranslatef(0.0f, size * 0.20f, 0.0f);
            drawPetalShape(size, color, alpha, trail);
            GL11.glPopMatrix();
        }
        if (!trail) {
            drawBillboardGlow(size * 0.38f, 0xFFFFF3A7, alpha * 0.88f, 12);
        }
        GL11.glPopMatrix();
    }

    private void drawPetalShape(float size, int color, float alpha, boolean trail) {
        float width = size * (trail ? 0.46f : 0.58f);
        float length = size * (trail ? 0.96f : 1.12f);
        float[][] points = new float[][]{
                {0.00f, -0.18f}, {-0.30f, -0.07f}, {-0.64f, 0.25f}, {-0.66f, 0.62f},
                {-0.36f, 0.94f}, {-0.10f, 0.82f}, {0.00f, 0.74f}, {0.10f, 0.82f},
                {0.36f, 0.94f}, {0.66f, 0.62f}, {0.64f, 0.25f}, {0.30f, -0.07f}, {0.00f, -0.18f}
        };

        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        setColor(0xFFFFEAF3, alpha * (trail ? 0.42f : 0.98f));
        GL11.glTexCoord2f(0.5f, 0.58f);
        GL11.glVertex3d(0.0D, length * 0.36D, 0.0D);
        int edgeColor = interpolate(color, 0xFFFFFFFF, trail ? 0.08f : 0.18f);
        for (int i = 0; i < points.length; i++) {
            double px = points[i][0] * width;
            double py = points[i][1] * length;
            float edgeAlpha = trail ? 0.14f : 0.56f;
            if (i == 5 || i == 6 || i == 7) {
                edgeAlpha *= 0.82f;
            }
            setColor(edgeColor, alpha * edgeAlpha);
            GL11.glTexCoord2f((float) (0.5D + px / Math.max(0.001f, width * 2.0f)),
                    (float) (0.5D + py / Math.max(0.001f, length * 2.1f)));
            GL11.glVertex3d(px, py, 0.0D);
        }
        GL11.glEnd();

        if (!trail) {
            GL11.glLineWidth(Math.max(0.8f, lineWidth.getValue().floatValue() * 0.32f));
            GL11.glBegin(GL11.GL_LINE_STRIP);
            setColor(0xFFFF6FB0, alpha * 0.22f);
            GL11.glVertex3d(0.0D, -length * 0.08D, 0.0D);
            GL11.glVertex3d(width * 0.035D, length * 0.56D, 0.0D);
            GL11.glEnd();
        }
    }

    private void drawAuroraOrbs(float radius, float height, float alpha, float time, boolean bloom) {
        float orbitRadius = radius * ((bloom ? 1.48f : 1.32f) + 0.08f * (float) Math.sin(time * 1.7f));
        for (int i = 0; i < 3; i++) {
            float phase = (float) (Math.PI * 2.0D * i / 3.0D);
            int orbColor = i == 0 ? 0xFF48D9FF : i == 1 ? 0xFFFFFFFF : 0xFFB487FF;
            drawAuroraTrail(orbitRadius, height, alpha, time, phase, orbColor, bloom);
            float[] p = auroraOrbPosition(orbitRadius, height, time, phase, 0.0f);
            float orbSize = (bloom ? 0.275f : 0.165f) + (bloom ? 0.052f : 0.030f) * (float) Math.sin(time * 4.0f + phase);
            drawEnergyOrb(p[0], p[1], p[2], orbSize, orbColor, alpha, bloom);
        }
    }

    private void drawAuroraTrail(float orbitRadius, float height, float alpha, float time, float phase, int color, boolean bloom) {
        int samples = bloom ? 46 : 34;
        for (int i = samples; i >= 1; i--) {
            float t = i / (float) samples;
            float[] p = auroraOrbPosition(orbitRadius, height, time, phase, t);
            float fade = (1.0f - t);
            fade *= fade;
            float size = (bloom ? 0.085f : 0.052f) + (bloom ? 0.245f : 0.142f) * fade;
            drawEnergyOrb(p[0], p[1], p[2], size, trailColor(color, t, true), alpha * fade * (bloom ? 1.10f : 0.78f), bloom);
        }

        GL11.glLineWidth(Math.max(1.1f, lineWidth.getValue().floatValue() * (bloom ? 1.05f : 0.72f)));
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int i = samples; i >= 0; i--) {
            float t = i / (float) samples;
            float[] p = auroraOrbPosition(orbitRadius, height, time, phase, t);
            float fade = 1.0f - t;
            fade *= fade;
            setColor(trailColor(color, t, true), alpha * fade * (bloom ? 1.05f : 0.72f));
            GL11.glTexCoord2f(fade, 1.0f - t);
            GL11.glVertex3d(p[0], p[1], p[2]);
        }
        GL11.glEnd();
    }

    private int trailColor(int base, float t, boolean bloom) {
        if (!bloom) {
            return base;
        }
        float wave = 0.5f + 0.5f * (float) Math.sin(t * Math.PI * 2.0D);
        int cyanWhite = interpolate(0xFF40DFFF, 0xFFFFFFFF, wave);
        return interpolate(cyanWhite, 0xFFB487FF, Math.max(0.0f, t - 0.35f) / 0.65f);
    }

    private float[] auroraOrbPosition(float orbitRadius, float height, float time, float phase, float trail) {
        float localTime = time - trail * 0.72f;
        double angle = localTime * 2.35D + phase;
        float vertical = 0.50f + 0.43f * (float) Math.sin(localTime * 1.35f + phase);
        float y = 0.08f + vertical * Math.max(0.65f, height - 0.04f);
        float r = orbitRadius * (0.92f + 0.12f * (float) Math.sin(localTime * 1.9f + phase * 0.7f));
        return new float[]{(float) Math.cos(angle) * r, y, (float) Math.sin(angle) * r};
    }

    private void drawEnergyOrb(float x, float y, float z, float size, int color, float alpha, boolean bloom) {
        if (alpha <= 0.002f || size <= 0.002f) {
            return;
        }
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, z);
        GL11.glRotatef(-mc.getRenderManager().playerViewY, 0.0f, 1.0f, 0.0f);
        GL11.glRotatef(mc.getRenderManager().playerViewX, 1.0f, 0.0f, 0.0f);
        drawBillboardGlow(size * (bloom ? 7.40f : 4.25f), color, alpha * (bloom ? 0.46f : 0.34f), bloom ? 32 : 24);
        drawBillboardGlow(size * (bloom ? 4.35f : 2.45f), trailColor(color, 0.15f, true),
                alpha * (bloom ? 0.82f : 0.62f), bloom ? 32 : 24);
        drawBillboardGlow(size * (bloom ? 2.10f : 1.18f), 0xFFFFFFFF, alpha * (bloom ? 1.25f : 1.0f), bloom ? 28 : 20);
        if (bloom) {
            drawBillboardGlow(size * 0.86f, 0xFFFFFFFF, alpha * 1.35f, 24);
        }
        GL11.glPopMatrix();
    }

    private void drawBillboardGlow(float radius, int color, float alpha, int segments) {
        if (alpha <= 0.002f) {
            return;
        }
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        setColor(color, alpha);
        GL11.glTexCoord2f(0.5f, 0.5f);
        GL11.glVertex3d(0.0D, 0.0D, 0.0D);
        for (int i = 0; i <= segments; i++) {
            double angle = Math.PI * 2.0D * i / segments;
            setColor(color, 0.0f);
            GL11.glTexCoord2f((float) (0.5D + Math.cos(angle) * 0.5D), (float) (0.5D + Math.sin(angle) * 0.5D));
            GL11.glVertex3d(Math.cos(angle) * radius, Math.sin(angle) * radius, 0.0D);
        }
        GL11.glEnd();
    }

    private void drawAuroraCrescent(float y, float radius, float thickness, int segments, float alpha,
                                    float phase, float sweepDegrees, float time, float hueOffset) {
        float sweep = (float) Math.toRadians(sweepDegrees);
        float start = phase - sweep * 0.5f;
        GL11.glBegin(GL11.GL_QUAD_STRIP);
        for (int i = 0; i <= segments; i++) {
            float u = i / (float) segments;
            double angle = start + sweep * u;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            float edge = (float) Math.sin(Math.PI * u);
            edge = (float) Math.pow(Math.max(0.0f, edge), 0.58f);
            float wave = 0.78f + 0.22f * (float) Math.sin(time * 5.2f + u * 9.0f + hueOffset * 11.0f);
            float localAlpha = alpha * edge * wave;
            int color = interpolate(0xFF39CEFF, 0xFFFFFFFF,
                    0.42f + 0.42f * (float) Math.sin(time * 2.6f + u * Math.PI * 1.7f + hueOffset));
            setColor(color, localAlpha * 0.50f);
            GL11.glTexCoord2f(u, 0.0f);
            GL11.glVertex3d(cos * (radius - thickness * 2.45f), y, sin * (radius - thickness * 2.45f));
            setColor(color, localAlpha);
            GL11.glTexCoord2f(u, 0.46f + edge * 0.54f);
            GL11.glVertex3d(cos * radius, y + 0.006D * edge, sin * radius);
            setColor(color, localAlpha * 0.46f);
            GL11.glTexCoord2f(u, 1.0f);
            GL11.glVertex3d(cos * (radius + thickness * 2.8f), y, sin * (radius + thickness * 2.8f));
        }
        GL11.glEnd();

        GL11.glLineWidth(Math.max(1.2f, lineWidth.getValue().floatValue() * 0.72f));
        GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int i = 0; i <= segments; i++) {
            float u = i / (float) segments;
            double angle = start + sweep * u;
            float edge = (float) Math.pow(Math.max(0.0f, Math.sin(Math.PI * u)), 0.72f);
            setColor(0xFFFFFFFF, alpha * edge * 0.58f);
            GL11.glTexCoord2f(u, edge);
            GL11.glVertex3d(Math.cos(angle) * radius, y + 0.010D, Math.sin(angle) * radius);
        }
        GL11.glEnd();
    }

    private void drawAuroraCore(float radius, float height, float alpha, float time) {
        int segments = 72;
        float coreRadius = radius * (0.86f + 0.04f * (float) Math.sin(time * 3.0f));
        GL11.glBegin(GL11.GL_QUAD_STRIP);
        for (int i = 0; i <= segments; i++) {
            double angle = Math.PI * 2.0D * i / segments;
            float u = i / (float) segments;
            float pulse = 0.56f + 0.44f * (float) Math.sin(time * 4.4f + angle * 2.0D);
            setColor(0xFF36D7FF, alpha * (0.08f + pulse * 0.08f));
            GL11.glTexCoord2f(u, 0.0f);
            GL11.glVertex3d(Math.cos(angle) * coreRadius, 0.05D, Math.sin(angle) * coreRadius);
            setColor(0xFFFFFFFF, alpha * (0.18f + pulse * 0.12f));
            GL11.glTexCoord2f(u, 1.0f);
            GL11.glVertex3d(Math.cos(angle) * (coreRadius * 0.92f), height * 0.96f, Math.sin(angle) * (coreRadius * 0.92f));
        }
        GL11.glEnd();
    }

    private void drawAuroraRain(float radius, float height, float alpha, float time) {
        int drops = 28;
        GL11.glLineWidth(Math.max(1.0f, lineWidth.getValue().floatValue() * 0.42f));
        GL11.glBegin(GL11.GL_LINES);
        for (int i = 0; i < drops; i++) {
            float seed = i * 12.9898f;
            double angle = i * 2.399963D + Math.sin(seed) * 0.18D + time * 0.11D;
            float lane = (float) ((Math.sin(seed * 1.73f) + 1.0f) * 0.5f);
            float top = height * (0.22f + 0.78f * lane);
            float fall = (time * (0.28f + (i % 5) * 0.035f) + lane) % 1.0f;
            float y1 = MathHelper.clamp_float(top - fall * height * 0.72f, 0.02f, height + 0.08f);
            float len = 0.10f + 0.20f * ((i % 4) / 3.0f);
            float r = radius * (0.84f + 0.54f * ((i % 7) / 6.0f));
            float pulse = 0.50f + 0.50f * (float) Math.sin(time * 5.0f + i * 0.71f);
            setColor(0xFFFFFFFF, alpha * (0.10f + pulse * 0.30f));
            GL11.glTexCoord2f(0.0f, 0.0f);
            GL11.glVertex3d(Math.cos(angle) * r, y1 + len, Math.sin(angle) * r);
            setColor(0xFF37CFFF, alpha * 0.02f);
            GL11.glTexCoord2f(0.0f, 1.0f);
            GL11.glVertex3d(Math.cos(angle) * r, y1 - len * 0.55f, Math.sin(angle) * r);
        }
        GL11.glEnd();
    }

    private void drawAuroraSparks(float radius, float height, float alpha, float time) {
        int sparks = 9;
        for (int i = 0; i < sparks; i++) {
            double angle = Math.PI * 2.0D * i / sparks + time * (0.48D + i * 0.018D);
            float y = height * (0.18f + 0.70f * ((i % 4) / 3.0f))
                    + 0.06f * (float) Math.sin(time * 2.7f + i);
            float size = 0.030f + 0.022f * (float) Math.sin(time * 4.2f + i * 1.4f);
            drawSpark(angle, radius * (0.82f + 0.20f * (i % 3)), y, size, alpha);
        }
    }

    private void drawSpark(double angle, float radius, float y, float size, float alpha) {
        double cx = Math.cos(angle) * radius;
        double cz = Math.sin(angle) * radius;
        setColor(0xFFFFFFFF, alpha * 0.58f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0f, 0.5f);
        GL11.glVertex3d(cx - size, y, cz);
        GL11.glTexCoord2f(0.5f, 1.0f);
        GL11.glVertex3d(cx, y + size * 1.75f, cz);
        GL11.glTexCoord2f(1.0f, 0.5f);
        GL11.glVertex3d(cx + size, y, cz);
        GL11.glTexCoord2f(0.5f, 0.0f);
        GL11.glVertex3d(cx, y - size * 1.75f, cz);
        GL11.glEnd();
    }

    private void drawCapsule(float radius, float height, float alpha, float time) {
        int segments = 48;
        float wave = 0.5f + 0.5f * (float) Math.sin(time * 2.2f);
        GL11.glBegin(GL11.GL_QUAD_STRIP);
        for (int i = 0; i <= segments; i++) {
            double angle = Math.PI * 2.0D * i / segments;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            float u = i / (float) segments;
            float local = 0.24f + 0.16f * (float) Math.sin(time * 3.0f + angle * 2.0D);
            GL11.glColor4f(1.0f, 1.0f, 1.0f, alpha * local);
            GL11.glTexCoord2f(u, 0.0f);
            GL11.glVertex3d(x, 0.0D, z);
            GL11.glColor4f(1.0f, 1.0f, 1.0f, alpha * (local + wave * 0.08f));
            GL11.glTexCoord2f(u, 1.0f);
            GL11.glVertex3d(x, height, z);
        }
        GL11.glEnd();
    }

    private void drawCosmicCapsule(float radius, float height, float alpha, float time) {
        int segments = 72;
        GL11.glBegin(GL11.GL_QUAD_STRIP);
        for (int i = 0; i <= segments; i++) {
            double angle = Math.PI * 2.0D * i / segments;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            float u = i / (float) segments;
            int bottomColor = rainbowColor(time * 0.09f, u * 0.24f);
            int topColor = rainbowColor(time * 0.09f, 0.42f + u * 0.24f);
            float pulse = 0.52f + 0.48f * (float) Math.sin(time * 4.0f + angle * 3.0D);
            setColor(bottomColor, alpha * (0.18f + pulse * 0.10f));
            GL11.glTexCoord2f(u, 0.0f);
            GL11.glVertex3d(x, 0.02D, z);
            setColor(topColor, alpha * (0.28f + pulse * 0.14f));
            GL11.glTexCoord2f(u, 1.0f);
            GL11.glVertex3d(x, height, z);
        }
        GL11.glEnd();
    }

    private void drawCosmicGround(float radius, float alpha, float time) {
        drawColoredRing(0.015f, radius * 1.68f, 0.045f, 96, alpha * 0.68f, time, 0.02f);
        drawColoredRing(0.025f, radius * (1.95f + 0.12f * (float) Math.sin(time * 2.1f)), 0.026f, 96,
                alpha * 0.38f, time + 0.45f, 0.30f);
        drawColoredRing(0.035f, radius * (2.22f + 0.14f * (float) Math.cos(time * 2.4f)), 0.018f, 96,
                alpha * 0.26f, time + 0.90f, 0.58f);
    }

    private void drawHelixRibbon(float radius, float height, float alpha, float time, float phase, boolean reverse) {
        int segments = 78;
        float ribbonWidth = 0.070f;
        float direction = reverse ? -1.0f : 1.0f;
        GL11.glBegin(GL11.GL_QUAD_STRIP);
        for (int i = 0; i <= segments; i++) {
            float t = i / (float) segments;
            double angle = phase + direction * (time * 2.15f + t * Math.PI * 5.4D);
            double side = ribbonWidth / Math.max(0.2f, radius);
            float y = 0.04f + t * (height - 0.02f);
            float wave = 0.62f + 0.38f * (float) Math.sin(time * 5.0f + t * 18.0f + phase);
            int color = rainbowColor(time * 0.10f, t * 0.55f + phase * 0.07f);
            setColor(color, alpha * wave);
            GL11.glTexCoord2f(t, 0.0f);
            GL11.glVertex3d(Math.cos(angle - side) * radius, y, Math.sin(angle - side) * radius);
            setColor(color, alpha * (0.42f + wave * 0.30f));
            GL11.glTexCoord2f(t, 1.0f);
            GL11.glVertex3d(Math.cos(angle + side) * (radius + 0.055f), y, Math.sin(angle + side) * (radius + 0.055f));
        }
        GL11.glEnd();
    }

    private void drawTiltedRing(float centerY, float radius, float thickness, int segments, float alpha, float time,
                                float tilt, float spin, float hueOffset) {
        GL11.glPushMatrix();
        GL11.glTranslatef(0.0f, centerY, 0.0f);
        GL11.glRotatef(spin, 0.0f, 1.0f, 0.0f);
        GL11.glRotatef(tilt, 1.0f, 0.0f, 0.0f);
        drawColoredRing(0.0f, radius, thickness, segments, alpha, time, hueOffset);
        GL11.glPopMatrix();
    }

    private void drawColoredRing(float y, float radius, float thickness, int segments, float alpha, float time,
                                 float hueOffset) {
        GL11.glLineWidth(Math.max(1.0f, lineWidth.getValue().floatValue() * 0.72f));
        GL11.glBegin(GL11.GL_QUAD_STRIP);
        for (int i = 0; i <= segments; i++) {
            double angle = Math.PI * 2.0D * i / segments;
            float u = i / (float) segments;
            float pulse = 0.74f + 0.26f * (float) Math.sin(time * 4.8f + angle * 3.0D);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            int color = rainbowColor(time * 0.09f, hueOffset + u * 0.55f);
            setColor(color, alpha * pulse);
            GL11.glTexCoord2f(u, 0.0f);
            GL11.glVertex3d(cos * (radius - thickness), y, sin * (radius - thickness));
            setColor(color, alpha * (pulse * 0.72f));
            GL11.glTexCoord2f(u, 1.0f);
            GL11.glVertex3d(cos * (radius + thickness), y, sin * (radius + thickness));
        }
        GL11.glEnd();
    }

    private void drawEnergySpikes(float radius, float height, float alpha, float time) {
        int spikes = 10;
        float centerY = height * 0.55f;
        GL11.glBegin(GL11.GL_TRIANGLES);
        for (int i = 0; i < spikes; i++) {
            double angle = Math.PI * 2.0D * i / spikes + time * 0.52D;
            double spread = 0.075D + 0.015D * Math.sin(time * 3.0f + i);
            float spike = 0.18f + 0.10f * (float) Math.sin(time * 4.5f + i * 1.7f);
            int color = rainbowColor(time * 0.11f, i / (float) spikes);
            setColor(color, alpha * 0.06f);
            GL11.glTexCoord2f(0.0f, 0.0f);
            GL11.glVertex3d(Math.cos(angle - spread) * radius, centerY - height * 0.18f, Math.sin(angle - spread) * radius);
            setColor(color, alpha * 0.82f);
            GL11.glTexCoord2f(0.5f, 1.0f);
            GL11.glVertex3d(Math.cos(angle) * (radius + spike), centerY, Math.sin(angle) * (radius + spike));
            setColor(color, alpha * 0.06f);
            GL11.glTexCoord2f(1.0f, 0.0f);
            GL11.glVertex3d(Math.cos(angle + spread) * radius, centerY + height * 0.18f, Math.sin(angle + spread) * radius);
        }
        GL11.glEnd();
    }

    private void drawOrbitingStars(float radius, float height, float alpha, float time) {
        int stars = 6;
        for (int i = 0; i < stars; i++) {
            double angle = Math.PI * 2.0D * i / stars + time * (0.86D + i * 0.025D);
            float y = height * (0.22f + 0.62f * ((i % 3) / 2.0f))
                    + 0.08f * (float) Math.sin(time * 2.0f + i);
            float size = 0.075f + 0.020f * (float) Math.sin(time * 3.2f + i * 2.1f);
            int color = rainbowColor(time * 0.12f, i / (float) stars + 0.15f);
            drawStar(angle, radius, y, size, color, alpha * (0.76f + 0.22f * (float) Math.sin(time * 4.0f + i)));
        }
    }

    private void drawStar(double angle, float radius, float y, float size, int color, float alpha) {
        double cx = Math.cos(angle) * radius;
        double cz = Math.sin(angle) * radius;
        double tx = -Math.sin(angle);
        double tz = Math.cos(angle);
        double rx = Math.cos(angle);
        double rz = Math.sin(angle);
        setColor(color, alpha);
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glTexCoord2f(0.5f, 1.0f);
        GL11.glVertex3d(cx, y + size * 1.7f, cz);
        GL11.glTexCoord2f(1.0f, 0.5f);
        GL11.glVertex3d(cx + tx * size, y, cz + tz * size);
        GL11.glTexCoord2f(0.5f, 0.5f);
        GL11.glVertex3d(cx, y, cz);
        GL11.glTexCoord2f(0.5f, 1.0f);
        GL11.glVertex3d(cx, y + size * 1.7f, cz);
        GL11.glTexCoord2f(0.5f, 0.5f);
        GL11.glVertex3d(cx, y, cz);
        GL11.glTexCoord2f(0.0f, 0.5f);
        GL11.glVertex3d(cx - tx * size, y, cz - tz * size);
        GL11.glTexCoord2f(0.5f, 0.0f);
        GL11.glVertex3d(cx, y - size * 1.7f, cz);
        GL11.glTexCoord2f(0.0f, 0.5f);
        GL11.glVertex3d(cx - tx * size, y, cz - tz * size);
        GL11.glTexCoord2f(0.5f, 0.5f);
        GL11.glVertex3d(cx, y, cz);
        GL11.glTexCoord2f(0.5f, 0.0f);
        GL11.glVertex3d(cx, y - size * 1.7f, cz);
        GL11.glTexCoord2f(0.5f, 0.5f);
        GL11.glVertex3d(cx, y, cz);
        GL11.glTexCoord2f(1.0f, 0.5f);
        GL11.glVertex3d(cx + tx * size, y, cz + tz * size);
        GL11.glEnd();

        setColor(color, alpha * 0.42f);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(cx - rx * size * 1.65D, y, cz - rz * size * 1.65D);
        GL11.glVertex3d(cx + rx * size * 1.65D, y, cz + rz * size * 1.65D);
        GL11.glVertex3d(cx - tx * size * 1.65D, y, cz - tz * size * 1.65D);
        GL11.glVertex3d(cx + tx * size * 1.65D, y, cz + tz * size * 1.65D);
        GL11.glEnd();
    }

    private void drawCosmicChains(float radius, float height, float alpha, float time) {
        int links = 24;
        GL11.glLineWidth(Math.max(1.1f, lineWidth.getValue().floatValue() * 0.58f));
        for (int i = 0; i < links; i++) {
            float t = i / (float) links;
            double angle = time * 0.72D + t * Math.PI * 5.6D;
            float y = 0.05f + t * (height + 0.05f);
            float wave = 0.72f + 0.28f * (float) Math.sin(time * 2.8f + i * 0.67f);
            int color = i % 2 == 0 ? 0xFFDDE8FF : rainbowColor(time * 0.055f, t * 0.32f + 0.58f);
            drawChainLink(angle, radius, y, 0.105f, 0.050f, color, alpha * wave, i % 2 == 0);
        }
        for (int i = 0; i < links; i++) {
            float t = i / (float) links;
            double angle = -time * 0.58D + Math.PI + t * Math.PI * 5.6D;
            float y = height + 0.05f - t * (height + 0.05f);
            float wave = 0.64f + 0.36f * (float) Math.cos(time * 2.4f + i * 0.71f);
            drawChainLink(angle, radius * 1.035f, y, 0.092f, 0.044f,
                    0xFFB7C2D8, alpha * 0.68f * wave, i % 2 != 0);
        }
    }

    private void drawChainLink(double angle, float radius, float y, float length, float width,
                               int color, float alpha, boolean vertical) {
        double cx = Math.cos(angle) * radius;
        double cz = Math.sin(angle) * radius;
        double tangentX = -Math.sin(angle);
        double tangentZ = Math.cos(angle);
        double radialX = Math.cos(angle);
        double radialZ = Math.sin(angle);
        double longX = vertical ? radialX : tangentX;
        double longZ = vertical ? radialZ : tangentZ;
        double wideX = vertical ? tangentX : radialX;
        double wideZ = vertical ? tangentZ : radialZ;
        float innerAlpha = MathHelper.clamp_float(alpha, 0.0f, 0.40f);
        float glowAlpha = innerAlpha * 0.46f;

        setColor(color, glowAlpha);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0.0f, 0.0f);
        GL11.glVertex3d(cx - longX * length * 1.28D - wideX * width * 1.35D, y - width * 0.9f,
                cz - longZ * length * 1.28D - wideZ * width * 1.35D);
        GL11.glTexCoord2f(1.0f, 0.0f);
        GL11.glVertex3d(cx + longX * length * 1.28D - wideX * width * 1.35D, y - width * 0.9f,
                cz + longZ * length * 1.28D - wideZ * width * 1.35D);
        GL11.glTexCoord2f(1.0f, 1.0f);
        GL11.glVertex3d(cx + longX * length * 1.28D + wideX * width * 1.35D, y + width * 0.9f,
                cz + longZ * length * 1.28D + wideZ * width * 1.35D);
        GL11.glTexCoord2f(0.0f, 1.0f);
        GL11.glVertex3d(cx - longX * length * 1.28D + wideX * width * 1.35D, y + width * 0.9f,
                cz - longZ * length * 1.28D + wideZ * width * 1.35D);
        GL11.glEnd();

        setColor(color, innerAlpha);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glTexCoord2f(0.0f, 0.0f);
        GL11.glVertex3d(cx - longX * length - wideX * width, y - width * 0.55f,
                cz - longZ * length - wideZ * width);
        GL11.glTexCoord2f(1.0f, 0.0f);
        GL11.glVertex3d(cx + longX * length - wideX * width, y - width * 0.55f,
                cz + longZ * length - wideZ * width);
        GL11.glTexCoord2f(1.0f, 1.0f);
        GL11.glVertex3d(cx + longX * length + wideX * width, y + width * 0.55f,
                cz + longZ * length + wideZ * width);
        GL11.glTexCoord2f(0.0f, 1.0f);
        GL11.glVertex3d(cx - longX * length + wideX * width, y + width * 0.55f,
                cz - longZ * length + wideZ * width);
        GL11.glEnd();

        setColor(color, innerAlpha * 0.58f);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex3d(cx - longX * length * 0.52D - wideX * width * 0.42D, y - width * 0.18f,
                cz - longZ * length * 0.52D - wideZ * width * 0.42D);
        GL11.glVertex3d(cx + longX * length * 0.52D - wideX * width * 0.42D, y - width * 0.18f,
                cz + longZ * length * 0.52D - wideZ * width * 0.42D);
        GL11.glVertex3d(cx + longX * length * 0.52D + wideX * width * 0.42D, y + width * 0.18f,
                cz + longZ * length * 0.52D + wideZ * width * 0.42D);
        GL11.glVertex3d(cx - longX * length * 0.52D + wideX * width * 0.42D, y + width * 0.18f,
                cz - longZ * length * 0.52D + wideZ * width * 0.42D);
        GL11.glEnd();
    }

    private void drawScanBeam(float radius, float height, float alpha, float time) {
        double angle = time * 1.35D;
        double width = 0.16D;
        GL11.glBegin(GL11.GL_QUADS);
        for (int i = 0; i < 2; i++) {
            double local = angle + i * Math.PI;
            int color = rainbowColor(time * 0.10f, i * 0.42f);
            setColor(color, alpha * 0.08f);
            GL11.glTexCoord2f(0.0f, 0.0f);
            GL11.glVertex3d(Math.cos(local - width) * radius, 0.04D, Math.sin(local - width) * radius);
            setColor(color, alpha * 0.56f);
            GL11.glTexCoord2f(0.5f, 0.0f);
            GL11.glVertex3d(Math.cos(local) * (radius + 0.10f), 0.04D, Math.sin(local) * (radius + 0.10f));
            setColor(color, alpha * 0.46f);
            GL11.glTexCoord2f(0.5f, 1.0f);
            GL11.glVertex3d(Math.cos(local) * (radius + 0.10f), height, Math.sin(local) * (radius + 0.10f));
            setColor(color, alpha * 0.08f);
            GL11.glTexCoord2f(1.0f, 1.0f);
            GL11.glVertex3d(Math.cos(local + width) * radius, height, Math.sin(local + width) * radius);
        }
        GL11.glEnd();
    }


    private void drawVerticalMarkers(float radius, float height, float alpha, float time) {
        int markers = 4;
        float markerHeight = height * 0.42f;
        float bottom = height * 0.29f;
        float width = 0.035f;
        for (int i = 0; i < markers; i++) {
            double angle = Math.PI * 2.0D * i / markers + time * 0.35D;
            double next = angle + 0.045D;
            float pulse = 0.72f + 0.28f * (float) Math.sin(time * 3.2f + i);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glColor4f(1.0f, 1.0f, 1.0f, alpha * pulse);
            GL11.glTexCoord2f(0.0f, 0.0f);
            GL11.glVertex3d(Math.cos(angle) * (radius + width), bottom, Math.sin(angle) * (radius + width));
            GL11.glTexCoord2f(1.0f, 0.0f);
            GL11.glVertex3d(Math.cos(next) * (radius + width), bottom, Math.sin(next) * (radius + width));
            GL11.glTexCoord2f(1.0f, 1.0f);
            GL11.glVertex3d(Math.cos(next) * (radius + width), bottom + markerHeight, Math.sin(next) * (radius + width));
            GL11.glTexCoord2f(0.0f, 1.0f);
            GL11.glVertex3d(Math.cos(angle) * (radius + width), bottom + markerHeight, Math.sin(angle) * (radius + width));
            GL11.glEnd();
        }
    }

    private void drawRing(float y, float radius, float thickness, int segments, float alpha, float time) {
        GL11.glLineWidth(lineWidth.getValue().floatValue());
        GL11.glBegin(GL11.GL_QUAD_STRIP);
        for (int i = 0; i <= segments; i++) {
            double angle = Math.PI * 2.0D * i / segments;
            float u = i / (float) segments;
            float pulse = 0.78f + 0.22f * (float) Math.sin(time * 4.0f + angle * 2.0D);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            GL11.glColor4f(1.0f, 1.0f, 1.0f, alpha * pulse);
            GL11.glTexCoord2f(u, 0.0f);
            GL11.glVertex3d(cos * (radius - thickness), y, sin * (radius - thickness));
            GL11.glTexCoord2f(u, 1.0f);
            GL11.glVertex3d(cos * (radius + thickness), y, sin * (radius + thickness));
        }
        GL11.glEnd();
    }

    private void drawHealthArc(EntityLivingBase target, float y, float radius, float thickness, float alpha) {
        float health = MathHelper.clamp_float(target.getHealth() / Math.max(1.0f, target.getMaxHealth()), 0.0f, 1.0f);
        int segments = Math.max(4, Math.round(72.0f * health));
        double start = -Math.PI / 2.0D;
        double end = start + Math.PI * 2.0D * health;
        GL11.glBegin(GL11.GL_QUAD_STRIP);
        for (int i = 0; i <= segments; i++) {
            double angle = start + (end - start) * i / segments;
            float u = i / (float) Math.max(1, segments);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            GL11.glColor4f(1.0f, 1.0f, 1.0f, alpha);
            GL11.glTexCoord2f(u, 0.0f);
            GL11.glVertex3d(cos * (radius - thickness), y, sin * (radius - thickness));
            GL11.glTexCoord2f(u, 1.0f);
            GL11.glVertex3d(cos * (radius + thickness), y, sin * (radius + thickness));
        }
        GL11.glEnd();
    }

    private int healthColor(EntityLivingBase target) {
        float health = MathHelper.clamp_float(target.getHealth() / Math.max(1.0f, target.getMaxHealth()), 0.0f, 1.0f);
        int low = 0xFFFF5E70;
        int mid = 0xFFFFC65B;
        int high = 0xFF67D992;
        return health > 0.55f
                ? interpolate(mid, high, (health - 0.55f) / 0.45f)
                : interpolate(low, mid, health / 0.55f);
    }

    private void setColor(int color, float alpha) {
        GL11.glColor4f(((color >> 16) & 255) / 255.0f,
                ((color >> 8) & 255) / 255.0f,
                (color & 255) / 255.0f,
                MathHelper.clamp_float(alpha, 0.0f, 1.0f));
    }

    private int rainbowColor(float base, float offset) {
        float hue = base + offset;
        hue -= (float) Math.floor(hue);
        return hsbToRgb(hue, 0.72f, 1.0f);
    }

    private int hsbToRgb(float hue, float saturation, float brightness) {
        float h = (hue - (float) Math.floor(hue)) * 6.0f;
        int sector = (int) Math.floor(h);
        float fraction = h - sector;
        float p = brightness * (1.0f - saturation);
        float q = brightness * (1.0f - saturation * fraction);
        float t = brightness * (1.0f - saturation * (1.0f - fraction));
        float r;
        float g;
        float b;
        switch (sector % 6) {
            case 0:
                r = brightness;
                g = t;
                b = p;
                break;
            case 1:
                r = q;
                g = brightness;
                b = p;
                break;
            case 2:
                r = p;
                g = brightness;
                b = t;
                break;
            case 3:
                r = p;
                g = q;
                b = brightness;
                break;
            case 4:
                r = t;
                g = p;
                b = brightness;
                break;
            default:
                r = brightness;
                g = p;
                b = q;
                break;
        }
        return 0xFF000000
                | Math.round(r * 255.0f) << 16
                | Math.round(g * 255.0f) << 8
                | Math.round(b * 255.0f);
    }

    private int interpolate(int first, int second, float progress) {
        float t = MathHelper.clamp_float(progress, 0.0f, 1.0f);
        int r = Math.round(((first >> 16) & 255) + (((second >> 16) & 255) - ((first >> 16) & 255)) * t);
        int g = Math.round(((first >> 8) & 255) + (((second >> 8) & 255) - ((first >> 8) & 255)) * t);
        int b = Math.round((first & 255) + ((second & 255) - (first & 255)) * t);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private float animationFactor(long now) {
        float delta = Math.max(1.0f, Math.min(50.0f, now - lastFrameMS));
        lastFrameMS = now;
        return 1.0f - (float) Math.pow(0.001D, delta / 230.0D);
    }

    private static final class TargetShader {
        private static int program;
        private static boolean disabled;
        private static boolean loggedFailure;

        static boolean begin(int primary, int secondary, float alpha, float time, float hurtPulse) {
            if (disabled || !supportsShaders()) {
                return false;
            }
            if (program == 0) {
                program = createProgram();
                if (program == 0) {
                    return false;
                }
            }
            GL20.glUseProgram(program);
            setColor("primary", primary, alpha);
            setColor("secondary", secondary, alpha);
            GL20.glUniform1f(GL20.glGetUniformLocation(program, "time"), time);
            GL20.glUniform1f(GL20.glGetUniformLocation(program, "hurtPulse"), hurtPulse);
            return true;
        }

        static void end() {
            GL20.glUseProgram(0);
        }

        private static int createProgram() {
            int vertex = 0;
            int fragment = 0;
            int linked = 0;
            try {
                vertex = compile(GL20.GL_VERTEX_SHADER, VERTEX_SHADER);
                fragment = compile(GL20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
                linked = GL20.glCreateProgram();
                GL20.glAttachShader(linked, vertex);
                GL20.glAttachShader(linked, fragment);
                GL20.glLinkProgram(linked);
                if (GL20.glGetProgrami(linked, GL20.GL_LINK_STATUS) == 0) {
                    throw new IllegalStateException(GL20.glGetProgramInfoLog(linked, 4096));
                }
                return linked;
            } catch (Throwable throwable) {
                disabled = true;
                logFailure(throwable);
                if (linked != 0) {
                    GL20.glDeleteProgram(linked);
                }
                return 0;
            } finally {
                if (linked != 0 && vertex != 0) {
                    GL20.glDetachShader(linked, vertex);
                }
                if (linked != 0 && fragment != 0) {
                    GL20.glDetachShader(linked, fragment);
                }
                if (vertex != 0) {
                    GL20.glDeleteShader(vertex);
                }
                if (fragment != 0) {
                    GL20.glDeleteShader(fragment);
                }
            }
        }

        private static int compile(int type, String source) {
            int shader = GL20.glCreateShader(type);
            GL20.glShaderSource(shader, source);
            GL20.glCompileShader(shader);
            if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
                String log = GL20.glGetShaderInfoLog(shader, 4096);
                GL20.glDeleteShader(shader);
                throw new IllegalStateException(log);
            }
            return shader;
        }

        private static boolean supportsShaders() {
            try {
                return GLContext.getCapabilities() != null && GLContext.getCapabilities().OpenGL20;
            } catch (Throwable ignored) {
                return false;
            }
        }

        private static void setColor(String uniform, int color, float alpha) {
            int location = GL20.glGetUniformLocation(program, uniform);
            GL20.glUniform4f(location,
                    ((color >> 16) & 255) / 255.0f,
                    ((color >> 8) & 255) / 255.0f,
                    (color & 255) / 255.0f,
                    MathHelper.clamp_float(alpha, 0.0f, 1.0f));
        }

        private static void logFailure(Throwable throwable) {
            if (loggedFailure) {
                return;
            }
            loggedFailure = true;
            System.err.println("[VapuLite] TargetESP shader disabled, falling back to GL11: " + throwable.getMessage());
        }

        private static final String VERTEX_SHADER =
                "#version 120\n" +
                "void main() {\n" +
                "    gl_TexCoord[0] = gl_MultiTexCoord0;\n" +
                "    gl_FrontColor = gl_Color;\n" +
                "    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;\n" +
                "}\n";

        private static final String FRAGMENT_SHADER =
                "#version 120\n" +
                "uniform vec4 primary;\n" +
                "uniform vec4 secondary;\n" +
                "uniform float time;\n" +
                "uniform float hurtPulse;\n" +
                "void main() {\n" +
                "    vec2 uv = gl_TexCoord[0].st;\n" +
                "    float sweep = 0.5 + 0.5 * sin(time * 3.4 + uv.x * 6.28318 + uv.y * 4.2);\n" +
                "    float vertical = smoothstep(0.0, 1.0, uv.y);\n" +
                "    vec4 color = mix(primary, secondary, vertical);\n" +
                "    float tint = 1.0 - min(gl_Color.r, min(gl_Color.g, gl_Color.b));\n" +
                "    color.rgb = mix(color.rgb, gl_Color.rgb, clamp(tint * 0.72, 0.0, 0.72));\n" +
                "    color.rgb += sweep * vec3(0.08, 0.10, 0.13);\n" +
                "    color.rgb = mix(color.rgb, vec3(1.0, 0.25, 0.28), hurtPulse * (0.35 + sweep * 0.25));\n" +
                "    float edge = 0.68 + 0.32 * sweep;\n" +
                "    gl_FragColor = vec4(clamp(color.rgb, 0.0, 1.0), color.a * gl_Color.a * edge);\n" +
                "}\n";
    }
}
