package com.github.kgeilmann.core.analysis;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.GenericListVisitorAdapter;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.utils.ProjectRoot;
import com.github.kgeilmann.core.AnalysisResult;

import java.util.Collections;
import java.util.List;
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
    private static final String MESSAGE_EXPLICIT = "Call of toString() might be executed on java.lang.Object.";
    private static final String MESSAGE_IMPLICIT = "Implicit call of toString() might be executed on java.lang.Object.";

    @Override
    public String getDescription() {
        return "Detects call to Object.toString() inside of logger calls, e.g logger.info(a) where the type of a does not overwrite toString().";
    }

    public ObjectToStringCallAnalysis(ProjectRoot project) {
        super(project);
    }

    @Override
    GenericListVisitorAdapter<AnalysisResult, ?> getVisitor() {
        return new FindLoggerCallVisitor();
    }

    private class FindLoggerCallVisitor extends GenericListVisitorAdapter<AnalysisResult, String> {

        @Override
        public List<AnalysisResult> visit(ClassOrInterfaceDeclaration n, String arg) {
            String declaredType = n.getName().asString();
            return super.visit(n, declaredType);
        }

        @Override
        public List<AnalysisResult> visit(EnumDeclaration n, String arg) {
            String declaredType = n.getName().asString();
            return super.visit(n, declaredType);
        }

        @Override
        public List<AnalysisResult> visit(MethodCallExpr mc, String surroundingType) {
            if (!LOGGER_METHODS.contains(mc.getName().asString())) {
                return super.visit(mc, surroundingType);
            }

            try {
                ResolvedMethodDeclaration resolved = mc.resolve();
                if (!QUALIFIED_LOGGER_METHODS.contains(resolved.getQualifiedSignature())) {
                    return super.visit(mc, surroundingType);
                }
            } catch (UnsolvedSymbolException e) {
                return Collections.singletonList(result(mc, UNSOLVED, surroundingType));
            }

            // interesting call to a logger method found, switch visitor to inspect the message argument
            Expression message = mc.getArgument(0);
            return message.accept(new FindToStringCallVisitor(), surroundingType);
        }
    }

    private class FindToStringCallVisitor extends GenericListVisitorAdapter<AnalysisResult, String> {

        // FIXME: not handled Logger.debug(someNoneStringExpression)

        @Override
        public List<AnalysisResult> visit(BinaryExpr expr, String arg) {
            if (!expr.getOperator().equals(BinaryExpr.Operator.PLUS)) {
                return super.visit(expr, arg);
            }

            if (!"java.lang.String".equals(expr.calculateResolvedType().describe())) {
                return super.visit(expr, arg);
            }

            boolean leftIsString = "java.lang.String".equals(expr.getLeft().calculateResolvedType().describe());
            boolean rightIsString = "java.lang.String".equals(expr.getRight().calculateResolvedType().describe());

            if (leftIsString && rightIsString) {
                // no string conversion takes place -> no implicit toString call
                return super.visit(expr, arg);
            }

            if (!leftIsString && isAcceptedImplicitCall(expr.getLeft())) {
                return super.visit(expr, arg);
            }

            if (!rightIsString && isAcceptedImplicitCall(expr.getRight())) {
                return super.visit(expr, arg);
            }

            if (!leftIsString && !rightIsString) {
                // calculateResolvedType did return string, but its not, because string conversion on both sides needed -> Bug in symbol solver
                throw new IllegalArgumentException("Something is wrong with binary expressions and string conversions.");
            }

            Expression problematicExpression = leftIsString ? expr.getRight() : expr.getLeft();
            return Collections.singletonList(result(problematicExpression, MESSAGE_IMPLICIT, arg));
        }

        @Override
        public List<AnalysisResult> visit(MethodCallExpr mc, String surroundingType) {
            try {
                if (!"toString".equals(mc.getName().asString())) {
                    return super.visit(mc, surroundingType);
                }

                if (isAcceptedCall(mc)) {
                    return super.visit(mc, surroundingType);
                }

                return Collections.singletonList(result(mc, MESSAGE_EXPLICIT, surroundingType));
            } catch (UnsolvedSymbolException e) {
                return Collections.singletonList(result(mc, UNSOLVED, surroundingType));
            }
        }

        private boolean isAcceptedImplicitCall(Expression exp) {
            if (exp.calculateResolvedType().isPrimitive()) {
                // primitive type would be converted to wrapper
                // the wrappers have nice implementations -> no problems
                return true;
            }

            // change the ast to contain an explicit call
            MethodCallExpr call = new MethodCallExpr();
            exp.replace(call);
            call.setScope(exp);
            call.setName("toString");

            boolean accepted = isAcceptedCall(call);

            // rollback the change in ast
            call.replace(exp);
            return accepted;
        }

        private boolean isAcceptedCall(MethodCallExpr toStringCall) {
            ResolvedMethodDeclaration toStringMethod = toStringCall.resolve();
            if (!("java.lang.Object.toString()".equals(toStringMethod.getQualifiedSignature()))) {
                return true;
            }

            // It's really a call to Object.toString(), which we don't want to have at runtime.
            // But flagging all found calls as error, would result is a lot of false positives, so let's accept some of these calls

            // Most interfaces don't declare toString() explicitly and we will accept that
            return isCallOnInterfaceType(toStringCall);
        }

        private boolean isCallOnInterfaceType(MethodCallExpr toStringCall) {
            ResolvedType typeOfScope = toStringCall.getScope()
                    .map(Expression::calculateResolvedType).orElseThrow(() -> new IllegalArgumentException("FIXME: No scope " + toStringCall.toString()));
            return isInterfaceType(typeOfScope);
        }

        private boolean isInterfaceType(ResolvedType typeOfScope) {
            if (typeOfScope.isReferenceType()) {
                ResolvedReferenceTypeDeclaration typeDecl = typeOfScope.asReferenceType().getTypeDeclaration();
                return typeDecl.isInterface();
            } else if (typeOfScope.isTypeVariable()) {
                ResolvedType bound = typeOfScope.asTypeParameter().getLowerBound();
                return isInterfaceType(bound);
            }
            return false;
        }
    }
}