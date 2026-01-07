package io.github.totchi_lagawi.scratchomised_plugin;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import com.eteks.sweethome3d.model.Home;

import com.eteks.sweethome3d.viewcontroller.HomeController;

public class PluginServer implements Runnable {
    private LanguageManager _languageManager;
    private Home _home;
    private int _port;
    private Server _server;
    private PluginServerDebugWindow _debugWindow;
    private HomeController _homeController;

    public PluginServer(int port, LanguageManager languageManager, Home home, PluginServerDebugWindow debugWindow, HomeController homeController) {
        this._languageManager = languageManager;
        this._home = home;
        this._port = port;
        this._server = new Server(this._port);
        this._debugWindow = debugWindow;
        this._homeController = homeController;
    }

    @Override
    public void run() {
        Thread.currentThread().setContextClassLoader(this.getClass().getClassLoader());
        ServletContextHandler contextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        contextHandler.setContextPath("/");
        this._server.setHandler(contextHandler);
        ServletHolder servletHolder = new ServletHolder(
                new PluginServerWebSocketServlet(this._languageManager, this._home, this._debugWindow, this._homeController));
        contextHandler.addServlet(servletHolder, "/");
        try {
            this._server.start();
            if (this._debugWindow != null) {
                this._debugWindow.setServerStatus(true, this._port);
                this._debugWindow.addLog("Server started on port " + this._port, PluginServerDebugWindow.LogType.INFO);
            }
        } catch (Exception ex) {
            System.err.println(this._languageManager.getString("log_prefix") + "Couldn't start the server :");
            ex.printStackTrace();
            if (this._debugWindow != null) {
                this._debugWindow.addLog("Error starting server: " + ex.getMessage(), PluginServerDebugWindow.LogType.ERROR);
            }
        }
    }

    public void stop() {
        if (this.isRunning()) {
            try {
                this._server.stop();
                if (this._debugWindow != null) {
                    this._debugWindow.setServerStatus(false, this._port);
                    this._debugWindow.addLog("Server stopped", PluginServerDebugWindow.LogType.INFO);
                }
            } catch (Exception ex) {
                System.err.println(this._languageManager.getString("log_prefix") + "Couldn't stop the server :");
                ex.printStackTrace();
                if (this._debugWindow != null) {
                    this._debugWindow.addLog("Error stopping server: " + ex.getMessage(), PluginServerDebugWindow.LogType.ERROR);
                }
            }
        }
    }

    public boolean isRunning() {
        return this._server.isRunning();
    }

    public int getPort() {
        return this._port;
    }

    public void setPort(int port) {
        this._port = port;
    }
}
