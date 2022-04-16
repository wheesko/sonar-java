class UnfriendlyClass {
  void u_foo1() {}
}

class FriendlyClass {
  UnfriendlyClass unfriendlyClass;

  UnfriendlyClass f_foo() { return this.unfriendlyClass;}
}

class MyClass {
  FriendlyClass friendlyClass;

  MyClass(MyClass mc) { }

  public void foozero() { this.foo1(); }

  public void doNothing() { Integer i = Integer.parseInt("1"); }

  public void foo1 () {
    friendlyClass.f_foo();
  }

  public void foo2 () {
    friendlyClass.f_foo().u_foo1(); // Noncompliant
  }

  public void foo3 (UnfriendlyClass unfriendlyClass) { unfriendlyClass.u_foo1(); }

  public void foo4 () {
    UnfriendlyClass unfriendlyClass = new UnfriendlyClass();
    unfriendlyClass.u_foo1();

    FriendlyClass friendlyClass = new FriendlyClass();
    friendlyClass
      // returns UnfriendlyClass
      .f_foo()
      // UnfriendlyClass.u_foo(1)
      .u_foo1(); // Noncompliant?
  }
}
