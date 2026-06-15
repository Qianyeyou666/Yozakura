package gq.yozakura.auth.vendor.skidonion.sWdSl;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

class RegisterLinkMouseListener extends MouseAdapter {
    final VerificationPanel panel;
    RegisterLinkMouseListener(VerificationPanel panel) { this.panel = panel; }
    public void mouseClicked(MouseEvent e) { VerificationPanel.onRegisterClicked(panel, e); }
    public void mouseEntered(MouseEvent e) { VerificationPanel.onRegisterEntered(panel, e); }
    public void mouseExited(MouseEvent e) { VerificationPanel.onRegisterExited(panel, e); }
}

