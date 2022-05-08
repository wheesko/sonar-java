package org.sonar.samples.java.checks;

import org.sonar.check.Rule;
import org.sonar.plugins.java.api.IssuableSubscriptionVisitor;
import org.sonar.plugins.java.api.semantic.Symbol;
import org.sonar.plugins.java.api.semantic.Type;
import org.sonar.plugins.java.api.tree.*;
import org.sonar.plugins.java.api.tree.Tree.Kind;

import java.util.*;
import java.util.stream.Collectors;

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

@Rule(key = "LawOfDemeterRule")
public class LawOfDemeterCheck extends IssuableSubscriptionVisitor {

  @Override
  public List<Kind> nodesToVisit() {
    return Collections.singletonList(Kind.METHOD_INVOCATION);
  }

  @Override
  public void visitNode(Tree tree) {
    MethodInvocationTree methodInvocationTree = (MethodInvocationTree) tree;
    MethodTree owningMethod = findOwningMethod(methodInvocationTree);

    validateLawOfDemeter(owningMethod, methodInvocationTree);
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

    MemberSelectExpressionTree methodInvocation = (MemberSelectExpressionTree) methodInvocationTree.methodSelect();

    reportIssue(methodInvocation.identifier(), "LawOfDemeterViolation");
  }

  private boolean calledMethodIsStatic(MethodInvocationTree methodInvocationTree) {
    MemberSelectExpressionTree memberSelectExpT = (MemberSelectExpressionTree) methodInvocationTree.methodSelect();
    return memberSelectExpT.identifier().symbol().isStatic();
  }

  private boolean calledMethodIsMemberOfSameObject(MethodTree methodTree, MethodInvocationTree methodInvocationTree) {
    Tree methodClass = findOwningClass(methodTree);
    Optional<Tree> declarationTree = getInvokedMethodDeclarationTree(methodInvocationTree);
    if (!declarationTree.isPresent()) {
      return true;
    }
    Tree invokedMethodOwner = findOwningClass(declarationTree.get());
    return invokedMethodOwner.equals(methodClass);
  }

  private boolean calledMethodBelongsToOneOfParameters(MethodTree methodTree, MethodInvocationTree methodInvocationTree) {
    Optional<Tree> invokedMethodDeclaration = getInvokedMethodDeclarationTree(methodInvocationTree);
    if (!invokedMethodDeclaration.isPresent()) {
      return true;
    }
    ClassTree invokedMethodOwner = findOwningClass(invokedMethodDeclaration.get());
    Type ownerType = invokedMethodOwner.symbol().type();
    List<Type> trees = methodTree.parameters().stream().map(param -> param.type().symbolType()).collect(Collectors.toList());
    return trees.contains(ownerType);
  }

  private boolean calledMethodBelongsToVariableInMethod(MethodTree methodTree, MethodInvocationTree methodInvocationTree) {
    Optional<Tree> invokedMethodDeclaration = getInvokedMethodDeclarationTree(methodInvocationTree);
    if (!invokedMethodDeclaration.isPresent()) {
      return true;
    }
    ClassTree invokedMethodOwner = findOwningClass(invokedMethodDeclaration.get());
    Type ownerType = invokedMethodOwner.symbol().type();
    List<Type> variablesInMethod = Optional.ofNullable(methodTree.block())
      .map(BlockTree::body)
      .orElse(Collections.emptyList())
      .stream()
      .filter(statementTree -> statementTree.is(Kind.VARIABLE))
      .map(VariableTree.class::cast)
      // If a variable is not initialized or initialized using a method invocation, it probably is incorrect to call it's methods,
      // hence it should not be added to the "whitelist" of usable variables in a method
      .filter(variableTree -> Optional.ofNullable(variableTree.initializer())
        .map(tree -> !tree.is(Kind.METHOD_INVOCATION))
        .orElse(false)
      )
      .map(VariableTree::symbol)
      .map(Symbol::type)
      .collect(Collectors.toList());

    return variablesInMethod.contains(ownerType);
  }

  private boolean calledMethodBelongsToOneOfClassVariables(MethodTree methodTree, MethodInvocationTree methodInvocationTree) {
    ClassTree methodClass = findOwningClass(methodTree);
    List<Type> classVariables = methodClass.members()
      .stream()
      .filter(tree -> tree.is(Kind.VARIABLE))
      .map(VariableTree.class::cast)
      .map(tree -> tree.symbol().type())
      .collect(Collectors.toList());

    Optional<Tree> methodDeclaration = getInvokedMethodDeclarationTree(methodInvocationTree);
    if (!methodDeclaration.isPresent()) {
      return true;
    }
    ClassTree invokedMethodOwner = findOwningClass(methodDeclaration.get());
    Type t = invokedMethodOwner.symbol().type();

    return classVariables.contains(t);
  }

  private Optional<Tree> getInvokedMethodDeclarationTree(MethodInvocationTree methodInvocationTree) {
    MemberSelectExpressionTree memberSelectExpressionTree = (MemberSelectExpressionTree) methodInvocationTree.methodSelect();
    return Optional.ofNullable(memberSelectExpressionTree.identifier().symbol().declaration());
  }

  private ClassTree findOwningClass(Tree methodTree) {
    Tree parent = methodTree.parent();
    while (parent != null && !parent.is(Kind.CLASS)) {
      parent = parent.parent();
    }
    return (ClassTree) parent;
  }

  private MethodTree findOwningMethod(MethodInvocationTree methodTree) {
    Tree parent = methodTree.parent();
    while (parent != null && !parent.is(Kind.METHOD)) {
      parent = parent.parent();
    }
    return (MethodTree) parent;
  }
}


/*
* Active bugs:
* Method chains not parsed correctly (only last method is evaluated)
* */
