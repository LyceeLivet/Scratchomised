package io.github.totchi_lagawi.scratchomised_plugin;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.SimpleDateFormat;
import java.util.Date;

public class PluginServerDebugWindow extends JFrame {
    private JLabel _statusLabel;
    private JTextArea _logArea;
    private LanguageManager _languageManager;
    private SimpleDateFormat _dateFormat;

    public PluginServerDebugWindow(LanguageManager languageManager) {
        this._languageManager = languageManager;
        this._dateFormat = new SimpleDateFormat("HH:mm:ss.SSS");
        
        initializeUI();
    }

    private void initializeUI() {
        setTitle(this._languageManager.getString("debug_window.title"));
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(700, 500);
        setLocationRelativeTo(null);
        
        // Fermer la fenêtre quand elle est fermée (mais on empêche la fermeture par défaut)
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // La fenêtre ne se ferme que si le serveur est arrêté
                // Cette méthode peut être override par le gestionnaire du serveur
                setVisible(false);
            }
        });

        // Panel principal
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Panel d'état
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.add(new JLabel(this._languageManager.getString("debug_window.status") + ": "));
        _statusLabel = new JLabel(this._languageManager.getString("debug_window.status_stopped"));
        _statusLabel.setFont(_statusLabel.getFont().deriveFont(Font.BOLD));
        statusPanel.add(_statusLabel);
        mainPanel.add(statusPanel, BorderLayout.NORTH);

        // Zone de logs
        _logArea = new JTextArea();
        _logArea.setEditable(false);
        _logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        _logArea.setBackground(Color.BLACK);
        _logArea.setForeground(Color.GREEN);
        
        // Auto-scroll
        DefaultCaret caret = (DefaultCaret) _logArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        
        JScrollPane scrollPane = new JScrollPane(_logArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Bouton pour effacer les logs
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton clearButton = new JButton(this._languageManager.getString("debug_window.clear"));
        clearButton.addActionListener(e -> clearLogs());
        buttonPanel.add(clearButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    public void setServerStatus(boolean running, int port) {
        SwingUtilities.invokeLater(() -> {
            if (running) {
                _statusLabel.setText(this._languageManager.getString("debug_window.status_running") + " (port " + port + ")");
                _statusLabel.setForeground(Color.GREEN);
            } else {
                _statusLabel.setText(this._languageManager.getString("debug_window.status_stopped"));
                _statusLabel.setForeground(Color.RED);
            }
        });
    }

    public void addLog(String message, LogType type) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = _dateFormat.format(new Date());
            String prefix;
            
            switch (type) {
                case RECEIVED:
                    prefix = "[← RECU]";
                    break;
                case SENT:
                    prefix = "[→ ENVOYÉ]";
                    break;
                case INFO:
                    prefix = "[INFO]";
                    break;
                case ERROR:
                    prefix = "[ERREUR]";
                    break;
                case CONNECTION:
                    prefix = "[CONNEXION]";
                    break;
                default:
                    prefix = "[LOG]";
            }
            
            _logArea.append("[" + timestamp + "] " + prefix + " " + message + "\n");
            
            // Limiter le nombre de lignes pour éviter les problèmes de mémoire
            String text = _logArea.getText();
            String[] lines = text.split("\n");
            if (lines.length > 1000) {
                StringBuilder newText = new StringBuilder();
                for (int i = lines.length - 1000; i < lines.length; i++) {
                    newText.append(lines[i]).append("\n");
                }
                _logArea.setText(newText.toString());
            }
        });
    }

    public void clearLogs() {
        SwingUtilities.invokeLater(() -> {
            _logArea.setText("");
        });
    }

    public enum LogType {
        RECEIVED,
        SENT,
        INFO,
        ERROR,
        CONNECTION
    }
}

