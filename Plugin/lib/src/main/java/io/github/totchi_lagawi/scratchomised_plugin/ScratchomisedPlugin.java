package io.github.totchi_lagawi.scratchomised_plugin;

import com.eteks.sweethome3d.model.UserPreferences;
import com.eteks.sweethome3d.model.UserPreferences.Property;
import com.eteks.sweethome3d.plugin.Plugin;
import com.eteks.sweethome3d.plugin.PluginAction;

public class ScratchomisedPlugin extends Plugin {
    private LanguageManager _languageManager;

    @Override
    public PluginAction[] getActions() {
        try {
            UserPreferences userPreferences = getUserPreferences();
            String language = userPreferences.getLanguage();
            this._languageManager = new LanguageManager(language);
            
            PluginActionManageServer pluginActionManageServer = new PluginActionManageServer(this._languageManager,
                    this.getHome(), this.getHomeController());
            PluginActionExportDatas pluginActionExportDatas = new PluginActionExportDatas(
                    this._languageManager, this.getHome(), this.getHomeController());
            userPreferences.addPropertyChangeListener(Property.LANGUAGE, this._languageManager);
            userPreferences.addPropertyChangeListener(Property.LANGUAGE, pluginActionManageServer);
            userPreferences.addPropertyChangeListener(Property.LANGUAGE, pluginActionExportDatas);
            return new PluginAction[] {
                    pluginActionManageServer,
                    pluginActionExportDatas
            };
        } catch (Exception e) {
            // En cas d'erreur lors du chargement du plugin, afficher l'erreur mais ne pas empêcher SweetHome3D de démarrer
            System.err.println("[Scratchomised] Error loading plugin: " + e.getMessage());
            System.err.println("[Scratchomised] Stack trace:");
            e.printStackTrace();
            // Retourner un tableau vide pour permettre à SweetHome3D de démarrer
            return new PluginAction[0];
        }
    }
}