package gq.yozakura.manager;

import gq.yozakura.module.ModuleType;
import gq.yozakura.module.config.ConfigProfiles;
import gq.yozakura.module.Module;
import gq.yozakura.module.movement.*;
import gq.yozakura.module.world.*;
import gq.yozakura.module.player.*;
import gq.yozakura.module.render.*;
import gq.yozakura.module.combat.*;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ModuleManager {

    static ArrayList<Module> Modules = new ArrayList<Module>();

    public static ArrayList<Module> getModules() {
        return Modules;
    }

    public ModuleManager() {

    }

    public static Module getModule(String name) {
        String target = normalize(name);
        for (Module m : Modules) {
            if (normalize(m.getName()).equals(target))
                return m;
        }
        return null;
    }

    public static List<Module> getModulesInType(ModuleType t) {
        ArrayList<Module> output = new ArrayList<Module>();
        for (Module m : Modules) {
            if (m.getCategory() != t) continue;
            output.add(m);
        }
        output.sort(Comparator.comparingInt((Module o) -> {
            String name = o.getName();
            return name == null || name.isEmpty() ? 0 : Character.toLowerCase(name.charAt(0));
        }).thenComparingInt(o -> {
            String name = o.getName();
            return name == null || name.isEmpty() ? 0 : name.charAt(0);
        }));
        return output;
    }

    public static List<Module> getEnabledModules() {
        ArrayList<Module> output = new ArrayList<Module>();
        for (Module module : Modules) {
            if (module.getState()) {
                output.add(module);
            }
        }
        output.sort(Comparator.comparingInt((Module o) -> {
            String name = o.getName();
            return name == null || name.isEmpty() ? 0 : Character.toLowerCase(name.charAt(0));
        }).thenComparingInt(o -> {
            String name = o.getName();
            return name == null || name.isEmpty() ? 0 : name.charAt(0);
        }));
        return output;
    }

    public static void disableAll(boolean playSound) {
        for (Module module : new ArrayList<Module>(Modules)) {
            if (module.getState()) {
                module.setState(false, playSound);
            }
        }
    }

    private static String normalize(String name) {
        return name == null ? "" : name.replace(" ", "").replace("_", "").toLowerCase();
    }

    private interface ModuleFactory {
        Module create();
    }

    private static void addModule(String name, ModuleFactory factory) {
        try {
            Module module = factory.create();
            if (module != null) {
                Modules.add(module);
            }
        } catch (Throwable throwable) {
            logModuleInitFailure(name, throwable);
        }
    }

    private static void logModuleInitFailure(String name, Throwable throwable) {
        try {
            File log = new File(System.getProperty("java.io.tmpdir"), "YozakuraModuleInit.log");
            PrintWriter writer = new PrintWriter(new FileWriter(log, true));
            try {
                writer.println("Failed to initialize module: " + name);
                throwable.printStackTrace(writer);
            } finally {
                writer.close();
            }
        } catch (Throwable ignored) {
        }
    }

    static {
        // 没Add的都是有问题的，不要add
        addModule("AntiBot", new ModuleFactory() { public Module create() { return new AntiBot(); } });
        addModule("Speed", new ModuleFactory() { public Module create() { return new Speed(); } });
        addModule("Sprint", new ModuleFactory() { public Module create() { return new Sprint(); } });
        addModule("KeepSprint", new ModuleFactory() { public Module create() { return new KeepSprint(); } });
        addModule("NoJumpDelay", new ModuleFactory() { public Module create() { return new NoJumpDelay(); } });
        addModule("ClickGUI", new ModuleFactory() { public Module create() { return new ClickGUI(); } });
        addModule("HUD", new ModuleFactory() { public Module create() { return new HUD(); } });
        addModule("HotBar", new ModuleFactory() { public Module create() { return new HotBar(); } });
        addModule("InventoryAnimation", new ModuleFactory() { public Module create() { return new InventoryAnimation(); } });
        addModule("DamageNumbers", new ModuleFactory() { public Module create() { return new DamageNumbers(); } });
        addModule("NoSprintFOV", new ModuleFactory() { public Module create() { return new NoSprintFOV(); } });
        addModule("TargetHUD", new ModuleFactory() { public Module create() { return new TargetHUD(); } });
        addModule("TargetESP", new ModuleFactory() { public Module create() { return new TargetESP(); } });
        addModule("NameTags", new ModuleFactory() { public Module create() { return new NameTags(); } });
        addModule("KillEffect", new ModuleFactory() { public Module create() { return new KillEffect(); } });
        addModule("KeyboardDisplay", new ModuleFactory() { public Module create() { return new KeyboardDisplay(); } });
        addModule("MiningProgress", new ModuleFactory() { public Module create() { return new MiningProgress(); } });
        addModule("ProjectileRay", new ModuleFactory() { public Module create() { return new ProjectileRay(); } });
        addModule("ProjectileWarning", new ModuleFactory() { public Module create() { return new ProjectileWarning(); } });
        addModule("BlinkSettings", new ModuleFactory() { public Module create() { return new BlinkSettings(); } });
//        addModule("MusicPlayer", new ModuleFactory() { public Module create() { return new MusicPlayer(); } });
        addModule("FullBright", new ModuleFactory() { public Module create() { return new FullBright(); } });
        addModule("FreeLook", new ModuleFactory() { public Module create() { return new FreeLook(); } });
        addModule("AutoTools", new ModuleFactory() { public Module create() { return new AutoTools(); } });
        addModule("InventoryManager", new ModuleFactory() { public Module create() { return new InventoryManager(); } });
        addModule("ChestStealer", new ModuleFactory() { public Module create() { return new ChestStealer(); } });
        addModule("IQBooster", new ModuleFactory() { public Module create() { return new IQBooster(); } });
        addModule("AutoClicker", new ModuleFactory() { public Module create() { return new AutoClicker(); } });
        addModule("FastPlace", new ModuleFactory() { public Module create() { return new FastPlace(); } });
        addModule("SpeedMine", new ModuleFactory() { public Module create() { return new SpeedMine(); } });
        addModule("Scaffold", new ModuleFactory() { public Module create() { return new Scaffold(); } });
        addModule("Clutch", new ModuleFactory() { public Module create() { return new Clutch(); } });
        addModule("BridgeAssist", new ModuleFactory() { public Module create() { return new BridgeAssist(); } });
        addModule("AutoDefense", new ModuleFactory() { public Module create() { return new AutoDefense(); } });
        addModule("cfgmanager", new ModuleFactory() { public Module create() { return new ConfigProfiles(); } });
        addModule("Aimbot", new ModuleFactory() { public Module create() { return new Aimbot(); } });
        addModule("Backtrack", new ModuleFactory() { public Module create() { return new Backtrack(); } });
        addModule("Criticals", new ModuleFactory() { public Module create() { return new Criticals(); } });
        addModule("WTap", new ModuleFactory() { public Module create() { return new WTap(); } });
        addModule("Displace", new ModuleFactory() { public Module create() { return new Displace(); } });
        addModule("AutoBlock", new ModuleFactory() { public Module create() { return new AutoBlock(); } });
        addModule("BlockHit", new ModuleFactory() { public Module create() { return new BlockHit(); } });
        addModule("FakeLag", new ModuleFactory() { public Module create() { return new FakeLag(); } });
        addModule("LagRange", new ModuleFactory() { public Module create() { return new LagRange(); } });
        addModule("KnockbackDelay", new ModuleFactory() { public Module create() { return new KnockbackDelay(); } });
        addModule("HitSelect", new ModuleFactory() { public Module create() { return new HitSelect(); } });
        addModule("Velocity", new ModuleFactory() { public Module create() { return new Velocity(); } });
        addModule("JumpReset", new ModuleFactory() { public Module create() { return new JumpReset(); } });
        addModule("InvMove", new ModuleFactory() { public Module create() { return new InvMove(); } });
        addModule("Health", new ModuleFactory() { public Module create() { return new Health(); } });
        addModule("KillAura", new ModuleFactory() { public Module create() { return new KillAura(); } });
        addModule("BowAimBot", new ModuleFactory() { public Module create() { return new BowAimBot(); } });
        addModule("NoFall", new ModuleFactory() { public Module create() { return new NoFall(); } });
        addModule("NoSlowDown", new ModuleFactory() { public Module create() { return new NoSlowDown(); } });
        addModule("MurderMystery", new ModuleFactory() { public Module create() { return new MurderMystery(); } });
        addModule("FuckServer", new ModuleFactory() { public Module create() { return new FuckServer(); } });
        addModule("Reach", new ModuleFactory() { public Module create() { return new Reach(); } });
        addModule("HitBoxes", new ModuleFactory() { public Module create() { return new HitBoxes(); } });
        addModule("GhostHand", new ModuleFactory() { public Module create() { return new GhostHand(); } });
        addModule("StorageESP", new ModuleFactory() { public Module create() { return new StorageESP(); } });
        addModule("BedESP", new ModuleFactory() { public Module create() { return new BedESP(); } });
        addModule("ItemESP", new ModuleFactory() { public Module create() { return new ItemESP(); } });
        addModule("BedNuker", new ModuleFactory() { public Module create() { return new BedNuker(); } });
        addModule("Chams", new ModuleFactory() { public Module create() { return new Chams(); } });
        addModule("ESP", new ModuleFactory() { public Module create() { return new ESP(); } });
//        addModule("Test", new ModuleFactory() {
//            @Override
//            public Module create() {
//                return new Test();
//            }
//        });
    }
}
