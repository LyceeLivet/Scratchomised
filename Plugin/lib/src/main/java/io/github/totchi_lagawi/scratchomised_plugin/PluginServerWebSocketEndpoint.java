package io.github.totchi_lagawi.scratchomised_plugin;

import com.eteks.sweethome3d.model.CollectionEvent;
import com.eteks.sweethome3d.model.CollectionListener;
import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.CollectionEvent.Type;
import com.fasterxml.jackson.jr.ob.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;

import com.eteks.sweethome3d.viewcontroller.HomeController;

public class PluginServerWebSocketEndpoint
        implements WebSocketListener, CollectionListener<HomePieceOfFurniture>, PropertyChangeListener {
    private Home _home;
    private LanguageManager _languageManager;
    private Session _session;
    private JSON _json;
    private PluginServerDebugWindow _debugWindow;
    private boolean _clientReady = false;

    public PluginServerWebSocketEndpoint(LanguageManager languageManager, Home home, PluginServerDebugWindow debugWindow, HomeController homeController) {
        this._home = home;
        this._languageManager = languageManager;
        this._debugWindow = debugWindow;
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason) {
        String address = (this._session != null && this._session.getRemoteAddress() != null) 
            ? this._session.getRemoteAddress().toString() 
            : "unknown";
        String message = address + " disconnected (reason : " + reason + ")";
        System.out.println(this._languageManager.getString("log_prefix") + message);
        if (this._debugWindow != null) {
            this._debugWindow.addLog(message, PluginServerDebugWindow.LogType.CONNECTION);
        }
        if (this._home != null) {
            for (HomePieceOfFurniture furniture : this._home.getFurniture()) {
                furniture.removePropertyChangeListener(this);
            }
        }
        
        // Désenregistrer ce endpoint des messages de clic
        // Note: On ne peut pas désenregistrer directement le callback lambda, donc on le laisse enregistré
        // Le callback vérifiera si la session est ouverte avant d'envoyer
        this._clientReady = false;
    }

    @Override
    public void onWebSocketConnect(Session session) {
        this._json = new JSON();
        this._session = session;
        String address = (session != null && session.getRemoteAddress() != null) 
            ? session.getRemoteAddress().toString() 
            : "unknown";
        String message = address + " connected";
        System.out.println(this._languageManager.getString("log_prefix") + message);
        if (this._debugWindow != null) {
            this._debugWindow.addLog(message, PluginServerDebugWindow.LogType.CONNECTION);
        }
        if (this._home != null) {
            this._home.addFurnitureListener(this);

            for (HomePieceOfFurniture furniture : this._home.getFurniture()) {
                furniture.addPropertyChangeListener(this);
            }
        }
        
        // Enregistrer ce endpoint pour recevoir les messages de clic
        ClickMessageManager.getInstance().registerCallback((objectId) -> {
            this.sendObjectClicked(objectId);
        });

        // Ne pas envoyer de message immédiatement - attendre que le client envoie d'abord client_ready
        // Cela évite les conflits et permet de s'assurer que la connexion est stable
        if (this._debugWindow != null) {
            this._debugWindow.addLog("Waiting for client to send client_ready before sending objects", PluginServerDebugWindow.LogType.INFO);
        }
    }

    @Override
    public void onWebSocketError(Throwable cause) {
        String address = (this._session != null && this._session.getRemoteAddress() != null) 
            ? this._session.getRemoteAddress().toString() 
            : "unknown";
        String message = "error in connection to " + address + " : " + cause.getClass().getCanonicalName();
        System.out.println(this._languageManager.getString("log_prefix") + message);
        if (this._debugWindow != null) {
            this._debugWindow.addLog(message + " - " + cause.getMessage(), PluginServerDebugWindow.LogType.ERROR);
        }
        cause.printStackTrace();
    }

    @Override
    public void onWebSocketBinary(byte[] payload, int offset, int len) {
    }

    @Override
    public void onWebSocketText(String message) {
        if (this._debugWindow != null) {
            this._debugWindow.addLog("Message received: " + message, PluginServerDebugWindow.LogType.RECEIVED);
        }
        ScratchomisedRequest request = new ScratchomisedRequest();
        try {
            request = this._json.beanFrom(ScratchomisedRequest.class, message);
        } catch (IOException e) {
            e.printStackTrace();
            if (this._debugWindow != null) {
                this._debugWindow.addLog("Error parsing message: " + e.getMessage(), PluginServerDebugWindow.LogType.ERROR);
            }
        }

        switch (request.action) {
            case "client_ready":
                if (this._debugWindow != null) {
                    this._debugWindow.addLog("Client is ready, sending objects now", PluginServerDebugWindow.LogType.INFO);
                }
                this._clientReady = true;
                // Envoyer les objets maintenant que le client est prêt
                if (this._session != null && this._session.isOpen()) {
                    this.updateObjects();
                }
                break;
            case "test_ack":
                if (this._debugWindow != null) {
                    this._debugWindow.addLog("Received test acknowledgment from client - connection is working!", PluginServerDebugWindow.LogType.INFO);
                }
                break;
            case "welcome_ack":
                if (this._debugWindow != null) {
                    this._debugWindow.addLog("Received welcome acknowledgment from client", PluginServerDebugWindow.LogType.INFO);
                }
                break;
            case "define_property":
            if (this._debugWindow != null) {
                this._debugWindow.addLog("Action: define_property - object: " + request.args.get("object") + 
                        ", property: " + request.args.get("property") + ", value: " + request.args.get("value"), 
                        PluginServerDebugWindow.LogType.INFO);
            }
                for (HomePieceOfFurniture furniture : this._home.getFurniture()) {
                    if (furniture.getId().equals(request.args.get("object"))) {
                        String propertyName = String.valueOf(request.args.get("property"));
                        String setMethodName = "set"
                                + propertyName.substring(0, 1).toUpperCase()
                                + propertyName.substring(1);
                        
                        // D'abord, essayer de trouver le setter directement (méthode publique)
                        // Cela fonctionne pour les propriétés comme "color", "shininess", etc.
                        Method setMethod = null;
                        Class<?> fieldType = null;
                        
                        // Essayer les types courants pour les setters
                        Class<?>[] commonTypes = {Integer.class, Float.class, String.class, Boolean.class, 
                                                 int.class, float.class, boolean.class};
                        
                        for (Class<?> type : commonTypes) {
                            try {
                                setMethod = furniture.getClass().getMethod(setMethodName, type);
                                fieldType = type;
                                break;
                            } catch (NoSuchMethodException ex) {
                                // Continuer à chercher
                            }
                        }
                        
                        Object value = null;
                        String valueStr = String.valueOf(request.args.get("value"));
                        
                        // Gérer le cas spécial de "null" (chaîne) pour permettre de réinitialiser certaines propriétés
                        if (valueStr.equalsIgnoreCase("null") || valueStr.trim().isEmpty()) {
                            // Pour Integer et Float, permettre null pour réinitialiser (ex: color = null)
                            if (fieldType == Integer.class || fieldType == Float.class) {
                                value = null;
                            } else {
                                System.out.println(this._languageManager.getString("log_prefix") + "Cannot set null value for primitive type " + fieldType.getCanonicalName());
                                return;
                            }
                        } else if (fieldType == int.class || fieldType == Integer.class) {
                            // Pour les couleurs RGB, accepter les formats hexadécimaux (0xFFFFFF) ou décimaux
                            if (valueStr.startsWith("0x") || valueStr.startsWith("0X") || valueStr.startsWith("#")) {
                                // Format hexadécimal pour les couleurs
                                valueStr = valueStr.replace("0x", "").replace("0X", "").replace("#", "");
                                value = Integer.parseInt(valueStr, 16);
                            } else {
                                value = Integer.parseInt(valueStr);
                            }
                        } else if (fieldType == float.class || fieldType == Float.class) {
                            value = Float.parseFloat(valueStr);
                        } else if (fieldType == boolean.class || fieldType == Boolean.class) {
                            value = Boolean.parseBoolean(valueStr);
                        } else if (fieldType == String.class) {
                            value = valueStr;
                        } else {
                            System.out.println(this._languageManager.getString("log_prefix") + "Unsupported field type "
                                    + (fieldType != null ? fieldType.getCanonicalName() : "null") + " for property "
                                    + propertyName + " of object "
                                    + furniture.getClass().getCanonicalName());
                        }

                        // Note: value peut être null pour Integer.class ou Float.class (pour réinitialiser)
                        if (value == null && fieldType != null && (fieldType == int.class || fieldType == float.class || fieldType == boolean.class)) {
                            System.out.println(this._languageManager.getString("log_prefix") + "Cannot set null value for primitive type " + fieldType.getCanonicalName());
                            return;
                        }
                        
                        // Si on a trouvé un setter, l'utiliser (méthode préférée)
                        if (setMethod != null) {
                            try {
                                setMethod.invoke(furniture, value);
                                if (this._debugWindow != null) {
                                    this._debugWindow.addLog("Successfully set property '" + propertyName + "' to " + value + 
                                            " using method " + setMethodName, PluginServerDebugWindow.LogType.INFO);
                                }
                                this.updateObjects();
                            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                                ex.printStackTrace();
                                if (this._debugWindow != null) {
                                    this._debugWindow.addLog("Error invoking setter " + setMethodName + ": " + ex.getMessage(), 
                                            PluginServerDebugWindow.LogType.ERROR);
                                }
                            }
                        } else {
                            System.out.println(this._languageManager.getString("log_prefix") + "Cannot set property " + propertyName + 
                                    ": no setter method or field found");
                            if (this._debugWindow != null) {
                                this._debugWindow.addLog("Error: no setter or field found for property '" + propertyName + "'", 
                                        PluginServerDebugWindow.LogType.ERROR);
                            }
                        }
                        return;
                    }
                }
                break;
        }
    }

    @Override
    public void collectionChanged(CollectionEvent<HomePieceOfFurniture> event) {
        if (event.getType() == Type.ADD) {
            event.getItem().addPropertyChangeListener(this);
        }
        // Ne mettre à jour les objets que si le client est prêt
        if (this._clientReady) {
            this.updateObjects();
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
        // Ne mettre à jour les objets que si le client est prêt
        if (this._clientReady) {
            this.updateObjects();
        }
    }
    
    /**
     * Envoie un message object_clicked au client
     */
    public void sendObjectClicked(String objectId) {
        if (this._session == null || !this._session.isOpen()) {
            return;
        }
        
        try {
            Hashtable<String, Object> args = new Hashtable<>();
            args.put("object_id", objectId);
            
            ScratchomisedRequest request = new ScratchomisedRequest("object_clicked", args);
            String message = this._json.asString(request);
            this._session.getRemote().sendString(message);
            
            if (this._debugWindow != null) {
                this._debugWindow.addLog("Sent object_clicked message for: " + objectId, PluginServerDebugWindow.LogType.SENT);
            }
        } catch (Exception ex) {
            if (this._debugWindow != null) {
                this._debugWindow.addLog("Error sending object_clicked message: " + ex.getMessage(), PluginServerDebugWindow.LogType.ERROR);
            }
            ex.printStackTrace();
        }
    }

    /**
     * Vérifie si un objet est une lumière, lampe ou interrupteur
     * Vérifie le catalogId (format: "prefix#name-switch", "prefix#name-light", etc.)
     */
    private boolean isLightLampOrSwitch(HomePieceOfFurniture furniture) {
        // Vérifier le nom de la classe (pour les lumières spécialisées comme HomeLight)
        Class<?> current_class = furniture.getClass();
        while (current_class != null && current_class != Object.class) {
            String className = current_class.getCanonicalName().toLowerCase();
            if (className.contains("light") || className.contains("lamp") || className.contains("switch")) {
                return true;
            }
            current_class = current_class.getSuperclass();
        }
        
        // Vérifier le catalogId (identifiant de référencement, format: "prefix#name-switch")
        try {
            ObjectMapper mapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            HashMap<String, Object> mappedObject = (HashMap<String, Object>) mapper.convertValue(furniture, HashMap.class);
            
            // Vérifier l'ID du catalogue (identifiant de référencement original, non traduit)
            // Format attendu: "Scopia#square-switch", "prefix#name-light", etc.
            if (mappedObject.containsKey("catalogId") && mappedObject.get("catalogId") != null) {
                String catalogId = mappedObject.get("catalogId").toString().toLowerCase();
                // Vérifier si l'ID du catalogue contient "switch", "light" ou "lamp"
                if (catalogId.contains("switch") || catalogId.contains("light") || catalogId.contains("lamp")) {
                    if (this._debugWindow != null) {
                        this._debugWindow.addLog("Object '" + (mappedObject.get("name") != null ? mappedObject.get("name") : "unnamed") + "' (catalogId: " + mappedObject.get("catalogId") + ") matched by catalogId filter", PluginServerDebugWindow.LogType.INFO);
                    }
                    return true;
                }
            }
            
            // Vérifier aussi le nom de l'objet (peut être traduit, pour compatibilité)
            if (mappedObject.containsKey("name") && mappedObject.get("name") != null) {
                String objectName = mappedObject.get("name").toString().toLowerCase();
                // Vérifier si le nom contient "switch", "light", "lamp", "interrupteur" (insensible à la casse)
                if (objectName.contains("switch") || objectName.contains("light") || objectName.contains("lamp") || objectName.contains("interrupteur")) {
                    if (this._debugWindow != null) {
                        this._debugWindow.addLog("Object '" + mappedObject.get("name") + "' matched by name filter", PluginServerDebugWindow.LogType.INFO);
                    }
                    return true;
                }
            }
        } catch (Exception e) {
            // En cas d'erreur, ignorer et continuer avec le résultat de la vérification de classe
            if (this._debugWindow != null) {
                this._debugWindow.addLog("Error checking object catalogId/name: " + e.getMessage(), PluginServerDebugWindow.LogType.ERROR);
            }
        }
        
        return false;
    }

    @SuppressWarnings("unchecked")
    private void updateObjects() {
        if (this._home == null) {
            if (this._debugWindow != null) {
                this._debugWindow.addLog("Cannot update objects: home is null", PluginServerDebugWindow.LogType.ERROR);
            }
            return;
        }
        
        Hashtable<String, Object> requestArguments = new Hashtable<>();
        requestArguments.put("objects", new ArrayList<Map<String, Object>>());
        ObjectMapper mapper = new ObjectMapper();
        int totalObjects = 0;
        for (HomePieceOfFurniture furniture : this._home.getFurniture()) {
            totalObjects++;
            
            // Log toutes les classes de l'objet pour identifier les interrupteurs
            if (this._debugWindow != null) {
                try {
                    ObjectMapper tempMapper = new ObjectMapper();
                    HashMap<String, Object> tempMappedObject = tempMapper.convertValue(furniture, HashMap.class);
                    String objectName = tempMappedObject.containsKey("name") ? tempMappedObject.get("name").toString() : "unnamed";
                    String catalogId = tempMappedObject.containsKey("catalogId") ? tempMappedObject.get("catalogId").toString() : "no catalogId";
                    
                    ArrayList<String> allClasses = new ArrayList<String>();
                    Class<?> current_class = furniture.getClass();
                    while (current_class != null && current_class != Object.class) {
                        allClasses.add(current_class.getCanonicalName());
                        current_class = current_class.getSuperclass();
                    }
                    
                    this._debugWindow.addLog("Object #" + totalObjects + ": name='" + objectName + "', catalogId='" + catalogId + "', classes=" + String.join(", ", allClasses), PluginServerDebugWindow.LogType.INFO);
                } catch (Exception e) {
                    // Ignorer les erreurs de logging
                }
            }
            
            // Filtrer pour ne garder que les lumières, lampes et interrupteurs
            if (!isLightLampOrSwitch(furniture)) {
                continue;
            }
            
            HashMap<String, Object> mappedObject = mapper.convertValue(furniture, HashMap.class);
            
            // Créer un objet simplifié avec seulement les propriétés essentielles
            HashMap<String, Object> simplifiedObject = new HashMap<String, Object>();
            
            // Propriétés essentielles
            if (mappedObject.containsKey("id")) {
                simplifiedObject.put("id", mappedObject.get("id"));
            }
            if (mappedObject.containsKey("name")) {
                simplifiedObject.put("name", mappedObject.get("name"));
            }
            
            // Classes
            ArrayList<String> classes = new ArrayList<String>();
            Class<?> current_class = furniture.getClass();
            while (true) {
                if (current_class == Object.class) {
                    break;
                }
                classes.add(current_class.getCanonicalName());
                current_class = current_class.getSuperclass();
            }
            simplifiedObject.put("__scratchomisedClasses", classes);
            
            // Copier seulement les propriétés les plus importantes pour réduire la taille
            // Liste des propriétés essentielles à copier
            String[] essentialProperties = {
                "x", "y", "z", "angle", "width", "depth", "height",
                "visible", "locked", "name", "id", "model", "color",
                "texture", "shininess", "power", "lightColor"
            };
            
            for (String prop : essentialProperties) {
                Object value = mappedObject.get(prop);
                // Toujours copier les propriétés essentielles, même si elles sont null ou absentes
                // Cela permet de les afficher dans le menu des propriétés même si elles n'ont pas de valeur
                // Ne copier que les types primitifs (String, Number, Boolean) ou null
                if (value == null) {
                    // Ajouter null pour que la propriété apparaisse dans le menu
                    simplifiedObject.put(prop, null);
                } else if (value instanceof String || 
                           value instanceof Number || 
                           value instanceof Boolean) {
                    simplifiedObject.put(prop, value);
                }
            }
            
            ((ArrayList<Map<String, Object>>)requestArguments.get("objects")).add(simplifiedObject);
        }
        ScratchomisedRequest updateObjectRequest = new ScratchomisedRequest("update_objects", requestArguments);
        try {
            if (this._session == null) {
                if (this._debugWindow != null) {
                    this._debugWindow.addLog("Cannot send update_objects: session is null", PluginServerDebugWindow.LogType.ERROR);
                }
                return;
            }
            
            if (!this._session.isOpen()) {
                if (this._debugWindow != null) {
                    this._debugWindow.addLog("Cannot send update_objects: session is not open", 
                        PluginServerDebugWindow.LogType.ERROR);
                }
                return;
            }
            
            String sent = this._json.asString(updateObjectRequest);
            
            // Vérifier la taille du message
            int messageSize = sent.length();
            int objectsCount = ((ArrayList<Map<String, Object>>)requestArguments.get("objects")).size();
            if (this._debugWindow != null) {
                this._debugWindow.addLog("Preparing to send update_objects: " + objectsCount + 
                    " objects filtered from " + totalObjects + " total objects, message size: " + messageSize + " chars", 
                    PluginServerDebugWindow.LogType.INFO);
            }
            
            // Envoyer le message
            this._session.getRemote().sendString(sent);
            
            if (this._debugWindow != null) {
                // Tronquer le message si trop long pour l'affichage
                String displayMessage = sent.length() > 200 ? sent.substring(0, 200) + "..." : sent;
                this._debugWindow.addLog("Message sent successfully: " + displayMessage + " (size: " + sent.length() + " chars)", PluginServerDebugWindow.LogType.SENT);
            }
        } catch (IOException e) {
            e.printStackTrace();
            if (this._debugWindow != null) {
                this._debugWindow.addLog("Error sending message: " + e.getMessage() + " (session open: " + 
                    (this._session != null && this._session.isOpen()) + ")", 
                    PluginServerDebugWindow.LogType.ERROR);
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (this._debugWindow != null) {
                this._debugWindow.addLog("Unexpected error sending message: " + e.getMessage(), PluginServerDebugWindow.LogType.ERROR);
            }
        }
    }
}
