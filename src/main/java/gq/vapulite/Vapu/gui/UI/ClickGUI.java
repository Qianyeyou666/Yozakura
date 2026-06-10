package gq.vapulite.Vapu.gui.UI;

import gq.vapulite.Vapu.Client;
import gq.vapulite.Vapu.ModuleType;
import gq.vapulite.Manager.ModuleManager;
import gq.vapulite.font.FontLoaders;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;

import java.awt.*;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class ClickGUI extends GuiScreen {

    private List<Panel> panels = new ArrayList<>();

    public ClickGUI() {
        int x = 20;

        for(final ModuleType moduleCategory : ModuleType.values()) {
            final Panel panel = new Panel(moduleCategory.getName(), x, 50, 100);
            ModuleManager.getModules().stream().filter(module -> module.getCategory() == moduleCategory).forEach(module -> panel.addButton(new Button(panel, module)));
            panels.add(panel);
            x = x + 110;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // DO NOT DELETE THIS!
        FontLoaders.C14.drawString(Client.name + Client.version + " Author: Ceeyourbac & mckuhei Credits: Medit_4",
                5, this.height - 20, new Color(255, 255, 255).hashCode());
        FontLoaders.C14.drawString("Vapulite is a free client, if you pay any money for it mean you got deceived.",
                5, this.height - 10, new Color(255, 0, 0).hashCode());
        for(final Panel panel : panels) {
            Gui.drawRect(panel.getX(), panel.getY(), panel.getX() + panel.getWidth(), panel.getY() + 20, new Color(8, 85, 204).hashCode());
            FontLoaders.C14.drawString(panel.getPanelName(), panel.getX() + 5, panel.getY() + 5, 0xffffff);

            for(int i = 0; i < panel.getButtons().size(); i++) {
                final Button button = panel.getButtons().get(i);
                Gui.drawRect(panel.getX(), panel.getY() + 20 + (20 * i), panel.getX() + panel.getWidth(), panel.getY() + (20 * i) + 40, Integer.MIN_VALUE);
                if(Client.StringBigSnakeDetection){
                    try {
                        FontLoaders.C14.drawString((button.getModule().getState() ? "§a" : "") + Base64.getEncoder().encodeToString(button.getModule().getName().getBytes("utf-8")), panel.getX() + 2, panel.getY() + 20 + (20 * i) + 7, 0xffffff);
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                } else {
                    FontLoaders.C14.drawString((button.getModule().getState() ? "§a" : "") + button.getModule().getName(), panel.getX() + 2, panel.getY() + 20 + (20 * i) + 7, 0xffffff);
                }
            }

            if(panel.isDrag()) {
                panel.setX(mouseX + panel.getDragX());
                panel.setY(mouseY + panel.getDragY());
            }
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if(mouseButton == 0) {
            for(int index = panels.size() - 1; index >= 0; index--) {
                final Panel panel = panels.get(index);


                if(panel.isHoverHead(mouseX, mouseY)) {
                    panel.setDrag(true);
                    panel.setDragX(panel.getX() - mouseX);
                    panel.setDragY(panel.getY() - mouseY);
                    panels.remove(panel);
                    panels.add(panel);
                    break;
                }


                for(int buttonIndex = 0; buttonIndex < panel.getButtons().size(); buttonIndex++) {
                    final Button button = panel.getButtons().get(buttonIndex);

                    if(button.isHover(panel.getX(),panel.getY() + 20 + (20 * buttonIndex), panel.getWidth(), 20, mouseX, mouseY)) {
                        button.getModule().setState(!button.getModule().getState());
                    }
                }
            }
        } else if(mouseButton == 1){
            for(int index = panels.size() - 1; index >= 0; index--) {
                final Panel panel = panels.get(index);


                if(panel.isHoverHead(mouseX, mouseY)) {
                    panel.setDrag(true);
                    panel.setDragX(panel.getX() - mouseX);
                    panel.setDragY(panel.getY() - mouseY);
                    panels.remove(panel);
                    panels.add(panel);
                    break;
                }


                for(int buttonIndex = 0; buttonIndex < panel.getButtons().size(); buttonIndex++) {
                    final Button button = panel.getButtons().get(buttonIndex);

                    if(button.isHover(panel.getX(),panel.getY() + 20 + (20 * buttonIndex), panel.getWidth(), 20, mouseX, mouseY)) {
                        mc.displayGuiScreen(new BindClickGui(button.getModule()));
                    }
                }
            }
        }


        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        for(final Panel panel : panels)
            panel.setDrag(false);
        super.mouseReleased(mouseX, mouseY, state);
    }
}
