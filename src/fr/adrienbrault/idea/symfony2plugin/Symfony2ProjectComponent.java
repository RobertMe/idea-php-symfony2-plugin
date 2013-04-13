package fr.adrienbrault.idea.symfony2plugin;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import fr.adrienbrault.idea.symfony2plugin.dic.ServiceMap;
import fr.adrienbrault.idea.symfony2plugin.dic.ServiceMapParser;
import fr.adrienbrault.idea.symfony2plugin.doctrine.component.EntityNamesParser;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Adrien Brault <adrien.brault@gmail.com>
 */
public class Symfony2ProjectComponent implements ProjectComponent {

    private Project project;
    private ServiceMap servicesMap;
    private Long servicesMapLastModified;

    private Long entityNamespacesMapLastModified;
    private Map<String, String> entityNamespaces;

    public Symfony2ProjectComponent(Project project) {
        this.project = project;
    }

    public void initComponent() {
        System.out.println("initComponent");
    }

    public void disposeComponent() {
        System.out.println("disposeComponent");
    }

    @NotNull
    public String getComponentName() {
        return "Symfony2ProjectComponent";
    }

    public void projectOpened() {
        System.out.println("projectOpened");
    }

    public void projectClosed() {
        System.out.println("projectClosed");
    }

    public ServiceMap getServicesMap() {
        String defaultServiceMapFilePath = Settings.getInstance(project).pathToProjectContainer;
        if (!FileUtil.isAbsolute(defaultServiceMapFilePath)) { // Project relative path
            defaultServiceMapFilePath = project.getBasePath() + "/" + defaultServiceMapFilePath;
        }

        File xmlFile = new File(defaultServiceMapFilePath);
        if (!xmlFile.exists()) {
            return new ServiceMap();
        }

        Long xmlFileLastModified = xmlFile.lastModified();
        if (xmlFileLastModified.equals(servicesMapLastModified)) {
            return servicesMap;
        }

        try {
            ServiceMapParser serviceMapParser = new ServiceMapParser();
            servicesMap = serviceMapParser.parse(xmlFile);
            servicesMapLastModified = xmlFileLastModified;

            return servicesMap;
        } catch (SAXException ignored) {
        } catch (IOException ignored) {
        } catch (ParserConfigurationException ignored) {
        }

        return new ServiceMap();
    }

    public Map<String, String> getEntityNamespacesMap() {
        String defaultServiceMapFilePath = Settings.getInstance(project).pathToProjectContainer;
        if (!FileUtil.isAbsolute(defaultServiceMapFilePath)) { // Project relative path
            defaultServiceMapFilePath = project.getBasePath() + "/" + defaultServiceMapFilePath;
        }

        File xmlFile = new File(defaultServiceMapFilePath);
        if (!xmlFile.exists()) {
            return new HashMap<String, String>();
        }

        Long xmlFileLastModified = xmlFile.lastModified();
        if (xmlFileLastModified.equals(entityNamespacesMapLastModified)) {
            return entityNamespaces;
        }

        try {
            EntityNamesParser entityNamesParser = new EntityNamesParser();
            entityNamespaces = entityNamesParser.parse(xmlFile);
            entityNamespacesMapLastModified = xmlFileLastModified;

            return entityNamespaces;
        } catch (SAXException ignored) {
        } catch (IOException ignored) {
        } catch (ParserConfigurationException ignored) {
        }

        return new HashMap<String, String>();
    }

}
