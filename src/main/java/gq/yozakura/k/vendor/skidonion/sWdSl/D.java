package gq.yozakura.k.vendor.skidonion.sWdSl;

import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.Random;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import gq.yozakura.k.A;

public class D extends JPanel {
    private static final String CACHE_DIR = "yozakura";
    private static final String CACHE_COMMENT = "local username cache";
    private static final ExecutorService LOGIN_EXECUTOR = Executors.newSingleThreadExecutor(D::newDaemonThread);

    private ResourceBundle bundle;
    private final JFrame frame;
    private final Object resultLock = new Object();
    private PipedInputStream resultInput;
    private PipedOutputStream resultOutput;
    private boolean resultWritten;
    private JLabel usernameLabel;
    private JTextField usernameField;
    private JLabel passwordLabel;
    private JPasswordField passwordField;
    private JButton loginButton;

    public D(JFrame frame) {
        this.frame = frame;
        openResultPipe();
        buildUi();
        loadCachedCredentials();
    }

    private void loadCachedCredentials() {
        String fileName = credentialFileName();
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(System.getProperty("user.home"), CACHE_DIR, fileName))) {
            Properties properties = new Properties();
            properties.load(reader);
            usernameField.setText(properties.getProperty("username", ""));
        } catch (Exception ex) {
        }
    }

    public int waitForResult() {
        if (resultInput == null) {
            return 0;
        }
        try {
            return resultInput.read();
        } catch (Exception ex) {
            return 0;
        }
    }

    public void cancel() {
        writeResult(0);
    }

    private void startLogin(ActionEvent event) {
        if (!loginButton.isEnabled()) {
            return;
        }
        String username = usernameField.getText();
        char[] passwordChars = passwordField.getPassword();
        loginButton.setEnabled(false);
        LOGIN_EXECUTOR.execute(() -> performLogin(username, passwordChars));
    }

    private void performLogin(String username, char[] passwordChars) {
        boolean loginAccepted = false;
        try {
            int code = A.login(username, passwordChars);
            if (code == 0) {
                loginAccepted = true;
                saveCredentials(username);
                writeResult(1);
                runOnEdt(() -> frame.dispose());
            } else {
                runOnEdt(() -> showLoginCode(code));
            }
        } catch (LinkageError error) {
            runOnEdt(() -> JOptionPane.showMessageDialog(this,
                    text("D.login.nativeError",
                            "The native verification component could not be loaded."),
                    "Yozakura", JOptionPane.ERROR_MESSAGE));
        } catch (Exception ex) {
            runOnEdt(() -> JOptionPane.showMessageDialog(this,
                    text("D.login.exception", "JVM Occurs a Error"),
                    "Yozakura", JOptionPane.ERROR_MESSAGE));
        } finally {
            java.util.Arrays.fill(passwordChars, '\0');
            final boolean clearPassword = loginAccepted;
            runOnEdt(() -> {
                if (clearPassword) {
                    passwordField.setText("");
                }
                if (loginButton != null) {
                    loginButton.setEnabled(true);
                }
            });
        }
    }

    private static void runOnEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }

    private void openResultPipe() {
        try {
            resultInput = new PipedInputStream();
            resultOutput = new PipedOutputStream(resultInput);
        } catch (IOException ignored) {
            resultInput = null;
            resultOutput = null;
        }
    }

    private void writeResult(int result) {
        synchronized (resultLock) {
            if (resultWritten) {
                return;
            }
            resultWritten = true;
            if (resultOutput == null) {
                return;
            }
            try {
                resultOutput.write(result);
                resultOutput.flush();
                resultOutput.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void clearCachedPasswordOnEdit(KeyEvent event) {
    }

    private void buildUi() {
        bundle = loadBundle();
        usernameLabel = new JLabel();
        usernameField = new JTextField();
        passwordLabel = new JLabel();
        passwordField = new JPasswordField();
        loginButton = new JButton();

        Font labelFont = new Font("Microsoft YaHei", Font.PLAIN, 14);
        Font inputFont = new Font("Microsoft YaHei UI", Font.PLAIN, 14);

        setFont(inputFont);
        setLayout(new GridBagLayout());

        GridBagLayout layout = (GridBagLayout) getLayout();
        layout.columnWidths = new int[] { 0, 245, 0 };
        layout.rowHeights = new int[] { 0, 0, 0, 0 };
        layout.columnWeights = new double[] { 0.0, 1.0, 1.0E-4 };
        layout.rowWeights = new double[] { 0.0, 0.0, 1.0E-4 };

        usernameLabel.setText(text("D.usernameLabel.text", "username"));
        usernameLabel.setFont(labelFont);
        usernameLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        add(usernameLabel, constraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(10, 10, 5, 5)));

        usernameField.setFont(inputFont);
        add(usernameField, constraints(1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(10, 0, 5, 10)));

        passwordLabel.setText(text("D.passwordLabel.text", "password"));
        passwordLabel.setFont(labelFont);
        passwordLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        add(passwordLabel, constraints(0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(0, 10, 5, 5)));

        passwordField.setFont(inputFont);
        passwordField.addKeyListener(new E(this));
        add(passwordField, constraints(1, 1, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 5, 10)));

        loginButton.setText(text("D.loginButton.text", "Login"));
        loginButton.setFont(inputFont);
        loginButton.addActionListener(this::startLogin);
        add(loginButton, constraints(1, 2, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(5, 0, 10, 10)));

    }

    private static Thread newDaemonThread(Runnable runnable) {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        return thread;
    }

    static void onPasswordKeyPressed(D panel, KeyEvent event) {
        panel.clearCachedPasswordOnEdit(event);
    }

    private static GridBagConstraints constraints(int gridx, int gridy, int gridwidth, int gridheight, double weightx, double weighty, int anchor, int fill, Insets insets) {
        return new GridBagConstraints(gridx, gridy, gridwidth, gridheight, weightx, weighty, anchor, fill, insets, 0, 0);
    }

    private static String credentialFileName() {
        Random random = new Random(183L * 1337L + 183L);
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder builder = new StringBuilder(".");
        for (int i = 0; i < 16; i++) {
            builder.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return builder.toString();
    }

    private void saveCredentials(String username) throws IOException {
        Path dir = Paths.get(System.getProperty("user.home"), CACHE_DIR);
        Files.createDirectories(dir);
        try (BufferedWriter writer = Files.newBufferedWriter(dir.resolve(credentialFileName()))) {
            Properties properties = new Properties();
            properties.setProperty("username", username);
            properties.store(writer, CACHE_COMMENT);
        }
    }

    private void showLoginCode(int code) {
        String key = "D.login.code." + code;
        JOptionPane.showMessageDialog(this, text(key, key), "Yozakura", JOptionPane.ERROR_MESSAGE);
    }

    private ResourceBundle loadBundle() {
        try {
            return ResourceBundle.getBundle("gq.yozakura.k.vendor.tech.skidonion.verification.lang");
        } catch (MissingResourceException ex) {
            return null;
        }
    }

    private String text(String key, String fallback) {
        if (bundle == null) {
            return fallback;
        }
        try {
            return bundle.getString(key);
        } catch (MissingResourceException ex) {
            return fallback;
        }
    }
}
