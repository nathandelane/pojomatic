package org.pojomatic.internal;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.util.CheckClassAdapter;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.pojomatic.Pojomator;
import org.pojomatic.PropertyElement;
import org.pojomatic.annotations.PojoFormat;
import org.pojomatic.diff.Differences;
import org.pojomatic.diff.NoDifferences;
import org.pojomatic.diff.PropertyDifferences;
import org.pojomatic.diff.ValueDifference;
import org.pojomatic.formatter.DefaultEnhancedPojoFormatter;
import org.pojomatic.formatter.EnhancedPojoFormatter;
import org.pojomatic.formatter.EnhancedPropertyFormatter;

import static org.objectweb.asm.Opcodes.*;

class PojomatorByteCodeGenerator {
  @Deprecated
  private static final String ENHANCED_POJO_FORMATTER_WRAPPER_INTERNAL_NAME =
    internalName(org.pojomatic.internal.EnhancedPojoFormatterWrapper.class);
  private static final Object[] NO_STACK = new Object[] {};
  private static final String OBJECT_INTERNAL_NAME = internalName(Object.class);
  private static final String BASE_POJOMATOR_INTERNAL_NAME = internalName(BasePojomator.class);
  @Deprecated
  private static final String ENHANCED_POJO_FORMATTER_WRAPPER_CONSTRUCTOR_DESCRIPTOR =
    methodDesc(void.class, org.pojomatic.formatter.PojoFormatter.class);

  static final String POJO_CLASS_FIELD_NAME = "pojoClass";
  private static final String BOOTSTRAP_METHOD_NAME = "bootstrap";

  private static final AtomicLong counter = new AtomicLong();

  final String pojomatorClassName;
  private final String pojomatorInternalClassName;
  private final Class<?> pojoClass;
  private final String pojoDescriptor;
  private final ClassProperties classProperties;
  private final Handle bootstrapMethod;

  PojomatorByteCodeGenerator(Class<?> pojoClass, ClassProperties classProperties) {
    this.pojomatorClassName = getClass().getName() + "$Pojomator$" + counter.incrementAndGet();
    this.pojomatorInternalClassName = internalName(pojomatorClassName);
    this.pojoClass = pojoClass;
    this.pojoDescriptor = classDesc(pojoClass);
    this.classProperties = classProperties;
    this.bootstrapMethod = new Handle(
      H_INVOKESTATIC,
      pojomatorInternalClassName,
      "bootstrap",
      methodDesc(CallSite.class, MethodHandles.Lookup.class, String.class, MethodType.class));
  }

  byte[] makeClassBytes() {
    ClassWriter classWriter = new ClassWriter(0);
    acceptClassVisitor(new CheckClassAdapter(classWriter));
    return classWriter.toByteArray();
  }

  private void acceptClassVisitor(ClassVisitor classWriter) {
    classWriter.visit(V1_7, ACC_PUBLIC + ACC_SUPER + ACC_SYNTHETIC, pojomatorInternalClassName, null,
        BASE_POJOMATOR_INTERNAL_NAME, new String[] { internalName(Pojomator.class) });

    classWriter.visitSource("Look for visitLineNumber", null);

    makeFields(classWriter);

    makeConstructor(classWriter);
    makeBootstrapMethod(classWriter);

    for (PropertyElement propertyElement: classProperties.getAllProperties()) {
      makeAccessor(classWriter, propertyElement);
    }

    makeDoEquals(classWriter);
    makeDoHashCode(classWriter);
    makeDoToString(classWriter);
    makeDoDiff(classWriter);

    classWriter.visitEnd();
  }

  private void makeFields(ClassVisitor classVisitor) {
    visitField(classVisitor, ACC_STATIC, POJO_CLASS_FIELD_NAME, classDesc(Class.class));
    for (PropertyElement property: classProperties.getToStringProperties()) {
      visitField(
        classVisitor, ACC_STATIC, propertyFormatterName(property), classDesc(EnhancedPropertyFormatter.class));
    }
    for (PropertyElement property: classProperties.getAllProperties()) {
      visitField(
        classVisitor, ACC_STATIC, propertyElementName(property), classDesc(PropertyElement.class));
    }
  }

  private static void visitField(ClassVisitor classVisitor, int flags, String name, String classDescriptor) {
    classVisitor.visitField(flags, name, classDescriptor, null, null).visitEnd();
  }

  private void makeBootstrapMethod(ClassVisitor classWriter) {
    LocalVariable caller = new LocalVariable("caller", MethodHandles.Lookup.class, null, 0);
    LocalVariable name = new LocalVariable("name", String.class, null, 1);
    LocalVariable type = new LocalVariable("type", MethodType.class, null, 2);
    MethodVisitor mv = classWriter.visitMethod(
       ACC_STATIC,
       BOOTSTRAP_METHOD_NAME,
       methodDesc(CallSite.class, MethodHandles.Lookup.class, String.class, MethodType.class),
       null,
       null);
    mv.visitCode();
    Label start = visitNewLabel(mv);
    caller.acceptLoad(mv);
    name.acceptLoad(mv);
    type.acceptLoad(mv);
    visitLineNumber(mv, 200);
    mv.visitLdcInsn(Type.getObjectType(pojomatorInternalClassName));
    visitLineNumber(mv, 201);
    mv.visitMethodInsn(
      INVOKESTATIC, BASE_POJOMATOR_INTERNAL_NAME,
      "bootstrap",
      methodDesc(CallSite.class, MethodHandles.Lookup.class, String.class, MethodType.class, Class.class));
    mv.visitInsn(ARETURN);
    Label end = visitNewLabel(mv);
    caller.withScope(start, end).acceptLocalVariable(mv);
    name.withScope(start, end).acceptLocalVariable(mv);
    type.withScope(start, end).acceptLocalVariable(mv);
    mv.visitMaxs(4, 3);
    mv.visitEnd();
  }

  private void makeAccessor(ClassVisitor classWriter, PropertyElement propertyElement) {
    LocalVariable pojo = new LocalVariable("pojo", Object.class, null, 0);
    int maxStackSize = 1;
    String accessorName = propertyAccessorName(propertyElement);
    MethodVisitor mv = classWriter.visitMethod(
      ACC_PRIVATE | ACC_STATIC, accessorName, accessorMethodType(propertyElement), null, null);
    mv.visitCode();
    Label start = visitNewLabel(mv);
    visitLineNumber(mv, 100);
    pojo.acceptLoad(mv);
    visitLineNumber(mv, 101);
    mv.visitInvokeDynamicInsn(accessorName, accessorMethodType(propertyElement), bootstrapMethod);
    visitLineNumber(mv, 102);
    Class<?> propertyType = propertyElement.getPropertyType();
    if (propertyType.isPrimitive()) {
      if (propertyType == float.class) {
        mv.visitInsn(FRETURN);
      }
      else if (propertyType == long.class) {
        maxStackSize++;
        mv.visitInsn(LRETURN);
      }
      else if (propertyType == double.class) {
        maxStackSize++;
        mv.visitInsn(DRETURN);
      }
      else {
        mv.visitInsn(IRETURN);
      }
    }
    else {
      mv.visitInsn(ARETURN);
    }
    Label end = visitNewLabel(mv);
    pojo.withScope(start, end).acceptLocalVariable(mv);
    mv.visitMaxs(maxStackSize, 2);
    mv.visitEnd();
  }

  private String accessorMethodType(PropertyElement propertyElement) {
    return methodDesc(propertyElement.getPropertyType(), Object.class);
  }

  private void makeConstructor(ClassVisitor cw) {
    LocalVariable varThis = new LocalVariable("this", classDesc(pojomatorInternalClassName), null, 0);
    LocalVariable varPojoClass = new LocalVariable("pojoClass", Class.class, null, 1);
    LocalVariable varClassProperties = new LocalVariable("classProperties", ClassProperties.class, null, 2);
    MethodVisitor mv = cw.visitMethod(
      ACC_PUBLIC, "<init>", methodDesc(void.class, Class.class, ClassProperties.class), null, null);
    mv.visitCode();
    Label start = visitNewLabel(mv);
    varThis.acceptLoad(mv);
    varPojoClass.acceptLoad(mv);
    varClassProperties.acceptLoad(mv);
    mv.visitMethodInsn(
      INVOKESPECIAL, BASE_POJOMATOR_INTERNAL_NAME, "<init>", methodDesc(void.class, Class.class, ClassProperties.class));
    mv.visitInsn(RETURN);
    Label end = visitNewLabel(mv);
    varThis.withScope(start, end).acceptLocalVariable(mv);
    varPojoClass.withScope(start, end).acceptLocalVariable(mv);
    varClassProperties.withScope(start, end).acceptLocalVariable(mv);
    mv.visitMaxs(3, 3);
    mv.visitEnd();
  }

  private void makeDoEquals(ClassVisitor cw) {
    LocalVariable varThis = new LocalVariable("this", classDesc(pojomatorInternalClassName), null, 0);
    LocalVariable varPojo1 = new LocalVariable("pojo1", pojoClass, pojoDescriptor, 1);
    LocalVariable varPojo2 = new LocalVariable("pojo2", pojoClass, pojoDescriptor, 2);

    int longOrDoubleStackAdjustment = 0;

    Object[] localVars = new Object[] {pojomatorInternalClassName, OBJECT_INTERNAL_NAME, OBJECT_INTERNAL_NAME};

    MethodVisitor mv = cw.visitMethod(
      ACC_PUBLIC, "doEquals", methodDesc(boolean.class, Object.class, Object.class), null, null);
    Label returnFalse = new Label();
    Label compatibleTypes = new Label();
    mv.visitCode();
    Label start = visitNewLabel(mv);
    varPojo1.acceptLoad(mv);
    checkNotNull(mv);
    varPojo2.acceptLoad(mv);
    visitLineNumber(mv, 1);
    Label notSameInstance = new Label();
    mv.visitJumpInsn(IF_ACMPNE, notSameInstance);
    mv.visitFrame(F_FULL, 3, localVars, 0, NO_STACK);

    // same instance; return true
    mv.visitInsn(ICONST_1);
    mv.visitInsn(IRETURN);

    // if other is null, return false.
    mv.visitLabel(notSameInstance);
    mv.visitFrame(F_FULL, 3, localVars, 0, NO_STACK);
    visitLineNumber(mv, 2);

    varPojo2.acceptLoad(mv);
    mv.visitJumpInsn(IFNULL, returnFalse);
    // if both types are equal, they are compatible
    varThis.acceptLoad(mv);
    invokeGetClass(mv);
    varPojo1.acceptLoad(mv);
    invokeGetClass(mv);
    mv.visitJumpInsn(IF_ACMPEQ, compatibleTypes);
    mv.visitFrame(F_FULL, 3, localVars, 0, NO_STACK);
    // types are not equals; check for compatibility
    varThis.acceptLoad(mv);
    varPojo2.acceptLoad(mv);
    invokeGetClass(mv);
    mv.visitMethodInsn(
      INVOKEVIRTUAL, BASE_POJOMATOR_INTERNAL_NAME, "isCompatibleForEquality", methodDesc(boolean.class, Class.class));
    mv.visitJumpInsn(IFEQ, returnFalse);
    mv.visitLabel(compatibleTypes);
    mv.visitFrame(F_FULL, 3, localVars, 0, NO_STACK);

    // compare properties
    for(PropertyElement propertyElement: classProperties.getHashCodeProperties()) {

      visitAccessorAndCompact(mv, varPojo1, propertyElement);
      visitAccessorAndCompact(mv, varPojo2, propertyElement);
      if(compareProperties(mv, returnFalse, propertyElement)) {
        longOrDoubleStackAdjustment = 1; // why doesn't this need to be 2? There are two of them...
      }
    }
    // everything checks out; return true.
    mv.visitInsn(ICONST_1);
    mv.visitInsn(IRETURN);

    mv.visitLabel(returnFalse);
    mv.visitFrame(F_FULL, 3, localVars, 0, NO_STACK);
    mv.visitInsn(ICONST_0);
    mv.visitInsn(IRETURN);

    Label end = visitNewLabel(mv);
    varThis.withScope(start, end).acceptLocalVariable(mv);
    varPojo1.withScope(start, end).acceptLocalVariable(mv);
    varPojo2.withScope(start, end).acceptLocalVariable(mv);
    mv.visitMaxs(3 + longOrDoubleStackAdjustment, 3);
    mv.visitEnd();
  }

  private boolean compareProperties(MethodVisitor mv, Label notEqualLabel, PropertyElement propertyElement) {
    boolean propertyRequiresTwoStackFrames = false;
    Class<?> propertyType = propertyElement.getPropertyType();
    if (propertyType.isPrimitive()) {
      if (isWide(propertyElement)) {
        propertyRequiresTwoStackFrames = true;
        mv.visitInsn(LCMP);
        mv.visitJumpInsn(IFNE, notEqualLabel);
      }
      else {
        mv.visitJumpInsn(IF_ICMPNE, notEqualLabel);
      }
    }
    else {
      if(propertyType.isArray()) {
        Class<? extends Object> arrayPropertyType =
          propertyType.getComponentType().isPrimitive() ? propertyType : Object[].class;
        mv.visitMethodInsn(
          INVOKESTATIC,
          internalName(Arrays.class),
          "equals",
          methodDesc(boolean.class, arrayPropertyType, arrayPropertyType));
      }
      else {
        mv.visitMethodInsn(
          INVOKESTATIC,
          BASE_POJOMATOR_INTERNAL_NAME,
          propertyType.equals(Object.class) ? "areObjectValuesEqual" : "areNonArrayValuesEqual",
          methodDesc(boolean.class, Object.class, Object.class));
      }
      mv.visitJumpInsn(IFEQ, notEqualLabel);
    }
    return propertyRequiresTwoStackFrames;
  }

  /**
   * Pop the top element off of the stack and invoke checkNotNull on it.
   * @param mv the current methodVisitor
   */
  private void checkNotNullPop(MethodVisitor mv) {
    mv.visitMethodInsn(
      INVOKESTATIC,
      BASE_POJOMATOR_INTERNAL_NAME,
      "checkNotNullPop",
      methodDesc(void.class, Object.class));
  }

  /**
   * Invoke checkNotNull on the top element of the stack, leaving that element there.
   * @param mv the current methodVisitor
   */
  private void checkNotNull(MethodVisitor mv) {
    mv.visitMethodInsn(
      INVOKESTATIC,
      BASE_POJOMATOR_INTERNAL_NAME,
      "checkNotNull",
      methodDesc(Object.class, Object.class));
  }

  /**
   * Invoke checkNotNull(message) on the top element of the stack, leaving that element there.
   * @param mv the current methodVisitor
   * @param message the message to include in the {@link NullPointerException} if the top element is null
   */
  private void checkNotNull(MethodVisitor mv, String message) {
    mv.visitLdcInsn(message);
    mv.visitMethodInsn(
      INVOKESTATIC,
      BASE_POJOMATOR_INTERNAL_NAME,
      "checkNotNull",
      methodDesc(Object.class, Object.class, String.class));
  }

  /**
   * invoke checkClass(message) on the specified variable
   * @param mv the current methodVisitor
   * @param varNumber the variable number to check
   * @param message the message to include in the {@link IllegalArgumentException} if the variable fails the
   * class test
   */
  private void checkClass(MethodVisitor mv, LocalVariable varThis, LocalVariable var, String message) {
    varThis.acceptLoad(mv);
    var.acceptLoad(mv);
    mv.visitLdcInsn(message);
    mv.visitMethodInsn(
      INVOKEVIRTUAL,
      BASE_POJOMATOR_INTERNAL_NAME,
      "checkClass",
      methodDesc(void.class, Object.class, String.class));
  }

  private void makeDoHashCode(ClassVisitor cw) {
    LocalVariable varThis = new LocalVariable("this", classDesc(pojomatorInternalClassName), null, 0);
    LocalVariable varPojo = new LocalVariable("pojo", pojoClass, pojoDescriptor, 1);

    int longOrDoubleStackAdjustment = 0;
    Object[] localVars = new Object[] {pojomatorInternalClassName, OBJECT_INTERNAL_NAME};

    MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "doHashCode", methodDesc(int.class, Object.class), null, null);
    mv.visitCode();
    Label start = visitNewLabel(mv);
    visitLineNumber(mv, 1);
    varPojo.acceptLoad(mv);
    checkNotNullPop(mv);
    mv.visitInsn(ICONST_1);

    for(PropertyElement propertyElement: classProperties.getHashCodeProperties()) {
      visitLineNumber(mv, 2);
      mv.visitIntInsn(BIPUSH, 31);
      visitLineNumber(mv, 3);
      mv.visitInsn(IMUL);
      visitLineNumber(mv, 4);

      visitAccessorAndCompact(mv, varPojo, propertyElement);
      Class<?> propertyType = propertyElement.getPropertyType();
      if (propertyType.isPrimitive()) {
        // need to compute the hash code for this primitive value, based on its type
        switch (propertyType.getName()) {
          case "boolean":
            Label ifeq = new Label();
            mv.visitJumpInsn(IFEQ, ifeq);
            mv.visitIntInsn(SIPUSH, 1231);
            Label hashCodeDetermined = new Label();
            mv.visitJumpInsn(GOTO, hashCodeDetermined);
            mv.visitLabel(ifeq);
            mv.visitFrame(F_FULL, 3, localVars, 1, new Object[] {INTEGER});
            mv.visitIntInsn(SIPUSH, 1237);
            mv.visitLabel(hashCodeDetermined);
            mv.visitFrame(F_FULL, 3, localVars, 2, new Object[] {INTEGER, INTEGER});
            break;
          case "byte":
          case "char":
          case "int":
          case "short":
          case "float":
            break; // already an int
          case "double":
          case "long":
            longOrDoubleStackAdjustment = 3;
            mv.visitInsn(DUP2);
            mv.visitIntInsn(BIPUSH, 32);
            mv.visitInsn(LUSHR);
            mv.visitInsn(LXOR);
            mv.visitInsn(L2I);
            break;
          default:
            throw new IllegalStateException("unknown primitive type " + propertyType.getName());
        }
      }
      else {
        Label ifNonNull = new Label();
        Label hashCodeDetermined = new Label();

        mv.visitInsn(DUP); // if it's non-null, let's not have to get it a second time.
        mv.visitJumpInsn(IFNONNULL, ifNonNull);
        // it's null
        mv.visitInsn(POP); // won't need that duped copy after all
        mv.visitInsn(ICONST_0);
        mv.visitJumpInsn(GOTO, hashCodeDetermined);
        // it's not null
        mv.visitLabel(ifNonNull);
        mv.visitFrame(F_FULL, 2, localVars, 2, new Object[] {INTEGER, Type.getInternalName(propertyType)});

        if(propertyType.isArray()) {
          mv.visitMethodInsn(
            INVOKESTATIC,
            internalName(Arrays.class),
            "hashCode",
            methodDesc(
              int.class, propertyType.getComponentType().isPrimitive() ? propertyType : Object[].class)
              );
        }
        else if (propertyType == Object.class) {
          mv.visitInsn(DUP);
          invokeGetClass(mv);
          mv.visitMethodInsn(INVOKEVIRTUAL, internalName(Class.class), "isArray", "()Z");
          Label isArray = new Label();
          mv.visitJumpInsn(IFNE, isArray); // if true
          mv.visitMethodInsn(INVOKEVIRTUAL, OBJECT_INTERNAL_NAME, "hashCode", "()I");
          mv.visitJumpInsn(GOTO, hashCodeDetermined);

          mv.visitLabel(isArray);
          mv.visitFrame(F_FULL, 2, localVars, 2, new Object[] { INTEGER, Type.getInternalName(propertyType) });
          mv.visitMethodInsn(INVOKESTATIC, BASE_POJOMATOR_INTERNAL_NAME, "arrayHashCode", methodDesc(int.class, Object.class));
        }
        else {
          mv.visitMethodInsn(INVOKEVIRTUAL, OBJECT_INTERNAL_NAME, "hashCode", "()I");
        }

        mv.visitLabel(hashCodeDetermined);
        mv.visitFrame(F_FULL, 2, localVars, 2, new Object[] {INTEGER, INTEGER});
      }
      mv.visitInsn(IADD);
    }
    mv.visitInsn(IRETURN);
    Label end = visitNewLabel(mv);
    varThis.withScope(start, end).acceptLocalVariable(mv);
    varPojo.withScope(start, end).acceptLocalVariable(mv);
    mv.visitMaxs(3 + longOrDoubleStackAdjustment, 2);
    mv.visitEnd();
  }

  private void makeDoToString(ClassVisitor cw) {
    int longOrDoubleStackAdjustment = 1;
    LocalVariable varThis = new LocalVariable("this", classDesc(pojomatorInternalClassName), null, 0);
    LocalVariable varPojo = new LocalVariable("pojo", pojoClass, null, 1);
    LocalVariable varPojoFormatter=
      new LocalVariable("pojoFormattor", classDesc(EnhancedPojoFormatter.class), null, 2);
    LocalVariable varBuilder= new LocalVariable("builder", classDesc(String.class), null, 3);


    MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "doToString", methodDesc(String.class, Object.class), null, null);
    mv.visitCode();
    Label start = visitNewLabel(mv);
    varPojo.acceptLoad(mv);
    checkNotNullPop(mv);

    constructEnhancedPojoFormatter(mv);
    varPojoFormatter.acceptStore(mv);

    mv.visitTypeInsn(NEW, internalName(StringBuilder.class));
    mv.visitInsn(DUP);
    mv.visitMethodInsn(INVOKESPECIAL, internalName(StringBuilder.class), "<init>", "()V");
    varBuilder.acceptStore(mv);

    varPojoFormatter.acceptLoad(mv);
    varBuilder.acceptLoad(mv);
    loadPojoClass(mv);

    visitLineNumber(mv, 200);

    mv.visitMethodInsn(INVOKEINTERFACE, internalName(EnhancedPojoFormatter.class), "appendToStringPrefix",
      methodDesc(void.class, StringBuilder.class, Class.class));

    for(PropertyElement propertyElement: classProperties.getToStringProperties()) {
      if (isWide(propertyElement)) {
        longOrDoubleStackAdjustment = 1;
      }
      varPojoFormatter.acceptLoad(mv);
      varBuilder.acceptLoad(mv);
      loadPropertyElementField(mv, propertyElement);
      mv.visitMethodInsn(
        INVOKEINTERFACE,
        internalName(EnhancedPojoFormatter.class),
        "appendPropertyPrefix",
        methodDesc(void.class, StringBuilder.class, PropertyElement.class));

      mv.visitFieldInsn(
        GETSTATIC,
        pojomatorInternalClassName,
        propertyFormatterName(propertyElement),
        classDesc(EnhancedPropertyFormatter.class));
      varBuilder.acceptLoad(mv);
      visitAccessor(mv, varPojo, propertyElement);
      mv.visitMethodInsn(
        INVOKEINTERFACE,
        internalName(EnhancedPropertyFormatter.class),
        "appendFormatted",
        methodDesc(
          void.class,
          StringBuilder.class,
          propertyElement.getPropertyType().isPrimitive() ? propertyElement.getPropertyType() : Object.class)
          );

      varPojoFormatter.acceptLoad(mv);
      varBuilder.acceptLoad(mv);
      loadPropertyElementField(mv, propertyElement);
      mv.visitMethodInsn(
        INVOKEINTERFACE,
        internalName(EnhancedPojoFormatter.class),
        "appendPropertySuffix",
        methodDesc(void.class, StringBuilder.class, PropertyElement.class));
    }

    varPojoFormatter.acceptLoad(mv);
    varBuilder.acceptLoad(mv);
    loadPojoClass(mv);
    mv.visitMethodInsn(INVOKEINTERFACE, internalName(EnhancedPojoFormatter.class), "appendToStringSuffix",
      methodDesc(void.class, StringBuilder.class, Class.class));

    varBuilder.acceptLoad(mv);
    mv.visitMethodInsn(INVOKEVIRTUAL, internalName(StringBuilder.class), "toString", "()Ljava/lang/String;");

    mv.visitInsn(ARETURN);
    Label end = visitNewLabel(mv);
    varThis.withScope(start, end).acceptLocalVariable(mv);
    varPojo.withScope(start, end).acceptLocalVariable(mv);
    varPojoFormatter.withScope(start, end).acceptLocalVariable(mv);
    varBuilder.withScope(start, end).acceptLocalVariable(mv);
    mv.visitMaxs(3 + longOrDoubleStackAdjustment, 4);
    mv.visitEnd();
  }

  private void loadPropertyElementField(MethodVisitor mv, PropertyElement propertyElement) {
    mv.visitFieldInsn(
      GETSTATIC,
      pojomatorInternalClassName,
      propertyElementName(propertyElement),
      classDesc(PropertyElement.class));
  }

  private void makeDoDiff(ClassVisitor cw) {

    // local vars in this method

    LocalVariable varThis = new LocalVariable("this", classDesc(pojomatorInternalClassName), null, 0);
    LocalVariable varPojo1 = new LocalVariable("pojo1", pojoClass, pojoDescriptor, 1);
    LocalVariable varPojo2 = new LocalVariable("pojo2", pojoClass, pojoDescriptor, 2);
    LocalVariable varDifferencesList = new LocalVariable(
      "differences", List.class, "Ljava/util/List<Lorg/pojomatic/diff/Difference;>;", 3);

    int longOrDoubleStackAdjustment = 0;

    Object[] localVarsPreListInstantiation = new Object[] {
      pojomatorInternalClassName, OBJECT_INTERNAL_NAME, OBJECT_INTERNAL_NAME };

    Object[] localVars = new Object[] {
      pojomatorInternalClassName, OBJECT_INTERNAL_NAME, OBJECT_INTERNAL_NAME, internalName(List.class) };
    Object[] localVarsWithProperties = new Object[] {
      pojomatorInternalClassName, OBJECT_INTERNAL_NAME, OBJECT_INTERNAL_NAME, internalName(List.class), null, null};

    MethodVisitor mv = cw.visitMethod(
      ACC_PUBLIC, "doDiff", methodDesc(Differences.class, Object.class, Object.class), null, null);
    mv.visitCode();
    Label start = visitNewLabel(mv);
    varPojo1.acceptLoad(mv);
    checkNotNull(mv, "instance is null");
    varPojo2.acceptLoad(mv);
    checkNotNull(mv, "other is null");
    Label notSameInstance = new Label();
    mv.visitJumpInsn(IF_ACMPNE, notSameInstance);

    // same instance; return NoDifferences.getInstance();
    mv.visitMethodInsn(INVOKESTATIC, internalName(NoDifferences.class), "getInstance", methodDesc(NoDifferences.class));
    mv.visitInsn(ARETURN);

    // not the same instance, some work to do
    mv.visitLabel(notSameInstance);
    mv.visitFrame(F_FULL, 3, localVarsPreListInstantiation, 0, NO_STACK);
    checkClass(mv, varThis, varPojo1, "instance");
    checkClass(mv, varThis, varPojo2, "other");

    Label makeDiferences = notSameInstance;
    mv.visitTypeInsn(NEW, "java/util/ArrayList");
    mv.visitInsn(DUP);
    mv.visitMethodInsn(INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V");
    varDifferencesList.acceptStore(mv);

    List<LocalVariable> propertyVariables = new ArrayList<>();
    // compare properties
    for(PropertyElement propertyElement: classProperties.getHashCodeProperties()) {
      int width = isWide(propertyElement) ? 2 : 1;
      if (width > 1) {
        longOrDoubleStackAdjustment = 2;
      }
      Class<?> propertyType = propertyElement.getPropertyType();
      LocalVariable varProp1 = new LocalVariable(
        "property_" + propertyElement.getName() + "_1", propertyType, null, 4);
      //FIXME - if the type is long or double, we need to store the next var at 6, not 5.
      LocalVariable varProp2 = new LocalVariable(
        "property_" + propertyElement.getName() + "_2", propertyType, null, 4 + width);
      propertyVariables.add(varProp1);
      propertyVariables.add(varProp2);

      Label blockStart = visitNewLabel(mv);

      visitAccessor(mv, varPojo1, propertyElement);
      varProp1.acceptStore(mv);
      visitAccessor(mv, varPojo2, propertyElement);
      varProp2.acceptStore(mv);

      visitAccessorAndCompact(mv, varPojo1, propertyElement);
      visitAccessorAndCompact(mv, varPojo2, propertyElement);

      Label propertiesNotEqual = new Label();
      Label next = new Label();
      compareProperties(mv, propertiesNotEqual, propertyElement);
      mv.visitJumpInsn(GOTO, next);
      mv.visitLabel(propertiesNotEqual);
      //FIXME - for primitives,need one of Opcodes.INTEGER, FLOAT, LONG, DOUBLE
      localVarsWithProperties[4] = propertyType.isPrimitive()
        ? Primitives.getOpcode(propertyType)
        : internalName(propertyType);
      localVarsWithProperties[5] = localVarsWithProperties[4];
      mv.visitFrame(F_FULL, 6, localVarsWithProperties, 0, NO_STACK);

      varDifferencesList.acceptLoad(mv);
      mv.visitTypeInsn(NEW, "org/pojomatic/diff/ValueDifference");
      mv.visitInsn(DUP);
      mv.visitLdcInsn(propertyElement.getName());
      varProp1.acceptLoad(mv);
      convertToObject(mv, propertyType);
      varProp2.acceptLoad(mv);
      convertToObject(mv, propertyType);
      mv.visitMethodInsn(
        INVOKESPECIAL,
        internalName(ValueDifference.class),
        "<init>",
        methodDesc(void.class, String.class, Object.class, Object.class));
      mv.visitMethodInsn(INVOKEINTERFACE, internalName(List.class), "add", methodDesc(boolean.class, Object.class));
      mv.visitInsn(POP);
      mv.visitLabel(next);
      mv.visitFrame(F_FULL, 4, localVars, 0, NO_STACK);

      varProp1.withScope(blockStart, next);
      varProp2.withScope(blockStart, next);
    }

    varDifferencesList.acceptLoad(mv);
    mv.visitMethodInsn(INVOKEINTERFACE, internalName(List.class), "isEmpty", "()Z");

    Label hasDifferences = new Label();
    mv.visitJumpInsn(IFEQ, hasDifferences);
    mv.visitMethodInsn(INVOKESTATIC, internalName(NoDifferences.class), "getInstance", methodDesc(NoDifferences.class));
    mv.visitInsn(ARETURN);

    mv.visitLabel(hasDifferences);
    mv.visitFrame(F_FULL, 4, localVars, 0, NO_STACK);

    mv.visitTypeInsn(NEW, internalName(PropertyDifferences.class));
    mv.visitInsn(DUP);
    varDifferencesList.acceptLoad(mv);
    mv.visitMethodInsn(
      INVOKESPECIAL, internalName(PropertyDifferences.class), "<init>", methodDesc(void.class, List.class));
    mv.visitInsn(ARETURN);

    Label end = visitNewLabel(mv);

    varThis.withScope(start, end).acceptLocalVariable(mv);
    varPojo1.withScope(start, end).acceptLocalVariable(mv);
    varPojo2.withScope(start, end).acceptLocalVariable(mv);
    varDifferencesList.withScope(makeDiferences, end).acceptLocalVariable(mv);
    for (LocalVariable var: propertyVariables) {
      var.acceptLocalVariable(mv);
    }
    mv.visitMaxs(6 + longOrDoubleStackAdjustment, 6 + longOrDoubleStackAdjustment);
    mv.visitEnd();
  }


  /**
   * If the parameter on the stack (of type propertyType) is primitive, convert it to the appropriate wrapper object.
   * Otherwise, leave it alone
   * @param mv
   * @param propertyType TODO
   */
  private void convertToObject(MethodVisitor mv, Class<?> propertyType) {
    if (propertyType.isPrimitive()) {
      Class<?> wrapperClass = Primitives.getWrapperClass(propertyType);
      mv.visitMethodInsn(INVOKESTATIC, internalName(wrapperClass), "valueOf", methodDesc(wrapperClass, propertyType));
    }
  }

  private void loadPojoClass(MethodVisitor mv) {
    mv.visitFieldInsn(GETSTATIC, pojomatorInternalClassName, POJO_CLASS_FIELD_NAME, classDesc(Class.class));
  }

  /**
   * Construct the pojoFormatter to use. This method will contribute 2 or 4 to the max stack depth,
   * depending on whether the pojoFormatter implements {@link EnhancedPojoFormatter} or not.
   * @param mv
   */
  private void constructEnhancedPojoFormatter(MethodVisitor mv) {
    PojoFormat format = pojoClass.getAnnotation(PojoFormat.class);
    if (format == null) {
      mv.visitTypeInsn(NEW, internalName(DefaultEnhancedPojoFormatter.class));
      mv.visitInsn(DUP);
      mv.visitMethodInsn(INVOKESPECIAL, internalName(DefaultEnhancedPojoFormatter.class), "<init>", "()V");
    }
    else {
      @SuppressWarnings("deprecation")
      Class<? extends org.pojomatic.formatter.PojoFormatter> pojoFormatterClass = format.value();
      boolean isEnhancedFormatter = EnhancedPojoFormatter.class.isAssignableFrom(pojoFormatterClass);
      if (! isEnhancedFormatter) {
        mv.visitTypeInsn(NEW, ENHANCED_POJO_FORMATTER_WRAPPER_INTERNAL_NAME);
        mv.visitInsn(DUP);
      }
      mv.visitTypeInsn(NEW, internalName(pojoFormatterClass));
      mv.visitInsn(DUP);
      mv.visitMethodInsn(INVOKESPECIAL, internalName(pojoFormatterClass), "<init>", "()V");
      if (! isEnhancedFormatter) {
        mv.visitMethodInsn(
          INVOKESPECIAL,
          ENHANCED_POJO_FORMATTER_WRAPPER_INTERNAL_NAME,
          "<init>",
          ENHANCED_POJO_FORMATTER_WRAPPER_CONSTRUCTOR_DESCRIPTOR);
      }
    }
  }

  /**
   * Visit an accessor, converting floats or doubles to int bits or long bits respectively
   * @param mv
   * @param variableNumber the index of the local variable holding a the pojo instance to access
   * @param propertyElement the property to access
   */
  private void visitAccessorAndCompact(MethodVisitor mv, LocalVariable var, PropertyElement propertyElement) {
    visitAccessor(mv, var, propertyElement);
    if (propertyElement.getPropertyType().equals(float.class)) {
      mv.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "floatToIntBits", "(F)I");
    }
    else if (propertyElement.getPropertyType().equals(double.class)) {
      mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "doubleToLongBits", "(D)J");
    }
  }

  /**
   * Visit an accessor
   * @param mv
   * @param var the index of the local variable holding a the pojo instance to access
   * @param propertyElement the property to access
   */
  private void visitAccessor(MethodVisitor mv, LocalVariable var, PropertyElement propertyElement) {
    var.acceptLoad(mv);
    mv.visitMethodInsn(
      INVOKESTATIC, pojomatorInternalClassName, propertyAccessorName(propertyElement),
      accessorMethodType(propertyElement));
  }

  private static void invokeGetClass(MethodVisitor mv) {
    mv.visitMethodInsn(INVOKEVIRTUAL, OBJECT_INTERNAL_NAME, "getClass", "()Ljava/lang/Class;");
  }

  private static Label visitNewLabel(MethodVisitor mv) {
    Label label = new Label();
    mv.visitLabel(label);
    return label;
  }

  private static void visitLineNumber(MethodVisitor mv, int lineNumber) {
    mv.visitLineNumber(lineNumber, visitNewLabel(mv));
  }

  private static boolean isWide(PropertyElement propertyElement) {
    Class<?> type = propertyElement.getPropertyType();
    return type == long.class || type == double.class;
  }

  private static String internalName(Class<?> clazz) {
    return internalName(clazz.getName());
  }

  private static String internalName(String className) {
    return className.replace('.', '/');
  }

  private static String classDesc(Class<?> clazz) {
    return Type.getDescriptor(clazz);
  }

  private static String classDesc(String className) {
    return "L" + internalName(className) + ";";
  }

  private static String methodDesc(Class<?> returnType) {
    return MethodType.methodType(returnType).toMethodDescriptorString();
  }

  private static String methodDesc(Class<?> returnType, Class<?> parameterType0) {
    return MethodType.methodType(returnType, parameterType0).toMethodDescriptorString();
  }

  private static String methodDesc(Class<?> returnType, Class<?> parameterType0, Class<?>... parameterTypes) {
    return MethodType.methodType(returnType, parameterType0, parameterTypes).toMethodDescriptorString();
  }

  private static String propertyAccessorName(PropertyElement property) {
    return "get_" + qualifiedPropertyName(property);
  }

  static String propertyElementName(PropertyElement property) {
    return "element_" + qualifiedPropertyName(property);
  }

  static String propertyFormatterName(PropertyElement property) {
    return "formatter_" + qualifiedPropertyName(property);
  }

  private static String qualifiedPropertyName(PropertyElement property) {
    return property.getType()
      + "_" + property.getDeclaringClass().getName().replace('.', '$')
      + "_" + property.getElementName();
  }
}