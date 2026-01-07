package io.github.totchi_lagawi.scratchomised_plugin;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import org.yaml.snakeyaml.Yaml;

public class LanguageManager implements PropertyChangeListener {
    private String _defaultLang;
    private Map<String, Translation> _translations = new HashMap<String, Translation>();

    public LanguageManager(String defaultLang) {
        // Normaliser la langue au démarrage
        if (defaultLang != null) {
            if (defaultLang.startsWith("fr")) {
                this._defaultLang = "fr";
            } else if (defaultLang.startsWith("en")) {
                this._defaultLang = "en";
            } else {
                this._defaultLang = "en";
            }
        } else {
            this._defaultLang = "en";
        }
    }

    public String getString(String id) {
        return getString(id, this._defaultLang);
    }

    public String getString(String id, String lang) {
        if (lang == null) {
            throw new IllegalArgumentException(
                    "Lang can't be null. Please use getString(String id) for automatic language selection");
        }
        // Normaliser la langue (lang ne peut pas être null ici)
        String normalizedLang = lang;
        if (lang.startsWith("fr")) {
            normalizedLang = "fr";
        } else if (lang.startsWith("en")) {
            normalizedLang = "en";
        } else {
            normalizedLang = "en";
        }
        try {
            this.loadTranslation(normalizedLang, false);
            Translation translation = this._translations.get(normalizedLang);
            if (translation != null) {
                try {
                    String result = translation.getTranslation(id);
                    if (result != null && !result.isEmpty()) {
                        return result;
                    }
                } catch (IllegalAccessException ex) {
                    ex.printStackTrace();
                } catch (NoSuchElementException ex) {
                    // La clé n'existe pas dans cette traduction, essayer avec "en"
                    if (!normalizedLang.equals("en")) {
                        try {
                            this.loadTranslation("en", false);
                            Translation enTranslation = this._translations.get("en");
                            if (enTranslation != null) {
                                try {
                                    String result = enTranslation.getTranslation(id);
                                    if (result != null && !result.isEmpty()) {
                                        return result;
                                    }
                                } catch (Exception ex2) {
                                    // Ignorer
                                }
                            }
                        } catch (Exception ex2) {
                            // Ignorer
                        }
                    }
                }
            }
        } catch (NoSuchElementException e) {
            // Si la langue n'existe pas, essayer avec "en" comme fallback
            if (!normalizedLang.equals("en")) {
                try {
                    this.loadTranslation("en", false);
                    Translation translation = this._translations.get("en");
                    if (translation != null) {
                        try {
                            String result = translation.getTranslation(id);
                            if (result != null && !result.isEmpty()) {
                                return result;
                            }
                        } catch (Exception ex) {
                            // Ignorer
                        }
                    }
                } catch (Exception ex) {
                    // Ignorer les erreurs du fallback
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        // Si tout échoue, retourner des valeurs par défaut
        if (id.equals("name")) {
            return "Scratchomised";
        }
        if (id.equals("menus.server.start")) {
            return "Start the server";
        }
        if (id.equals("menus.server.stop")) {
            return "Stop the server";
        }
        if (id.equals("menus.export")) {
            return "Export...";
        }
        return "";
    }

    public void setDefaultLang(String lang) {
        // Normaliser la langue
        String normalizedLang = lang;
        if (lang != null) {
            if (lang.startsWith("fr")) {
                normalizedLang = "fr";
            } else if (lang.startsWith("en")) {
                normalizedLang = "en";
            } else {
                normalizedLang = "en";
            }
        } else {
            normalizedLang = "en";
        }
        try {
            this.loadTranslation(normalizedLang, false);
            this._defaultLang = normalizedLang;
        } catch (Exception e) {
            // Si la langue échoue, essayer avec "en" comme fallback
            if (!normalizedLang.equals("en")) {
                try {
                    this.loadTranslation("en", false);
                    this._defaultLang = "en";
                } catch (Exception ex) {
                    // Ignorer les erreurs du fallback
                    this._defaultLang = "en";
                }
            } else {
                this._defaultLang = "en";
            }
        }
    }

    public String getDefaultLang() {
        return this._defaultLang;
    }

    public void loadTranslation(String lang, boolean replace) {
        // Check if it is really useful to load the translation
        if (!replace) {
            if (this._translations.containsKey(lang)) {
                return;
            }
        }

        // Normaliser la langue : extraire le code de langue de base (ex: "fr_FR" -> "fr")
        String normalizedLang = lang;
        if (lang != null) {
            if (lang.startsWith("fr")) {
                normalizedLang = "fr";
            } else if (lang.startsWith("en")) {
                normalizedLang = "en";
            } else {
                normalizedLang = "en";
            }
        } else {
            normalizedLang = "en";
        }

        // Load the translation
        InputStream file_stream = this.getClass().getResourceAsStream("translations/" + normalizedLang + ".yaml");

        // Ensure that the translation file actually exists
        if (file_stream == null) {
            throw new NoSuchElementException("The translation file for " + normalizedLang + " was not found");
        }

        try {
            Yaml yaml = new Yaml();
            Translation translation = (Translation) yaml.loadAs(file_stream, Translation.class);
            if (translation == null) {
                throw new NoSuchElementException("Failed to parse translation file for " + normalizedLang);
            }
            this._translations.put(normalizedLang, translation);
        } finally {
            try {
                if (file_stream != null) {
                    file_stream.close();
                }
            } catch (Exception e) {
                // Ignorer les erreurs de fermeture
            }
        }
    }

    public void propertyChange(PropertyChangeEvent event) {
        if (event.getPropertyName().equals("LANGUAGE")) {
            this.setDefaultLang((String) event.getNewValue());
        }
    }
}
