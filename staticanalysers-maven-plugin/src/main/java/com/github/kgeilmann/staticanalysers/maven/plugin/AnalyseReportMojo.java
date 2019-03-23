package com.github.kgeilmann.staticanalysers.maven.plugin;

import com.github.kgeilmann.core.Analyser;
import com.github.kgeilmann.core.AnalysisResult;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.doxia.sink.impl.SinkEventAttributeSet;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.AbstractMavenReport;
import org.apache.maven.reporting.MavenReportException;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.maven.doxia.sink.Sink.JUSTIFY_LEFT;


@Mojo(name = "analyse", defaultPhase = LifecyclePhase.SITE, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class AnalyseReportMojo extends AbstractMavenReport {

    private ProjectReporter reporter = new ProjectReporter(getSink());

    @Override
    public String getOutputName() {
        return "com.github.kgeilmann.staticanalysers";
    }

    @Override
    public String getName(Locale locale) {
        return "Static Analysers Report";
    }

    @Override
    public String getDescription(Locale locale) {
        return "";
    }

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

            getLog().info("Analysing " + project.getName());
            reporter.execute(project);
            getLog().info("... done");

            s.body_();

        } catch (DependencyResolutionRequiredException | IOException e) {
            getLog().error("Cannot analyse project: " + e.getMessage());
            throw new MavenReportException("Cannot analyse project", e);
        }

    }

}
