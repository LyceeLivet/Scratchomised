package io.github.totchi_lagawi.scratchomised_plugin;

import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.viewcontroller.HomeController;

public class PluginServerWebSocketServlet extends WebSocketServlet {
    private LanguageManager _languageManager;
    private Home _home;
    private PluginServerDebugWindow _debugWindow;
    private HomeController _homeController;

    public PluginServerWebSocketServlet(LanguageManager languageManager, Home home, PluginServerDebugWindow debugWindow, HomeController homeController) {
        this._languageManager = languageManager;
        this._home = home;
        this._debugWindow = debugWindow;
        this._homeController = homeController;
    }

    @Override
    public void configure(WebSocketServletFactory factory) {
        factory.getPolicy().setIdleTimeout(Long.MAX_VALUE);
        // Augmenter la taille maximale du buffer pour les gros messages
        factory.getPolicy().setMaxTextMessageBufferSize(10 * 1024 * 1024); // 10 MB
        factory.getPolicy().setMaxBinaryMessageBufferSize(10 * 1024 * 1024); // 10 MB
        factory.register(PluginServerWebSocketEndpoint.class);
        factory.setCreator(new PluginServerWebSocketCreator(this._languageManager, this._home, this._debugWindow, this._homeController));
    }

}
