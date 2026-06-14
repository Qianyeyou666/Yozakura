package gq.yozakura.module.render;

import gq.yozakura.module.ModuleType;
import gq.yozakura.module.Module;
import gq.yozakura.module.combat.AntiBot;
import gq.yozakura.util.color.ColorUtil;
import gq.yozakura.value.Mode;
import gq.yozakura.value.Numbers;
import gq.yozakura.value.Option;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAmbientCreature;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityWaterMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;

public class ESP extends Module {
    public enum EspBoxMode {
        OUTLINE,
        FILLED,
        BOTH
    }

    private final Mode<EspBoxMode> boxMode =
            new Mode<EspBoxMode>("Mode", "Mode", EspBoxMode.values(), EspBoxMode.BOTH);
    private final Option<Boolean> rainbow = new Option<Boolean>("Palette Rainbow", "Rainbow", true);
    private final Option<Boolean> players = new Option<Boolean>("Players", "Players", true);
    private final Option<Boolean> mobs = new Option<Boolean>("Mobs", "Mobs", false);
    private final Option<Boolean> animals = new Option<Boolean>("Animals", "Animals", false);
    private final Option<Boolean> invisible = new Option<Boolean>("Invisible", "Invisible", false);
    private final Option<Boolean> redOnDamage = new Option<Boolean>("Red On Damage", "RedOnDamage", true);
    private final Numbers<Double> red = new Numbers<Double>("Red", "Red", 95.0, 0.0, 255.0, 1.0);
    private final Numbers<Double> green = new Numbers<Double>("Green", "Green", 190.0, 0.0, 255.0, 1.0);
    private final Numbers<Double> blue = new Numbers<Double>("Blue", "Blue", 255.0, 0.0, 255.0, 1.0);
    private final Numbers<Double> alpha = new Numbers<Double>("Alpha", "Alpha", 160.0, 35.0, 255.0, 5.0);

    public ESP() {
        super("ESP", Keyboard.KEY_NONE, ModuleType.Render, "Draw entity boxes");
        this.addValues(boxMode, rainbow, players, mobs, animals, invisible, redOnDamage, red, green, blue, alpha);
        Chinese = "实体框体";
    }

    @SubscribeEvent
    public void onWorld(RenderWorldLastEvent event) {
        if (!isInGame()) {
            return;
        }

        for (Object object : mc.theWorld.loadedEntityList) {
            if (!(object instanceof EntityLivingBase)) {
                continue;
            }
            EntityLivingBase entity = (EntityLivingBase) object;
            if (!isValidTarget(entity)) {
                continue;
            }
            StorageESP.ee((Entity) entity, getColor(entity), false, getRenderType());
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

    private int getRenderType() {
        EspBoxMode current = boxMode.getValue();
        if (current == EspBoxMode.OUTLINE) {
            return 2;
        }
        if (current == EspBoxMode.FILLED) {
            return 3;
        }
        return 1;
    }

    private int getColor(EntityLivingBase entity) {
        if (Boolean.TRUE.equals(redOnDamage.getValue()) && entity.hurtTime > 0) {
            return withAlpha(0xFFFF5E70);
        }
        if (Boolean.TRUE.equals(rainbow.getValue())) {
            return withAlpha(ColorUtil.getRainbow().getRGB());
        }
        return withAlpha(0xFF000000 | clampColor(red.getValue().intValue()) << 16
                | clampColor(green.getValue().intValue()) << 8
                | clampColor(blue.getValue().intValue()));
    }

    private int withAlpha(int color) {
        return (color & 0x00FFFFFF) | (clampColor(alpha.getValue().intValue()) << 24);
    }

    private int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
