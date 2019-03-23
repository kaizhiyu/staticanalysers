package com.github.kgeilmann.core.analysis;

import com.github.javaparser.Range;
import com.github.javaparser.TokenRange;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.GenericVisitorAdapter;
import com.github.javaparser.utils.ProjectRoot;
import com.github.javaparser.utils.SourceRoot;
import com.github.kgeilmann.core.AnalysisResult;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

public abstract class Analysis {
    public abstract String getDescription();

    private static final Logger LOG = Logger.getLogger(ObjectToStringCallAnalysis.class.getSimpleName());
    static final String UNSOLVED = "Could not solve method call in %s.";

    private ProjectRoot project;

    Analysis(ProjectRoot project) {
        this.project = project;
    }

    public List<AnalysisResult> analyse() {
        List<AnalysisResult> results = new LinkedList<>();
        project.getSourceRoots().forEach(sr -> analyse(results, sr));
        return results;
    }

    private void analyse(List<AnalysisResult> results, SourceRoot sourceRoot) {
        try {
            sourceRoot.parse("", (localPath, absolutePath, parseResult) -> {
                if (parseResult.isSuccessful()) {
                    analyse(results, parseResult.getResult().get());
                }
                return SourceRoot.Callback.Result.DONT_SAVE;
            });
        } catch (IOException e) {
            System.err.println("Parsing failed for source root " + sourceRoot.getRoot() + ". Reason: " + e.getMessage());
        }
    }

    private void analyse(List<AnalysisResult> results, CompilationUnit cu) {
        try {
            Optional<AnalysisResult> result = cu.accept(this.getVisitor(), null);
            if (result != null) {
                result.ifPresent(ar -> {
                    ar.setFilePath(cu.getStorage().get().getFileName());
                    results.add(ar);
                });
            }
        } catch (Exception e) {
            LOG.info(cu.getStorage().get().getFileName());
            e.printStackTrace();
        }
    }

    abstract GenericVisitorAdapter<Optional<AnalysisResult>, ?> getVisitor();

    Optional<AnalysisResult> result(Node node, String message, String surroundingType) {
        String location = node.getTokenRange().flatMap(TokenRange::toRange).map(Range::toString).orElse("");
        String expression = node.toString();
        return Optional.of(new AnalysisResult(location, expression, String.format(message, surroundingType)));
    }
}



