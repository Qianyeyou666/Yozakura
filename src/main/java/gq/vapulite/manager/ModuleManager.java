package gq.vapulite.manager;

import gq.vapulite.module.ModuleType;
import gq.vapulite.module.config.IGN;
import gq.vapulite.module.config.LoadConfig;
import gq.vapulite.module.config.SaveConfig;
import gq.vapulite.module.config.Uninject;
import gq.vapulite.module.Module;
import gq.vapulite.module.movement.*;
import gq.vapulite.module.world.*;
import gq.vapulite.module.player.*;
import gq.vapulite.module.render.*;
import gq.vapulite.module.combat.*;

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
            File log = new File(System.getProperty("java.io.tmpdir"), "VapuLiteModuleInit.log");
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
        addModule("NoJumpDelay", new ModuleFactory() { public Module create() { return new NoJumpDelay(); } });
        addModule("ClickGUI", new ModuleFactory() { public Module create() { return new ClickGUI(); } });
        addModule("IGN", new ModuleFactory() { public Module create() { return new IGN(); } });
        addModule("StateMessage", new ModuleFactory() { public Module create() { return new StateMessage(); } });
        addModule("HUD", new ModuleFactory() { public Module create() { return new HUD(); } });
        addModule("TargetHUD", new ModuleFactory() { public Module create() { return new TargetHUD(); } });
        addModule("TargetESP", new ModuleFactory() { public Module create() { return new TargetESP(); } });
        addModule("KillEffect", new ModuleFactory() { public Module create() { return new KillEffect(); } });
        addModule("KeyboardDisplay", new ModuleFactory() { public Module create() { return new KeyboardDisplay(); } });
        addModule("FullBright", new ModuleFactory() { public Module create() { return new FullBright(); } });
        addModule("AutoTools", new ModuleFactory() { public Module create() { return new AutoTools(); } });
        addModule("InventoryManager", new ModuleFactory() { public Module create() { return new InventoryManager(); } });
        addModule("ChestStealer", new ModuleFactory() { public Module create() { return new ChestStealer(); } });
        addModule("IQBooster", new ModuleFactory() { public Module create() { return new IQBooster(); } });
        addModule("AutoClicker", new ModuleFactory() { public Module create() { return new AutoClicker(); } });
        addModule("FastPlace", new ModuleFactory() { public Module create() { return new FastPlace(); } });
        addModule("Scaffold", new ModuleFactory() { public Module create() { return new Scaffold(); } });
        addModule("Clutch", new ModuleFactory() { public Module create() { return new Clutch(); } });
        addModule("BridgeAssist", new ModuleFactory() { public Module create() { return new BridgeAssist(); } });
        addModule("LoadConfig", new ModuleFactory() { public Module create() { return new LoadConfig(); } });
        addModule("SaveConfig", new ModuleFactory() { public Module create() { return new SaveConfig(); } });
        addModule("Aimbot", new ModuleFactory() { public Module create() { return new Aimbot(); } });
        addModule("Backtrack", new ModuleFactory() { public Module create() { return new Backtrack(); } });
        addModule("Criticals", new ModuleFactory() { public Module create() { return new Criticals(); } });
        addModule("WTap", new ModuleFactory() { public Module create() { return new WTap(); } });
        addModule("BlockHit", new ModuleFactory() { public Module create() { return new BlockHit(); } });
        addModule("FakeLag", new ModuleFactory() { public Module create() { return new FakeLag(); } });
        addModule("KnockbackDelay", new ModuleFactory() { public Module create() { return new KnockbackDelay(); } });
        addModule("HitSelect", new ModuleFactory() { public Module create() { return new HitSelect(); } });
        addModule("Velocity", new ModuleFactory() { public Module create() { return new Velocity(); } });
        addModule("Uninject", new ModuleFactory() { public Module create() { return new Uninject(); } });
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
        addModule("StorageESP", new ModuleFactory() { public Module create() { return new StorageESP(); } });
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
