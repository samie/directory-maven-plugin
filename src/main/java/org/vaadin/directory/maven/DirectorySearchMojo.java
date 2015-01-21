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

import org.vaadin.directory.Addon;
import java.util.List;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.vaadin.directory.Directory;
import org.vaadin.directory.License;

/**
 * Maven plugin API for Vaadin Directory.
 *
 * @author Sami Ekblad
 */
@Mojo(name = "search")
public class DirectorySearchMojo extends AbstractMojo {

    @Parameter(property = "addon")
    private String searchAddon;

    @Parameter(property = "full", defaultValue = "false", readonly = true)
    private boolean fullSearch;

    @Parameter(property = "project", defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (searchAddon == null) {
            throw new MojoFailureException("Missing search parameter.");
        }
        directorySearch(project, searchAddon, false, fullSearch);
    }

    protected static void directorySearch(MavenProject project, String searchFor, boolean add, boolean fullSearch) {
        List<Addon> list = Directory.search("7", searchFor, fullSearch);
        Model model = project.getModel();
        for (Addon a : list) {
            System.out.println(a.getName() + " - " + a.getSummary());
            if (a.getLicenses() != null) {
                for (License l : a.getLicenses()) {
                    System.out.print("\tLicense: " + a.getLicenses().get(0).getName() + "\n");
                }
            }

            System.out.println("\tRating: " + a.getAvgRating() + " / 5");
            if (a.getGroupId() == null || a.getArtifactId() == null) {
                System.out.print("\tMaven: n/a");
            } else {
                System.out.print("\tMaven: " + a.getGroupId() + ":" + a.getArtifactId() + ":" + a.getVersion());
            }
            if (PomUtils.findDependency(model, a.getGroupId(), a.getArtifactId()) != null) {
                System.out.println(" (in pom.xml)");
            } else if (add && a.getArtifactId() != null && a.getGroupId() != null) {
                PomUtils.addDependency(model, a.getGroupId(), a.getArtifactId(), a.getVersion());
                System.out.println(" (ADDED)");
            } else if (a.getArtifactId() != null && a.getGroupId() != null) {
                System.out.println(" (not present)");
            } else {
            }
        }
    }
}
