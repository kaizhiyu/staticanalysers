package com.github.kgeilmann.core.analysis;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.GenericVisitorAdapter;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
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
    private static final String MESSAGE_EXPLICIT = "Call of toString() might be executed on java.lang.Object";

    @Override
    public String getDescription() {
        return "Detects call to Object.toString() inside of logger calls, e.g logger.info(a) where the type of a does not overwrite toString().";
    }

    public ObjectToStringCallAnalysis(ProjectRoot project) {
        super(project);
    }

    @Override
    GenericVisitorAdapter<Optional<AnalysisResult>, ?> getVisitor() {
        return new FindLoggerCallVisitor();
    }

    private class FindLoggerCallVisitor extends GenericVisitorAdapter<Optional<AnalysisResult>, String> {

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
            if (!LOGGER_METHODS.contains(mc.getName().asString())) {
                return super.visit(mc, surroundingType);
            }

            try {
                ResolvedMethodDeclaration resolved = mc.resolve();
                if (!QUALIFIED_LOGGER_METHODS.contains(resolved.getQualifiedSignature())) {
                    return super.visit(mc, surroundingType);
                }
            } catch (UnsolvedSymbolException e) {
                return result(mc, UNSOLVED, surroundingType);
            }

            // interesting call to a logger method found, switch visitor to inspect the message argument
            Expression message = mc.getArgument(0);
            return message.accept(new FindToStringCallVisitor(), surroundingType);
        }
    }

    private class FindToStringCallVisitor extends GenericVisitorAdapter<Optional<AnalysisResult>, String> {

        @Override
        public Optional<AnalysisResult> visit(BinaryExpr expr, String arg) {
            return super.visit(expr, arg);
        }

        @Override
        public Optional<AnalysisResult> visit(MethodCallExpr mc, String surroundingType) {
            if (!"toString".equals(mc.getName().asString())) {
                return super.visit(mc, surroundingType);
            }

            try {
                ResolvedMethodDeclaration resolved = mc.resolve();
                if (!("java.lang.Object.toString()".equals(resolved.getQualifiedSignature()))) {
                    return super.visit(mc, surroundingType);
                }
            } catch (UnsolvedSymbolException e) {
                return result(mc, UNSOLVED, surroundingType);
            }

            // It's really a call to Object.toString(), which we don't want to have at runtime.
            // But flagging all found calls as error, would result is a lot of false positives, so let's accept some of these calls

            // Most interfaces don't declare toString() explicitly and we will accept that
            if (isCallOnInterfaceType(mc)) {
                return super.visit(mc, surroundingType);
            }

            return result(mc, MESSAGE_EXPLICIT, surroundingType);
        }

        private boolean isCallOnInterfaceType(MethodCallExpr mc) {
            ResolvedType typeOfScope = mc.getScope().map(exp -> exp.calculateResolvedType()).orElseThrow(() -> new IllegalArgumentException("Unexpected type of scope"));
            if (typeOfScope.isReferenceType()) {
                ResolvedReferenceTypeDeclaration typeDecl = typeOfScope.asReferenceType().getTypeDeclaration();
                if (typeDecl.isInterface()) {
                    // TODO: make analysis more meaningful, by assuming closed world and checking that all classes implementing interface have correct toString method
                    return true;
                } else {
                    return false;
                }
            } else if (typeOfScope.isTypeVariable()) {
                // FIXME: handle by check on type bound?
                throw new IllegalArgumentException("Unexpected type of scope: Type variable: how to solve?");
            } else {
                // FIXME: throw instead of default value to find out, what has been missed
                throw new IllegalArgumentException("Unexpected type of scope");
            }
        }
    }
}