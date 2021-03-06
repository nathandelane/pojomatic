package org.pojomatic.internal;

import static org.testng.Assert.*;

import org.testng.annotations.Test;
import org.pojomatic.annotations.Property;
import org.pojomatic.Pojomator;
import org.pojomatic.Pojomatic;

public class EqualsInheritanceTest {
  private static class Parent {
    @Property int x = 3;

    @Override
    public int hashCode() {
      return Pojomatic.hashCode(this);
    }

    @Override
    public boolean equals(Object o) {
      return Pojomatic.equals(this, o);
    }
  }

  private static Pojomator<Parent> PARENT_POJOMATOR = PojomatorFactory.makePojomator(Parent.class);
  private static Parent PARENT = new Parent();

  @Test public void testChildWithNoNewProperties() {
    class Child extends Parent {}
    Child child = new Child();
    Pojomator<Child> childPojomator = PojomatorFactory.makePojomator(Child.class);

    assertTrue(PARENT_POJOMATOR.doEquals(PARENT, child));
    assertTrue(PARENT_POJOMATOR.doEquals(child, PARENT));
    assertTrue(childPojomator.doEquals(child, PARENT));
  }

  @Test public void testChildWithNewProperty() {
    class Child extends Parent { @Property int y = 4; }
    Child child = new Child();
    Pojomator<Child> childPojomator = PojomatorFactory.makePojomator(Child.class);

    assertFalse(PARENT.equals(child));
    assertFalse(child.equals(PARENT));
    assertFalse(PARENT_POJOMATOR.doEquals(PARENT, child));
    assertFalse(childPojomator.doEquals(child, PARENT));
    // If we explicitly use a PARENT_POJOMATOR to compare child to parent, we'll miss the additional
    //child property.
    assertTrue(PARENT_POJOMATOR.doEquals(child, PARENT));
  }

  @Test public void testTwoChildrenWithNoNewProperties() {
    class Child1 extends Parent {}
    class Child2 extends Parent {}
    Child1 child1 = new Child1();
    Child2 child2 = new Child2();
    Pojomator<Child1> childPojomator = PojomatorFactory.makePojomator(Child1.class);

    assertTrue(PARENT_POJOMATOR.doEquals(child1, child2));
    assertTrue(childPojomator.doEquals(child1, child2));
  }

  @Test public void testTwoChildrenWithOneHavingNewProperties() {
    class Child1 extends Parent { @Property int y = 4; }
    class Child2 extends Parent {}
    Child1 child1 = new Child1();
    Child2 child2 = new Child2();
    Pojomator<Child1> child1Pojomator = PojomatorFactory.makePojomator(Child1.class);
    Pojomator<Child2> child2Pojomator = PojomatorFactory.makePojomator(Child2.class);

    assertFalse(child1Pojomator.doEquals(child1, child2));
    assertFalse(child2Pojomator.doEquals(child2, child1));
  }
}
