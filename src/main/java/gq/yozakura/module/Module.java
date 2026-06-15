package gq.yozakura.module;

import gq.yozakura.auth.YozakuraAuthGate;
import gq.yozakura.bridge.ForgeEnvironment;
import gq.yozakura.core.YozakuraClientState;
import gq.yozakura.event.bus.EventManager;
import gq.yozakura.manager.NotificationManager;
import gq.yozakura.module.ModuleType;
import gq.yozakura.util.minecraft.Helper;
import gq.yozakura.value.Value;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

public class Module {
    public static final Minecraft mc = Minecraft.getMinecraft();
    public boolean state = false;
    public int key;
    public List<Value> values = new ArrayList<>();
    public String Chinese;
    public String About;
    public boolean NoToggle = false;
    public String name;
    public String Descript;
    public ModuleType category;
    public float optionAnim = 0;// present
    public float optionAnimNow = 0;// present
    private boolean registered;
    private boolean customEventRegistered;
    private boolean forgeEventRegistered;

    public Module(String name, int key, ModuleType category, String Descript) {
        this.name = name;
        this.key = key;
        this.category = category;
        this.Descript = Descript;
        this.Chinese = name;
        this.About = Descript;
    }
    public Module(String name, int key, ModuleType category) {
        this.name = name;
        this.key = key;
        this.category = category;
        this.Descript = "no desc";
        this.Chinese = name;
        this.About = this.Descript;
    }
    protected void addValues(Value... values) {
        Value[] var5 = values;
        int var4 = values.length;

        for (int var3 = 0; var3 < var4; ++var3) {
            Value value = var5[var3];
            this.values.add(value);
        }

    }

    public List<Value> getValues() {
        return this.values;
    }



    public void toggle() {
        if(NoToggle){
            if(YozakuraClientState.isMessageOn()){
                if (this.state) {
                    Helper.sendMessage("Module" + " "+ this.getName() + " Disabled");
                } else {
                    Helper.sendMessage("Module" + " " + this.getName() + " Enabled");
                }
            }
        }
        this.setState(!this.state);
    }

    public void setState(boolean state) {
        setState(state, true);
    }

    public void setState(boolean state, boolean playSound) {
        if (this.state == state && !NoToggle) {
            return;
        }
        if (state && !YozakuraAuthGate.allowRuntime("module:" + getName())) {
            return;
        }
        if (state) {
            this.state = true;
            registerEvents();
            try {
                enable();
            } finally {
                if (NoToggle) {
                    this.state = false;
                    unregisterEvents();
                }
            }
        } else {
            this.state = false;
            try {
                disable();
            } finally {
                unregisterEvents();
            }
        }

        if (playSound && YozakuraClientState.isMessageOn() && !NoToggle) {
            NotificationManager.show(this.getName(), state ? "Enabled" : "Disabled", this);
        }
        if (!NoToggle) {
            YozakuraClientState.markConfigDirty();
        }
    }

    private void registerEvents() {
        if (!registered) {
            try {
                EventManager.register(this);
                customEventRegistered = true;
            } catch (Throwable throwable) {
                customEventRegistered = false;
            }
            forgeEventRegistered = ForgeEnvironment.isForgeAvailable() && ForgeEnvironment.register(this);
            registered = customEventRegistered || forgeEventRegistered;
        }
    }

    private void unregisterEvents() {
        if (registered) {
            if (forgeEventRegistered && ForgeEnvironment.isForgeAvailable()) {
                ForgeEnvironment.unregister(this);
            }
            if (customEventRegistered) {
                EventManager.unregister(this);
            }
            forgeEventRegistered = false;
            customEventRegistered = false;
            registered = false;
        }
    }

    public void enable() {

    }

    public void disable() {

    }

    protected boolean isInGame() {
        return mc.thePlayer != null && mc.theWorld != null;
    }

    public String getName() {
        return name;
    }

    public String getDes() {
        return About == null ? Descript : About;
    }

    public String getChinese() {
        return Chinese == null ? name : Chinese;
    }

    public int getKey() {
        return key;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean getState() {
        return state;
    }

    public void setKey(int key) {
        if (this.key == key) {
            return;
        }
        this.key = key;
        YozakuraClientState.markConfigDirty();
    }

    public ModuleType getCategory() {
        return category;
    }

    public void setCategory(ModuleType category) {
        this.category = category;
    }

    public void onRenderWorldLast(Object event) {
    }


    public String getDescription() {
        return Descript == null ? getDes() : Descript;
    }
}
