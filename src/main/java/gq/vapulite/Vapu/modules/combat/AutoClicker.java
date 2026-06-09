package gq.vapulite.Vapu.modules.combat;

import gq.vapulite.Vapu.ModuleType;
import gq.vapulite.Vapu.modules.Module;
import gq.vapulite.Vapu.utils.TimerUtil;
import gq.vapulite.Vapu.value.Numbers;
import gq.vapulite.Vapu.value.Option;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

public class AutoClicker extends Module {
    private final TimerUtil timer = new TimerUtil();
    private final Numbers<Double> minCps = new Numbers<Double>("Min CPS", "MinCPS", 8.0, 1.0, 20.0, 1.0);
    private final Numbers<Double> cps = new Numbers<Double>("Max CPS", "Cps", 12.0, 1.0, 20.0, 1.0);
    private final Option<Boolean> weaponOnly = new Option<Boolean>("Only Weapon", "OnlyWeapon", false);
    private final Option<Boolean> entitiesOnly = new Option<Boolean>("Entities Only", "EntitiesOnly", false);
    private final Option<Boolean> breakBlocks = new Option<Boolean>("Break Blocks", "BreakBlocks", true);
    private int delayMs;

    public AutoClicker() {
        super("AutoClicker", Keyboard.KEY_K, ModuleType.Combat, "Click automatically while attack is held");
        this.addValues(minCps, cps, weaponOnly, entitiesOnly, breakBlocks);
        Chinese = "连点器";
    }

    @Override
    public void enable() {
        delayMs = CombatUtil.nextDelay(minCps.getValue(), cps.getValue());
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!isInGame() || CombatUtil.shouldPauseForScreen()) {
            return;
        }
        if (!mc.gameSettings.keyBindAttack.isKeyDown()) {
            return;
        }
        if (Boolean.TRUE.equals(weaponOnly.getValue()) && !CombatUtil.isHoldingWeapon()) {
            return;
        }
        Backtrack.applyBacktrackHit();
        if (!canClickCurrentTarget()) {
            return;
        }
        if (!timer.delay(delayMs)) {
            return;
        }

        Entity entity = mc.objectMouseOver == null ? null : mc.objectMouseOver.entityHit;
        if (entity != null && (!HitSelect.shouldAttack(entity) || !KnockbackDelay.shouldAttack(entity))) {
            return;
        }

        int key = mc.gameSettings.keyBindAttack.getKeyCode();
        KeyBinding.onTick(key);
        if (entity != null) {
            Criticals.tryCritical();
            mc.thePlayer.swingItem();
            mc.playerController.attackEntity(mc.thePlayer, entity);
            HitSelect.onAttack(entity);
            BlockHit.onAttack(entity);
            WTap.onAttack(entity);
        }

        delayMs = CombatUtil.nextDelay(minCps.getValue(), cps.getValue());
        timer.reset();
    }

    private boolean canClickCurrentTarget() {
        if (mc.objectMouseOver == null) {
            return !Boolean.TRUE.equals(entitiesOnly.getValue());
        }
        if (mc.objectMouseOver.entityHit != null) {
            return true;
        }
        if (mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            return Boolean.TRUE.equals(breakBlocks.getValue()) && !Boolean.TRUE.equals(entitiesOnly.getValue());
        }
        return !Boolean.TRUE.equals(entitiesOnly.getValue());
    }
}
