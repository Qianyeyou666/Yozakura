package gq.vapulite.Vapu.modules;

import gq.vapulite.Manager.NotificationManager;
import gq.vapulite.Vapu.ModuleType;
import gq.vapulite.Vapu.utils.Helper;
import gq.vapulite.Vapu.Client;
import gq.vapulite.Vapu.value.Value;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;

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
            if(Client.MessageON){
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

        if (playSound && Client.MessageON && !NoToggle) {
            NotificationManager.show(this.getName(), state ? "Enabled" : "Disabled", this);
        }
    }

    private void registerEvents() {
        if (!registered) {
            MinecraftForge.EVENT_BUS.register(this);
            FMLCommonHandler.instance().bus().register(this);
            registered = true;
        }
    }

    private void unregisterEvents() {
        if (registered) {
            MinecraftForge.EVENT_BUS.unregister(this);
            FMLCommonHandler.instance().bus().unregister(this);
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
        this.key = key;
    }

    public ModuleType getCategory() {
        return category;
    }

    public void setCategory(ModuleType category) {
        this.category = category;
    }

    public void onRenderWorldLast(RenderWorldLastEvent event) {
    }


    public String getDescription() {
        return Descript == null ? getDes() : Descript;
    }
}
