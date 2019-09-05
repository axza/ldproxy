/**
 * Copyright 2019 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.wfs3.transactional;

import com.google.common.collect.ImmutableSet;
import de.ii.ldproxy.ogcapi.domain.*;
import de.ii.ldproxy.ogcapi.domain.OgcApiContext.HttpMethods;
import de.ii.xtraplatform.auth.api.User;
import io.dropwizard.auth.Auth;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Optional;


/**
 * @author zahnen
 */
@Component
@Provides
@Instantiate
public class Wfs3EndpointTransactional implements OgcApiEndpointExtension {

    private static final OgcApiContext API_CONTEXT = new ImmutableOgcApiContext.Builder()
            .apiEntrypoint("collections")
            .subPathPattern("^/(?:\\w+)/items/?\\w*$")
            .addMethods(HttpMethods.POST, HttpMethods.PUT, HttpMethods.DELETE)
            .putSubPathsAndMethods("^/(?:\\w+)/items/?", Arrays.asList(new HttpMethods[]{HttpMethods.POST}))
            .putSubPathsAndMethods("^/(?:[^/]+)/items/?(?:\\w+)$", Arrays.asList(new HttpMethods[]{HttpMethods.PUT, HttpMethods.DELETE}))
            .build();

    private final CommandHandlerTransactional commandHandler;

    public Wfs3EndpointTransactional() {
        this.commandHandler = new CommandHandlerTransactional();
    }

    @Override
    public OgcApiContext getApiContext() {
        return API_CONTEXT;
    }

    @Override
    public ImmutableSet<OgcApiMediaType> getMediaTypes(OgcApiDatasetData dataset, String subPath) {
        if (subPath.matches("^/(?:\\w+)/items/?\\w*$"))
            return ImmutableSet.of(
                    new ImmutableOgcApiMediaType.Builder()
                            .type(new MediaType("application", "geo+json"))
                            .build()
            );

        throw new ServerErrorException("Invalid sub path: "+subPath, 500);
    }

    @Override
    public boolean isEnabledForApi(OgcApiDatasetData apiData) {
        return isExtensionEnabled(apiData, TransactionalConfiguration.class);
    }

    @Path("/{id}/items")
    @POST
    @Consumes("application/geo+json")
    public Response postItems(@Auth Optional<User> optionalUser, @PathParam("id") String id,
                              @Context OgcApiDataset service, @Context OgcApiRequestContext wfs3Request,
                              @Context HttpServletRequest request, InputStream requestBody) {
        checkTransactional(service);

        checkAuthorization(service.getData(), optionalUser);

        return commandHandler.postItemsResponse(service.getFeatureProvider(), wfs3Request.getMediaType(), wfs3Request.getUriCustomizer()
                                                                                                                     .copy(), id, service.getData()
                                                                                                                                         .getFeatureProvider()
                                                                                                                                         .getMappings()
                                                                                                                                         .get(id), service.getCrsReverseTransformer(null), requestBody);
    }

    @Path("/{id}/items/{featureid}")
    @PUT
    @Consumes("application/geo+json")
    public Response putItem(@Auth Optional<User> optionalUser, @PathParam("id") String id,
                            @PathParam("featureid") final String featureId, @Context OgcApiDataset service,
                            @Context OgcApiRequestContext wfs3Request, @Context HttpServletRequest request,
                            InputStream requestBody) {
        checkTransactional(service);

        checkAuthorization(service.getData(), optionalUser);

        return commandHandler.putItemResponse(service.getFeatureProvider(), wfs3Request.getMediaType(), id, featureId, service.getData()
                                                                                                                              .getFeatureProvider()
                                                                                                                              .getMappings()
                                                                                                                              .get(id), service.getCrsReverseTransformer(null), requestBody);
    }

    @Path("/{id}/items/{featureid}")
    @DELETE
    public Response deleteItem(@Auth Optional<User> optionalUser, @Context OgcApiDataset service,
                               @PathParam("id") String id, @PathParam("featureid") final String featureId) {
        checkTransactional(service);

        checkAuthorization(service.getData(), optionalUser);

        return commandHandler.deleteItemResponse(service.getFeatureProvider(), id, featureId);
    }

    private void checkTransactional(OgcApiDataset wfs3Service) {
        if (!wfs3Service.getData()
                        .getFeatureProvider()
                        .supportsTransactions()) {
            throw new NotFoundException();
        }
    }
}
