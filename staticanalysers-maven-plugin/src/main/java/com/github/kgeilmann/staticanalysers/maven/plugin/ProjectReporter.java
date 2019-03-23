package com.github.kgeilmann.staticanalysers.maven.plugin;

import com.github.kgeilmann.core.Analyser;
import com.github.kgeilmann.core.AnalysisResult;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.doxia.sink.impl.SinkEventAttributeSet;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.maven.doxia.sink.Sink.JUSTIFY_LEFT;

public class ProjectReporter {

    protected void execute(MavenProject p, Sink sink) throws IOException, DependencyResolutionRequiredException {

        sink.section(1, new SinkEventAttributeSet());
        sink.sectionTitle(1, new SinkEventAttributeSet());
        sink.text(p.getName());
        sink.sectionTitle_(1);

        Map<String, List<AnalysisResult>> grouped = analyse(p).stream().collect(Collectors.groupingBy(a -> a.getFilePath()));
        if (grouped.isEmpty()) {
            sink.text("Nothing found.");
        }
        for (Map.Entry<String, List<AnalysisResult>> e : grouped.entrySet()) {
            fileReport(sink, e.getKey(), e.getValue());
        }
        sink.section_(1);
    }


    private void fileReport(Sink sink, String file, List<AnalysisResult> results) {
        final int level = 2;
        sink.section(level, new SinkEventAttributeSet());
        sink.sectionTitle(level, new SinkEventAttributeSet());
        sink.text(file);
        sink.sectionTitle_(level);

        sink.table();
        sink.tableRows(new int[]{JUSTIFY_LEFT, JUSTIFY_LEFT}, true);
        sink.tableRow();
        sink.tableHeaderCell();
        sink.text("Position");
        sink.tableHeaderCell_();
        sink.tableHeaderCell();
        sink.text("Expression");
        sink.tableHeaderCell_();
        sink.tableHeaderCell();
        sink.text("Complaint");
        sink.tableHeaderCell_();
        sink.tableRow_();
        for (AnalysisResult r : results) {
            sink.tableRow();
            sink.tableCell();
            sink.text(r.getLocation());
            sink.tableCell_();
            sink.tableCell();
            sink.text(r.getExpression());
            sink.tableCell_();
            sink.tableCell();
            sink.text(r.getMessage());
            sink.tableCell_();
            sink.tableRow_();
        }
        sink.table_();
        sink.section_(level);
    }

    private List<AnalysisResult> analyse(MavenProject project) throws DependencyResolutionRequiredException, IOException {

        if (project.getCompileClasspathElements() == null || project.getCompileSourceRoots() == null) {
            return Collections.emptyList();
        }

        List<String> existingSourceRoots = project.getCompileSourceRoots().stream().filter(s -> new File(s).exists()).collect(Collectors.toList());
        if (existingSourceRoots.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> existingClasspathElements = project.getCompileClasspathElements().stream().filter(s -> new File(s).exists()).collect(Collectors.toList());
        return new Analyser().analyze(existingSourceRoots, existingClasspathElements);
    }
}