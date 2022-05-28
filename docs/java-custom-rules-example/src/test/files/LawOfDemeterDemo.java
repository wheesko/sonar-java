class LawOfDemeterDemo {
  FriendlyClass friendlyClass;

  void foo1 (UnfriendlyClass unfriendlyClass) {
    // Leidžiama kviesti atributų metodus
    friendlyClass.calculate();

    // Leidžiama kviesti metodo parametrų metodus
    unfriendlyClass.doWork();

    // Leidžiama kviesti metodo viduje sukurtų objektų metodus
    UnfriendlyClass unfriendlyClass1 = new UnfriendlyClass();
    unfriendlyClass1.doWork();
  }

  void foo2() {
    // Draudžiamas kvietimas
    friendlyClass.getUnfriendlyClass().doWork(); // Noncompliant
  }
}

class UnfriendlyClass {
  public UnfriendlyClass() {

  }

  void doWork() {

  }
}

class FriendlyClass {
  UnfriendlyClass unfriendlyClass;

  public FriendlyClass() {
    this.unfriendlyClass = new UnfriendlyClass();
  }

  int calculate() {
    return 15 - 5;
  }

  UnfriendlyClass getUnfriendlyClass() {
    return this.unfriendlyClass;
  }
}
