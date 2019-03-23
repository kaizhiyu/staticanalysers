package com.github.kgeilmann.core;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.utils.ProjectRoot;
import com.github.kgeilmann.core.analysis.WrongLoggerAnalysis;
import com.google.common.base.Preconditions;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

public class Analyser {

    public List<AnalysisResult> analyze(List<String> sourceRoots, List<String> jars) throws IOException {
        if (sourceRoots == null || sourceRoots.isEmpty()) {
            return Collections.emptyList();
        }

        ProjectRoot project = createProjectRoot(sourceRoots, jars);
        WrongLoggerAnalysis wrongLogger = new WrongLoggerAnalysis(project);
        List<AnalysisResult> results = wrongLogger.analyse();
        return results;
    }

    private ProjectRoot createProjectRoot(List<String> sourceRoots, List<String> jars) throws IOException {

        CombinedTypeSolver typeSolver = new CombinedTypeSolver(new ReflectionTypeSolver(false));
        for (String root : sourceRoots) {
            typeSolver.add(new JavaParserTypeSolver(root));
        }
        for (String jar : jars) {
            typeSolver.add(new JarTypeSolver(jar));
        }

        ParserConfiguration parserConfiguration = new ParserConfiguration().setSymbolResolver(new JavaSymbolSolver(typeSolver));
        ProjectRoot projectRoot = new ProjectRoot(Paths.get(sourceRoots.get(0)), parserConfiguration);
        sourceRoots.forEach(s -> projectRoot.addSourceRoot(Paths.get(s)));

        return projectRoot;
    }

}
