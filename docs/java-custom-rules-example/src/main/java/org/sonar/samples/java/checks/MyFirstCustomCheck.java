package org.sonar.samples.java.checks;

import org.sonar.check.Rule;
import org.sonar.plugins.java.api.IssuableSubscriptionVisitor;
import org.sonar.plugins.java.api.semantic.Symbol;
import org.sonar.plugins.java.api.semantic.Type;
import org.sonar.plugins.java.api.tree.*;
import org.sonar.plugins.java.api.tree.Tree.Kind;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/***
* Law of Demeter check
 * There are primarily 4 principles of the least knowledge in java as follows:
 *
 * Methods of Object O itself (because you could talk with yourself) :check:
 * Methods of objects passed as arguments to M :check:
 * Methods of objects held in instance variable :done:
 * Methods of objects which are created locally in method M :done:
 * Methods of static fields :check:
* ***/

@Rule(key = "MyFirstCustomRule")
public class MyFirstCustomCheck extends IssuableSubscriptionVisitor {

  @Override
  public List<Kind> nodesToVisit() {
    return Collections.singletonList(Kind.CLASS);
  }

  @Override
  public void visitNode(Tree tree) {
    ClassTree classTree = (ClassTree) tree;
    List<MethodTree> methods = findClassMethods(classTree);

    methods.forEach(method ->
      findMethodInvocationsInMethod(method).forEach(methodInvocation -> validateLawOfDemeter(method, methodInvocation))
    );
  }

  private void validateLawOfDemeter(MethodTree methodTree, MethodInvocationTree methodInvocationTree) {
    // Check Law of Demeter conditions one by one. If all are false, return false, if any are true, return true.
    if (calledMethodIsStatic(methodInvocationTree)) {
      return;
    }

    if (calledMethodIsMemberOfSameObject(methodTree, methodInvocationTree)) {
      return;
    }

    if (calledMethodBelongsToOneOfParameters(methodTree, methodInvocationTree)) {
      return;
    }

    if (calledMethodBelongsToOneOfClassVariables(methodTree, methodInvocationTree)) {
      return;
    }

    if (calledMethodBelongsToVariableInMethod(methodTree, methodInvocationTree)) {
      return;
    }

    reportIssue(methodInvocationTree, "LawOfDemeterViolation");
  }

  private boolean calledMethodIsStatic(MethodInvocationTree methodInvocationTree) {
    MemberSelectExpressionTree memberSelectExpT = (MemberSelectExpressionTree) methodInvocationTree.methodSelect();
    return memberSelectExpT.identifier().symbol().isStatic();
  }

  private boolean calledMethodIsMemberOfSameObject(MethodTree methodTree, MethodInvocationTree methodInvocationTree) {
    Tree methodClass = findOwningClass(methodTree);
    Tree invokedMethodOwner = findOwningClass(getInvokedMethodDeclarationTree(methodInvocationTree));
    return invokedMethodOwner.equals(methodClass);
  }

  private boolean calledMethodBelongsToOneOfParameters(MethodTree methodTree, MethodInvocationTree methodInvocationTree) {
    ClassTree invokedMethodOwner = (ClassTree) findOwningClass(getInvokedMethodDeclarationTree(methodInvocationTree));
    Type ownerType = invokedMethodOwner.symbol().type();
    List<Type> trees = methodTree.parameters().stream().map(param -> param.type().symbolType()).collect(Collectors.toList());
    return trees.contains(ownerType);
  }

  private boolean calledMethodBelongsToVariableInMethod(MethodTree methodTree, MethodInvocationTree methodInvocationTree) {
    ClassTree invokedMethodOwner = (ClassTree) findOwningClass(getInvokedMethodDeclarationTree(methodInvocationTree));
    Type ownerType = invokedMethodOwner.symbol().type();
    List<Type> variablesInMethod = methodTree.block().body()
      .stream()
      .filter(statementTree -> statementTree.is(Kind.VARIABLE))
      .map(VariableTree.class::cast)
      .map(VariableTree::symbol)
      .map(Symbol::type)
      .collect(Collectors.toList());

    return variablesInMethod.contains(ownerType);
  }

  private boolean calledMethodBelongsToOneOfClassVariables(MethodTree methodTree, MethodInvocationTree methodInvocationTree) {
    ClassTree methodClass = (ClassTree) findOwningClass(methodTree);
    List<Type> classVariables = methodClass.members()
      .stream()
      .filter(tree -> tree.is(Kind.VARIABLE))
      .map(VariableTree.class::cast)
      .map(tree -> tree.symbol().type())
      .collect(Collectors.toList());
    ClassTree invokedMethodOwner = (ClassTree) findOwningClass(getInvokedMethodDeclarationTree(methodInvocationTree));
    Type t = invokedMethodOwner.symbol().type();

    return classVariables.contains(t);
  }

  private Tree getInvokedMethodDeclarationTree(MethodInvocationTree methodInvocationTree) {
    MemberSelectExpressionTree memberSelectExpressionTree = (MemberSelectExpressionTree) methodInvocationTree.methodSelect();
    return memberSelectExpressionTree.identifier().symbol().declaration();
  }

  private Tree findOwningClass(Tree methodTree) {
    Tree parent = methodTree.parent();
    while (parent != null && !parent.is(Kind.CLASS)) {
      parent = parent.parent();
    }
    return parent;
  }

  /* Find methods in class */
  private List<MethodTree> findClassMethods(ClassTree classTree) {
    return classTree.members()
      .stream().filter(m -> m.is(Kind.METHOD))
      .map(MethodTree.class::cast)
      .collect(Collectors.toList());
  }

  /* In a method m, find all method invocations MI */
  private List<MethodInvocationTree> findMethodInvocationsInMethod(MethodTree methodTree) {
    List<MethodInvocationTree> methodInvocationTrees = findExpressionsInMethod(methodTree)
      .filter(expressionStatementTree -> expressionStatementTree.expression().is(Kind.METHOD_INVOCATION))
      .map(expressionStatementTree -> (MethodInvocationTree) expressionStatementTree.expression())
      .collect(Collectors.toList());
    return methodInvocationTrees;
  }

  /* In a method m, find all method invocations MI */
  private Stream<ExpressionStatementTree> findExpressionsInMethod(MethodTree methodTree) {
    return Optional.ofNullable(methodTree.block())
      .map(BlockTree::body)
      .orElse(Collections.emptyList())
      .stream()
      .filter(body -> body.is(Kind.EXPRESSION_STATEMENT))
      .map(ExpressionStatementTree.class::cast);
  }
}


/*
* Active bugs:
* Method chains not parsed correctly (only last method is evaluated)
* */
