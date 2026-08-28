package gq.yozakura.k.vendor.skidonion.sWdSl;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

class E extends KeyAdapter {
    final D panel;
    E(D panel) { this.panel = panel; }
    public void keyPressed(KeyEvent e) { D.onPasswordKeyPressed(panel, e); }
}

