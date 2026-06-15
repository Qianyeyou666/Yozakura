package gq.yozakura.auth.vendor.skidonion.sWdSl;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

class PasswordFieldKeyListener extends KeyAdapter {
    final VerificationPanel panel;
    PasswordFieldKeyListener(VerificationPanel panel) { this.panel = panel; }
    public void keyPressed(KeyEvent e) { VerificationPanel.onPasswordKeyPressed(panel, e); }
}

