/*
 * Copyright (C) 2013 salesforce.com, inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.auraframework.impl.renderer;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.StringWriter;

import org.auraframework.def.DefDescriptor;
import org.auraframework.def.RendererDef;
import org.auraframework.impl.AuraImplTestCase;
import org.auraframework.impl.java.renderer.JavaRendererDef;
import org.auraframework.impl.renderer.sampleJavaRenderers.TestSimpleRenderer;
import org.auraframework.impl.system.DefDescriptorImpl;
import org.auraframework.impl.system.RenderContextImpl;
import org.auraframework.system.RenderContext;
import org.auraframework.throwable.AuraError;
import org.auraframework.throwable.AuraExecutionException;
import org.auraframework.throwable.quickfix.InvalidDefinitionException;
import org.auraframework.util.json.JsonEncoder;
import org.junit.Test;

/**
 * Test class to verify implementation of JavaRendererDef.
 */
public class JavaRendererDefTest extends AuraImplTestCase {

    /**
     * Verify that JavaRendererDef is defined as local.
     */
    @Test
    public void testIsLocal() {
        JavaRendererDef def = (new JavaRendererDef.Builder()).build();
        assertTrue("Server side renderers should be defined as Local", def.isLocal());
    }

    /**
     * Verify that JavaRendererDef creates nothing when serialized.
     */
    @Test
    public void testSerializeCreatesNothing() throws Exception {
        JavaRendererDef def = (new JavaRendererDef.Builder()).build();
        JsonEncoder jsonEncoder = mock(JsonEncoder.class);
        def.serialize(jsonEncoder);
        verify(jsonEncoder, never()).writeValue(any());
    }

    /**
     * Verify that calling render function on JavaRendererDef returns the mark up generated by render() method in the
     * renderer.
     */
    @Test
    public void testRender() throws Exception {
        RendererDef def = createRenderer("java://org.auraframework.impl.renderer.sampleJavaRenderers.TestSimpleRenderer");
        StringWriter sw = new StringWriter();
        RenderContext renderContext = new RenderContextImpl(sw, null);
        def.render(null, renderContext);

        String expected = TestSimpleRenderer.htmlOutput;
        String actual = sw.toString();
        assertEquals(expected, actual);
    }

    @Test
    public void testWrappingExceptionFromComponentRenderer() throws Exception {
        RendererDef def = createRenderer("java://org.auraframework.impl.renderer.sampleJavaRenderers.TestSimpleRenderer");
        Appendable mockAppendable = mock(Appendable.class);
        Exception expectedCause = new IOException();
        when(mockAppendable.append(any())).thenThrow(expectedCause);
        RenderContext renderContext = new RenderContextImpl(mockAppendable, null);

        try {
            def.render(null, renderContext);
            fail("Expecting an AuraExecutionException.");
        } catch (Exception e) {
            this.checkExceptionContains(e, AuraExecutionException.class, "");
            assertSame("The cause should be a IOException from append().", expectedCause, e.getCause());
        }
    }

    @Test
    public void testOriginalAuraErrorIsThrownOutFromRender() throws Exception {
        RendererDef def = createRenderer("java://org.auraframework.impl.renderer.sampleJavaRenderers.TestSimpleRenderer");
        Appendable mockAppendable = mock(Appendable.class);
        AuraError expectedAuraError = new AuraError("expected aura error");
        when(mockAppendable.append(any())).thenThrow(expectedAuraError);
        RenderContext renderContext = new RenderContextImpl(mockAppendable, null);

        try {
            def.render(null, renderContext);
            fail("Expecting an AuraExecutionException.");
        } catch (Throwable e) {
            this.checkExceptionContains(e, AuraError.class, "expected aura error");
        }
    }

    @Test
    public void testWrappingRuntimeExceptionFromComponentRenderer() throws Exception {
        RendererDef def = createRenderer("java://org.auraframework.impl.renderer.sampleJavaRenderers.TestSimpleRenderer");
        Appendable mockAppendable = mock(Appendable.class);
        Exception expectedCause = new RuntimeException();
        when(mockAppendable.append(any())).thenThrow(expectedCause);
        RenderContext renderContext = new RenderContextImpl(mockAppendable, null);

        try {
            def.render(null, renderContext);
            fail("Expecting an AuraExecutionException.");
        } catch (Exception e) {
            this.checkExceptionContains(e, AuraExecutionException.class, "");
            assertSame("The cause should be a IOException from append().", expectedCause, e.getCause());
        }
    }

    @Test
    public void testOriginalQuickfixExceptionIsThrownOutFromRender() throws Exception {
        RendererDef def = createRenderer("java://org.auraframework.impl.renderer.sampleJavaRenderers.TestRendererThrowsQFEDuringRender");
        RenderContext renderContext = new RenderContextImpl(null, null);
        try {
            def.render(null, renderContext);
            fail("Should be able to catch QuickFixExceptions during rendering.");
        } catch (Exception e) {
            checkExceptionFull(e, InvalidDefinitionException.class, "From TestRendererThrowsQFEDuringRender");
        }
    }

    /**
     * create a renderer def from a qualified name of a java class.
     *
     * @param qualifiedName
     * @return the new RendererDef
     * @throws Exception
     */
    private RendererDef createRenderer(String qualifiedName) throws Exception {
        JavaRendererDef.Builder builder = new JavaRendererDef.Builder();
        DefDescriptor<RendererDef> descriptor = DefDescriptorImpl.getInstance(qualifiedName, RendererDef.class);
        Class<?> rendererClass = Class.forName(String.format("%s.%s", descriptor.getNamespace(), descriptor.getName()));

        builder.setLocation(rendererClass.getCanonicalName(), -1);
        builder.setRendererClass(rendererClass);
        return builder.build();
    }
}
