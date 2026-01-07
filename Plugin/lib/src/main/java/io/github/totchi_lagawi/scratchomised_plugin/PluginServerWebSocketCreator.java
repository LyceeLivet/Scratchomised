package io.github.totchi_lagawi.scratchomised_plugin;

import org.eclipse.jetty.websocket.servlet.ServletUpgradeRequest;
import org.eclipse.jetty.websocket.servlet.ServletUpgradeResponse;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.viewcontroller.HomeController;

public class PluginServerWebSocketCreator implements WebSocketCreator {
    private LanguageManager _languageManager;
    private Home _home;
    private PluginServerDebugWindow _debugWindow;
    private HomeController _homeController;

    public PluginServerWebSocketCreator(LanguageManager languageManager, Home home, PluginServerDebugWindow debugWindow, HomeController homeController) {
        this._languageManager = languageManager;
        this._home = home;
        this._debugWindow = debugWindow;
        this._homeController = homeController;
    }

    @Override
    public Object createWebSocket(ServletUpgradeRequest req, ServletUpgradeResponse resp) {
        // Accepter la connexion même sans le protocole "scratchomised" pour plus de compatibilité
        // Le protocole est optionnel
        if (this._debugWindow != null) {
            String protocols = req.getSubProtocols() != null ? String.join(", ", req.getSubProtocols()) : "none";
            this._debugWindow.addLog("WebSocket connection request with protocols: " + protocols, PluginServerDebugWindow.LogType.INFO);
        }
        
        // Accepter la connexion dans tous les cas
        PluginServerWebSocketEndpoint endpoint = new PluginServerWebSocketEndpoint(this._languageManager, this._home, this._debugWindow, this._homeController);
        
        // Si le protocole "scratchomised" est présent, l'accepter explicitement
        if (req.hasSubProtocol("scratchomised")) {
            resp.setAcceptedSubProtocol("scratchomised");
            if (this._debugWindow != null) {
                this._debugWindow.addLog("Accepted connection with scratchomised protocol", PluginServerDebugWindow.LogType.INFO);
            }
        } else {
            if (this._debugWindow != null) {
                this._debugWindow.addLog("Accepted connection without scratchomised protocol (compatibility mode)", PluginServerDebugWindow.LogType.INFO);
            }
        }
        
        return endpoint;
    }

}
