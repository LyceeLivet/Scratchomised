// TODO : add functions for the protocol, plus a function to filter objects

// Class representing the extension
class Scratchomised {
    constructor(runtime) {
        this.runtime = runtime;
        this.port = 55125;
        this.server = "localhost";
        this.protocol = "ws";
        this.socket = null;
        this._reconnectSocket();
        this._objects = {};
        this._clickedSwitches = {}; // Map pour stocker l'état des clics sur les switches
        this._prefix = "[Scratchomised] - ";
        this._menuVersion = 0; // Compteur pour forcer le rafraîchissement des menus
        this._reconnectAttempts = 0;
        this._maxReconnectAttempts = 10;
        this._reconnectDelay = 1000; // Délai initial de 1 seconde
        this._shouldReconnect = true;
        // Détecter la langue (français ou anglais par défaut)
        try {
            const lang = (typeof navigator !== 'undefined' && navigator.language) 
                ? navigator.language.substring(0, 2).toLowerCase() 
                : 'en';
            this._locale = (lang === 'fr') ? 'fr' : 'en';
        } catch (e) {
            this._locale = 'en';
        }
    }

    // Helper pour obtenir le texte traduit
    _t(texts) {
        if (typeof texts === 'string') {
            return texts;
        }
        return texts[this._locale] || texts['en'] || Object.values(texts)[0];
    }

    // Give informations to Scratch about the extension
    getInfo() {
        return {
            // Identifier of the extension (unique)
            id: "scratchomised",

            // Name of the extension, as shown in Scratch
            name: "Scratchomised",

            // Blocks shown in Sratch
            blocks: [
                // -- HAT BLOCKS (Events) --
                {
                    opcode: "whenPropertyIs",
                    blockType: "hat",
                    text: this._t({
                        en: "when [OBJECT] is [VALUE]",
                        fr: "quand le [OBJECT] est [VALUE]"
                    }),
                    arguments: {
                        OBJECT: {
                            type: "string",
                            menu: "objects"
                        },
                        VALUE: {
                            type: "string"
                        }
                    }
                },
                // -- COMMAND BLOCKS (Actions) --
                {
                    opcode: "setLightState",
                    blockType: "command",
                    text: this._t({
                        en: "Set [LIGHT] to [STATE]",
                        fr: "Mettre [LIGHT] à [STATE]"
                    }),
                    arguments: {
                        STATE: {
                            type: "string",
                            defaultValue: ""
                        },
                        LIGHT: {
                            type: "string",
                            menu: "objectsPowerableLights"
                        }
                    }
                },
                {
                    opcode: "objectsLightsTurnOn",
                    blockType: "command",
                    text: this._t({
                        en: "Turn on [POWERABLE_LIGHT]",
                        fr: "Allumer [POWERABLE_LIGHT]"
                    }),
                    arguments: {
                        POWERABLE_LIGHT: {
                            type: "string",
                            menu: "objectsPowerableLights"
                        }
                    }
                },
                {
                    opcode: "objectsLightsTurnOff",
                    blockType: "command",
                    text: this._t({
                        en: "Turn off [POWERABLE_LIGHT]",
                        fr: "Éteindre [POWERABLE_LIGHT]"
                    }),
                    arguments: {
                        POWERABLE_LIGHT: {
                            type: "string",
                            menu: "objectsPowerableLights"
                        }
                    }
                },
                {
                    opcode: "connectToServer",
                    blockType: "command",
                    text: this._t({
                        en: "Connect to server [SERVER] with port [PORT] using protocol [PROTOCOL]",
                        fr: "Se connecter au serveur [SERVER] sur le port [PORT] avec le protocole [PROTOCOL]"
                    }),
                    arguments: {
                        SERVER: {
                            type: "string"
                        },
                        PORT: {
                            type: "number"
                        },
                        PROTOCOL: {
                            type: "string",
                            menu: "connection_protocols"
                        }
                    }
                },
                {
                    opcode: "reconnectToServer",
                    blockType: "command",
                    text: this._t({
                        en: "Reconnect to the current server",
                        fr: "Reconnecter au serveur actuel"
                    })
                },
                {
                    opcode: "resetExtension",
                    blockType: "command",
                    text: this._t({
                        en: "Reset extension",
                        fr: "Réinitialiser l'extension"
                    })
                },
                // -- BOOLEAN BLOCKS --
                {
                    opcode: "isLightOn",
                    blockType: "Boolean",
                    text: this._t({
                        en: "on",
                        fr: "allumé"
                    }),
                    disableMonitor: true
                },
                {
                    opcode: "isLightOff",
                    blockType: "Boolean",
                    text: this._t({
                        en: "off",
                        fr: "éteint"
                    }),
                    disableMonitor: true
                },
                {
                    opcode: "isSwitchClicked",
                    blockType: "Boolean",
                    text: this._t({
                        en: "[SWITCH] is clicked",
                        fr: "[SWITCH] est cliqué"
                    }),
                    arguments: {
                        SWITCH: {
                            type: "string",
                            menu: "objectsSwitches"
                        }
                    }
                },
                {
                    opcode: "isConnectedToServer",
                    blockType: "Boolean",
                    text: this._t({
                        en: "Is connected to server?",
                        fr: "Est connecté au serveur ?"
                    })
                },
                // -- REPORTER BLOCKS (Variables) --
                {
                    opcode: "getServerAddress",
                    blockType: "reporter",
                    text: this._t({
                        en: "Server address",
                        fr: "Adresse du serveur"
                    })
                },
                {
                    opcode: "getServerPort",
                    blockType: "reporter",
                    text: this._t({
                        en: "Server port",
                        fr: "Port du serveur"
                    })
                },
                {
                    opcode: "getServerProtocol",
                    blockType: "reporter",
                    text: this._t({
                        en: "Server protocol",
                        fr: "Protocole du serveur"
                    })
                },
                {
                    opcode: "getObjectsCount",
                    blockType: "reporter",
                    text: this._t({
                        en: "Number of objects",
                        fr: "Nombre d'objets"
                    }),
                    disableMonitor: false
                }
            ],

            // Define the extension's menus
            menus: {

                // "Properties" menu
                // It is a static menu
                properties: {
                    // Menu elements
                    items: "getProperties"
                },
                objects: {
                    // The menu "object" is a dynamic menu
                    // Instead of giving an array, the name of a function is given
                    // This function will be called each time the menu is opened
                    // It will return an array containing the menu's elements
                    //
                    // /!\ WARNING /!\
                    // Due to a strange behaviour of the TurboWarp VM, dynamic menus only works
                    // in unsandboxed extensions
                    items: "getObjects"
                },
                objectsPowerableLights: {
                    items: "getObjectsPowerableLights"
                },
                objectsSwitches: {
                    items: "getObjectsSwitches"
                },
                connection_protocols: {
                    items: [{
                        text: "WS",
                        value: "ws"
                    },
                    {
                        text: "WSS",
                        value: "wss"
                    }]
                }
            }
        }
    }

    // TODO
    whenPropertyIs(args) {
        try {
            // Vérifier si l'objet existe et si sa propriété "color" correspond à la valeur
            // Pour simplifier, on compare avec "on"/"off" ou les valeurs de couleur
            if (this._objects[args.OBJECT]) {
                const obj = this._objects[args.OBJECT];
                const valueStr = String(args.VALUE).toLowerCase().trim();
                
                // Vérifier si c'est "on" ou "allumé" (couleur jaune)
                if (valueStr === "on" || valueStr === "allumé" || valueStr === "true" || valueStr === "1" || valueStr === "yes" || valueStr === "oui") {
                    // Vérifier si la couleur est jaune (0xFFFF00 ou -256)
                    return obj.color === 16776960 || obj.color === -256 || obj.color === 0xFFFF00;
                } else {
                    // Sinon, vérifier si la couleur est null ou absente (éteint)
                    return obj.color == null || obj.color === undefined;
                }
            }
            return false;
        } catch (error) {
            console.error(this._prefix + "error in whenPropertyIs: " + error);
            return false;
        }
    }

    setLightState(args) {
        // Convertir la valeur du champ libre en boolean
        // Accepte "on", "allumé", "true", "1", etc. pour allumé
        const stateStr = String(args.STATE).toLowerCase().trim();
        const isOn = stateStr === "on" || stateStr === "allumé" || stateStr === "true" || stateStr === "1" || stateStr === "yes" || stateStr === "oui";
        const colorValue = isOn ? "0xFFFF00" : "null";
        this._send("define_property", {
            object: args.LIGHT,
            property: "color",
            value: colorValue
        });
    }

    isLightOn() {
        // Bloc boolean qui retourne true (allumé)
        return true;
    }

    isLightOff() {
        // Bloc boolean qui retourne false (éteint)
        return false;
    }

    isSwitchClicked(args) {
        // Retourner l'état de clic et le réinitialiser (comme dans ScratchHome2.0)
        // Cela consomme le clic : une fois lu, il est remis à false
        const objectId = args.SWITCH;
        const wasClicked = this._clickedSwitches[objectId] === true;
        // Réinitialiser l'état après lecture
        this._clickedSwitches[objectId] = false;
        return wasClicked;
    }
    

    defineProperty(args) {
        this._send("define_property", {
            object: args.OBJECT,
            property: args.PROPERTY,
            value: args.VALUE
        });
    }


    getObjects() {
        console.log(this._prefix + "getObjects() called, objects count: " + Object.keys(this._objects).length);
        if (Object.keys(this._objects).length == 0) {
            return ["[No objects]"];
        }

        let objects = [];

        let ids = Object.keys(this._objects);
        for (let i = 0; i < ids.length; i++) {
            if (this._objects[ids[i]] && this._objects[ids[i]].name) {
                objects.push({
                    text: this._objects[ids[i]].name,
                    value: ids[i]
                });
            }
        }

        if (objects.length == 0) {
            console.warn(this._prefix + "looks like some objects are stored but their names can't be retrieved");
            return ["[No objects]"];
        }

        console.log(this._prefix + "getObjects() returning " + objects.length + " objects");
        return objects;
    }

    getObjectsPowerableLights() {
        let lights = this._sortObjects("com.eteks.sweethome3d.model.HomeLight");
        if (lights.length == 0) {
            return ["[No lights]"];
        }
        return lights;
    }

    getObjectsSwitches() {
        let switches = [];
        
        let ids = Object.keys(this._objects);
        for (let i = 0; i < ids.length; i++) {
            if (this._objects[ids[i]] && this._objects[ids[i]].name) {
                const objName = this._objects[ids[i]].name.toLowerCase();
                // Filtrer les objets dont le nom contient "switch" ou "interrupteur"
                if (objName.includes("switch") || objName.includes("interrupteur")) {
                    switches.push({
                        text: this._objects[ids[i]].name,
                        value: ids[i]
                    });
                }
            }
        }
        
        if (switches.length == 0) {
            return ["[No switches]"];
        }
        return switches;
    }

    getProperties() {
        console.log(this._prefix + "getProperties() called");
        
        // Liste statique des propriétés essentielles qui doivent toujours apparaître
        const essentialProperties = [
            "color", "shininess", "power", "lightColor",
            "x", "y", "z", "angle", "width", "depth", "height",
            "visible", "locked", "name", "id", "model", "texture"
        ];
        
        let properties = [...essentialProperties]; // Commencer avec les propriétés essentielles

        if (Object.keys(this._objects).length == 0) {
            // Même sans objets, retourner les propriétés essentielles
            return properties;
        }

        // Ajouter toutes les propriétés trouvées dans les objets
        let ids = Object.keys(this._objects);
        for (let i = 0; i < ids.length; i++) {
            if (this._objects[ids[i]]) {
                let current_properties = Object.keys(this._objects[ids[i]]);
                for (let u = 0; u < current_properties.length; u++) {
                    // Ignorer les propriétés internes
                    if (current_properties[u] !== "__scratchomisedClasses" && 
                        !properties.includes(current_properties[u])) {
                        properties.push(current_properties[u]);
                    }
                }
            }
        }

        // Trier les propriétés pour un affichage cohérent
        properties.sort();

        console.log(this._prefix + "getProperties() returning " + properties.length + " properties: " + properties.join(", "));
        return properties;
    }

    objectsLightsTurnOn(args) {
        // Allumer: mettre la couleur en jaune (0xFFFF00)
        this.defineProperty({
            OBJECT: args.POWERABLE_LIGHT,
            PROPERTY: "color",
            VALUE: "0xFFFF00"
        })
    }

    objectsLightsTurnOff(args) {
        // Éteindre: réinitialiser la couleur (null pour revenir à la texture d'origine)
        this.defineProperty({
            OBJECT: args.POWERABLE_LIGHT,
            PROPERTY: "color",
            VALUE: "null"
        })
    }

    isConnectedToServer(args) {
        if (this.socket && this.socket.readyState == WebSocket.OPEN) {
            return true;
        } else {
            return false;
        }
    }

    getServerAddress(args) {
        return this.server;
    }

    getServerPort(args) {
        return this.port;
    }

    getServerProtocol(args) {
        return this.protocol;
    }

    getObjectsCount(args) {
        return Object.keys(this._objects).length;
    }

    reconnectToServer(args) {
        // Permettre la reconnexion même après un reset
        this._shouldReconnect = true;
        this._reconnectAttempts = 0;
        this._reconnectDelay = 1000;
        this._reconnectSocket();
    }

    resetExtension(args) {
        // Fermer la connexion WebSocket
        if (this.socket) {
            try {
                // Empêcher la reconnexion automatique
                this._shouldReconnect = false;
                
                // Retirer les event listeners
                if (this._onCloseHandler) {
                    this.socket.removeEventListener("close", this._onCloseHandler);
                }
                if (this._onErrorHandler) {
                    this.socket.removeEventListener("error", this._onErrorHandler);
                }
                
                // Fermer la connexion proprement
                if (this.socket.readyState === WebSocket.OPEN || this.socket.readyState === WebSocket.CONNECTING) {
                    this.socket.close(1000, "Reset requested");
                }
            } catch (error) {
                console.error(this._prefix + "error closing socket during reset: " + error);
            }
            this.socket = null;
        }
        
        // Vider la liste des objets
        this._objects = {};
        this._clickedSwitches = {}; // Réinitialiser aussi les clics
        
        // Réinitialiser les variables
        this._menuVersion = 0;
        this._reconnectAttempts = 0;
        this._reconnectDelay = 1000;
        this._shouldReconnect = false; // Empêcher la reconnexion automatique, mais reconnectToServer/connectToServer peuvent la réactiver
        
        // Incrémenter menuVersion pour forcer la mise à jour des menus
        this._menuVersion++;
        
        console.log(this._prefix + "extension reset: connection closed and objects cleared (menu version: " + this._menuVersion + ")");
    }

    connectToServer(args) {
        // Permettre la reconnexion même après un reset
        this._shouldReconnect = true;
        this.server = args.SERVER;
        this.port = args.PORT;
        this.protocol = args.PROTOCOL;
        this._reconnectSocket();
    }

    _sortObjects(className) {
        let objects = [];
        
        let ids = Object.keys(this._objects);
        for (let i = 0; i < ids.length; i++) {
            if (this._objects[ids[i]] && 
                this._objects[ids[i]]["__scratchomisedClasses"] && 
                this._objects[ids[i]]["__scratchomisedClasses"].includes(className)) {
                objects.push({
                    text: this._objects[ids[i]].name,
                    value: ids[i]
                });
            }
        }

        if (objects.length == 0) {
            console.warn(this._prefix + "looks like some objects are stored but their names can't be retrieved");
            return ["[No objects]"];
        }

        return objects;
    }

    // Function to send data to the server
    _send(action, args = {}) {
        try {
            if (!this.socket) {
                console.warn(this._prefix + "cannot send data: socket is null");
                return;
            }
            if (this.socket.readyState !== WebSocket.OPEN) {
                console.warn(this._prefix + "cannot send data: socket state is " + this.socket.readyState + " (expected " + WebSocket.OPEN + ")");
                return;
            }
            const message = JSON.stringify({
                action: action,
                args: args
            });
            console.log(this._prefix + "sending message: " + action + " (size: " + message.length + " chars)");
            this.socket.send(message);
            console.log(this._prefix + "message sent successfully: " + action);
        } catch (error) {
            console.error(this._prefix + "error while sending data : \n" + error);
            console.error(this._prefix + "error stack: " + (error.stack || "no stack trace"));
        }
    }

    // Reconnect the socket (in case it failed to connect, or the server disconnected)
    _reconnectSocket() {
        // Close existing socket if any
        if (this.socket) {
            try {
                if (this._onCloseHandler) {
                    this.socket.removeEventListener("close", this._onCloseHandler);
                }
                if (this._onErrorHandler) {
                    this.socket.removeEventListener("error", this._onErrorHandler);
                }
                this.socket.close();
            } catch (error) {
                // Ignore errors when closing
            }
        }
        
        if (!this._shouldReconnect) {
            return;
        }
        
        try {
            const wsUrl = this.protocol + "://" + this.server + ":" + this.port;
            console.log(this._prefix + "attempting to connect to: " + wsUrl + " with protocol 'scratchomised'");
            this.socket = new WebSocket(wsUrl, "scratchomised");
            console.log(this._prefix + "WebSocket created, initial readyState: " + this.socket.readyState);
            this.socket.addEventListener("message", this._handleIncomingData.bind(this));
            
            this.socket.addEventListener("open", () => {
                console.log(this._prefix + "connected to server, readyState: " + this.socket.readyState);
                this._reconnectAttempts = 0; // Réinitialiser le compteur en cas de succès
                this._reconnectDelay = 1000; // Réinitialiser le délai
                
                // Envoyer un message de confirmation pour maintenir la connexion active
                // Utiliser un petit délai pour s'assurer que la connexion est complètement établie
                setTimeout(() => {
                    try {
                        if (this.socket && this.socket.readyState === WebSocket.OPEN) {
                            console.log(this._prefix + "sending client_ready message");
                            this._send("client_ready", {});
                            console.log(this._prefix + "client_ready message sent");
                        } else {
                            console.warn(this._prefix + "cannot send client_ready: socket state is " + (this.socket ? this.socket.readyState : "null"));
                        }
                    } catch (error) {
                        console.error(this._prefix + "error sending client_ready: " + error);
                        console.error(this._prefix + "error stack: " + (error.stack || "no stack trace"));
                    }
                }, 50); // Délai de 50ms pour s'assurer que la connexion est stable
            });
            
            // Gérer les erreurs
            this._onErrorHandler = (error) => {
                console.error(this._prefix + "WebSocket error: " + error);
                if (error && error.message) {
                    console.error(this._prefix + "WebSocket error message: " + error.message);
                }
                if (error && error.type) {
                    console.error(this._prefix + "WebSocket error type: " + error.type);
                }
                if (error && error.target) {
                    console.error(this._prefix + "WebSocket error target readyState: " + error.target.readyState);
                }
                // Ne pas fermer la connexion en cas d'erreur, laisser la reconnexion automatique gérer
            };
            this.socket.addEventListener("error", this._onErrorHandler);
            
            // Gérer la fermeture avec reconnexion automatique
            this._onCloseHandler = (event) => {
                console.log(this._prefix + "disconnected from server (code: " + event.code + ", reason: " + event.reason + ", wasClean: " + event.wasClean + ")");
                
                // Ne pas reconnecter si c'est une fermeture intentionnelle (code 1000)
                if (event.code === 1000 && event.wasClean) {
                    console.log(this._prefix + "clean close, not reconnecting");
                    this._shouldReconnect = false;
                    return;
                }
                
                // Si la fermeture est trop rapide (< 1 seconde), attendre un peu plus avant de reconnecter
                const wasQuickClose = event.code !== 1000;
                
                // Reconnexion automatique avec délai exponentiel
                if (this._shouldReconnect && this._reconnectAttempts < this._maxReconnectAttempts) {
                    this._reconnectAttempts++;
                    let delay = Math.min(this._reconnectDelay * Math.pow(2, this._reconnectAttempts - 1), 30000); // Max 30 secondes
                    
                    // Si la fermeture était rapide, attendre un peu plus
                    if (wasQuickClose && this._reconnectAttempts === 1) {
                        delay = 2000; // Attendre 2 secondes pour la première reconnexion après une fermeture rapide
                    }
                    
                    console.log(this._prefix + "reconnecting in " + delay + "ms (attempt " + this._reconnectAttempts + "/" + this._maxReconnectAttempts + ")");
                    
                    setTimeout(() => {
                        if (this._shouldReconnect) {
                            this._reconnectSocket();
                        }
                    }, delay);
                } else if (this._reconnectAttempts >= this._maxReconnectAttempts) {
                    console.error(this._prefix + "max reconnection attempts reached, stopping reconnection");
                }
            };
            this.socket.addEventListener("close", this._onCloseHandler);
            
        } catch (error) {
            console.error(this._prefix + "error creating WebSocket: " + error);
            // Essayer de reconnecter après un délai
            if (this._shouldReconnect && this._reconnectAttempts < this._maxReconnectAttempts) {
                this._reconnectAttempts++;
                setTimeout(() => {
                    if (this._shouldReconnect) {
                        this._reconnectSocket();
                    }
                }, this._reconnectDelay);
            }
        }
    }

    // Handle data sent by the server
    _handleIncomingData(event) {
        // Wrapper global pour éviter que les erreurs ne ferment la connexion
        try {
            // Vérifier si les données sont valides
            if (!event || !event.data) {
                console.error(this._prefix + "received invalid event data");
                return;
            }
            
            let dataLength = event.data.length;
            console.log(this._prefix + "received message, size: " + dataLength + " chars");
            
            let message;
            try {
                message = JSON.parse(event.data);
                console.log(this._prefix + "message parsed successfully, action: " + (message ? message.action : "null"));
            } catch (error) {
                console.error(this._prefix + "unable to parse message : \n" + (event.data ? event.data.substring(0, 200) + "..." : "null") + "\n" + this._prefix + " got error : " + error);
                // Ne pas fermer la connexion en cas d'erreur de parsing
                return;
            }

            if (!message || !message.action) {
                console.warn(this._prefix + "message received but no action field");
                return;
            }

            // Traiter le message de manière asynchrone pour éviter de bloquer
            setTimeout(() => {
                try {
                    this._processMessage(message);
                } catch (error) {
                    console.error(this._prefix + "error in async message processing: " + error);
                    console.error(this._prefix + "error stack: " + (error.stack || "no stack trace"));
                }
            }, 0);
        } catch (error) {
            console.error(this._prefix + "critical error in _handleIncomingData: " + error);
            console.error(this._prefix + "error stack: " + (error.stack || "no stack trace"));
            // Ne pas fermer la connexion même en cas d'erreur critique
        }
    }

    // Traiter le message de manière séparée
    _processMessage(message) {
        switch (message.action) {
            case "test": {
                console.log(this._prefix + "received test message from server: " + (message.args ? message.args.message : "no message"));
                // Répondre au serveur pour confirmer la connexion
                this._send("test_ack", {});
                break;
            }
            case "welcome": {
                console.log(this._prefix + "received welcome message from server");
                // Répondre au serveur pour confirmer la connexion
                this._send("welcome_ack", {});
                break;
            }
            case "update_objects": {
                if (!message.args || !message.args.objects) {
                    console.error(this._prefix + "missing argument for action update_objects : objects");
                    return;
                }
                try {
                    console.log(this._prefix + "processing update_objects with " + message.args.objects.length + " objects");
                    
                    // Simplifier : stocker directement les objets sans transformation complexe
                    // Utiliser JSON.parse/stringify pour créer une copie profonde et éviter les références circulaires
                    const objectsStr = JSON.stringify(message.args.objects);
                    const objectsCopy = JSON.parse(objectsStr);
                    
                    const newObjects = {};
                    let processedCount = 0;
                    
                    // Traiter les objets de manière simple
                    for (let i = 0; i < objectsCopy.length; i++) {
                        try {
                            const obj = objectsCopy[i];
                            if (obj && obj["id"]) {
                                // Stocker l'objet tel quel (déjà nettoyé par JSON.parse/stringify)
                                newObjects[obj.id] = obj;
                                processedCount++;
                            }
                        } catch (objError) {
                            console.error(this._prefix + "error processing object " + i + ": " + objError);
                        }
                    }
                    
                    // Remplacer les objets seulement si le traitement a réussi
                    this._objects = newObjects;
                    
                    // Incrémenter le compteur pour signaler que les menus doivent être réévalués
                    this._menuVersion++;
                    console.log(this._prefix + "updated " + processedCount + " objects (menu version: " + this._menuVersion + ")");
                } catch (error) {
                    console.error(this._prefix + "error processing update_objects: " + error);
                    console.error(this._prefix + "error stack: " + (error.stack || "no stack trace"));
                    // Ne pas fermer la connexion en cas d'erreur
                }
                break;
            }
            case "object_clicked": {
                // Message reçu quand un objet est cliqué dans SweetHome3D
                if (message.args && message.args.object_id) {
                    const objectId = message.args.object_id;
                    console.log(this._prefix + "object clicked: " + objectId);
                    // Marquer l'objet comme cliqué
                    this._clickedSwitches[objectId] = true;
                }
                break;
            }
            default: {
                console.warn(this._prefix + "unknown action : " + message.action);
            }
        }
    }
}

(function() {
    var extensionClass = Scratchomised;
    if (typeof window === "undefined" || !window.vm) {
        Scratch.extensions.register(new extensionClass());
    } else {
        var extensionInstance = new extensionClass(window.vm.extensionManager.runtime);
        var serviceName = window.vm.extensionManager._registerInternalExtension(extensionInstance);
        window.vm.extensionManager._loadedExtensions.set(extensionInstance.getInfo().id, serviceName);
    }
})();