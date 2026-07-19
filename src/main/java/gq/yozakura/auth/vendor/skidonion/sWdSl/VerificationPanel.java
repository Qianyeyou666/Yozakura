package gq.yozakura.auth.vendor.skidonion.sWdSl;

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
import gq.yozakura.auth.NativeAuthBridge;

public class VerificationPanel extends JPanel {
    private static final String CACHE_DIR = "yozakura";
    private static final String CACHE_COMMENT = "local username cache";
    private static final ExecutorService LOGIN_EXECUTOR = Executors.newSingleThreadExecutor(VerificationPanel::newDaemonThread);

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
    private JTextField licenseField;
    private JButton redeemButton;
    private JLabel registerUsernameLabel;
    private JTextField registerUsernameField;
    private JLabel registerPasswordLabel;
    private JPasswordField registerPasswordField;

    public VerificationPanel(JFrame frame) {
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
        loginButton.setEnabled(false);
        LOGIN_EXECUTOR.submit(this::performLogin);
    }

    private void startRedeem(ActionEvent event) {
        if (!redeemButton.isEnabled() || !loginButton.isEnabled()) {
            return;
        }
        redeemButton.setEnabled(false);
        loginButton.setEnabled(false);
        LOGIN_EXECUTOR.submit(this::performRedeem);
    }

    private void performRedeem() {
        char[] passwordChars = registerPasswordField.getPassword();
        try {
            int code = NativeAuthBridge.redeemLicense(
                    licenseField.getText(), registerUsernameField.getText(), passwordChars);
            if (code == 0) {
                usernameField.setText(registerUsernameField.getText());
                passwordField.setText("");
                saveCredentials(registerUsernameField.getText());
                licenseField.setText("");
                registerPasswordField.setText("");
                JOptionPane.showMessageDialog(this,
                        text("VerificationPanel.redeem.success", "Registration successful. Please log in."),
                        "Yozakura", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this,
                        registrationMessage(code),
                        "Yozakura", JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    text("VerificationPanel.login.code.-1", "please check your internet connection"),
                    "Yozakura", JOptionPane.ERROR_MESSAGE);
        } finally {
            java.util.Arrays.fill(passwordChars, '\0');
            registerPasswordField.setText("");
            redeemButton.setEnabled(true);
            loginButton.setEnabled(true);
        }
    }

    private void performLogin() {
        int code = 0;
        char[] passwordChars = passwordField.getPassword();
        boolean loginAccepted = false;
        try {
            code = NativeAuthBridge.login(usernameField.getText(), passwordChars);

            if (code == 0) {
                loginAccepted = true;
                saveCredentials(usernameField.getText());
                showVerifiedEntitlement();
                writeResult(1);
                SwingUtilities.invokeLater(frame::dispose);
            } else {
                showLoginCode(code);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, text("VerificationPanel.login.exception", "JVM Occurs a Error"));
        } finally {
            java.util.Arrays.fill(passwordChars, '\0');
            if (loginAccepted) {
                passwordField.setText("");
            }
            if (loginButton != null) {
                loginButton.setEnabled(true);
            }
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
        licenseField = new JTextField();
        redeemButton = new JButton();

        Font labelFont = new Font("Microsoft YaHei", Font.PLAIN, 14);
        Font inputFont = new Font("Microsoft YaHei UI", Font.PLAIN, 14);

        setFont(inputFont);
        setLayout(new GridBagLayout());

        GridBagLayout layout = (GridBagLayout) getLayout();
        layout.columnWidths = new int[] { 0, 245, 0 };
        layout.rowHeights = new int[] { 0, 0, 0, 0, 0, 0, 0, 0 };
        layout.columnWeights = new double[] { 0.0, 1.0, 1.0E-4 };
        layout.rowWeights = new double[] { 0.0, 0.0, 0.0, 0.0, 1.0E-4 };

        usernameLabel.setText(text("VerificationPanel.usernameLabel.text", "username"));
        usernameLabel.setFont(labelFont);
        usernameLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        add(usernameLabel, constraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(10, 10, 5, 5)));

        usernameField.setFont(inputFont);
        add(usernameField, constraints(1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(10, 0, 5, 10)));

        passwordLabel.setText(text("VerificationPanel.passwordLabel.text", "password"));
        passwordLabel.setFont(labelFont);
        passwordLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        add(passwordLabel, constraints(0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(0, 10, 5, 5)));

        passwordField.setFont(inputFont);
        passwordField.addKeyListener(new PasswordFieldKeyListener(this));
        add(passwordField, constraints(1, 1, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 5, 10)));

        loginButton.setText(text("VerificationPanel.loginButton.text", "Login"));
        loginButton.setFont(inputFont);
        loginButton.addActionListener(this::startLogin);
        add(loginButton, constraints(1, 2, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(5, 0, 8, 10)));

        registerUsernameLabel = new JLabel(text("VerificationPanel.registerUsernameLabel.text", "register username"));
        registerUsernameLabel.setFont(labelFont);
        registerUsernameLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        add(registerUsernameLabel, constraints(0, 3, 1, 1, 0.0, 0.0, GridBagConstraints.EAST,
                GridBagConstraints.NONE, new Insets(5, 10, 5, 5)));
        registerUsernameField = new JTextField();
        registerUsernameField.setFont(inputFont);
        add(registerUsernameField, constraints(1, 3, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER,
                GridBagConstraints.HORIZONTAL, new Insets(5, 0, 5, 10)));

        registerPasswordLabel = new JLabel(text("VerificationPanel.registerPasswordLabel.text", "register password"));
        registerPasswordLabel.setFont(labelFont);
        registerPasswordLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        add(registerPasswordLabel, constraints(0, 4, 1, 1, 0.0, 0.0, GridBagConstraints.EAST,
                GridBagConstraints.NONE, new Insets(0, 10, 5, 5)));
        registerPasswordField = new JPasswordField();
        registerPasswordField.setFont(inputFont);
        add(registerPasswordField, constraints(1, 4, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER,
                GridBagConstraints.HORIZONTAL, new Insets(0, 0, 5, 10)));

        JLabel licenseLabel = new JLabel(text("VerificationPanel.licenseLabel.text", "license key"));
        licenseLabel.setFont(labelFont);
        licenseLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        add(licenseLabel, constraints(0, 5, 1, 1, 0.0, 0.0, GridBagConstraints.EAST,
                GridBagConstraints.NONE, new Insets(0, 10, 5, 5)));
        licenseField.setFont(inputFont);
        add(licenseField, constraints(1, 5, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER,
                GridBagConstraints.HORIZONTAL, new Insets(0, 0, 5, 10)));
        redeemButton.setText(text("VerificationPanel.redeemButton.text", "Redeem license"));
        redeemButton.setFont(inputFont);
        redeemButton.addActionListener(this::startRedeem);
        add(redeemButton, constraints(1, 6, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER,
                GridBagConstraints.HORIZONTAL, new Insets(5, 0, 10, 10)));

    }

    private static Thread newDaemonThread(Runnable runnable) {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        return thread;
    }

    static void onPasswordKeyPressed(VerificationPanel panel, KeyEvent event) {
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
        String key = "VerificationPanel.login.code." + code;
        JOptionPane.showMessageDialog(this, text(key, key), "Yozakura", JOptionPane.ERROR_MESSAGE);
    }

    private String registrationMessage(int code) {
        String key = "VerificationPanel.redeem.code." + code;
        if (code == -1) {
            return text(key, "Unable to connect to the verification server.");
        }
        if (code == 4) {
            return text(key, "License key was not found.");
        }
        if (code == 5) {
            return text(key, "License key has already been redeemed.");
        }
        if (code == 6) {
            return text(key, "This username already exists. Choose another username.");
        }
        return text(key, "Registration details are invalid. Use a unique username and a password of at least 8 characters.");
    }

    private void showVerifiedEntitlement() {
        String role = NativeAuthBridge.getVerifiedRole();
        String expiry = NativeAuthBridge.getVerifiedExpiry();
        String message = text("VerificationPanel.login.success", "Verification successful")
                + "\n" + text("VerificationPanel.login.role", "Role") + ": " + role
                + "\n" + text("VerificationPanel.login.expires", "Expires") + ": " + expiry;
        JOptionPane.showMessageDialog(this, message, "Yozakura", JOptionPane.INFORMATION_MESSAGE);
    }

    private ResourceBundle loadBundle() {
        try {
            return ResourceBundle.getBundle("gq.yozakura.auth.vendor.tech.skidonion.verification.lang");
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
