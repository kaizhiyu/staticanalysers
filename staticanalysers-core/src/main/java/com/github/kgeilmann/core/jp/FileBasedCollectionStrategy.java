package com.github.kgeilmann.core.jp;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JarTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.utils.CollectionStrategy;
import com.github.javaparser.utils.ProjectRoot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class FileBasedCollectionStrategy implements CollectionStrategy {

    private final CombinedTypeSolver typeSolver = new CombinedTypeSolver(new ReflectionTypeSolver(false));
    private final ParserConfiguration parserConfiguration = new ParserConfiguration().setSymbolResolver(new JavaSymbolSolver(typeSolver));

    @Override
    public ProjectRoot collect(Path path) {
        // TODO: Dateiformat beschreiben
        // TODO: Fehlerbeahndlung, nicht pauschal sondern einzeln
        List<Path> sourceroots = new ArrayList<>();
        try {
            List<String> entries = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (String entry : entries) {
                if (entry.trim().endsWith("jar")) {
                    typeSolver.add(new JarTypeSolver(entry));
                } else {
                    typeSolver.add(new JavaParserTypeSolver(entry));
                    sourceroots.add(Paths.get(entry));
                }
            }

            ProjectRoot projectRoot = new ProjectRoot(sourceroots.get(0), parserConfiguration);
            sourceroots.forEach(s -> projectRoot.addSourceRoot(s));

            return projectRoot;
        } catch (IOException e) {
            System.err.println("Cannot collect data from input file " + path + ".");
        }
        return null;
    }

}
