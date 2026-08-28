package io.github.timer_err.qml4j.compiler.bytecode.rhino;

import io.github.timer_err.qml4j.engine.js.JsConstRepair;
import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Node;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.ast.ArrayLiteral;
import org.mozilla.javascript.ast.AstNode;
import org.mozilla.javascript.ast.AstRoot;
import org.mozilla.javascript.ast.CatchClause;
import org.mozilla.javascript.ast.ConditionalExpression;
import org.mozilla.javascript.ast.DoLoop;
import org.mozilla.javascript.ast.ElementGet;
import org.mozilla.javascript.ast.ExpressionStatement;
import org.mozilla.javascript.ast.ForInLoop;
import org.mozilla.javascript.ast.ForLoop;
import org.mozilla.javascript.ast.FunctionCall;
import org.mozilla.javascript.ast.FunctionNode;
import org.mozilla.javascript.ast.IfStatement;
import org.mozilla.javascript.ast.InfixExpression;
import org.mozilla.javascript.ast.LabeledStatement;
import org.mozilla.javascript.ast.Name;
import org.mozilla.javascript.ast.NewExpression;
import org.mozilla.javascript.ast.ObjectLiteral;
import org.mozilla.javascript.ast.ObjectProperty;
import org.mozilla.javascript.ast.ParenthesizedExpression;
import org.mozilla.javascript.ast.PropertyGet;
import org.mozilla.javascript.ast.ReturnStatement;
import org.mozilla.javascript.ast.Scope;
import org.mozilla.javascript.ast.SwitchCase;
import org.mozilla.javascript.ast.SwitchStatement;
import org.mozilla.javascript.ast.TemplateLiteral;
import org.mozilla.javascript.ast.ThrowStatement;
import org.mozilla.javascript.ast.TryStatement;
import org.mozilla.javascript.ast.UnaryExpression;
import org.mozilla.javascript.ast.VariableDeclaration;
import org.mozilla.javascript.ast.VariableInitializer;
import org.mozilla.javascript.ast.WhileLoop;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/** Java 8 Rhino-compatible form of qml4j's free-variable collector. */
public final class RhinoFreeVars {
    private RhinoFreeVars() {
    }

    public static Set<String> collect(String source, Set<String> initiallyBound) {
        FunctionNode body = parseAsFunctionBody(source);
        Set<String> free = new LinkedHashSet<String>();
        Set<String> bound = new HashSet<String>(initiallyBound);
        scope(body.getBody(), bound, free);
        return free;
    }

    public static boolean usesBareQtBinding(String source) {
        return scanBareQtBinding(parseAsFunctionBody(source).getBody());
    }

    private static boolean scanBareQtBinding(AstNode node) {
        if (node == null) {
            return false;
        }
        if (node instanceof FunctionCall && !(node instanceof NewExpression)) {
            FunctionCall call = (FunctionCall) node;
            if (isQtBinding(call.getTarget())
                    && (call.getArguments().isEmpty()
                    || !(call.getArguments().get(0) instanceof FunctionNode))) {
                return true;
            }
        }
        for (Node child : node) {
            if (scanBareQtBinding((AstNode) child)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isQtBinding(AstNode target) {
        if (!(target instanceof PropertyGet)) {
            return false;
        }
        PropertyGet property = (PropertyGet) target;
        return property.getTarget() instanceof Name
                && "Qt".equals(((Name) property.getTarget()).getIdentifier())
                && "binding".equals(property.getProperty().getIdentifier());
    }

    private static FunctionNode parseAsFunctionBody(String source) {
        String wrapped = JsConstRepair.toLet("(function(){\n" + source + "\n})");
        CompilerEnvirons environment = new CompilerEnvirons();
        environment.setLanguageVersion(Context.VERSION_ES6);
        environment.setRecordingComments(false);
        environment.setIdeMode(false);
        try {
            AstRoot root = new Parser(environment).parse(wrapped, "qml-binding", 1);
            ExpressionStatement statement = (ExpressionStatement) root.getFirstChild();
            ParenthesizedExpression expression = (ParenthesizedExpression) statement.getExpression();
            return (FunctionNode) expression.getExpression();
        } catch (RhinoException exception) {
            throw new IllegalArgumentException("invalid JS: " + exception.getMessage(), exception);
        }
    }

    private static void scope(AstNode body, Set<String> bound, Set<String> free) {
        hoist(body, bound);
        for (Node child : body) {
            walk((AstNode) child, bound, free);
        }
    }

    private static void hoist(AstNode body, Set<String> bound) {
        for (Node child : body) {
            AstNode node = (AstNode) child;
            if (node instanceof FunctionNode) {
                Name name = ((FunctionNode) node).getFunctionName();
                if (name != null) {
                    bound.add(name.getIdentifier());
                }
            } else if (node instanceof VariableDeclaration) {
                for (VariableInitializer variable : ((VariableDeclaration) node).getVariables()) {
                    bindTarget(variable.getTarget(), bound);
                }
            }
        }
    }

    private static void walk(AstNode node, Set<String> bound, Set<String> free) {
        if (node == null) {
            return;
        }
        if (node instanceof Name) {
            String identifier = ((Name) node).getIdentifier();
            if (!bound.contains(identifier)) {
                free.add(identifier);
            }
        } else if (node instanceof PropertyGet) {
            walk(((PropertyGet) node).getTarget(), bound, free);
        } else if (node instanceof ElementGet) {
            walk(((ElementGet) node).getTarget(), bound, free);
            walk(((ElementGet) node).getElement(), bound, free);
        } else if (node instanceof FunctionCall) {
            FunctionCall call = (FunctionCall) node;
            walk(call.getTarget(), bound, free);
            for (AstNode argument : call.getArguments()) {
                walk(argument, bound, free);
            }
            if (node instanceof NewExpression) {
                walk(((NewExpression) node).getInitializer(), bound, free);
            }
        } else if (node instanceof FunctionNode) {
            walkFunction((FunctionNode) node, bound, free);
        } else if (node instanceof VariableDeclaration) {
            for (VariableInitializer variable : ((VariableDeclaration) node).getVariables()) {
                walk(variable.getInitializer(), bound, free);
                bindTarget(variable.getTarget(), bound);
            }
        } else if (node instanceof InfixExpression) {
            walk(((InfixExpression) node).getLeft(), bound, free);
            walk(((InfixExpression) node).getRight(), bound, free);
        } else if (node instanceof UnaryExpression) {
            walk(((UnaryExpression) node).getOperand(), bound, free);
        } else if (node instanceof ConditionalExpression) {
            ConditionalExpression conditional = (ConditionalExpression) node;
            walk(conditional.getTestExpression(), bound, free);
            walk(conditional.getTrueExpression(), bound, free);
            walk(conditional.getFalseExpression(), bound, free);
        } else if (node instanceof ParenthesizedExpression) {
            walk(((ParenthesizedExpression) node).getExpression(), bound, free);
        } else if (node instanceof ArrayLiteral) {
            for (AstNode element : ((ArrayLiteral) node).getElements()) {
                walk(element, bound, free);
            }
        } else if (node instanceof ObjectLiteral) {
            for (ObjectProperty property : ((ObjectLiteral) node).getElements()) {
                walk(property.getRight(), bound, free);
            }
        } else if (node instanceof TemplateLiteral) {
            for (AstNode substitution : ((TemplateLiteral) node).getSubstitutions()) {
                walk(substitution, bound, free);
            }
        } else if (node instanceof ExpressionStatement) {
            walk(((ExpressionStatement) node).getExpression(), bound, free);
        } else if (node instanceof ReturnStatement) {
            walk(((ReturnStatement) node).getReturnValue(), bound, free);
        } else if (node instanceof IfStatement) {
            IfStatement statement = (IfStatement) node;
            walk(statement.getCondition(), bound, free);
            walk(statement.getThenPart(), bound, free);
            walk(statement.getElsePart(), bound, free);
        } else if (node instanceof ForLoop) {
            ForLoop loop = (ForLoop) node;
            Set<String> inner = new HashSet<String>(bound);
            walk(loop.getInitializer(), inner, free);
            walk(loop.getCondition(), inner, free);
            walk(loop.getIncrement(), inner, free);
            walk(loop.getBody(), inner, free);
        } else if (node instanceof ForInLoop) {
            ForInLoop loop = (ForInLoop) node;
            Set<String> inner = new HashSet<String>(bound);
            AstNode iterator = loop.getIterator();
            if (iterator instanceof VariableDeclaration) {
                for (VariableInitializer variable : ((VariableDeclaration) iterator).getVariables()) {
                    bindTarget(variable.getTarget(), inner);
                }
            } else {
                bindTarget(iterator, inner);
            }
            walk(loop.getIteratedObject(), inner, free);
            walk(loop.getBody(), inner, free);
        } else if (node instanceof WhileLoop) {
            walk(((WhileLoop) node).getCondition(), bound, free);
            walk(((WhileLoop) node).getBody(), bound, free);
        } else if (node instanceof DoLoop) {
            walk(((DoLoop) node).getCondition(), bound, free);
            walk(((DoLoop) node).getBody(), bound, free);
        } else if (node instanceof SwitchStatement) {
            SwitchStatement statement = (SwitchStatement) node;
            walk(statement.getExpression(), bound, free);
            for (SwitchCase item : statement.getCases()) {
                walk(item.getExpression(), bound, free);
                if (item.getStatements() != null) {
                    for (AstNode child : item.getStatements()) {
                        walk(child, bound, free);
                    }
                }
            }
        } else if (node instanceof TryStatement) {
            TryStatement statement = (TryStatement) node;
            walk(statement.getTryBlock(), bound, free);
            for (CatchClause item : statement.getCatchClauses()) {
                walk(item, bound, free);
            }
            walk(statement.getFinallyBlock(), bound, free);
        } else if (node instanceof CatchClause) {
            CatchClause clause = (CatchClause) node;
            Set<String> inner = new HashSet<String>(bound);
            bindTarget(clause.getVarName(), inner);
            walk(clause.getCatchCondition(), inner, free);
            walk(clause.getBody(), inner, free);
        } else if (node instanceof LabeledStatement) {
            walk(((LabeledStatement) node).getStatement(), bound, free);
        } else if (node instanceof ThrowStatement) {
            walk(((ThrowStatement) node).getExpression(), bound, free);
        } else if (node instanceof Scope) {
            scope(node, bound, free);
        } else {
            for (Node child : node) {
                walk((AstNode) child, bound, free);
            }
        }
    }

    private static void walkFunction(FunctionNode function, Set<String> bound, Set<String> free) {
        Set<String> inner = new HashSet<String>(bound);
        Name name = function.getFunctionName();
        if (name != null) {
            inner.add(name.getIdentifier());
        }
        for (AstNode parameter : function.getParams()) {
            bindTarget(parameter, inner);
        }
        if (function.isExpressionClosure()) {
            walk(function.getBody(), inner, free);
        } else {
            scope(function.getBody(), inner, free);
        }
    }

    private static void bindTarget(AstNode target, Set<String> bound) {
        if (target instanceof Name) {
            bound.add(((Name) target).getIdentifier());
        } else if (target instanceof ArrayLiteral) {
            for (AstNode element : ((ArrayLiteral) target).getElements()) {
                bindTarget(element, bound);
            }
        } else if (target instanceof ObjectLiteral) {
            for (ObjectProperty property : ((ObjectLiteral) target).getElements()) {
                bindTarget(property.getRight(), bound);
            }
        } else if (target instanceof InfixExpression) {
            bindTarget(((InfixExpression) target).getLeft(), bound);
        }
    }
}
