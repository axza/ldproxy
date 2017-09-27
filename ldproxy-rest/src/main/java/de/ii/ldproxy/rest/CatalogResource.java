
/**
 * Copyright 2016 interactive instruments GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.ii.ldproxy.rest;

import com.fasterxml.aalto.stax.InputFactoryImpl;
import com.sun.jersey.api.core.ResourceContext;
import de.ii.xsf.core.api.MediaTypeCharset;
import de.ii.xsf.core.api.permission.Auth;
import de.ii.xsf.core.api.permission.AuthenticatedUser;
import de.ii.xsf.logging.XSFLogger;
import de.ii.xtraplatform.ogc.csw.parser.ExtractWFSUrlsFromCSW;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.http.impl.client.DefaultHttpClient;
import org.codehaus.staxmate.SMInputFactory;
import org.forgerock.i18n.slf4j.LocalizedLogger;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import java.util.Collection;

/**
 *
 * @author zahnen
 */
@Component
@Provides(specifications = {de.ii.ldproxy.rest.CatalogResource.class})
@Instantiate
@Path("/catalog/")
@Produces(MediaTypeCharset.APPLICATION_JSON_UTF8)
public class CatalogResource {

    private static final LocalizedLogger LOGGER = XSFLogger.getLogger(CatalogResource.class);
    @Context
    ResourceContext rc;
    @Context
    UriInfo uriInfo;
    @Context
    HttpServletRequest request;



    @GET
    public Collection<String> parseCatalogPost(@Auth(required = false) AuthenticatedUser user, @QueryParam("url") String url) {
        LOGGER.getLogger().debug("CATALOG {}", url);
        ExtractWFSUrlsFromCSW urlsFromCSW = new ExtractWFSUrlsFromCSW(new DefaultHttpClient(), new SMInputFactory(new InputFactoryImpl()));

           return urlsFromCSW.extract(url);
    }

}
