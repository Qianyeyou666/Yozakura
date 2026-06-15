package gq.yozakura.auth.vendor.skidonion.sWdSl;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

class PrivacyLinkMouseListener extends MouseAdapter {
    final VerificationPanel panel;
    PrivacyLinkMouseListener(VerificationPanel panel) { this.panel = panel; }
    public void mouseClicked(MouseEvent e) { VerificationPanel.onPrivacyClicked(panel, e); }
    public void mouseEntered(MouseEvent e) { VerificationPanel.onPrivacyEntered(panel, e); }
    public void mouseExited(MouseEvent e) { VerificationPanel.onPrivacyExited(panel, e); }
}

