package com.github.kgeilmann.core;

import com.github.javaparser.utils.ProjectRoot;
import com.github.kgeilmann.core.analysis.WrongLoggerAnalysis;
import com.github.kgeilmann.core.jp.FileBasedCollectionStrategy;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class StaticAnalyzers {
    private static final Logger LOG = Logger.getLogger(StaticAnalyzers.class.getSimpleName());

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: ");
            System.err.println("\tfirst parameter: file with all input jars and source dirs");
            System.err.println("\tsecond parameter: output file");
            return;
        }

        Path input = Paths.get(args[0]);
        Path resolved = Paths.get(".").toAbsolutePath().resolve(input).normalize();
        ProjectRoot project = new FileBasedCollectionStrategy().collect(resolved);

        WrongLoggerAnalysis wrongLogger = new WrongLoggerAnalysis(project);
        List<AnalysisResult> results = wrongLogger.analyse();

        Path output = Paths.get(args[1]);
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)) {
            Map<String, List<AnalysisResult>> grouped = results.stream().collect(Collectors.groupingBy(AnalysisResult::getFilePath));
            for (Map.Entry<String, List<AnalysisResult>> entry : grouped.entrySet()) {
                writer.write(entry.getKey());
                writer.newLine();
                for (AnalysisResult r : entry.getValue()) {
                    writer.write("\t" + r.getLocation() + "\t" + r.getMessage());
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            LOG.severe("Problems writing output file: " + e.getMessage());
        }
    }

}
