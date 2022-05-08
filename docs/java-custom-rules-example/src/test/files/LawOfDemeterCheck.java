import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class UnfriendlierClass {
  FriendlyClass friendlyClass;

  FriendlyClass uf_foo1() { return this.friendlyClass; }
}

class UnfriendlyClass {
  UnfriendlierClass unfriendlyClass;

  void u_foo1() {}

  UnfriendlierClass u_foo2() {
    return this.unfriendlyClass;
  }
}

class FriendlyClass {
  UnfriendlyClass unfriendlyClass;

  UnfriendlyClass f_foo() { return this.unfriendlyClass;}
}

class MyClass {
  FriendlyClass friendlyClass;

  MyClass(MyClass mc) { this.friendlyClass = new FriendlyClass();}

  public void foozero() { this.foo1(); }

  public void doNothing() { Integer.parseInt("1"); }

  public void foo1() {
    friendlyClass.f_foo();
  }

  public void foo2() {
    friendlyClass
      .f_foo()
      .u_foo1(); // Noncompliant
  }

  public void foo3(UnfriendlyClass unfriendlyClass) { unfriendlyClass.u_foo1(); }

  public void foo4() {
    UnfriendlyClass unfriendlyClass = new UnfriendlyClass();
    unfriendlyClass.u_foo1();

    FriendlyClass friendlyClass = new FriendlyClass();
    friendlyClass
      .f_foo()
      .u_foo2()
      .uf_foo1() // Noncompliant
      .f_foo();
  }

  public void foo5() {
    UnfriendlyClass unfriendlyClass = new UnfriendlyClass();
    unfriendlyClass.u_foo1();

    FriendlyClass friendlyClass = new FriendlyClass();
    UnfriendlyClass uf = friendlyClass.f_foo();
    UnfriendlierClass unfC = uf.u_foo2();
    FriendlyClass fc = unfC.uf_foo1(); // Noncompliant
    fc.f_foo();
    friendlyClass
      .f_foo()
      .u_foo2()
      .uf_foo1() // Noncompliant
      .f_foo();
  }
}
