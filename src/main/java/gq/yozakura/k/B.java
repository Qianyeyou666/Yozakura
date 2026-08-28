package gq.yozakura.k;

import gq.yozakura.k.vendor.skidonion.sWdSl.D;

import java.awt.GraphicsEnvironment;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

public final class B {
    // Release builds require native verification before any runtime channel is permitted.
    private static final boolean CLIENT_VERIFICATION_ENABLED = true;
    private static final String LOCAL_USERNAME = "YozakuraUser";

    private B() {
    }

    public static synchronized void verifyOrThrow(String environment) {
        if (!CLIENT_VERIFICATION_ENABLED) {
            return;
        }
        if (A.permitStartup()) {
            return;
        }
        if (!showVerification(environment)) {
            throw new IllegalStateException("Yozakura verification rejected");
        }
    }

    public static String getVerifiedUsername() {
        if (!CLIENT_VERIFICATION_ENABLED) {
            return LOCAL_USERNAME;
        }
        return A.getVerifiedUsername();
    }

    public static String getVerifiedSessionProof() {
        return CLIENT_VERIFICATION_ENABLED
                ? A.getVerifiedSessionProof() : null;
    }

    public static boolean permitModuleActivation() {
        return !CLIENT_VERIFICATION_ENABLED || A.permitModuleActivation();
    }

    public static boolean permitTickDispatch() {
        return !CLIENT_VERIFICATION_ENABLED || A.permitTickDispatch();
    }

    public static boolean permitRenderDispatch() {
        return !CLIENT_VERIFICATION_ENABLED || A.permitRenderDispatch();
    }

    public static boolean permitInputDispatch() {
        return !CLIENT_VERIFICATION_ENABLED || A.permitInputDispatch();
    }

    public static boolean permitPacketDispatch() {
        return !CLIENT_VERIFICATION_ENABLED || A.permitPacketDispatch();
    }

    public static boolean permitEventDispatch() {
        return !CLIENT_VERIFICATION_ENABLED || A.permitEventDispatch();
    }

    public static boolean permitMovementDispatch() {
        return !CLIENT_VERIFICATION_ENABLED || A.permitMovementDispatch();
    }

    private static boolean showVerification(String environment) {
        if (GraphicsEnvironment.isHeadless()) {
            log("Verification UI cannot open in a headless environment: " + environment, null);
            return false;
        }

        final AtomicReference<JFrame> frameRef = new AtomicReference<JFrame>();
        final AtomicReference<D> panelRef = new AtomicReference<D>();
        Runnable createUi = new Runnable() {
            @Override
            public void run() {
                JFrame frame = new JFrame("Yozakura Verification");
                D panel = new D(frame);
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
            D panel = panelRef.get();
            if (panel == null) {
                return false;
            }
            int result = panel.waitForResult();
            dispose(frameRef.get());
            return result == 1 && A.permitStartup();
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
