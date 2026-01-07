package io.github.totchi_lagawi.scratchomised_plugin;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.HashMap;
import java.util.function.Consumer;

import com.eteks.sweethome3d.model.HomePieceOfFurniture;
import com.eteks.sweethome3d.model.Selectable;
import com.eteks.sweethome3d.swing.HomeComponent3D;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Mouse Listener pour détecter les clics sur les objets dans la vue 3D
 * et envoyer les événements via WebSocket, ainsi que modifier le champ 'power' des switches
 */
public class PluginClickListener implements MouseListener {
    private Consumer<String> _clickMessageCallback;
    private PluginServerDebugWindow _debugWindow;
    
    public PluginClickListener(Consumer<String> clickMessageCallback, PluginServerDebugWindow debugWindow) {
        this._clickMessageCallback = clickMessageCallback;
        this._debugWindow = debugWindow;
    }
    
    @Override
    public void mouseClicked(MouseEvent e) {
        int x = e.getX();
        int y = e.getY();
        HomeComponent3D comp = (HomeComponent3D) e.getSource();
        
        // Obtenir l'objet le plus proche du point cliqué (utiliser la méthode non dépréciée)
        Selectable closestSelectable = comp.getClosestSelectableItemAt(x, y);
        
        // Si c'est un HomePieceOfFurniture, vérifier si c'est un switch
        if (closestSelectable instanceof HomePieceOfFurniture) {
            HomePieceOfFurniture piece = (HomePieceOfFurniture) closestSelectable;
            
            // Vérifier si c'est un switch (nom ou catalogId contient "switch" ou "interrupteur")
            if (isSwitch(piece)) {
                String objectId = piece.getId();
                
                if (this._debugWindow != null) {
                    this._debugWindow.addLog("Switch clicked: " + piece.getName() + " (id: " + objectId + ")", 
                            PluginServerDebugWindow.LogType.INFO);
                }
                
                // Modifier le champ 'power' : alterner entre 0 et 1
                try {
                    // Utiliser la réflexion pour trouver et appeler setPower
                    java.lang.reflect.Method setPowerMethod = null;
                    try {
                        setPowerMethod = piece.getClass().getMethod("setPower", Float.class);
                    } catch (NoSuchMethodException ex) {
                        // Essayer avec float primitif
                        try {
                            setPowerMethod = piece.getClass().getMethod("setPower", float.class);
                        } catch (NoSuchMethodException ex2) {
                            // Pas de méthode setPower
                        }
                    }
                    
                    if (setPowerMethod != null) {
                        // Obtenir la valeur actuelle de power
                        java.lang.reflect.Method getPowerMethod = piece.getClass().getMethod("getPower");
                        Float currentPower = (Float) getPowerMethod.invoke(piece);
                        
                        // Alterner entre 0 et 1
                        Float newPower = (currentPower == null || currentPower == 0.0f) ? 1.0f : 0.0f;
                        setPowerMethod.invoke(piece, newPower);
                        
                        if (this._debugWindow != null) {
                            this._debugWindow.addLog("Power changed to: " + newPower, PluginServerDebugWindow.LogType.INFO);
                        }
                    }
                } catch (Exception ex) {
                    if (this._debugWindow != null) {
                        this._debugWindow.addLog("Error changing power: " + ex.getMessage(), PluginServerDebugWindow.LogType.ERROR);
                    }
                    ex.printStackTrace();
                }
                
                // Envoyer le message via le callback
                if (this._clickMessageCallback != null) {
                    this._clickMessageCallback.accept(objectId);
                }
            }
        }
    }
    
    /**
     * Vérifie si un objet est un switch (interrupteur)
     */
    @SuppressWarnings("unchecked")
    private boolean isSwitch(HomePieceOfFurniture furniture) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            HashMap<String, Object> mappedObject = (HashMap<String, Object>) mapper.convertValue(furniture, HashMap.class);
            
            // Vérifier le catalogId (identifiant de référencement)
            if (mappedObject.containsKey("catalogId") && mappedObject.get("catalogId") != null) {
                String catalogId = mappedObject.get("catalogId").toString().toLowerCase();
                if (catalogId.contains("switch") || catalogId.contains("interrupteur")) {
                    return true;
                }
            }
            
            // Vérifier le nom de l'objet
            if (mappedObject.containsKey("name") && mappedObject.get("name") != null) {
                String objectName = mappedObject.get("name").toString().toLowerCase();
                if (objectName.contains("switch") || objectName.contains("interrupteur")) {
                    return true;
                }
            }
        } catch (Exception ex) {
            // En cas d'erreur, ne pas considérer comme switch
            ex.printStackTrace();
        }
        
        return false;
    }
    
    // Autres méthodes requises par MouseListener (non utilisées)
    @Override
    public void mousePressed(MouseEvent e) {
    }
    
    @Override
    public void mouseReleased(MouseEvent e) {
    }
    
    @Override
    public void mouseEntered(MouseEvent e) {
    }
    
    @Override
    public void mouseExited(MouseEvent e) {
    }
}
