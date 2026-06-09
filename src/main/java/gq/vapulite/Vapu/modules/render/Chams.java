package gq.vapulite.Vapu.modules.render;

import gq.vapulite.Vapu.ModuleType;
import gq.vapulite.Vapu.modules.Module;
import gq.vapulite.Vapu.modules.combat.AntiBot;
import gq.vapulite.Vapu.utils.ColorUtil;
import gq.vapulite.Vapu.value.Numbers;
import gq.vapulite.Vapu.value.Option;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAmbientCreature;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityWaterMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.HashSet;
import java.util.Set;

public class Chams extends Module {
    private final Option<Boolean> throughWalls = new Option<Boolean>("Through Walls", "ThroughWalls", true);
    private final Option<Boolean> textured = new Option<Boolean>("Textured", "Textured", false);
    private final Option<Boolean> rainbow = new Option<Boolean>("Rainbow", "Rainbow", true);
    private final Option<Boolean> players = new Option<Boolean>("Players", "Players", true);
    private final Option<Boolean> mobs = new Option<Boolean>("Mobs", "Mobs", false);
    private final Option<Boolean> animals = new Option<Boolean>("Animals", "Animals", false);
    private final Option<Boolean> invisible = new Option<Boolean>("Invisible", "Invisible", false);
    private final Numbers<Double> red = new Numbers<Double>("Red", "Red", 88.0, 0.0, 255.0, 1.0);
    private final Numbers<Double> green = new Numbers<Double>("Green", "Green", 190.0, 0.0, 255.0, 1.0);
    private final Numbers<Double> blue = new Numbers<Double>("Blue", "Blue", 255.0, 0.0, 255.0, 1.0);
    private final Numbers<Double> alpha = new Numbers<Double>("Alpha", "Alpha", 115.0, 35.0, 210.0, 5.0);
    private final Set<Integer> renderedEntities = new HashSet<Integer>();

    public Chams() {
        super("Chams", Keyboard.KEY_NONE, ModuleType.Render, "Render entities with colored chams");
        this.addValues(throughWalls, textured, rainbow, players, mobs, animals, invisible, red, green, blue, alpha);
        Chinese = "透视染色";
    }

    @Override
    public void disable() {
        renderedEntities.clear();
    }

    @SubscribeEvent
    public void onRenderLivingPre(RenderLivingEvent.Pre event) {
        if (!isInGame() || !(event.entity instanceof EntityLivingBase)) {
            return;
        }
        EntityLivingBase entity = (EntityLivingBase) event.entity;
        if (!isValidTarget(entity)) {
            return;
        }

        renderedEntities.add(entity.getEntityId());
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_POLYGON_BIT | GL11.GL_LIGHTING_BIT | GL11.GL_TEXTURE_BIT);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDepthMask(false);

        if (Boolean.TRUE.equals(throughWalls.getValue())) {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glPolygonOffset(1.0f, -1000000.0f);
        }
        if (!Boolean.TRUE.equals(textured.getValue())) {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
        }

        int color = getColor(entity);
        GL11.glColor4f(((color >> 16) & 255) / 255.0f,
                ((color >> 8) & 255) / 255.0f,
                (color & 255) / 255.0f,
                Math.max(0.0f, Math.min(1.0f, alpha.getValue().floatValue() / 255.0f)));
    }

    @SubscribeEvent
    public void onRenderLivingPost(RenderLivingEvent.Post event) {
        if (!(event.entity instanceof EntityLivingBase)) {
            return;
        }
        if (renderedEntities.remove(event.entity.getEntityId())) {
            GL11.glPopAttrib();
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    private boolean isValidTarget(EntityLivingBase entity) {
        if (entity == null || entity == mc.thePlayer || entity.isDead || entity.deathTime > 0) {
            return false;
        }
        if (!Boolean.TRUE.equals(invisible.getValue()) && entity.isInvisible()) {
            return false;
        }
        if (entity instanceof EntityPlayer) {
            return Boolean.TRUE.equals(players.getValue()) && !AntiBot.isServerBot(entity);
        }
        if (entity instanceof EntityAnimal || entity instanceof EntityWaterMob || entity instanceof EntityAmbientCreature) {
            return Boolean.TRUE.equals(animals.getValue());
        }
        if (entity instanceof EntityMob || entity instanceof EntitySlime || entity instanceof IMob) {
            return Boolean.TRUE.equals(mobs.getValue());
        }
        return Boolean.TRUE.equals(mobs.getValue());
    }

    private int getColor(EntityLivingBase entity) {
        if (entity.hurtTime > 0) {
            return 0xFFFF5E70;
        }
        if (Boolean.TRUE.equals(rainbow.getValue())) {
            return ColorUtil.getRainbow().getRGB();
        }
        int r = clampColor(red.getValue().intValue());
        int g = clampColor(green.getValue().intValue());
        int b = clampColor(blue.getValue().intValue());
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
