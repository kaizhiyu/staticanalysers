package com.github.kgeilmann.staticanalysers.maven.plugin;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.lifecycle.internal.MojoExecutor;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.AbstractMavenReport;
import org.apache.maven.reporting.MavenReportException;

import java.io.IOException;
import java.util.List;
import java.util.Locale;


@Mojo(name = "analyse-aggregate", aggregator = true, defaultPhase = LifecyclePhase.SITE, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, requiresProject = true)
public class AnalyseAggregateReportMojo extends AbstractMavenReport {

    @Override
    public String getOutputName() {
        return "com.github.kgeilmann.staticanalysers.aggregate";
    }

    @Override
    public String getName(Locale locale) {
        return "Static Analysers Report";
    }

    @Override
    public String getDescription(Locale locale) {
        return "";
    }

    @Parameter(property = "reactorProjects", readonly = true)
    private List<MavenProject> reactorProjects;

    @Override
    protected void executeReport(Locale locale) throws MavenReportException {

        Log logger = getLog();
        logger.info("Generating " + getOutputName() + ".html for " + project.getName() + " " + project.getVersion() + " ...");

        Sink s = getSink();
        if (s == null) {
            throw new MavenReportException("Could not get the Doxia sink");
        }

        try {
            s.head();
            s.title();
            s.text("Static Analyses Report for " + project.getName() + " " + project.getVersion());
            s.title_();
            s.head_();

            s.body();

            for (MavenProject p : reactorProjects) {
                getLog().info("Analysing " + p.getName());
                new ProjectReporter().execute(p, getSink(), getLog());
                getLog().info("... done");
            }

            s.body_();

        } catch (DependencyResolutionRequiredException | IOException e) {
            getLog().error("Cannot analyse project: " + e.getMessage());
            throw new MavenReportException("Cannot analyse project", e);
        }

    }

}
