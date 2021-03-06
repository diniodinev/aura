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
package org.auraframework.integration.test.http.resource;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletResponse;

import org.auraframework.Aura;
import org.auraframework.adapter.ConfigAdapter;
import org.auraframework.def.ApplicationDef;
import org.auraframework.def.ComponentDef;
import org.auraframework.def.DefDescriptor;
import org.auraframework.http.resource.Manifest;
import org.auraframework.impl.AuraImplTestCase;
import org.auraframework.impl.adapter.ConfigAdapterImpl;
import org.auraframework.impl.adapter.ServletUtilAdapterImpl;
import org.auraframework.impl.clientlibrary.ClientLibraryServiceImpl;
import org.auraframework.service.ContextService;
import org.auraframework.system.AuraContext;
import org.junit.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

public class ManifestTest extends AuraImplTestCase {

    ContextService contextService = Aura.getContextService();

    /**
     * Verify response status is SC_NOT_FOUND when writing manifest throws Exception.
     */
    @Test
    public void testResponseSourceNotFoundWhenWritingManifestThrowsException() throws Exception {
        // Arrange
        if (contextService.isEstablished()) {
            contextService.endContext();
        }
        String appMarkup = String.format(baseApplicationTag, "useAppcache='true'", "");
        DefDescriptor<ApplicationDef> appDesc = addSourceAutoCleanup(ApplicationDef.class, appMarkup);

        AuraContext context = contextService.startContext(AuraContext.Mode.PROD,
                AuraContext.Format.MANIFEST, AuraContext.Authentication.AUTHENTICATED, appDesc);

        ConfigAdapter configAdapter = new ConfigAdapterImpl();
        ConfigAdapter spyConfigAdapter = spy(configAdapter);
        doThrow(new RuntimeException()).when(spyConfigAdapter).getResetCssURL();

        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        MockHttpServletResponse mockResponse = new MockHttpServletResponse();
        Manifest manifest = new Manifest();
        manifest.setConfigAdapter(spyConfigAdapter);

        // Act
        manifest.write(mockRequest, mockResponse, context);

        // Assert
        // make sure the exception is from expected point
        verify(spyConfigAdapter, times(1)).getResetCssURL();

        int actual = mockResponse.getStatus();
        assertEquals(HttpServletResponse.SC_NOT_FOUND, actual);
    }

    /**
     * Verify manifest doesn't include null when ResetCss is null.
     */
    @Test
    public void testManifestNotIncludeNullResetCssURL() throws Exception {
        // Arrange
        if (contextService.isEstablished()) {
            contextService.endContext();
        }
        String cmpTagAttributes = "isTemplate='true' extends='aura:template'";
        String cmpMarkup = String.format(baseComponentTag, cmpTagAttributes, "<aura:set attribute='auraResetStyle' value=''/>");
        DefDescriptor<ComponentDef> cmpDesc = addSourceAutoCleanup(ComponentDef.class, cmpMarkup);

        String appAttributes = String.format(" useAppcache='true' template='%s'", cmpDesc.getDescriptorName());
        String appMarkup = String.format(baseApplicationTag, appAttributes, "");
        DefDescriptor<ApplicationDef> appDesc = addSourceAutoCleanup(ApplicationDef.class, appMarkup);

        AuraContext context = contextService.startContext(AuraContext.Mode.PROD,
                AuraContext.Format.MANIFEST, AuraContext.Authentication.AUTHENTICATED, appDesc);
        String uid = context.getDefRegistry().getUid(null, appDesc);
        context.addLoaded(appDesc, uid);
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        MockHttpServletResponse mockResponse = new MockHttpServletResponse();

        Manifest manifest = new Manifest();
        ServletUtilAdapterImpl servletUtilAdapter = new ServletUtilAdapterImpl();
        servletUtilAdapter.setClientLibraryService(new ClientLibraryServiceImpl());
        manifest.setServletUtilAdapter(servletUtilAdapter);

        // Act
        manifest.write(mockRequest, mockResponse, context);
        String content = mockResponse.getContentAsString();

        // Assert
        String[] lines = content.split("\n");
        int start = Arrays.asList(lines).indexOf("CACHE:");
        if(start < 0) {
            fail("Could not find CACHE part in appcache manifest: " + content);
        }

        for(int i=start + 1; i < lines.length; i++) {
            assertThat("auraResetStyle is empty, so manifest should not contains resetCSS.css",
                    lines[i], not(containsString("resetCSS.css")));
            assertThat(lines[i], not(containsString("null")));
        }
    }

   /**
    * Verify that context path is prepended on all Aura URLs in appcache manifest
    */
    @Test
    public void testManifestContentWithContextPath() throws Exception {
        // Arrange
        if (contextService.isEstablished()) {
            contextService.endContext();
        }
        DefDescriptor<ApplicationDef> appDesc = definitionService.getDefDescriptor("appCache:testApp", ApplicationDef.class);
        AuraContext context = contextService.startContext(AuraContext.Mode.PROD, AuraContext.Format.MANIFEST,
                AuraContext.Authentication.AUTHENTICATED, appDesc);
        context.setApplicationDescriptor(appDesc);
        String expectedContextName = "/cool";
        context.setContextPath(expectedContextName);
        String uid = context.getDefRegistry().getUid(null, appDesc);
        context.addLoaded(appDesc, uid);
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        MockHttpServletResponse mockResponse = new MockHttpServletResponse();

        Manifest manifest = new Manifest();
        ServletUtilAdapterImpl servletUtilAdapter = new ServletUtilAdapterImpl();
        servletUtilAdapter.setClientLibraryService(new ClientLibraryServiceImpl());
        manifest.setServletUtilAdapter(servletUtilAdapter);

        // Act
        manifest.write(mockRequest, mockResponse, context);
        String content = mockResponse.getContentAsString();

        // Assert
        // find URLs which contain /auraFW/ or /l/
        Pattern pattern = Pattern.compile("(?m)^(.*)(/auraFW|/l/)(.*)$");
        Matcher matcher = pattern.matcher(content);

        if(!matcher.find()) {
            fail("Failed to find any Aura URL in manifest:\n" + content);
        }

        String url = matcher.group();
        assertThat("Aura URL in manifest should start with context name.", url, startsWith(expectedContextName));
        while(matcher.find()) {
            url = matcher.group();
            assertThat("Aura URL in manifest should start with context name.", url, startsWith(expectedContextName));
        }
    }

    /**
     * Verify framework UID exists in auraFW URLs in appcache manifest
     */
    @Test
    public void testManifestFwJsUrlContainsFWId() throws Exception {
        // Arrange
        if (contextService.isEstablished()) {
            contextService.endContext();
        }
        DefDescriptor<ApplicationDef> appDesc = definitionService.getDefDescriptor("appCache:testApp", ApplicationDef.class);
        AuraContext context = contextService.startContext(AuraContext.Mode.PROD,
                AuraContext.Format.MANIFEST, AuraContext.Authentication.AUTHENTICATED, appDesc);
        String uid = context.getDefRegistry().getUid(null, appDesc);
        context.addLoaded(appDesc, uid);
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        MockHttpServletResponse mockResponse = new MockHttpServletResponse();

        Manifest manifest = new Manifest();
        ServletUtilAdapterImpl servletUtilAdapter = new ServletUtilAdapterImpl();
        servletUtilAdapter.setClientLibraryService(new ClientLibraryServiceImpl());
        manifest.setServletUtilAdapter(servletUtilAdapter);

        // Act
        manifest.write(mockRequest, mockResponse, context);
        String content = mockResponse.getContentAsString();

        // Assert
        // find UID in manifest file
        Pattern pattern = Pattern.compile("FW=(.*)\n");
        Matcher matcher = pattern.matcher(content);
        if(!matcher.find()) {
            fail("Failed to find UID in manifest:\n" + content);
        }
        String fwId = matcher.group(1);

        Pattern urlPattern = Pattern.compile("/auraFW|/l/");
        Matcher urlMatcher = urlPattern.matcher(content);
        if(!urlMatcher.find()) {
            fail("Failed to find any Aura URL in manifest:\n" + content);
        }
        String url = matcher.group();
        assertThat("Aura URL in manifest should contain UID.", url, containsString(fwId));
        while(matcher.find()) {
            url = matcher.group();
            assertThat("Aura URL in manifest should contain UID.", url, containsString(fwId));
        }
    }

}
