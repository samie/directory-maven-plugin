package org.vaadin.directory.maven;
/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.project.MavenProject;
import org.codehaus.mojo.versions.api.PomHelper;
import org.codehaus.mojo.versions.rewriting.ModifiedPomXMLEventReader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.codehaus.stax2.XMLInputFactory2;

/**
 * Utilities for updating pom.xml file.
 *
 * Depends on maven-versions-plugin, but to avoid transitive dependencies the
 * relevant classes are copied into this project.
 *
 * @author Sami Ekblad
 */
public class PomUtils {

    /**
     * Load pom.xml form this project.
     *
     * @param project
     * @return
     */
    public static Model getProjectPom(MavenProject project) {
        try {
            MavenXpp3Reader reader = new MavenXpp3Reader();
            return reader.read(new FileReader(project.getFile()));
        } catch (FileNotFoundException ex) {
            Logger.getLogger(PomUtils.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException | XmlPullParserException ex) {
            Logger.getLogger(PomUtils.class.getName()).log(Level.SEVERE, null, ex);
        }

        return null;

    }

    /**
     * Save/replace this projects pom.xml.
     *
     * @param project
     * @param pomModel
     */
    public static void saveToProject(MavenProject project, Model pomModel) {
        try {
            MavenXpp3Writer w = new MavenXpp3Writer();
            w.write(new FileOutputStream(project.getFile()), pomModel);
        } catch (FileNotFoundException ex) {
            Logger.getLogger(PomUtils.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(PomUtils.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Find a dependency in pom.
     *
     * @param pomModel
     * @param groupId
     * @param artifactId
     * @return
     */
    public static Dependency findDependency(Model pomModel, String groupId, String artifactId) {
        if (groupId == null || artifactId == null) {
            return null;
        }
        List<Dependency> deps = pomModel.getDependencies();
        for (Dependency dep : deps) {
            if (dep.getArtifactId().equals(artifactId) && dep.getGroupId().equals(groupId)) {
                return dep;
            }
        }
        return null;
    }

    /**
     * Adds a new dependency or updates the version of existing one.
     *
     * @param pomModel
     * @param groupId
     * @param artifactId
     * @param version
     * @return
     */
    public static Model addDependency(Model pomModel, String groupId, String artifactId, String version) {
        Dependency dep = findDependency(pomModel, groupId, artifactId);
        if (dep == null) {
            try {
                StringBuilder input = PomHelper.readXmlFile(pomModel.getPomFile());
                ModifiedPomXMLEventReader newPom = newModifiedPomXER(input);
                addNewDependency(newPom, groupId, artifactId, version);
                try (FileWriter out = new FileWriter(pomModel.getPomFile())) {
                    out.write(newPom.asStringBuilder().toString());
                }
            } catch (XMLStreamException | IOException ex) {
                Logger.getLogger(PomUtils.class.getName()).log(Level.SEVERE, null, ex);
            }
            dep = new Dependency();
            dep.setGroupId(groupId);
            dep.setArtifactId(artifactId);
            dep.setVersion(version);
            pomModel.getDependencies().add(dep);
            return pomModel;
        }
        return null;
    }

    protected static final ModifiedPomXMLEventReader newModifiedPomXER(StringBuilder input) {
        ModifiedPomXMLEventReader newPom = null;
        try {
            XMLInputFactory inputFactory = XMLInputFactory2.newInstance();
            inputFactory.setProperty(XMLInputFactory2.P_PRESERVE_LOCATION, Boolean.TRUE);
            newPom = new ModifiedPomXMLEventReader(input, inputFactory);
        } catch (XMLStreamException ex) {
            Logger.getLogger(PomUtils.class.getName()).log(Level.SEVERE, null, ex);
        }
        return newPom;
    }

    /**
     * Adds dependency to the the pom.
     *
     * @param pom The pom.
     * @param groupId
     * @param artifactId
     * @param version
     * @throws XMLStreamException if something went wrong.
     */
    public static void addNewDependency(final ModifiedPomXMLEventReader pom, String groupId, String artifactId, String version)
            throws XMLStreamException {
        Stack<String> stack = new Stack<>();
        String path = "";
        final Pattern matchScopeRegex = Pattern.compile("/project/dependencies");

        pom.rewind();

        int i = 0;
        while (pom.hasNext()) {
            XMLEvent event = pom.nextEvent();
            if (event.isStartElement()) {
                stack.push(path);
                path = path + "/" + event.asStartElement().getName().getLocalPart();

                if (matchScopeRegex.matcher(path).matches()) {
                    pom.mark(0);
                }
            }
            if (event.isEndElement()) {
                if (matchScopeRegex.matcher(path).matches()) {
                    pom.mark(1);
                    if (pom.hasMark(0) && pom.hasMark(1)) {
                        pom.replaceMark(1, "    <dependency><groupId>"
                                + groupId
                                + "</groupId><artifactId>"
                                + artifactId
                                + "</artifactId><version>"
                                + version
                                + "</version></dependency>\n\t</dependencies>");
                        return;
                    }
                    pom.clearMark(0);
                    pom.clearMark(1);
                }
                path = stack.pop();
            }
        }
    }
}
