package gq.yozakura.auth;

import gq.yozakura.auth.vendor.skidonion.sWdSl.VerificationPanel;
import gq.yozakura.auth.vendor.tech.skidonion.obfuscator.inline.Wrapper;

import java.awt.GraphicsEnvironment;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

public final class YozakuraAuthGate {
    private static final AtomicBoolean runtimeBlockedLogged = new AtomicBoolean();

    private YozakuraAuthGate() {
    }

    public static synchronized void verifyOrThrow(String environment) {
        if (Wrapper.isVerifiedSession()) {
            return;
        }
        if (!showVerification(environment)) {
            throw new IllegalStateException("Yozakura verification rejected");
        }
    }

    public static boolean allowRuntime(String surface) {
        if (Wrapper.isVerifiedSession()) {
            runtimeBlockedLogged.set(false);
            return true;
        }
        if (runtimeBlockedLogged.compareAndSet(false, true)) {
            log("Runtime blocked without verified session: " + surface, null);
        }
        return false;
    }

    public static void requireRuntime(String surface) {
        if (!allowRuntime(surface)) {
            throw new IllegalStateException("Yozakura runtime verification rejected: " + surface);
        }
    }

    private static boolean showVerification(String environment) {
        if (GraphicsEnvironment.isHeadless()) {
            log("Verification UI cannot open in a headless environment: " + environment, null);
            return false;
        }

        final AtomicReference<JFrame> frameRef = new AtomicReference<JFrame>();
        final AtomicReference<VerificationPanel> panelRef = new AtomicReference<VerificationPanel>();
        Runnable createUi = new Runnable() {
            @Override
            public void run() {
                JFrame frame = new JFrame("Yozakura Verification");
                VerificationPanel panel = new VerificationPanel(frame);
                frameRef.set(frame);
                panelRef.set(panel);
                frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
                frame.setContentPane(panel);
                frame.pack();
                frame.setResizable(false);
                frame.setAlwaysOnTop(true);
                frame.setLocationRelativeTo(null);
                frame.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(WindowEvent event) {
                        panel.cancel();
                        frame.dispose();
                    }
                });
                frame.setVisible(true);
            }
        };

        try {
            if (SwingUtilities.isEventDispatchThread()) {
                createUi.run();
            } else {
                SwingUtilities.invokeAndWait(createUi);
            }
            VerificationPanel panel = panelRef.get();
            if (panel == null) {
                return false;
            }
            int result = panel.waitForResult();
            dispose(frameRef.get());
            return result == 1 && Wrapper.isVerifiedSession();
        } catch (Throwable throwable) {
            log("Verification failed: " + environment, throwable);
            dispose(frameRef.get());
            return false;
        }
    }

    private static void dispose(final JFrame frame) {
        if (frame == null) {
            return;
        }
        Runnable task = new Runnable() {
            @Override
            public void run() {
                frame.dispose();
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    private static void log(String message, Throwable throwable) {
        try {
            File log = new File(System.getProperty("java.io.tmpdir"), "YozakuraAuth.log");
            PrintWriter writer = new PrintWriter(new FileWriter(log, true));
            try {
                writer.println(message);
                if (throwable != null) {
                    throwable.printStackTrace(writer);
                }
            } finally {
                writer.close();
            }
        } catch (Throwable ignored) {
        }
    }
}
