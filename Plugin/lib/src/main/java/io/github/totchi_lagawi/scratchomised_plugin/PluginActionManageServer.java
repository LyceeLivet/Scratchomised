package io.github.totchi_lagawi.scratchomised_plugin;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.function.Consumer;

import javax.swing.JOptionPane;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.plugin.PluginAction;
import com.eteks.sweethome3d.swing.HomeComponent3D;
import com.eteks.sweethome3d.viewcontroller.HomeController;

public class PluginActionManageServer extends PluginAction implements PropertyChangeListener, UncaughtExceptionHandler {
    private LanguageManager _languageManager;
    private Home _home;
    private HomeController _homeController;
    private int _port = 55125;
    private PluginServer _server;
    private Thread _serverThread;
    private PluginServerDebugWindow _debugWindow;
    private PluginClickListener _clickListener;
    private Consumer<String> _clickMessageCallback;

    public PluginActionManageServer(LanguageManager languageManager, Home home, HomeController homeController) {
        this._languageManager = languageManager;
        this._home = home;
        this._homeController = homeController;
        // When instanciated, define the menu, name and enabled state of this action
        putPropertyValue(Property.NAME, this._languageManager.getString("menus.server.start"));
        putPropertyValue(Property.MENU, this._languageManager.getString("name"));
        putPropertyValue(Property.ENABLED, true);

    }

    @Override
    public void execute() {
        if (this.isServerRunning()) {
            this.stopServer();
        } else {
            this.startServer();
        }
    }

    /**
     * Start the server, stopping it if already running.
     * Make sure to check wether it is running before trying to start it.
     */
    public void startServer() {
        if (this.isServerRunning()) {
            return;
        }

        // Créer la fenêtre de debug
        if (this._debugWindow == null) {
            this._debugWindow = new PluginServerDebugWindow(this._languageManager);
            this._debugWindow.setServerStatus(false, this._port);
        }
        this._debugWindow.setVisible(true);

        if (this._server == null) {
            this._server = new PluginServer(this._port, this._languageManager, this._home, this._debugWindow, this._homeController);
        }

        if (this._serverThread == null) {
            this._serverThread = new Thread(this._server, "Scratchomised - Server thread");
        }

        this._serverThread.start();
        
        // Ajouter le listener de clic sur le composant 3D (après un court délai pour s'assurer que le serveur est démarré)
        // Le callback sera défini par PluginServerWebSocketServlet
        try {
            Thread.sleep(100); // Attendre un peu que le serveur démarre
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        this.addClickListener();

        // And we say that the server is started
        // We have to say that it is started before actually starting it as of a
        // concurrency bug if the server instantly crashes
        // If it does, then :
        // - The server is started
        // - It throws an Exception, which is caught
        // - this.stopServer() is called
        // - The name is changed as if the server was stopped (which is the case)
        // - But now this.startServer() finishes executing and change the name as if the
        // server was running, which is not the case
        // Sometimes, this.startServer() finishes executing before this.stopServer(),
        // and so the name is correct. But sometimes not, that's why the name should be
        // changed before it ever could be changed again
        putPropertyValue(Property.NAME, this._languageManager.getString("menus.server.stop"));
    }

    /**
     * Stop the server if needed, and clean things a bit.
     */
    public void stopServer() {
        if (this._server != null && this._serverThread != null && this._server.isRunning()) {
            this._server.stop();
        }

        this._server = null;
        this._serverThread = null;

        // Retirer le listener de clic
        this.removeClickListener();

        // Masquer la fenêtre de debug (mais la garder pour le prochain démarrage)
        if (this._debugWindow != null) {
            this._debugWindow.setVisible(false);
        }

        // Now we say that the server is stopped
        putPropertyValue(Property.NAME, this._languageManager.getString("menus.server.start"));
    }
    
    /**
     * Ajoute le listener de clic sur le composant 3D
     */
    private void addClickListener() {
        try {
            if (this._homeController != null) {
                HomeComponent3D comp3D = (HomeComponent3D) this._homeController.getHomeController3D().getView();
                if (comp3D != null && this._clickListener == null) {
                    // Créer le callback qui utilise ClickMessageManager pour envoyer les messages
                    this._clickMessageCallback = (objectId) -> {
                        ClickMessageManager.getInstance().sendClickMessage(objectId);
                    };
                    
                    // Créer et ajouter le listener
                    this._clickListener = new PluginClickListener(this._clickMessageCallback, this._debugWindow);
                    comp3D.addMouseListener(this._clickListener);
                    
                    if (this._debugWindow != null) {
                        this._debugWindow.addLog("Click listener added to 3D view", PluginServerDebugWindow.LogType.INFO);
                    }
                }
            }
        } catch (Exception ex) {
            if (this._debugWindow != null) {
                this._debugWindow.addLog("Error adding click listener: " + ex.getMessage(), PluginServerDebugWindow.LogType.ERROR);
            }
            ex.printStackTrace();
        }
    }
    
    /**
     * Retire le listener de clic du composant 3D
     */
    private void removeClickListener() {
        try {
            if (this._homeController != null && this._clickListener != null) {
                HomeComponent3D comp3D = (HomeComponent3D) this._homeController.getHomeController3D().getView();
                if (comp3D != null) {
                    comp3D.removeMouseListener(this._clickListener);
                    this._clickListener = null;
                }
            }
        } catch (Exception ex) {
            // Ignorer les erreurs silencieusement
            ex.printStackTrace();
        }
    }

    public boolean isServerRunning() {
        if (this._server == null || this._serverThread == null) {
            return false;
        } else {
            return this._server.isRunning();
        }
    }

    // Normally called in case of a language change
    public void propertyChange(PropertyChangeEvent event) {
        if ("LANGUAGE".equals(event.getPropertyName())) {
            putPropertyValue(Property.MENU, this._languageManager.getString("name"));

            if (this.isServerRunning()) {
                putPropertyValue(Property.NAME, this._languageManager.getString("menus.server.stop"));
            } else {
                putPropertyValue(Property.NAME, this._languageManager.getString("menus.server.start"));
            }
        }
    }

    // Normally called in case of an error in the server thread
    public void uncaughtException(Thread thread, Throwable exception) {
        // We've got an exception from the server : it means it crashed
        // Exceptions from connexion handler are handled differently
        // We should then clean the server and its thread
        this.stopServer();
        JOptionPane.showMessageDialog(null, exception.getMessage(),
                this._languageManager.getString("name") + " - " + this._languageManager.getString("errors.error"),
                JOptionPane.ERROR_MESSAGE);
    }
}