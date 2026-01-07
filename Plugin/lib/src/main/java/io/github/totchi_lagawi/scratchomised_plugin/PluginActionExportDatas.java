package io.github.totchi_lagawi.scratchomised_plugin;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileFilter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import com.eteks.sweethome3d.model.Home;
import com.eteks.sweethome3d.model.ObserverCamera;
import com.eteks.sweethome3d.plugin.PluginAction;
import com.eteks.sweethome3d.viewcontroller.HomeController;

/**
 * Action pour exporter le plan SweetHome3D vers un fichier SB3 (Scratch 3.0)
 * Similaire à JSONAction de ScratchHome2.0
 */
public class PluginActionExportDatas extends PluginAction implements PropertyChangeListener {
    private LanguageManager _languageManager;
    private Home _home;
    private HomeController _homeController;
    private JFileChooser _fileChooser;
    private double _svgWidth;
    private double _svgHeight;
    
    // IDs fixes pour les assets (comme dans ScratchHome2.0)
    private static final String BACKDROP_ASSET_ID = "cd21514d0531fdffb22204e0ec5ed84a";
    private static final String OBSERVER_SPRITE_ASSET_ID = "bcf454acf82e4504149f7ffe07081dbc";

    public PluginActionExportDatas(LanguageManager languageManager, Home home, HomeController homeController) {
        this._languageManager = languageManager;
        this._home = home;
        this._homeController = homeController;
        this._fileChooser = new JFileChooser();
        putPropertyValue(Property.NAME, this._languageManager.getString("menus.export"));
        putPropertyValue(Property.MENU, this._languageManager.getString("name"));
        putPropertyValue(Property.ENABLED, true);
    }

    @Override
    public void execute() {
        if (this._home.getFurniture().isEmpty()) {
            JOptionPane.showMessageDialog(null, this._languageManager.getString("export.no_objects"));
            return;
        }
        
        // Afficher la popup pour choisir le type d'export (comme dans ScratchHome2.0)
        boolean listOfObjects = false;
        
        // Forcer les valeurs par défaut pour éviter les chaînes vides
        String optionListOfObjects = "Un bloc avec la liste des objets";
        String optionBlockPerObject = "Un bloc par objet";
        String dialogMessage = "Choisissez le type d'export :";
        String dialogTitle = "Type d'export";
        
        // Essayer de récupérer les traductions, mais utiliser les valeurs par défaut si elles sont vides
        try {
            String temp = this._languageManager.getString("export.option_list_of_objects");
            if (temp != null && !temp.isEmpty()) {
                optionListOfObjects = temp;
            }
        } catch (Exception e) {}
        
        try {
            String temp = this._languageManager.getString("export.option_block_per_object");
            if (temp != null && !temp.isEmpty()) {
                optionBlockPerObject = temp;
            }
        } catch (Exception e) {}
        
        try {
            String temp = this._languageManager.getString("export.dialog_message");
            if (temp != null && !temp.isEmpty()) {
                dialogMessage = temp;
            }
        } catch (Exception e) {}
        
        try {
            String temp = this._languageManager.getString("export.dialog_title");
            if (temp != null && !temp.isEmpty()) {
                dialogTitle = temp;
            }
        } catch (Exception e) {}
        
        // Utiliser JOptionPane.showOptionDialog comme dans ScratchHome2.0
        Object[] options = { optionListOfObjects, optionBlockPerObject };
        
        int reply = JOptionPane.showOptionDialog(
            null, 
            dialogMessage, 
            dialogTitle, 
            JOptionPane.DEFAULT_OPTION, 
            JOptionPane.PLAIN_MESSAGE, 
            null, 
            options, 
            options[0] // Option par défaut : liste d'objets
        );
        
        // 0 = première option (liste d'objets), 1 = deuxième option (bloc par objet), -1 = fermé/annulé
        if (reply == JOptionPane.YES_OPTION || reply == 0) {
            listOfObjects = true;
        }
        if (reply == JOptionPane.CLOSED_OPTION || reply == -1) {
            return; // L'utilisateur a annulé ou fermé
        }
        
        try {
            if (listOfObjects) {
                // Export avec liste d'objets : plan 2D + observer sprite
                this.exportToSB3WithList();
            } else {
                // Export avec un bloc par objet (à implémenter plus tard)
                JOptionPane.showMessageDialog(null, 
                    "Export avec un bloc par objet - À implémenter",
                    this._languageManager.getString("export.error_title"),
                    JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, 
                this._languageManager.getString("export.error") + "\n" + e.getMessage(), 
                this._languageManager.getString("export.error_title"), 
                JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }
    

    /**
     * Exporte le plan vers un fichier SB3 avec liste d'objets (plan 2D + observer sprite)
     */
    private void exportToSB3WithList() throws Exception {
        // Configurer le file chooser
        this._fileChooser.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".sb3");
            }

            @Override
            public String getDescription() {
                return "Scratch 3.0 Project (*.sb3)";
            }
        });

        int result = this._fileChooser.showSaveDialog(null);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        String filePath = this._fileChooser.getSelectedFile().toString();
        if (!filePath.toLowerCase().endsWith(".sb3")) {
            filePath += ".sb3";
        }

        // Créer le fichier SB3 (ZIP) avec plan 2D et observer sprite
        this.createSB3WithPlanAndObserver(filePath);
        
        JOptionPane.showMessageDialog(null, 
            this._languageManager.getString("export.success") + "\n" + filePath,
            this._languageManager.getString("export.success_title"),
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    /**
     * Crée un fichier SB3 avec le plan 2D comme arrière-plan et l'observer comme sprite
     */
    private void createSB3WithPlanAndObserver(String filePath) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(filePath))) {
            // 1. Exporter le plan en SVG
            String tempDir = System.getProperty("java.io.tmpdir");
            String svgPath = tempDir + "/scratchomised_plan.svg";
            this.exportPlanToSVG(svgPath);
            
            // 2. Redimensionner le SVG à 480x360 (comme ScratchHome2.0)
            this.resizeSVG(svgPath);
            
            // 3. Ajouter le SVG du plan au ZIP
            this.addFileToZip(zos, svgPath, BACKDROP_ASSET_ID + ".svg");
            
            // 4. Calculer la position de l'Observer centré à (0,0)
            ObserverCamera observerCamera = this._home.getObserverCamera();
            float observerX = 0.0f; // Toujours à (0,0) - position centrée par défaut
            float observerY = 0.0f; // Toujours à (0,0) - position centrée par défaut
            float observerDirection = 90.0f; // Par défaut vers le haut (90° dans Scratch)
            
            if (observerCamera != null) {
                // Position toujours à (0,0) - centrée par défaut
                observerX = 0.0f;
                observerY = 0.0f;
                
                // Corriger l'orientation : dans Scratch, 90° = vers le haut
                // getYaw() retourne l'angle en radians, 0 = vers l'est, PI/2 = vers le nord
                // On veut que 0° dans SweetHome3D (est) = 90° dans Scratch (haut)
                // Formule : direction = 90 - (yaw * 180 / PI)
                observerDirection = (float)(90.0 - (observerCamera.getYaw() * 180.0 / Math.PI));
            }
            
            // 6. Créer le JSON du projet
            String projectJson = this.createProjectJSON(observerX, observerY, observerDirection);
            
            // 7. Ajouter le JSON au ZIP
            ZipEntry jsonEntry = new ZipEntry("project.json");
            zos.putNextEntry(jsonEntry);
            zos.write(projectJson.getBytes("UTF-8"));
            zos.closeEntry();
            
            // 8. Ajouter le SVG du sprite Observer au ZIP
            this.addObserverSpriteSVG(zos);
            
            zos.finish();
        }
    }

    /**
     * Exporte le plan en SVG (exactement comme ScratchHome2.0)
     */
    private void exportPlanToSVG(String svgPath) throws Exception {
        // Utiliser exactement la même méthode que ScratchHome2.0
        // controller.getView().exportToSVG(svgPath)
        com.eteks.sweethome3d.viewcontroller.HomeView homeView = this._homeController.getView();
        if (homeView == null) {
            throw new Exception("HomeView not available");
        }
        
        try {
            homeView.exportToSVG(svgPath);
        } catch (Exception e) {
            throw new Exception("Failed to export SVG: " + e.getMessage(), e);
        }
    }

    /**
     * Redimensionne le SVG à 480x360 (exactement comme ScratchHome2.0)
     */
    private void resizeSVG(String svgPath) throws Exception {
        // Exactement comme ScratchHome2.0 JSONAction ligne 258-293
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        Document doc = docBuilder.parse(new File(svgPath));

        // Obtenir l'élément SVG
        Node svgNode = doc.getElementsByTagName("svg").item(0);
        NamedNodeMap attr = svgNode.getAttributes();
        
        // Obtenir width (exactement comme ScratchHome2.0 ligne 271-272)
        Node widthNode = attr.getNamedItem("width");
        if (widthNode != null) {
            String widthStr = widthNode.getTextContent().replaceAll("\\D", "");
            this._svgWidth = Double.parseDouble(widthStr);
        } else {
            this._svgWidth = 480.0;
        }
        
        // Obtenir height (exactement comme ScratchHome2.0 ligne 274-275)
        Node heightNode = attr.getNamedItem("height");
        if (heightNode != null) {
            String heightStr = heightNode.getTextContent().replaceAll("\\D", "");
            this._svgHeight = Double.parseDouble(heightStr);
        } else {
            this._svgHeight = 360.0;
        }
        
        // Calculer le facteur d'échelle (exactement comme ScratchHome2.0 ligne 278-281)
        double x1 = 480.0 / this._svgWidth;
        double x2 = 360.0 / this._svgHeight;
        double xmin = Math.min(x1, x2);
        
        // Appliquer le scale au premier élément <g> (exactement comme ScratchHome2.0 ligne 284-286)
        Node gNode = doc.getElementsByTagName("g").item(0);
        if (gNode != null && gNode instanceof Element) {
            ((Element) gNode).setAttribute("transform", "scale(" + xmin + ")");
        }
        
        // Sauvegarder le SVG modifié (exactement comme ScratchHome2.0 ligne 289-293)
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(new File(svgPath));
        transformer.transform(source, result);
    }

    /**
     * Ajoute un fichier au ZIP
     */
    private void addFileToZip(ZipOutputStream zos, String filePath, String zipEntryName) throws IOException {
        ZipEntry entry = new ZipEntry(zipEntryName);
        zos.putNextEntry(entry);
        
        try (FileInputStream fis = new FileInputStream(filePath)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                zos.write(buffer, 0, bytesRead);
            }
        }
        
        zos.closeEntry();
    }

    /**
     * Crée le JSON du projet Scratch
     */
    private String createProjectJSON(float observerX, float observerY, float observerDirection) {
        // Créer le JSON du projet (similaire à ScratchHome2.0)
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"targets\":[");
        
        // Stage avec le backdrop SVG
        json.append("{");
        json.append("\"isStage\":true,");
        json.append("\"name\":\"Stage\",");
        json.append("\"variables\":{},");
        json.append("\"lists\":{},");
        json.append("\"broadcasts\":{},");
        json.append("\"blocks\":{},");
        json.append("\"comments\":{},");
        json.append("\"currentCostume\":0,");
        json.append("\"costumes\":[{");
        json.append("\"assetId\":\"").append(BACKDROP_ASSET_ID).append("\",");
        json.append("\"name\":\"arrière plan1\",");
        json.append("\"md5ext\":\"").append(BACKDROP_ASSET_ID).append(".svg\",");
        json.append("\"dataFormat\":\"svg\",");
        json.append("\"rotationCenterX\":240,");
        json.append("\"rotationCenterY\":180");
        json.append("}],");
        json.append("\"sounds\":[],");
        json.append("\"volume\":100,");
        json.append("\"layerOrder\":0,");
        json.append("\"tempo\":60,");
        json.append("\"videoTransparency\":50,");
        json.append("\"videoState\":\"on\",");
        json.append("\"textToSpeechLanguage\":null");
        json.append("},");
        
        // Sprite Observer
        json.append("{");
        json.append("\"isStage\":false,");
        json.append("\"name\":\"Observer\",");
        json.append("\"variables\":{},");
        json.append("\"lists\":{},");
        json.append("\"broadcasts\":{},");
        json.append("\"blocks\":{},");
        json.append("\"comments\":{},");
        json.append("\"currentCostume\":0,");
        json.append("\"costumes\":[{");
        json.append("\"assetId\":\"").append(OBSERVER_SPRITE_ASSET_ID).append("\",");
        json.append("\"name\":\"costume1\",");
        json.append("\"bitmapResolution\":1,");
        json.append("\"md5ext\":\"").append(OBSERVER_SPRITE_ASSET_ID).append(".svg\",");
        json.append("\"dataFormat\":\"svg\",");
        json.append("\"rotationCenterX\":8,");
        json.append("\"rotationCenterY\":8");
        json.append("}],");
        json.append("\"sounds\":[],");
        json.append("\"volume\":100,");
        json.append("\"layerOrder\":1,");
        json.append("\"visible\":true,");
        json.append("\"x\":").append(observerX).append(",");
        json.append("\"y\":").append(observerY).append(",");
        json.append("\"size\":200,");
        json.append("\"direction\":").append(observerDirection).append(",");
        json.append("\"draggable\":false,");
        json.append("\"rotationStyle\":\"all around\"");
        json.append("}");
        
        json.append("],");
        json.append("\"monitors\":[],");
        // Référencer l'extension scratchomised
        json.append("\"extensions\":[\"scratchomised\"],");
        json.append("\"meta\":{");
        json.append("\"semver\":\"3.0.0\",");
        json.append("\"vm\":\"0.2.0\",");
        json.append("\"agent\":\"Scratchomised/1.0.0\"");
        json.append("}");
        json.append("}");
        
        return json.toString();
    }

    /**
     * Ajoute le SVG du sprite Observer au ZIP
     */
    private void addObserverSpriteSVG(ZipOutputStream zos) throws IOException {
        ZipEntry entry = new ZipEntry(OBSERVER_SPRITE_ASSET_ID + ".svg");
        zos.putNextEntry(entry);
        
        // Charger le SVG depuis les ressources
        InputStream is = this.getClass().getResourceAsStream("/io/github/totchi_lagawi/scratchomised_plugin/observer_sprite.svg");
        if (is == null) {
            throw new FileNotFoundException("Observer sprite SVG not found in resources");
        }
        
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = is.read(buffer)) != -1) {
            zos.write(buffer, 0, bytesRead);
        }
        is.close();
        
        zos.closeEntry();
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
        if ("LANGUAGE".equals(event.getPropertyName())) {
            putPropertyValue(Property.MENU, this._languageManager.getString("name"));
            putPropertyValue(Property.NAME, this._languageManager.getString("menus.export"));
        }
    }
}
