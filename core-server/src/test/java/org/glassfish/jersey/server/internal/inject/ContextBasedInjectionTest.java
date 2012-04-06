/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2012 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.glassfish.jersey.server.internal.inject;

import org.glassfish.hk2.Services;
import org.glassfish.jersey.message.internal.Requests;
import org.glassfish.jersey.message.internal.Responses;
import org.glassfish.jersey.process.Inflector;
import org.glassfish.jersey.process.internal.InvocationContext;
import org.glassfish.jersey.process.internal.ResponseProcessor.RespondingContext;
import org.glassfish.jersey.server.ApplicationHandler;
import org.glassfish.jersey.server.internal.routing.RouterModule.RoutingContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.ws.rs.core.Context;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.model.ResourceBuilder;
import static org.junit.Assert.assertEquals;

/**
 * Unit test for creating an application with asynchronously handled request processing
 * via {@link JerseyApplication.Builder}'s programmatic API.
 *
 * @author Marek Potociar (marek.potociar at oracle.com)
 */
@RunWith(Parameterized.class)
public class ContextBasedInjectionTest {

    private static final URI BASE_URI = URI.create("http://localhost:8080/base/");

    @Parameterized.Parameters
    public static List<String[]> testUriSuffixes() {
        return Arrays.asList(new String[][]{
                {"a/b/c", "A-B-C"},
                {"a/b/d/", "A-B-D"}
        });
    }
    private final String uriSuffix;
    private final String expectedResponse;

    public ContextBasedInjectionTest(String uriSuffix, String expectedResponse) {
        this.uriSuffix = uriSuffix;
        this.expectedResponse = expectedResponse;
    }

    private static class AsyncInflector implements Inflector<Request, Response> {

        @Context
        private InvocationContext invocationContext;
        @Context
        private RespondingContext respondingCtx;
        @Context
        private RoutingContext routingCtx;
        @Context
        Services services;
        private final String responseContent;

        public AsyncInflector() {
            this.responseContent = "DEFAULT";
        }

        public AsyncInflector(String responseContent) {
            this.responseContent = responseContent;
        }

        @Override
        public Response apply(final Request req) {
            // Suspend current request
            invocationContext.suspend();

            Executors.newSingleThreadExecutor().submit(new Runnable() {

                @Override
                public void run() {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace(System.err);
                    }

                    // Returning will enter the suspended request
                    invocationContext.resume(Responses.from(200, req).entity(responseContent).build());
                }
            });

            return null;
        }
    }
    private ApplicationHandler app;

    @Before
    public void setupApplication() {
        final ResourceBuilder rb = ResourceConfig.resourceBuilder();
        rb.path("a/b/c").method("GET").to(new AsyncInflector("A-B-C"));
        rb.path("a/b/d").method("GET").to(new AsyncInflector("A-B-D"));
        ResourceConfig rc = new ResourceConfig();
        rc.addResources(rb.build());
        app = new ApplicationHandler(rc);
    }

    @Test
    public void testAsyncApp() throws InterruptedException, ExecutionException {
        Request req = Requests.from(BASE_URI, URI.create(BASE_URI.getPath() + uriSuffix), "GET").build();

        Future<Response> res = app.apply(req);

        assertEquals(expectedResponse, res.get().getEntity());
    }
}
