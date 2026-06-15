package gq.yozakura.auth.vendor.skidonion.sWdSl;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

class TermsLinkMouseListener extends MouseAdapter {
    final VerificationPanel panel;
    TermsLinkMouseListener(VerificationPanel panel) { this.panel = panel; }
    public void mouseClicked(MouseEvent e) { VerificationPanel.onTermsClicked(panel, e); }
    public void mouseEntered(MouseEvent e) { VerificationPanel.onTermsEntered(panel, e); }
    public void mouseExited(MouseEvent e) { VerificationPanel.onTermsExited(panel, e); }
}

