package org.sonar.samples.java.checks;

import org.sonar.check.Rule;
import org.sonar.check.RuleProperty;
import org.sonar.plugins.java.api.IssuableSubscriptionVisitor;
import org.sonar.plugins.java.api.semantic.Type;
import org.sonar.plugins.java.api.tree.*;
import org.sonar.plugins.java.api.tree.Tree.Kind;

import java.util.*;
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

@Rule(key = "LawOfDemeterRule")
public class LawOfDemeterCheck extends IssuableSubscriptionVisitor {

  private static final String ALLOWED_METHOD_NAME_PATTERNS = "java.lang.InterruptedException, " +
    "java.lang.NumberFormatException, " +
    "java.lang.NoSuchMethodException, " +
    "java.text.ParseException, " +
    "java.net.MalformedURLException, " +
    "java.time.format.DateTimeParseException";

  @RuleProperty(
    key = "methodNameExceptions",
    description = "List of allowed method names, seperated by commas",
    defaultValue = "" + ALLOWED_METHOD_NAME_PATTERNS)
  public String allowedMethodNamePatterns = ALLOWED_METHOD_NAME_PATTERNS;

  @RuleProperty(
    key = "enableExceptions",
    description = "Enable use of exceptions",
    defaultValue = "false")
  public boolean allowRuleExceptions = false;

  @Override
  public List<Kind> nodesToVisit() {
    return Collections.singletonList(Kind.METHOD_INVOCATION);
  }

  @Override
  public void visitNode(Tree tree) {
    MethodInvocationTree methodInvocationTree = (MethodInvocationTree) tree;
    Optional<MethodTree> owningMethod = findOwningMethod(methodInvocationTree);

    validateLawOfDemeter(owningMethod, methodInvocationTree);
  }

  private void validateLawOfDemeter(Optional<MethodTree> methodTree, MethodInvocationTree methodInvocationTree) {
    // if method has no parent, means it probably is an internal java class
    if (!methodTree.isPresent()) {
      return;
    }

    if (methodNameBelongsToOneOfExceptions(methodInvocationTree)) {
      return;
    }

    // Check Law of Demeter conditions one by one. If all are false, return false, if any are true, return true.
    if (calledMethodIsStatic(methodInvocationTree)) {
      return;
    }

    if (calledMethodIsMemberOfSameObject(methodTree.get(), methodInvocationTree)) {
      return;
    }

    if (calledMethodBelongsToOneOfParameters(methodTree.get(), methodInvocationTree)) {
      return;
    }

    if (calledMethodBelongsToOneOfClassVariables(methodTree.get(), methodInvocationTree)) {
      return;
    }

    if (calledMethodBelongsToVariableInMethod(methodTree.get(), methodInvocationTree)) {
      return;
    }

    MemberSelectExpressionTree methodInvocation = (MemberSelectExpressionTree) methodInvocationTree.methodSelect();

    reportIssue(methodInvocation.identifier(), "LawOfDemeterViolation");
  }

  private boolean methodNameBelongsToOneOfExceptions(MethodInvocationTree methodInvocationTree) {
    MemberSelectExpressionTree memberSelectExpT = (MemberSelectExpressionTree) methodInvocationTree.methodSelect();

    return this.allowRuleExceptions && Arrays.stream(this.allowedMethodNamePatterns.split(", "))
      .anyMatch(ruleExceptionPattern -> memberSelectExpT.identifier().name().matches(ruleExceptionPattern));
  }

  private boolean calledMethodIsStatic(MethodInvocationTree methodInvocationTree) {
    MemberSelectExpressionTree memberSelectExpT = (MemberSelectExpressionTree) methodInvocationTree.methodSelect();
    return memberSelectExpT.identifier().symbol().isStatic();
  }

  private boolean calledMethodIsMemberOfSameObject(MethodTree methodTree, MethodInvocationTree methodInvocationTree) {
    Optional<ClassTree> methodClass = findOwningClass(methodTree);
    Optional<Tree> declarationTree = getInvokedMethodDeclarationTree(methodInvocationTree);
    if (!declarationTree.isPresent() || !methodClass.isPresent()) {
      return true;
    }
    Optional<ClassTree> invokedMethodOwner = findOwningClass(declarationTree.get());
    return invokedMethodOwner.equals(methodClass);
  }

  private boolean calledMethodBelongsToOneOfParameters(MethodTree methodTree, MethodInvocationTree methodInvocationTree) {
    Optional<Tree> invokedMethodDeclaration = getInvokedMethodDeclarationTree(methodInvocationTree);
    if (!invokedMethodDeclaration.isPresent()) {
      return true;
    }

    Optional<ClassTree> invokedMethodOwner = findOwningClass(invokedMethodDeclaration.get());
    if (!invokedMethodOwner.isPresent()) {
      return true;
    }

    Type ownerType = invokedMethodOwner.get().symbol().type();
    List<Type> trees = methodTree.parameters().stream().map(param -> param.type().symbolType()).collect(Collectors.toList());
    return trees.contains(ownerType);
  }

  private boolean calledMethodBelongsToVariableInMethod(MethodTree methodTree, MethodInvocationTree methodInvocationTree) {
    Optional<Tree> invokedMethodDeclaration = getInvokedMethodDeclarationTree(methodInvocationTree);
    if (!invokedMethodDeclaration.isPresent()) {
      return true;
    }

    Optional<ClassTree> invokedMethodOwner = findOwningClass(invokedMethodDeclaration.get());
    if(!invokedMethodOwner.isPresent()) {
      return true;
    }

    Type ownerType = invokedMethodOwner.get().symbol().type();
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
      .flatMap(this::findAllChildTypes)
      .collect(Collectors.toList());

    return variablesInMethod.contains(ownerType);
  }

  private boolean calledMethodBelongsToOneOfClassVariables(MethodTree methodTree, MethodInvocationTree methodInvocationTree) {
    Optional<ClassTree> methodClass = findOwningClass(methodTree);
    if (!methodClass.isPresent()) {
      return true;
    }

    List<Type> classVariables = methodClass.get()
      .members().stream()
      .filter(tree -> tree.is(Kind.VARIABLE))
      .map(VariableTree.class::cast)
      .flatMap(this::findAllChildTypes)
      .collect(Collectors.toList());

    Optional<Tree> methodDeclaration = getInvokedMethodDeclarationTree(methodInvocationTree);
    if (!methodDeclaration.isPresent()) {
      return true;
    }

    Optional<ClassTree> invokedMethodOwner = findOwningClass(methodDeclaration.get());
    if (!invokedMethodOwner.isPresent()) {
      return true;
    }

    Type t = invokedMethodOwner.get().symbol().type();

    return classVariables.contains(t);
  }

  private Optional<Tree> getInvokedMethodDeclarationTree(MethodInvocationTree methodInvocationTree) {
    MemberSelectExpressionTree memberSelectExpressionTree = (MemberSelectExpressionTree) methodInvocationTree.methodSelect();
    return Optional.ofNullable(memberSelectExpressionTree.identifier().symbol().declaration());
  }

  private Optional<ClassTree> findOwningClass(Tree methodTree) {
    Tree parent = methodTree.parent();
    while (parent != null && !parent.is(Kind.CLASS)) {
      parent = parent.parent();
    }
    return Optional.ofNullable((ClassTree) parent);
  }

  private Optional<MethodTree> findOwningMethod(MethodInvocationTree methodTree) {
    Tree parent = methodTree.parent();
    while (parent != null && !parent.is(Kind.METHOD)) {
      parent = parent.parent();
    }

    return Optional.ofNullable((MethodTree) parent);
  }

  private Stream<Type> findAllChildTypes(VariableTree tree) {
    Type type = tree.symbol().type();
    return Stream.concat(
      Stream.of(type),
      getAllTypeArgumentTypes(type, new ArrayList<>()).stream()
    );
  }

  private List<Type> getAllTypeArgumentTypes(Type t, List<Type> foundTypes) {
    t.typeArguments().forEach(type -> {
      if (!type.typeArguments().isEmpty()) {
        getAllTypeArgumentTypes(type, foundTypes);
      }

      if (!foundTypes.contains(type)) {
        foundTypes.add(type);
      }
    });

    return foundTypes;
  }
}

