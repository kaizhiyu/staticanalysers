package com.github.kgeilmann.core.analysis;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.GenericVisitorAdapter;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.utils.ProjectRoot;
import com.github.kgeilmann.core.AnalysisResult;

import java.util.Optional;
import java.util.Set;

public class ObjectToStringCallAnalysis extends Analysis {

    private static final Set<String> LOGGER_METHODS = Set.of("trace", "debug", "info", "warn", "error", "fatal");
    private static final Set<String> QUALIFIED_LOGGER_METHODS = Set.of(
            "org.apache.log4j.Logger.trace(java.lang.Object)",
            "org.apache.log4j.Category.debug(java.lang.Object)",
            "org.apache.log4j.Category.info(java.lang.Object)",
            "org.apache.log4j.Category.warn(java.lang.Object)",
            "org.apache.log4j.Category.error(java.lang.Object)",
            "org.apache.log4j.Category.fatal(java.lang.Object)",
            "org.apache.log4j.Logger.trace(java.lang.Object, java.lang.Throwable)",
            "org.apache.log4j.Category.debug(java.lang.Object, java.lang.Throwable)",
            "org.apache.log4j.Category.info(java.lang.Object, java.lang.Throwable)",
            "org.apache.log4j.Category.warn(java.lang.Object, java.lang.Throwable)",
            "org.apache.log4j.Category.error(java.lang.Object, java.lang.Throwable)",
            "org.apache.log4j.Category.fatal(java.lang.Object, java.lang.Throwable)"
    );

    @Override
    public String getDescription() {
        return "Detects call to Object.toString() inside of logger calls, e.g logger.info(a) where the type of a does not overwrite toString().";
    }

    public ObjectToStringCallAnalysis(ProjectRoot project) {
        super(project);
    }

    @Override
    GenericVisitorAdapter<Optional<AnalysisResult>, ?> getVisitor() {
        return new Visitor();
    }

    private class Visitor extends GenericVisitorAdapter<Optional<AnalysisResult>, String> {

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
        public Optional<AnalysisResult> visit(MethodCallExpr mc, String arg) {
            if (!LOGGER_METHODS.contains(mc.getName().asString())) {
                return super.visit(mc, arg);
            }

            try {
                ResolvedMethodDeclaration resolved = mc.resolve();
                if (!QUALIFIED_LOGGER_METHODS.contains(resolved.getQualifiedSignature())) {
                    return super.visit(mc, arg);
                }
            } catch (UnsolvedSymbolException e) {
                return result(mc, UNSOLVED, arg);
            }

            return super.visit(mc, arg);
        }
    }
}