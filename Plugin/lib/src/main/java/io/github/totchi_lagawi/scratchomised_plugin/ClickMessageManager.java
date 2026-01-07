package io.github.totchi_lagawi.scratchomised_plugin;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Gestionnaire centralisé pour envoyer des messages de clic à toutes les sessions WebSocket actives
 */
public class ClickMessageManager {
    private static ClickMessageManager _instance;
    private CopyOnWriteArrayList<Consumer<String>> _callbacks = new CopyOnWriteArrayList<>();
    
    private ClickMessageManager() {
    }
    
    public static ClickMessageManager getInstance() {
        if (_instance == null) {
            _instance = new ClickMessageManager();
        }
        return _instance;
    }
    
    /**
     * Enregistre un callback pour recevoir les messages de clic
     */
    public void registerCallback(Consumer<String> callback) {
        _callbacks.add(callback);
    }
    
    /**
     * Désenregistre un callback
     */
    public void unregisterCallback(Consumer<String> callback) {
        _callbacks.remove(callback);
    }
    
    /**
     * Envoie un message de clic à tous les callbacks enregistrés
     */
    public void sendClickMessage(String objectId) {
        for (Consumer<String> callback : _callbacks) {
            try {
                callback.accept(objectId);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
}

