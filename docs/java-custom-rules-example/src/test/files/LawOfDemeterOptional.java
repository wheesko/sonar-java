import java.util.*;

class TestClass {
  private int id;

  public TestClass(int id) {
    this.id = id;
  }

  public int getId() {
    return id;
  }
}

class LawOfDemeterOptional {
  List<TestClass> testClassList;
  List<Optional<Optional<List<TestClass>>>> nestedList;

  public LawOfDemeterOptional() {
    this.testClassList = new ArrayList();
    this.addListElements();
  }

  public void addListElements () {
    for (i = 0; i < 10; i++) {
      testClassList.add(new TestClass(i));
    }
  }

  public void doFoo() {
     Optional<String> foundClass = this.findClassMoreThanFive(this.testClassList);

     foundClass.ifPresent(c -> System.out.println(c));
  }

  public Optional<String> findClassMoreThanFive(List<TestClass> t) {
    return t.stream()
      .filter(testClass -> testClass.getId() > 5)
      .findFirst()
      .map(testClass -> String.format("The id is: %s", testClass.getId()));
  }
}
