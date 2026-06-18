package gq.yozakura.runtime;

import gq.yozakura.manager.ModuleManager;
import gq.yozakura.event.bus.EventManager;
import gq.yozakura.manager.BlinkManager;
import gq.yozakura.manager.PlayerStateManager;
import gq.yozakura.manager.RotationManager;
import gq.yozakura.module.runtime.Module;
import gq.yozakura.module.world.BedNuker;
import gq.yozakura.module.movement.KeepSprint;
import gq.yozakura.module.movement.LongJump;
import gq.yozakura.module.render.runtime.HUD;

import java.util.LinkedHashMap;
import java.util.Map;

public final class YozakuraRuntime {
    public static final RotationManager rotationManager = new RotationManager();
    public static final BlinkManager blinkManager = new BlinkManager();
    public static final PlayerStateManager playerStateManager = new PlayerStateManager();
    public static final FriendManager friendManager = new FriendManager();
    public static final TargetManager targetManager = new TargetManager();
    public static final ModuleManagerBridge moduleManager = new ModuleManagerBridge();
    private static boolean registered;

    private YozakuraRuntime() {
    }

    public static void init() {
        if (registered) {
            return;
        }
        registered = true;
        EventManager.register(rotationManager);
        EventManager.register(blinkManager);
        EventManager.register(playerStateManager);
    }

    public static final class ModuleManagerBridge {
        public final ModuleMap modules = new ModuleMap();

        public Module getModule(Class<?> clazz) {
            return modules.get(clazz);
        }

        public void playSound() {
        }
    }

    public static final class ModuleMap extends LinkedHashMap<Class<?>, Module> {
        private final Map<Class<?>, Module> stubs = new LinkedHashMap<Class<?>, Module>();

        @Override
        public Module get(Object key) {
            if (!(key instanceof Class<?>)) {
                return super.get(key);
            }
            Class<?> clazz = (Class<?>) key;
            Module direct = super.get(clazz);
            if (direct != null) {
                return direct;
            }
            for (gq.yozakura.module.Module module : ModuleManager.getModules()) {
                if (clazz.isInstance(module) && module instanceof Module) {
                    Module runtimeModule = (Module) module;
                    super.put(clazz, runtimeModule);
                    return runtimeModule;
                }
            }
            Module stub = stubs.get(clazz);
            if (stub == null) {
                stub = createStub(clazz);
                stubs.put(clazz, stub);
            }
            return stub;
        }

        private Module createStub(Class<?> clazz) {
            if (clazz == HUD.class) {
                return new HUD();
            }
            if (clazz == BedNuker.class) {
                return new BedNuker();
            }
            if (clazz == LongJump.class) {
                return new LongJump();
            }
            if (clazz == KeepSprint.class) {
                return new KeepSprint();
            }
            try {
                Object object = clazz.getDeclaredConstructor().newInstance();
                if (object instanceof Module) {
                    return (Module) object;
                }
            } catch (Throwable ignored) {
            }
            return new Module(clazz.getSimpleName(), false) {
            };
        }
    }

    public static final class FriendManager {
        public boolean isFriend(String name) {
            return false;
        }
    }

    public static final class TargetManager {
        public boolean isFriend(String name) {
            return false;
        }
    }
}
