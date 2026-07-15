package gq.yozakura.auth.vendor.skidonion.sWdSl;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
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
import gq.yozakura.auth.vendor.tech.skidonion.obfuscator.inline.Wrapper;
import gq.yozakura.auth.NativeAuthBridge;

public class VerificationPanel extends JPanel {
    private static final String CACHE_DIR = "yozakura";
    private static final String CACHE_COMMENT = "local username cache";
    private static final Color LINK_HOVER = new Color(4276735);
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
    private JLabel registerLabel;
    private JLabel termsLabel;
    private JLabel privacyLabel;

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

    private void performLogin() {
        int code = 0;
        char[] passwordChars = passwordField.getPassword();
        try {
            code = NativeAuthBridge.login(usernameField.getText(), passwordChars);

            if (code == 0) {
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
            passwordField.setText("");
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

    private void openTerms(MouseEvent event) {
        browseService();
    }

    private void hoverTerms(MouseEvent event) {
        setLinkHover(termsLabel);
    }

    private void unhoverTerms(MouseEvent event) {
        setLinkNormal(termsLabel);
    }

    private void openPrivacy(MouseEvent event) {
        browseService();
    }

    private void hoverPrivacy(MouseEvent event) {
        setLinkHover(privacyLabel);
    }

    private void unhoverPrivacy(MouseEvent event) {
        setLinkNormal(privacyLabel);
    }

    private void openRegister(MouseEvent event) {
        browseService();
    }

    private void hoverRegister(MouseEvent event) {
        setLinkHover(registerLabel);
    }

    private void unhoverRegister(MouseEvent event) {
        setLinkNormal(registerLabel);
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
        registerLabel = new JLabel();
        termsLabel = new JLabel();
        privacyLabel = new JLabel();

        Font labelFont = new Font("Microsoft YaHei", Font.PLAIN, 14);
        Font inputFont = new Font("Microsoft YaHei UI", Font.PLAIN, 14);
        Font linkFont = new Font("Microsoft YaHei", Font.PLAIN, 12);

        setFont(inputFont);
        setLayout(new GridBagLayout());

        GridBagLayout layout = (GridBagLayout) getLayout();
        layout.columnWidths = new int[] { 0, 245, 0 };
        layout.rowHeights = new int[] { 0, 0, 0, 0, 0 };
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

        registerLabel.setText(text("VerificationPanel.registerLabel.text", "Register"));
        registerLabel.setFont(linkFont);
        registerLabel.setHorizontalAlignment(SwingConstants.CENTER);
        registerLabel.addMouseListener(new RegisterLinkMouseListener(this));
        add(registerLabel, constraints(0, 3, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0, 10, 10, 5)));

        termsLabel.setText(text("VerificationPanel.termsLabel.text", "Terms of Service"));
        termsLabel.setFont(linkFont);
        termsLabel.setHorizontalAlignment(SwingConstants.CENTER);
        termsLabel.addMouseListener(new TermsLinkMouseListener(this));
        add(termsLabel, constraints(1, 3, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0, 0, 10, 5)));

        privacyLabel.setText(text("VerificationPanel.privacyLabel.text", "Privacy Policy"));
        privacyLabel.setFont(linkFont);
        privacyLabel.setHorizontalAlignment(SwingConstants.CENTER);
        privacyLabel.addMouseListener(new PrivacyLinkMouseListener(this));
        add(privacyLabel, constraints(2, 3, 1, 1, 0.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(0, 0, 10, 10)));
    }

    private static Thread newDaemonThread(Runnable runnable) {
        Thread thread = new Thread(runnable);
        thread.setDaemon(true);
        return thread;
    }

    static void onPasswordKeyPressed(VerificationPanel panel, KeyEvent event) {
        panel.clearCachedPasswordOnEdit(event);
    }

    static void onTermsClicked(VerificationPanel panel, MouseEvent event) {
        panel.openTerms(event);
    }

    static void onTermsEntered(VerificationPanel panel, MouseEvent event) {
        panel.hoverTerms(event);
    }

    static void onTermsExited(VerificationPanel panel, MouseEvent event) {
        panel.unhoverTerms(event);
    }

    static void onPrivacyClicked(VerificationPanel panel, MouseEvent event) {
        panel.openPrivacy(event);
    }

    static void onPrivacyEntered(VerificationPanel panel, MouseEvent event) {
        panel.hoverPrivacy(event);
    }

    static void onPrivacyExited(VerificationPanel panel, MouseEvent event) {
        panel.unhoverPrivacy(event);
    }

    static void onRegisterClicked(VerificationPanel panel, MouseEvent event) {
        panel.openRegister(event);
    }

    static void onRegisterEntered(VerificationPanel panel, MouseEvent event) {
        panel.hoverRegister(event);
    }

    static void onRegisterExited(VerificationPanel panel, MouseEvent event) {
        panel.unhoverRegister(event);
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

    private void showVerifiedEntitlement() {
        String role = NativeAuthBridge.getVerifiedRole();
        String expiry = NativeAuthBridge.getVerifiedExpiry();
        String message = text("VerificationPanel.login.success", "Verification successful")
                + "\n" + text("VerificationPanel.login.role", "Role") + ": " + role
                + "\n" + text("VerificationPanel.login.expires", "Expires") + ": " + expiry;
        JOptionPane.showMessageDialog(this, message, "Yozakura", JOptionPane.INFORMATION_MESSAGE);
    }

    private void browse(String uri) {
        try {
            Desktop.getDesktop().browse(URI.create(uri));
        } catch (Exception ignored) {
        }
    }

    private void browseService() {
        try {
            browse(Wrapper.getServiceBaseUrl());
        } catch (RuntimeException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), "Yozakura", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void setLinkHover(JLabel label) {
        if (label != null) {
            label.setForeground(LINK_HOVER);
        }
    }

    private void setLinkNormal(JLabel label) {
        if (label != null) {
            label.setForeground(Color.black);
        }
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
