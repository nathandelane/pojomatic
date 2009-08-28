package org.pojomatic.internal;

import static org.junit.Assert.*;
import static org.pojomatic.internal.PojomatorImpl.HASH_CODE_MULTIPLIER;
import static org.pojomatic.internal.PojomatorImpl.HASH_CODE_SEED;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.pojomatic.Pojomator;
import org.pojomatic.annotations.AutoProperty;
import org.pojomatic.annotations.PojoFormat;
import org.pojomatic.annotations.Property;
import org.pojomatic.annotations.PropertyFormat;
import org.pojomatic.diff.Difference;
import org.pojomatic.diff.DifferenceFromNull;
import org.pojomatic.diff.DifferenceToNull;
import org.pojomatic.diff.Differences;
import org.pojomatic.diff.PropertyDifferences;
import org.pojomatic.formatter.DefaultPojoFormatter;
import org.pojomatic.formatter.DefaultPropertyFormatter;

public class PojomatorImplTest {
  private static Pojomator<ObjectProperty> OBJECT_PROPERTY_POJOMATOR =
    makePojomatorImpl(ObjectProperty.class);

  private static Pojomator<ObjectPairProperty> OBJECT_PAIR_PROPERTY_POJOMATOR =
    makePojomatorImpl(ObjectPairProperty.class);

  private static Pojomator<IntProperty> INT_PROPERTY_POJOMATOR =
    makePojomatorImpl(IntProperty.class);

  private static Pojomator<StringArrayProperty> STRING_ARRAY_PROPERTY_POJOMATOR =
    makePojomatorImpl(StringArrayProperty.class);

  private static final Pojomator<AccessCheckedProperties> ACCESS_CHECKED_PROPERTIES_POJOMATOR =
    makePojomatorImpl(AccessCheckedProperties.class);

  private static final List<Class<?>> PRIMATIVE_TYPES = Arrays.<Class<?>>asList(
    Boolean.TYPE, Byte.TYPE, Character.TYPE, Short.TYPE,
    Integer.TYPE, Long.TYPE, Float.TYPE, Double.TYPE);


  @Test(expected=NullPointerException.class) public void testNullHashCode() {
    OBJECT_PROPERTY_POJOMATOR.doHashCode(null);
  }

  @Test(expected=NullPointerException.class) public void testToStringOnNull() {
    OBJECT_PROPERTY_POJOMATOR.doToString(null);
  }

  @Test(expected=NullPointerException.class) public void testNullInstanceEquals() {
    OBJECT_PROPERTY_POJOMATOR.doEquals(null, new ObjectProperty("e"));
  }

  @Test public void testNullEquals() {
    assertFalse(OBJECT_PROPERTY_POJOMATOR.doEquals(new ObjectProperty(null), null));
  }

  @Test public void testReflexiveEquals() {
    ExceptionThrowingProperty instance = new ExceptionThrowingProperty();
    assertTrue(makePojomatorImpl(ExceptionThrowingProperty.class).doEquals(instance, instance));
  }

  @Test public void testCastCheckFailureForEquals() {
    assertFalse(OBJECT_PROPERTY_POJOMATOR.doEquals(new ObjectProperty("test"), "differentClass"));
  }

  @Test public void testPropertyEquals() {
    String s1 = "hello";
    String s2 = copyString(s1); // ensure we are using .equals, and not ==

    assertTrue(OBJECT_PROPERTY_POJOMATOR.doEquals(new ObjectProperty(s1), new ObjectProperty(s2)));
    assertFalse(OBJECT_PROPERTY_POJOMATOR.doEquals(
      new ObjectProperty("hello"), new ObjectProperty("goodbye")));
  }

  @Test public void testNullPropertyEquals() {
    assertTrue(OBJECT_PROPERTY_POJOMATOR.doEquals(
      new ObjectProperty(null), new ObjectProperty(null)));
    assertFalse(OBJECT_PROPERTY_POJOMATOR.doEquals(
      new ObjectProperty(null), new ObjectProperty("not null over here")));
  }

  @Test public void testPrimativePropertyEquals() {
    assertTrue(INT_PROPERTY_POJOMATOR.doEquals(new IntProperty(3), new IntProperty(3)));
    assertFalse(INT_PROPERTY_POJOMATOR.doEquals(new IntProperty(3), new IntProperty(4)));
  }

  @Test public void testObjectArrayPropertyEquals() {
    String s1 = "hello";
    String s2 = copyString(s1);
    assertTrue(STRING_ARRAY_PROPERTY_POJOMATOR.doEquals(
      new StringArrayProperty(s1, "goodbye"), new StringArrayProperty(s2, "goodbye")));
    assertFalse(STRING_ARRAY_PROPERTY_POJOMATOR.doEquals(
      new StringArrayProperty(s1, "goodbye"), new StringArrayProperty("goodbye", s1)));
  }

  private String copyString(String s1) {
    return new String(s1); //NOPMD - we mean to make a copy
  }

  @Test public void testPrimitiveArrayEquals() throws Exception {
    final ObjectProperty nullProperty = new ObjectProperty(null);
    final ObjectProperty objectArrayProperty = new ObjectProperty(new String[] {"foo"});
    for (Class<?> primitiveType : PRIMATIVE_TYPES) {
      ObjectProperty main = new ObjectProperty(Array.newInstance(primitiveType, 3));
      ObjectProperty other = new ObjectProperty(Array.newInstance(primitiveType, 3));
      ObjectProperty different = new ObjectProperty(Array.newInstance(primitiveType, 4));
      assertTrue(OBJECT_PROPERTY_POJOMATOR.doEquals(main, other));
      assertFalse(OBJECT_PROPERTY_POJOMATOR.doEquals(nullProperty, main));
      assertFalse(OBJECT_PROPERTY_POJOMATOR.doEquals(main, nullProperty));
      assertFalse(OBJECT_PROPERTY_POJOMATOR.doEquals(objectArrayProperty, main));
      assertFalse(OBJECT_PROPERTY_POJOMATOR.doEquals(main, objectArrayProperty));
      assertFalse(OBJECT_PROPERTY_POJOMATOR.doEquals(main, different));
    }
  }

  @Test public void testDeepObjectArrayEquals() {
    //tests array of arrays, and that .equals is being called per element
    assertTrue(OBJECT_PROPERTY_POJOMATOR.doEquals(
      new ObjectProperty(new Object[] { "foo", new String[] {"bar"} }),
      new ObjectProperty(new Object[] { new String("foo"), new String[] {new String("bar")} })));

    assertFalse(OBJECT_PROPERTY_POJOMATOR.doEquals(
      new ObjectProperty(new Object[] { "foo", new String[] {"bar"} }),
      new ObjectProperty(new Object[] { new String("foo"), new String[] {new String("baz")} })));
  }

  @Test public void testArrayVsNonArrayEquals() {
    ObjectProperty arrayProperty = new ObjectProperty(new String[] {""});
    ObjectProperty stringProperty = new ObjectProperty("");
    ObjectProperty nullProperty = new ObjectProperty(null);

    assertFalse(OBJECT_PROPERTY_POJOMATOR.doEquals(arrayProperty, stringProperty));
    assertFalse(OBJECT_PROPERTY_POJOMATOR.doEquals(stringProperty, arrayProperty));
    assertFalse(OBJECT_PROPERTY_POJOMATOR.doEquals(arrayProperty, nullProperty));
    assertFalse(OBJECT_PROPERTY_POJOMATOR.doEquals(nullProperty, arrayProperty));
  }

  @Test public void testShortCircuitEquals() {
    AccessCheckedProperties left = new AccessCheckedProperties(1,1);
    AccessCheckedProperties right = new AccessCheckedProperties(2,2);
    assertFalse(ACCESS_CHECKED_PROPERTIES_POJOMATOR.doEquals(left, right));
    assertFalse(left.getBCalled);
    assertFalse(right.getBCalled);

    assertTrue(ACCESS_CHECKED_PROPERTIES_POJOMATOR.doEquals(left, left));
    assertFalse(left.getBCalled);

    assertFalse(ACCESS_CHECKED_PROPERTIES_POJOMATOR.doEquals(left, null));
    assertFalse(left.getBCalled);

    assertFalse(ACCESS_CHECKED_PROPERTIES_POJOMATOR.doEquals(left, "hello"));
    assertFalse(left.getBCalled);

    assertTrue(ACCESS_CHECKED_PROPERTIES_POJOMATOR.doEquals(left, new AccessCheckedProperties(1,1)));
    assertTrue(left.getBCalled);
  }

  @Test public void testNullValueHashCode() {
    assertEquals(HASH_CODE_MULTIPLIER * HASH_CODE_SEED,
      OBJECT_PROPERTY_POJOMATOR.doHashCode(new ObjectProperty(null)));
  }

  @Test public void testPropertyHashCode() {
    assertEquals(
      HASH_CODE_MULTIPLIER * HASH_CODE_SEED + "foo".hashCode(),
      OBJECT_PROPERTY_POJOMATOR.doHashCode(new ObjectProperty("foo")));
  }

  @Test public void testPropertyPairHashCode() {
    assertEquals(
      HASH_CODE_MULTIPLIER * (HASH_CODE_MULTIPLIER * HASH_CODE_SEED + "foo".hashCode())
      + "bar".hashCode(),
      OBJECT_PAIR_PROPERTY_POJOMATOR.doHashCode(new ObjectPairProperty("foo", "bar")));
  }

  @Test public void testPrimativeHashCode() {
    assertEquals(
      HASH_CODE_MULTIPLIER * HASH_CODE_SEED + 7,
      INT_PROPERTY_POJOMATOR.doHashCode(new IntProperty(7)));
  }

  @Test public void testObjectArrayHashCode() {
    String[] strings = new String[] {"hello", "world" };
    assertEquals(
      HASH_CODE_MULTIPLIER *  HASH_CODE_SEED + Arrays.hashCode(strings),
      OBJECT_PROPERTY_POJOMATOR.doHashCode(new ObjectProperty(strings)));
  }

  @Test public void testPrimativeArrayHashCode() throws Exception {
    for (Class<?> primitiveType : PRIMATIVE_TYPES) {
      Object primativeArray = Array.newInstance(primitiveType, 2);
      int expected = HASH_CODE_MULTIPLIER * HASH_CODE_SEED +
        (Integer) Arrays.class.getDeclaredMethod("hashCode", primativeArray.getClass())
        .invoke(null, primativeArray);
      assertEquals(
        "primative type " + primitiveType,
        expected,
        OBJECT_PROPERTY_POJOMATOR.doHashCode(
          new ObjectProperty(primativeArray)));
    }
  }

  @Test public void testSimpleToString() {
    String actual = OBJECT_PAIR_PROPERTY_POJOMATOR.doToString(new ObjectPairProperty("ess", "tee"));
    assertEquals("ObjectPairProperty{s: {ess}, t: {tee}}", actual);
  }

  @Test public void testToStringNames() {
    assertEquals(
      "AccessCheckedProperties{a: {1}, b: {2}}",
      ACCESS_CHECKED_PROPERTIES_POJOMATOR.doToString(new AccessCheckedProperties(1, 2)));
  }

  @Test public void testCustomFormatters() {
    assertEquals("PREFIXFormattedObject{s: {BEFOREx}}",
      makePojomatorImpl(FormattedObject.class).doToString(new FormattedObject("x")));
  }

  @Test public void testDiffNullInstance() {
    ObjectPairProperty other = new ObjectPairProperty("this", "that");
    Differences actual = OBJECT_PAIR_PROPERTY_POJOMATOR.doDiff(null, other);
    assertTrue(actual instanceof DifferenceFromNull);
  }

  @Test public void testDiffNullOther() {
    ObjectPairProperty instance = new ObjectPairProperty("this", "that");
    Differences actual = OBJECT_PAIR_PROPERTY_POJOMATOR.doDiff(instance, null);
    assertTrue(actual instanceof DifferenceToNull);
  }

  @Test public void testDiffNulls() {
    assertTrue(OBJECT_PAIR_PROPERTY_POJOMATOR.doDiff(null, null).areEqual());
  }

  @Test public void testDiffSameObject() {
    ObjectPairProperty objectPairProperty = new ObjectPairProperty("this", "that");
    assertTrue(
      OBJECT_PAIR_PROPERTY_POJOMATOR.doDiff(objectPairProperty, objectPairProperty).areEqual());
  }

  @Test public void testDiffEqualObjects() {
    assertTrue(
      OBJECT_PAIR_PROPERTY_POJOMATOR.doDiff(
        new ObjectPairProperty("this", "that"), new ObjectPairProperty("this", "that")).areEqual());
  }

  @Test public void testDiffDifferentObjectsWithSinglePropertyDifferent() {
    final Differences diffs = OBJECT_PAIR_PROPERTY_POJOMATOR.doDiff(
      new ObjectPairProperty("this", "that"), new ObjectPairProperty("THIS", "that"));
    assertTrue(diffs instanceof PropertyDifferences);
    assertEquals(
      Arrays.asList(new Difference("s", "this", "THIS")),
      ((PropertyDifferences) diffs).getDifferences());
  }

  @Test public void testDiffDifferentObjectsWithMultiplePropertiesDifferent() {
    final Differences diffs = OBJECT_PAIR_PROPERTY_POJOMATOR.doDiff(
      new ObjectPairProperty("this", "that"), new ObjectPairProperty("THIS", "THAT"));
    assertEquals(PropertyDifferences.class, diffs.getClass());
    assertEquals(
      Arrays.asList(new Difference("s", "this", "THIS"), new Difference("t", "that", "THAT")),
      ((PropertyDifferences) diffs).getDifferences());
  }

  @Test public void testDiffAgainstNull() {
    ObjectPairProperty instance = new ObjectPairProperty("this", "that");
    Differences differences = OBJECT_PAIR_PROPERTY_POJOMATOR.doDiff(instance, null);
    assertFalse(differences.areEqual());
    assertEquals(DifferenceToNull.class, differences.getClass());
  }

  @Test public void testDiffAgainstWrongType() {
    Pojomator<?> pojomator = OBJECT_PAIR_PROPERTY_POJOMATOR;
    @SuppressWarnings("unchecked") Pojomator<Object> misCastPojomator = (Pojomator<Object>) pojomator;
    try {
      misCastPojomator.doDiff(new ObjectPairProperty(1,2), "wrong");
      fail("exception expcected");
    }
    catch (ClassCastException e) {
      assertEquals(
        "other has type java.lang.String which is not a subtype of org.pojomatic.internal.PojomatorImplTest$ObjectPairProperty",
        e.getMessage());
    }
  }

  @Test public void testDiffWrongType() {
    Pojomator<?> pojomator = OBJECT_PAIR_PROPERTY_POJOMATOR;
    @SuppressWarnings("unchecked") Pojomator<Object> misCastPojomator = (Pojomator<Object>) pojomator;
    try {
      misCastPojomator.doDiff("wrong", new ObjectPairProperty(1,2));
      fail("exception expcected");
    }
    catch (ClassCastException e) {
      assertEquals(
        "instance has type java.lang.String which is not a subtype of org.pojomatic.internal.PojomatorImplTest$ObjectPairProperty",
        e.getMessage());
    }
  }

  @Test(expected=IllegalArgumentException.class)
  public void testNonPojomatedClass() {
    makePojomatorImpl(String.class);
  }

  @Test public void testPrivateClass() {
    Pojomator<PrivateClass> pojomator = makePojomatorImpl(PrivateClass.class);
    assertTrue(pojomator.doToString(new PrivateClass()).contains("number"));
  }

  @PojoFormat(SimplePojoFormatter.class)
  public static class FormattedObject {
    public FormattedObject(Object s) {
      this.s = s;
    }
    @Property
    @PropertyFormat(SimplePropertyFormatter.class)
    public Object s;
  }

  public static class SimplePojoFormatter extends DefaultPojoFormatter {

    @Override
    public String getToStringPrefix(Class<?> pojoClass) {
      return "PREFIX" + super.getToStringPrefix(pojoClass);
    }
  }

  public static class SimplePropertyFormatter extends DefaultPropertyFormatter {
    @Override
    public String format(Object value) {
      return "BEFORE" + super.format(value);
    }
  }

  public static class ObjectProperty {
    public ObjectProperty(Object s) {
      this.s = s;
    }
    @Property public Object s;
  }

  public static class ObjectPairProperty {
    public ObjectPairProperty(Object s, Object t) {
      this.s = s;
      this.t = t;
    }
    @Property public Object s;
    @Property public Object t;
  }

  public static class IntProperty {
    public IntProperty(int i) {
      this.i = i;
    }
    @Property int i;
  }

  public static class StringArrayProperty {
    public StringArrayProperty(String... strings) {
      this.strings = strings;
    }

    @Property String[] strings;
  }

  public static class ExceptionThrowingProperty {
    @Property public int bomb() {
      throw new RuntimeException();
    }
  }

  public static class AccessCheckedProperties {
    public AccessCheckedProperties(int a, int b) {
      this.a = a;
      this.b = b;
    }

    @Property public int getA() {
      return a;
    }

    @Property public int getB() {
      getBCalled = true;
      return b;
    }

    private int a, b;
    private boolean getBCalled;
  }

  @AutoProperty
  private static class PrivateClass {
    @SuppressWarnings("unused")
    private int number;
  }

  private static <T> Pojomator<T> makePojomatorImpl(Class<T> clazz) {
    return new PojomatorImpl<T>(clazz);
  }
}