package net.bytebuddy.implementation;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.scaffold.BridgeMethodResolver;
import net.bytebuddy.dynamic.scaffold.MethodLookupEngine;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.implementation.bytecode.StackSize;
import net.bytebuddy.test.utility.MockitoRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Collections;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.*;

public abstract class AbstractImplementationTargetTest {

    protected static final String FOO = "foo", QUX = "qux", QUXBAZ = "quxbaz", FOOBAZ = "foobaz", FOOQUX = "fooqux", BAZBAR = "bazbar";

    @Rule
    public TestRule mockitoRule = new MockitoRule(this);

    @Mock
    protected MethodLookupEngine.Finding finding;

    @Mock
    protected TypeDescription instrumentedType, methodType, returnType, defaultType;

    @Mock
    protected BridgeMethodResolver.Factory bridgeMethodResolverFactory;

    @Mock
    protected BridgeMethodResolver bridgeMethodResolver;

    @Mock
    protected Implementation.Target.MethodLookup methodLookup;

    @Mock
    protected MethodDescription invokableMethod, defaultMethod;

    protected Implementation.Target implementationTarget;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        when(finding.getTypeDescription()).thenReturn(instrumentedType);
        when(finding.getInvokableMethods()).thenReturn(new MethodList.Explicit(Collections.singletonList(invokableMethod)));
        when(finding.getInvokableDefaultMethods()).thenReturn(Collections.singletonMap(defaultType, Collections.singleton(defaultMethod)));
        when(bridgeMethodResolverFactory.make(any(MethodList.class))).thenReturn(bridgeMethodResolver);
        when(methodLookup.resolve(any(MethodDescription.class), any(Map.class), eq(bridgeMethodResolver)))
                .then(new Answer<MethodDescription>() {
                    @Override
                    public MethodDescription answer(InvocationOnMock invocation) throws Throwable {
                        return (MethodDescription) invocation.getArguments()[0];
                    }
                });
        when(invokableMethod.getDeclaringType()).thenReturn(methodType);
        when(invokableMethod.getReturnType()).thenReturn(returnType);
        when(returnType.getStackSize()).thenReturn(StackSize.ZERO);
        when(invokableMethod.getInternalName()).thenReturn(FOO);
        when(invokableMethod.getDescriptor()).thenReturn(QUX);
        when(defaultMethod.getInternalName()).thenReturn(QUXBAZ);
        when(defaultMethod.getDescriptor()).thenReturn(FOOBAZ);
        when(defaultMethod.getUniqueSignature()).thenReturn(FOOQUX);
        when(defaultMethod.getDeclaringType()).thenReturn(defaultType);
        when(defaultMethod.getReturnType()).thenReturn(returnType);
        when(defaultType.isInterface()).thenReturn(true);
        when(defaultMethod.isSpecializableFor(defaultType)).thenReturn(true);
        when(defaultType.getInternalName()).thenReturn(BAZBAR);
        implementationTarget = makeimplementationTarget();
    }

    protected abstract Implementation.Target makeimplementationTarget();

    @Test
    public void testDefaultMethodInvocation() throws Exception {
        Implementation.SpecialMethodInvocation specialMethodInvocation = implementationTarget.invokeDefault(defaultType, FOOQUX);
        assertThat(specialMethodInvocation.isValid(), is(true));
        assertThat(specialMethodInvocation.getMethodDescription(), is(defaultMethod));
        assertThat(specialMethodInvocation.getTypeDescription(), is(defaultType));
        MethodVisitor methodVisitor = mock(MethodVisitor.class);
        Implementation.Context implementationContext = mock(Implementation.Context.class);
        StackManipulation.Size size = specialMethodInvocation.apply(methodVisitor, implementationContext);
        assertThat(size.getSizeImpact(), is(0));
        assertThat(size.getMaximalSize(), is(0));
        verify(methodVisitor).visitMethodInsn(Opcodes.INVOKESPECIAL, BAZBAR, QUXBAZ, FOOBAZ, true);
        verifyNoMoreInteractions(methodVisitor);
        verifyZeroInteractions(implementationContext);
    }

    @Test
    public void testIllegalDefaultMethod() throws Exception {
        assertThat(implementationTarget.invokeDefault(defaultType, FOOBAZ).isValid(), is(false));
    }

    @Test
    public void testIllegalSuperMethod() throws Exception {
        MethodDescription methodDescription = mock(MethodDescription.class);
        when(methodDescription.getReturnType()).thenReturn(returnType);
        TypeDescription typeDescription = mock(TypeDescription.class);
        when(methodDescription.getDeclaringType()).thenReturn(typeDescription);
        assertThat(implementationTarget.invokeSuper(methodDescription, methodLookup).isValid(), is(false));
    }
}
