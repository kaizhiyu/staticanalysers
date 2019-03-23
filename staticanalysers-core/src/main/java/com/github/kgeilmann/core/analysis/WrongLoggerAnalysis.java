package com.github.kgeilmann.core.analysis;

import com.github.javaparser.Range;
import com.github.javaparser.TokenRange;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.GenericVisitorAdapter;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.utils.ProjectRoot;
import com.github.javaparser.utils.SourceRoot;
import com.github.kgeilmann.core.AnalysisResult;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

public class WrongLoggerAnalysis {

    public static final String DESCRIPTION = "If org.apache.log4j.Logger#getLogger(Class) is called with X.class, X should be the surrounding class of the call.";

    private static final Logger LOG = Logger.getLogger(WrongLoggerAnalysis.class.getSimpleName());
    private static final String MESSAGE = "Wrong class in Logger.getLogger(Class), surrounding type is %s.";
    private static final String UNSOLVED = "Could not solve method call in %s.";

    private ProjectRoot project;

    public WrongLoggerAnalysis(ProjectRoot project) {
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
            Optional<AnalysisResult> result = cu.accept(new Visitor(), "");
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


    class Visitor extends GenericVisitorAdapter<Optional<AnalysisResult>, String> {

        @Override
        public Optional<AnalysisResult> visit(ClassOrInterfaceDeclaration n, String arg) {
            String declaredType = n.getName().asString();
            return super.visit(n, declaredType);
        }

        @Override
        public Optional<AnalysisResult> visit(EnumDeclaration n, String arg) {
            String declaredType = n.getName().asString();
            return super.visit(n, declaredType);
        }

        @Override
        public Optional<AnalysisResult> visit(MethodCallExpr mc, String surroundingType) {
            if (!mc.getName().asString().equals("getLogger")) {
                return super.visit(mc, surroundingType);
            }

            try {
                ResolvedMethodDeclaration resolved = mc.resolve();
                if (!resolved.getQualifiedSignature().equals("org.apache.log4j.Logger.getLogger(java.lang.Class)")) {
                    return super.visit(mc, surroundingType);
                }
            } catch (UnsolvedSymbolException e) {
                return result(mc, UNSOLVED, surroundingType);
            }

            Expression argument = mc.getArgument(0);
            if (!argument.isClassExpr()) {
                return super.visit(mc, surroundingType);
            }

            String className = argument.asClassExpr().getType().asClassOrInterfaceType().getNameAsString();
            if (surroundingType.equals(className)) {
                return Optional.empty();
            } else {
                return result(mc, MESSAGE, surroundingType);
            }
        }
    }

    private Optional<AnalysisResult> result(MethodCallExpr mc, String message, String surroundingType) {
        String location = mc.getTokenRange().flatMap(TokenRange::toRange).map(Range::toString).orElse("");
        String expression = mc.toString();
        return Optional.of(new AnalysisResult(location, expression, String.format(message, surroundingType)));
    }
}
