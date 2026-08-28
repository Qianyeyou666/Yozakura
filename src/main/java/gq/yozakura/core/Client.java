package gq.yozakura.core;

import gq.yozakura.manager.FileManager;
import gq.yozakura.manager.NotificationManager;
import gq.yozakura.module.Module;
import gq.yozakura.manager.ModuleManager;
import gq.yozakura.ui.click.web.WebClickGuiService;
import gq.yozakura.ui.click.yozakura.PanelModuleKeybind;
import gq.yozakura.util.color.ColorUtils;
import gq.yozakura.util.minecraft.Helper;
import gq.yozakura.command.Bind;
import gq.yozakura.command.ChatBypassCommand;
//import gq.yozakura.command.Report;
import gq.yozakura.command.WaterMark;
import gq.yozakura.engine.font.FontLoaders;
import gq.yozakura.bridge.YozakuraEventBridge;
import gq.yozakura.k.B;
import gq.yozakura.k.t.F;
import gq.yozakura.ui.overlay.InjectionSuccessAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Random;

import static gq.yozakura.util.minecraft.Helper.mc;

public class Client {
    public static String name = "Yozakura";
    public static String username = "YozakuraUser";
    public static String version = "1.52";
    public static String config = "module";

    public static int Theme = new Color(0, 156, 161, 255).getRGB();
    public static int ThemeR = 0;
    public static int ThemeG = 156;
    public static int ThemeB = 161;

    public static boolean MessageON = true;
    public static boolean StringBigSnakeDetection = false;
    public static boolean AutoBlock = false;
    public static boolean ChatBypass = false;
    public static boolean FluxTheme = false;
    public static boolean CHINESE = false;
    public static Client instance;
    public static boolean state = false;
    private static boolean shutdownHookRegistered;
    private F tokenAuthGuiHandler;
    private boolean mainMenuClickGuiKeyDown;
    public static Random rand=new Random();
    public final FileManager fileManager = new FileManager();
    public static ModuleManager moduleManager = new ModuleManager();
    public List<String> faList = new ArrayList<String>();
    public void updateFA() {
        if(Module.mc.theWorld==null || Module.mc.thePlayer==null) {
            faList.clear();
            return;
        }
        if (faList.size()>0) {
            String msg = faList.remove(0);
            Module.mc.thePlayer.sendChatMessage(msg);
        }
    }
    public Client() throws IOException {
        if (state) {
            showInjectionSuccessAnimation();
            return;
        }
        B.verifyOrThrow("forge");
        username = B.getVerifiedUsername();
        state = true;
        MinecraftForge.EVENT_BUS.register(this);
        tokenAuthGuiHandler = new F();
        MinecraftForge.EVENT_BUS.register(tokenAuthGuiHandler);
        FMLCommonHandler.instance().bus().register(this);
        YozakuraEventBridge.initBridge();
        instance = this;
        CommandInit();
        loadConfigOnStartup();
        registerShutdownHook();
        showInjectionSuccessAnimation();
//        FontLoaders.C20.drawStringWithShadow(Client.name,114514,114514, -1);
//        FontLoaders.F14.drawStringWithShadow(Client.name,114514,114514, -1);
//        FontLoaders.Logo.drawStringWithShadow(Client.name,114514,114514, -1);
        if(mc.isIntegratedServerRunning() || mc.isSingleplayer()){
            Helper.sendMessageWithoutPrefix("Yozakura Load done! Press RSHIFT open ClickGui, Press H Open HUD");
        }
    }

    public static void showInjectionSuccessAnimation() {
        InjectionSuccessAnimation.show();
    }


    private void CommandInit() {
//        ClientCommandHandler.instance.registerCommand(new ChatBypassCommand(Client.instance));
//        ClientCommandHandler.instance.registerCommand(new Report(Client.instance));
        ClientCommandHandler.instance.registerCommand(new Bind(Client.instance));
        ClientCommandHandler.instance.registerCommand(new WaterMark(Client.instance));
    }

    @SubscribeEvent
    public void keyInput(InputEvent.KeyInputEvent event) {
        if (Keyboard.isRepeatEvent()) {
            return;
        }
        boolean pressed = Keyboard.getEventKeyState();
        int key = Keyboard.getEventKey();
        if (key == Keyboard.KEY_NONE) {
            return;
        }
        if (pressed && ClickGuiKeyDispatcher.handleKeyPress(key, mc.currentScreen)) {
            mainMenuClickGuiKeyDown = true;
            return;
        }
        // Other module presses are ignored while a screen is open, but releases
        // still reach HOLD modules so they never stay stuck enabled.
        if (pressed && mc.currentScreen != null) {
            return;
        }
        applyModuleBindInput(key, pressed);
    }

    @SubscribeEvent
    public void mouseInput(InputEvent.MouseInputEvent event) {
        int button = Mouse.getEventButton();
        if (button < 0) {
            return;
        }
        boolean pressed = Mouse.getEventButtonState();
        if (pressed && mc.currentScreen != null) {
            return;
        }
        applyModuleBindInput(PanelModuleKeybind.encodeMouseButton(button), pressed);
    }

    private void applyModuleBindInput(int key, boolean pressed) {
        for (Module module : moduleManager.getModules()) {
            if (module.getKey() != key) {
                continue;
            }
            if (module.getBindMode() == Module.BindMode.HOLD) {
                module.setState(pressed);
            } else if (pressed) {
                module.toggle();
            }
            break;
        }
    }

    public static void SaveConfig() throws IOException {
        ConfigBridge.saveModules();
    }

    public static void LoadConfig() throws IOException {
        ConfigBridge.loadModules();
    }

    public static void markConfigDirty() {
        ConfigBridge.markDirty();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        fileManager.autoSaveTick();
        pollMainMenuClickGuiBind();
    }

    private void pollMainMenuClickGuiBind() {
        if (!(mc.currentScreen instanceof net.minecraft.client.gui.GuiMainMenu)
                || !Keyboard.isCreated()) {
            mainMenuClickGuiKeyDown = false;
            return;
        }
        Module clickGui = ModuleManager.getModule("ClickGUI");
        int key = clickGui == null ? Keyboard.KEY_NONE : clickGui.getKey();
        boolean down = key != Keyboard.KEY_NONE && Keyboard.isKeyDown(key);
        if (down && !mainMenuClickGuiKeyDown) {
            ClickGuiKeyDispatcher.handleKeyPress(key, mc.currentScreen);
        }
        mainMenuClickGuiKeyDown = down;
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        fileManager.saveIfDirtyQuietly();
        faList.clear();
    }

    public static void unInject() {
        state = false;
        Client client = instance;
        try {
            if (client != null) {
                client.fileManager.saveIfDirtyQuietly();
                client.fileManager.setAutoSaveSuspended(true);
                try {
                    ModuleManager.disableAll(false);
                } finally {
                    client.fileManager.setAutoSaveSuspended(false);
                }
            }
        } finally {
            try {
                YozakuraEventBridge.shutdownBridge();
            } finally {
                try {
                    if (client != null) {
                        unregisterClient(client);
                    }
                } finally {
                    instance = null;
                }
            }
        }
    }

    private static void unregisterClient(Client client) {
        try {
            if (client.tokenAuthGuiHandler != null) {
                MinecraftForge.EVENT_BUS.unregister(client.tokenAuthGuiHandler);
                client.tokenAuthGuiHandler = null;
            }
        } finally {
            try {
                MinecraftForge.EVENT_BUS.unregister(client);
            } finally {
                try {
                    FMLCommonHandler.instance().bus().unregister(client);
                } finally {
                    WebClickGuiService.stop();
                }
            }
        }
    }

    private void loadConfigOnStartup() {
        try {
            fileManager.loadModules(true);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private void registerShutdownHook() {
        if (shutdownHookRegistered) {
            return;
        }
        shutdownHookRegistered = true;
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                if (Client.instance != null) {
                    Client.instance.fileManager.saveModulesQuietly();
                }
            }
        }, "Yozakura Config Save"));
    }

}
