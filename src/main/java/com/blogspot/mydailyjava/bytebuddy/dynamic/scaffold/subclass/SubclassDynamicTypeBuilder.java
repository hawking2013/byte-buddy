package com.blogspot.mydailyjava.bytebuddy.dynamic.scaffold.subclass;

import com.blogspot.mydailyjava.bytebuddy.ClassFormatVersion;
import com.blogspot.mydailyjava.bytebuddy.NamingStrategy;
import com.blogspot.mydailyjava.bytebuddy.asm.ClassVisitorWrapper;
import com.blogspot.mydailyjava.bytebuddy.dynamic.DynamicType;
import com.blogspot.mydailyjava.bytebuddy.dynamic.scaffold.FieldRegistry;
import com.blogspot.mydailyjava.bytebuddy.dynamic.scaffold.MethodRegistry;
import com.blogspot.mydailyjava.bytebuddy.dynamic.scaffold.TypeWriter;
import com.blogspot.mydailyjava.bytebuddy.instrumentation.Instrumentation;
import com.blogspot.mydailyjava.bytebuddy.instrumentation.ModifierContributor;
import com.blogspot.mydailyjava.bytebuddy.instrumentation.attribute.FieldAttributeAppender;
import com.blogspot.mydailyjava.bytebuddy.instrumentation.attribute.MethodAttributeAppender;
import com.blogspot.mydailyjava.bytebuddy.instrumentation.attribute.TypeAttributeAppender;
import com.blogspot.mydailyjava.bytebuddy.instrumentation.method.MethodDescription;
import com.blogspot.mydailyjava.bytebuddy.instrumentation.method.matcher.JunctionMethodMatcher;
import com.blogspot.mydailyjava.bytebuddy.instrumentation.method.matcher.MethodMatcher;
import com.blogspot.mydailyjava.bytebuddy.instrumentation.type.InstrumentedType;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.blogspot.mydailyjava.bytebuddy.instrumentation.method.matcher.MethodMatchers.*;
import static com.blogspot.mydailyjava.bytebuddy.utility.ByteBuddyCommons.*;

public class SubclassDynamicTypeBuilder<T> extends DynamicType.Builder.AbstractBase<T> {

    private class SubclassFieldAnnotationTarget<T> extends AbstractDelegatingBuilder<T> implements FieldAnnotationTarget<T> {

        private final FieldToken fieldToken;
        private final FieldAttributeAppender.Factory attributeAppenderFactory;

        private SubclassFieldAnnotationTarget(FieldToken fieldToken, FieldAttributeAppender.Factory attributeAppenderFactory) {
            this.fieldToken = fieldToken;
            this.attributeAppenderFactory = attributeAppenderFactory;
        }

        @Override
        protected DynamicType.Builder<T> materialize() {
            return new SubclassDynamicTypeBuilder<T>(classFormatVersion,
                    namingStrategy,
                    superType,
                    interfaceTypes,
                    modifiers,
                    attributeAppender,
                    ignoredMethods,
                    classVisitorWrapperChain,
                    fieldRegistry.prepend(fieldToken, attributeAppenderFactory),
                    methodRegistry,
                    defaultFieldAttributeAppenderFactory,
                    defaultMethodAttributeAppenderFactory,
                    join(fieldTokens, fieldToken),
                    methodTokens);
        }

        @Override
        public FieldAnnotationTarget<T> attribute(FieldAttributeAppender.Factory attributeAppenderFactory) {
            return new SubclassFieldAnnotationTarget<T>(fieldToken,
                    new FieldAttributeAppender.Factory.Compound(this.attributeAppenderFactory, attributeAppenderFactory));
        }

        @Override
        public FieldAnnotationTarget<T> annotateField(Annotation annotation) {
            return attribute(new FieldAttributeAppender.ForAnnotation(annotation));
        }
    }

    private class SubclassMatchedMethodInterception<T> implements MatchedMethodInterception<T> {

        private final MethodRegistry.LatentMethodMatcher latentMethodMatcher;
        private final List<MethodToken> methodTokens;

        private SubclassMatchedMethodInterception(MethodRegistry.LatentMethodMatcher latentMethodMatcher, List<MethodToken> methodTokens) {
            this.latentMethodMatcher = latentMethodMatcher;
            this.methodTokens = methodTokens;
        }

        @Override
        public MethodAnnotationTarget<T> intercept(Instrumentation instrumentation) {
            return new SubclassMethodAnnotationTarget<T>(methodTokens,
                    latentMethodMatcher,
                    instrumentation,
                    defaultMethodAttributeAppenderFactory);
        }

        @Override
        public MethodAnnotationTarget<T> withoutCode() {
            return intercept(Instrumentation.ForAbstractMethod.INSTANCE);
        }
    }

    private class SubclassMethodAnnotationTarget<T> extends AbstractDelegatingBuilder<T> implements MethodAnnotationTarget<T> {

        private final List<MethodToken> methodTokens;
        private final MethodRegistry.LatentMethodMatcher latentMethodMatcher;
        private final Instrumentation instrumentation;
        private final MethodAttributeAppender.Factory attributeAppenderFactory;

        private SubclassMethodAnnotationTarget(List<MethodToken> methodTokens,
                                               MethodRegistry.LatentMethodMatcher latentMethodMatcher,
                                               Instrumentation instrumentation,
                                               MethodAttributeAppender.Factory attributeAppenderFactory) {
            this.latentMethodMatcher = latentMethodMatcher;
            this.methodTokens = methodTokens;
            this.instrumentation = instrumentation;
            this.attributeAppenderFactory = attributeAppenderFactory;
        }

        @Override
        protected DynamicType.Builder<T> materialize() {
            return new SubclassDynamicTypeBuilder<T>(classFormatVersion,
                    namingStrategy,
                    superType,
                    interfaceTypes,
                    modifiers,
                    attributeAppender,
                    ignoredMethods,
                    classVisitorWrapperChain,
                    fieldRegistry,
                    methodRegistry.prepend(latentMethodMatcher, instrumentation, attributeAppenderFactory),
                    defaultFieldAttributeAppenderFactory,
                    defaultMethodAttributeAppenderFactory,
                    fieldTokens,
                    methodTokens);
        }

        @Override
        public MethodAnnotationTarget<T> attribute(MethodAttributeAppender.Factory attributeAppenderFactory) {
            return new SubclassMethodAnnotationTarget<T>(
                    methodTokens,
                    latentMethodMatcher,
                    instrumentation,
                    new MethodAttributeAppender.Factory.Compound(this.attributeAppenderFactory, attributeAppenderFactory));
        }

        @Override
        public MethodAnnotationTarget<T> annotateMethod(Annotation annotation) {
            return attribute(new MethodAttributeAppender.ForAnnotation(annotation));
        }

        @Override
        public MethodAnnotationTarget<T> annotateParameter(int parameterIndex, Annotation annotation) {
            return attribute(new MethodAttributeAppender.ForAnnotation(annotation, parameterIndex));
        }
    }

    private static class MethodTokenListForConstructors extends AbstractList<MethodToken> {

        private final Constructor<?>[] constructor;

        public MethodTokenListForConstructors(Constructor<?>[] constructor) {
            this.constructor = constructor;
        }

        @Override
        public MethodToken get(int index) {
            return new MethodToken(MethodDescription.CONSTRUCTOR_INTERNAL_NAME,
                    void.class,
                    Arrays.asList(constructor[index].getParameterTypes()),
                    constructor[index].getModifiers());
        }

        @Override
        public int size() {
            return constructor.length;
        }
    }

    private final ClassFormatVersion classFormatVersion;
    private final NamingStrategy namingStrategy;
    private final Class<?> superType;
    private final List<Class<?>> interfaceTypes;
    private final int modifiers;
    private final TypeAttributeAppender attributeAppender;
    private final MethodMatcher ignoredMethods;
    private final ClassVisitorWrapper.Chain classVisitorWrapperChain;
    private final FieldRegistry fieldRegistry;
    private final MethodRegistry methodRegistry;
    private final FieldAttributeAppender.Factory defaultFieldAttributeAppenderFactory;
    private final MethodAttributeAppender.Factory defaultMethodAttributeAppenderFactory;

    public SubclassDynamicTypeBuilder(ClassFormatVersion classFormatVersion,
                                      NamingStrategy namingStrategy,
                                      Class<?> superType,
                                      List<Class<?>> interfaceTypes,
                                      int modifiers,
                                      TypeAttributeAppender attributeAppender,
                                      MethodMatcher ignoredMethods,
                                      ClassVisitorWrapper.Chain classVisitorWrapperChain,
                                      FieldRegistry fieldRegistry,
                                      MethodRegistry methodRegistry,
                                      FieldAttributeAppender.Factory defaultFieldAttributeAppenderFactory,
                                      MethodAttributeAppender.Factory defaultMethodAttributeAppenderFactory,
                                      ConstructorStrategy constructorStrategy) {
        super(Collections.<FieldToken>emptyList(),
                new MethodTokenListForConstructors(constructorStrategy.extractConstructors(superType)));
        this.classFormatVersion = classFormatVersion;
        this.namingStrategy = namingStrategy;
        this.superType = superType;
        this.interfaceTypes = interfaceTypes;
        this.modifiers = modifiers;
        this.attributeAppender = attributeAppender;
        this.ignoredMethods = ignoredMethods;
        this.classVisitorWrapperChain = classVisitorWrapperChain;
        this.fieldRegistry = fieldRegistry;
        this.defaultFieldAttributeAppenderFactory = defaultFieldAttributeAppenderFactory;
        this.defaultMethodAttributeAppenderFactory = defaultMethodAttributeAppenderFactory;
        this.methodRegistry = constructorStrategy.inject(methodRegistry, defaultMethodAttributeAppenderFactory);
    }

    protected SubclassDynamicTypeBuilder(ClassFormatVersion classFormatVersion,
                                         NamingStrategy namingStrategy,
                                         Class<?> superType,
                                         List<Class<?>> interfaceTypes,
                                         int modifiers,
                                         TypeAttributeAppender attributeAppender,
                                         MethodMatcher ignoredMethods,
                                         ClassVisitorWrapper.Chain classVisitorWrapperChain,
                                         FieldRegistry fieldRegistry,
                                         MethodRegistry methodRegistry,
                                         FieldAttributeAppender.Factory defaultFieldAttributeAppenderFactory,
                                         MethodAttributeAppender.Factory defaultMethodAttributeAppenderFactory,
                                         List<FieldToken> fieldTokens,
                                         List<MethodToken> methodTokens) {
        super(fieldTokens, methodTokens);
        this.classFormatVersion = classFormatVersion;
        this.namingStrategy = namingStrategy;
        this.superType = superType;
        this.interfaceTypes = interfaceTypes;
        this.modifiers = modifiers;
        this.attributeAppender = attributeAppender;
        this.ignoredMethods = ignoredMethods;
        this.classVisitorWrapperChain = classVisitorWrapperChain;
        this.fieldRegistry = fieldRegistry;
        this.methodRegistry = methodRegistry;
        this.defaultFieldAttributeAppenderFactory = defaultFieldAttributeAppenderFactory;
        this.defaultMethodAttributeAppenderFactory = defaultMethodAttributeAppenderFactory;
    }

    @Override
    public DynamicType.Builder<T> classFormatVersion(ClassFormatVersion classFormatVersion) {
        return new SubclassDynamicTypeBuilder<T>(classFormatVersion,
                namingStrategy,
                superType,
                interfaceTypes,
                modifiers,
                attributeAppender,
                ignoredMethods,
                classVisitorWrapperChain,
                fieldRegistry,
                methodRegistry,
                defaultFieldAttributeAppenderFactory,
                defaultMethodAttributeAppenderFactory,
                fieldTokens,
                methodTokens);
    }

    @Override
    public DynamicType.Builder<T> implement(Class<?> interfaceType) {
        return new SubclassDynamicTypeBuilder<T>(classFormatVersion,
                namingStrategy,
                superType,
                join(interfaceTypes, isInterface(interfaceType)),
                modifiers,
                attributeAppender,
                ignoredMethods,
                classVisitorWrapperChain,
                fieldRegistry,
                methodRegistry,
                defaultFieldAttributeAppenderFactory,
                defaultMethodAttributeAppenderFactory,
                fieldTokens,
                methodTokens);
    }

    @Override
    public DynamicType.Builder<T> name(String name) {
        return new SubclassDynamicTypeBuilder<T>(classFormatVersion,
                new NamingStrategy.Fixed(isValidIdentifier(name)),
                superType,
                interfaceTypes,
                modifiers,
                attributeAppender,
                ignoredMethods,
                classVisitorWrapperChain,
                fieldRegistry,
                methodRegistry,
                defaultFieldAttributeAppenderFactory,
                defaultMethodAttributeAppenderFactory,
                fieldTokens,
                methodTokens);
    }

    @Override
    public DynamicType.Builder<T> modifier(ModifierContributor.ForType... modifier) {
        return new SubclassDynamicTypeBuilder<T>(classFormatVersion,
                namingStrategy,
                superType,
                interfaceTypes,
                resolveModifiers(InstrumentedType.TYPE_MODIFIER_MASK, modifier),
                attributeAppender,
                ignoredMethods,
                classVisitorWrapperChain,
                fieldRegistry,
                methodRegistry,
                defaultFieldAttributeAppenderFactory,
                defaultMethodAttributeAppenderFactory,
                fieldTokens,
                methodTokens);
    }

    @Override
    public DynamicType.Builder<T> ignoreMethods(MethodMatcher ignoredMethods) {
        return new SubclassDynamicTypeBuilder<T>(classFormatVersion,
                namingStrategy,
                superType,
                interfaceTypes,
                modifiers,
                attributeAppender,
                new JunctionMethodMatcher.Conjunction(this.ignoredMethods, nonNull(ignoredMethods)),
                classVisitorWrapperChain,
                fieldRegistry,
                methodRegistry,
                defaultFieldAttributeAppenderFactory,
                defaultMethodAttributeAppenderFactory,
                fieldTokens,
                methodTokens);
    }

    @Override
    public DynamicType.Builder<T> attribute(TypeAttributeAppender attributeAppender) {
        return new SubclassDynamicTypeBuilder<T>(classFormatVersion,
                namingStrategy,
                superType,
                interfaceTypes,
                modifiers,
                new TypeAttributeAppender.Compound(this.attributeAppender, nonNull(attributeAppender)),
                ignoredMethods,
                classVisitorWrapperChain,
                fieldRegistry,
                methodRegistry,
                defaultFieldAttributeAppenderFactory,
                defaultMethodAttributeAppenderFactory,
                fieldTokens,
                methodTokens);
    }

    @Override
    public DynamicType.Builder<T> annotateType(Annotation annotation) {
        return attribute(new TypeAttributeAppender.ForAnnotation(annotation));
    }

    @Override
    public DynamicType.Builder<T> classVisitor(ClassVisitorWrapper classVisitorWrapper) {
        return new SubclassDynamicTypeBuilder<T>(classFormatVersion,
                namingStrategy,
                superType,
                interfaceTypes,
                modifiers,
                attributeAppender,
                ignoredMethods,
                classVisitorWrapperChain.append(nonNull(classVisitorWrapper)),
                fieldRegistry,
                methodRegistry,
                defaultFieldAttributeAppenderFactory,
                defaultMethodAttributeAppenderFactory,
                fieldTokens,
                methodTokens);
    }

    @Override
    public FieldAnnotationTarget<T> defineField(String name,
                                                Class<?> fieldType,
                                                ModifierContributor.ForField... modifier) {
        FieldToken fieldToken = new FieldToken(isValidIdentifier(name),
                nonNull(fieldType),
                resolveModifiers(InstrumentedType.FIELD_MODIFIER_MASK, nonNull(modifier)));
        return new SubclassFieldAnnotationTarget<T>(fieldToken, defaultFieldAttributeAppenderFactory);
    }

    @Override
    public MatchedMethodInterception<T> defineMethod(String name,
                                                     Class<?> returnType,
                                                     List<Class<?>> parameterTypes,
                                                     ModifierContributor.ForMethod... modifier) {
        MethodToken methodToken = new MethodToken(isValidIdentifier(name),
                nonNull(returnType),
                nonNull(parameterTypes),
                resolveModifiers(InstrumentedType.METHOD_MODIFIER_MASK, nonNull(modifier)));
        return new SubclassMatchedMethodInterception<T>(methodToken, join(methodTokens, methodToken));
    }

    @Override
    public MatchedMethodInterception<T> defineConstructor(List<Class<?>> parameterTypes,
                                                          ModifierContributor.ForMethod... modifier) {
        MethodToken methodToken = new MethodToken(MethodDescription.CONSTRUCTOR_INTERNAL_NAME,
                void.class,
                nonNull(parameterTypes),
                resolveModifiers(InstrumentedType.METHOD_MODIFIER_MASK, nonNull(modifier)));
        return new SubclassMatchedMethodInterception<T>(methodToken, join(methodTokens, methodToken));
    }

    @Override
    public MatchedMethodInterception<T> method(MethodMatcher methodMatcher) {
        return invokable(isMethod().and(methodMatcher));
    }

    @Override
    public MatchedMethodInterception<T> constructor(MethodMatcher methodMatcher) {
        return invokable(isConstructor().and(methodMatcher));
    }

    @Override
    public MatchedMethodInterception<T> invokable(MethodMatcher methodMatcher) {
        return new SubclassMatchedMethodInterception<T>(new MethodRegistry.LatentMethodMatcher.Simple(methodMatcher), methodTokens);
    }

    @Override
    public DynamicType.Unloaded<T> make() {
        InstrumentedType instrumentedType = applyRecordedMembersTo(new SubclassTypeInstrumentation(classFormatVersion,
                superType,
                interfaceTypes,
                modifiers,
                namingStrategy));
        SubclassInstrumentationContextDelegate contextDelegate = new SubclassInstrumentationContextDelegate(instrumentedType);
        Instrumentation.Context instrumentationContext = new Instrumentation.Context.Default(contextDelegate, contextDelegate);
        return new TypeWriter.Builder<T>(instrumentedType, instrumentationContext, classFormatVersion)
                .build(classVisitorWrapperChain)
                .attributeType(attributeAppender)
                .fields()
                .write(instrumentedType.getDeclaredFields(),
                        fieldRegistry.compile(instrumentedType, FieldAttributeAppender.NoOp.INSTANCE))
                .methods()
                .write(instrumentedType.getDeclaredMethods().filter(not(ignoredMethods).and(isOverridable())),
                        methodRegistry.compile(instrumentedType, MethodRegistry.Compiled.Entry.Skip.INSTANCE))
                .write(contextDelegate.getProxiedMethods(), contextDelegate)
                .make();
    }
}