/**
 * Copyright 2020 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.wfs3.transactional;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import de.ii.ldproxy.ogcapi.domain.*;
import de.ii.ldproxy.ogcapi.domain.OgcApiContext.HttpMethods;
import de.ii.ldproxy.ogcapi.features.core.api.OgcApiFeatureCoreProviders;
import de.ii.ldproxy.ogcapi.features.core.api.OgcApiFeatureFormatExtension;
import de.ii.xtraplatform.auth.api.User;
import de.ii.xtraplatform.features.domain.FeatureProvider2;
import de.ii.xtraplatform.features.domain.FeatureTransactions;
import io.dropwizard.auth.Auth;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.NotAllowedException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.ServerErrorException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;


/**
 * @author zahnen
 */
@Component
@Provides
@Instantiate
public class Wfs3EndpointTransactional extends OgcApiEndpointSubCollection {

    private static final Logger LOGGER = LoggerFactory.getLogger(Wfs3EndpointTransactional.class);
    private static final OgcApiContext API_CONTEXT = new ImmutableOgcApiContext.Builder()
            .apiEntrypoint("collections")
            .subPathPattern("^/(?:[\\w\\-]+)/items/?[^/\\s]*$")
            .addMethods(HttpMethods.POST, HttpMethods.PUT, HttpMethods.DELETE)
            .putSubPathsAndMethods("^/(?:[\\w\\-]+)/items/?", Arrays.asList(new HttpMethods[]{HttpMethods.POST}))
            .putSubPathsAndMethods("^/(?:[\\w\\-]+)/items/?(?:[^/\\s]+)$", Arrays.asList(new HttpMethods[]{HttpMethods.PUT, HttpMethods.DELETE}))
            .build();
    private static final List<String> TAGS = ImmutableList.of("Mutate data");

    private final OgcApiFeatureCoreProviders providers;
    private final CommandHandlerTransactional commandHandler;

    public Wfs3EndpointTransactional(@Requires OgcApiExtensionRegistry extensionRegistry,
                                     @Requires OgcApiFeatureCoreProviders providers) {
        super(extensionRegistry);
        this.providers = providers;
        this.commandHandler = new CommandHandlerTransactional();
    }

    @Override
    public OgcApiContext getApiContext() {
        return API_CONTEXT;
    }

    /*
    @Override
    public ImmutableSet<OgcApiMediaType> getMediaTypes(OgcApiApiDataV2 dataset, String subPath) {
        if (subPath.matches("^/(?:[\\w\\-]+)/items/?[^/\\s]*$"))
            return ImmutableSet.of(
                    new ImmutableOgcApiMediaType.Builder()
                            .type(new MediaType("application", "geo+json"))
                            .build()
            );

        throw new ServerErrorException("Invalid sub path: "+subPath, 500);
    }

     */

    @Override
    public boolean isEnabledForApi(OgcApiApiDataV2 apiData) {
        return isExtensionEnabled(apiData, TransactionalConfiguration.class) && providers.getFeatureProvider(apiData).supportsTransactions();
    }

    @Override
    public OgcApiEndpointDefinition getDefinition(OgcApiApiDataV2 apiData) {
        String apiId = apiData.getId();
        if (!apiDefinitions.containsKey(apiId)) {
            ImmutableOgcApiEndpointDefinition.Builder definitionBuilder = new ImmutableOgcApiEndpointDefinition.Builder()
                    .apiEntrypoint("collections")
                    .sortPriority(OgcApiEndpointDefinition.SORT_PRIORITY_FEATURES_TRANSACTION);
            String subSubPath = "/items";
            String path = "/collections/{collectionId}" + subSubPath;
            Set<OgcApiPathParameter> pathParameters = getPathParameters(extensionRegistry, apiData, path);
            Optional<OgcApiPathParameter> optCollectionIdParam = pathParameters.stream().filter(param -> param.getName().equals("collectionId")).findAny();
            if (!optCollectionIdParam.isPresent()) {
                LOGGER.error("Path parameter 'collectionId' missing for resource at path '" + path + "'. The resource will not be available.");
            } else {
                final OgcApiPathParameter collectionIdParam = optCollectionIdParam.get();
                final boolean explode = collectionIdParam.getExplodeInOpenApi();
                final Set<String> collectionIds = (explode) ?
                        collectionIdParam.getValues(apiData) :
                        ImmutableSet.of("{collectionId}");
                for (String collectionId : collectionIds) {
                    final Set<OgcApiQueryParameter> queryParameters = explode ?
                            getQueryParameters(extensionRegistry, apiData, collectionId, path, HttpMethods.POST) :
                            getQueryParameters(extensionRegistry, apiData, path, HttpMethods.POST);
                    final String operationSummary = "add a feature in the feature collection '" + collectionId + "'";
                    Optional<String> operationDescription = Optional.of("The content of the request is a new feature in one of the supported encodings. The URI of the new feature is returned in the header `Location`.");
                    String resourcePath = "/collections/" + collectionId + subSubPath;
                    ImmutableOgcApiResourceData.Builder resourceBuilder = new ImmutableOgcApiResourceData.Builder()
                            .path(resourcePath)
                            .pathParameters(pathParameters);
                    OgcApiOperation operation = addOperation(apiData, OgcApiContext.HttpMethods.POST, queryParameters, collectionId, subSubPath, operationSummary, operationDescription, TAGS);
                    if (operation!=null)
                        resourceBuilder.putOperations("POST", operation);
                    definitionBuilder.putResources(resourcePath, resourceBuilder.build());
                }
            }
            subSubPath = "/items/{featureId}";
            path = "/collections/{collectionId}" + subSubPath;
            pathParameters = getPathParameters(extensionRegistry, apiData, path);
            optCollectionIdParam = pathParameters.stream().filter(param -> param.getName().equals("collectionId")).findAny();
            if (!optCollectionIdParam.isPresent()) {
                LOGGER.error("Path parameter 'collectionId' missing for resource at path '" + path + "'. The resource will not be available.");
            } else {
                final OgcApiPathParameter collectionIdParam = optCollectionIdParam.get();
                final boolean explode = collectionIdParam.getExplodeInOpenApi();
                final Set<String> collectionIds = explode ?
                        collectionIdParam.getValues(apiData) :
                        ImmutableSet.of("{collectionId}");
                for (String collectionId : collectionIds) {
                    Set<OgcApiQueryParameter> queryParameters = explode ?
                            getQueryParameters(extensionRegistry, apiData, collectionId, path, HttpMethods.PUT) :
                            getQueryParameters(extensionRegistry, apiData, path, HttpMethods.PUT);
                    String operationSummary = "add or update a feature in the feature collection '" + collectionId + "'";
                    Optional<String> operationDescription = Optional.of("The content of the request is a new feature in one of the supported encodings. The id of the new or updated feature is `{featureId}`.");
                    String resourcePath = "/collections/" + collectionId + subSubPath;
                    ImmutableOgcApiResourceData.Builder resourceBuilder = new ImmutableOgcApiResourceData.Builder()
                            .path(resourcePath)
                            .pathParameters(pathParameters);
                    OgcApiOperation operation = addOperation(apiData, OgcApiContext.HttpMethods.PUT, queryParameters, collectionId, subSubPath, operationSummary, operationDescription, TAGS);
                    if (operation!=null)
                        resourceBuilder.putOperations("PUT", operation);
                    queryParameters = explode ?
                            getQueryParameters(extensionRegistry, apiData, collectionId, path, HttpMethods.DELETE) :
                            getQueryParameters(extensionRegistry, apiData, path, HttpMethods.DELETE);
                    operationSummary = "delete a feature in the feature collection '" + collectionId + "'";
                    operationDescription = Optional.of("The feature with id `{featureId}` will be deleted.");
                    operation = addOperation(apiData, OgcApiContext.HttpMethods.DELETE, queryParameters, collectionId, subSubPath, operationSummary, operationDescription, TAGS);
                    if (operation!=null)
                        resourceBuilder.putOperations("DELETE", operation);
                    definitionBuilder.putResources(resourcePath, resourceBuilder.build());
                }

            }
            apiDefinitions.put(apiId, definitionBuilder.build());
        }

        return apiDefinitions.get(apiId);
    }

    @Path("/{id}/items")
    @POST
    @Consumes("application/geo+json")
    public Response postItems(@Auth Optional<User> optionalUser, @PathParam("id") String id,
                              @Context OgcApiApi service, @Context OgcApiRequestContext wfs3Request,
                              @Context HttpServletRequest request, InputStream requestBody) {
        FeatureProvider2 featureProvider = providers.getFeatureProvider(service.getData(), service.getData().getCollections().get(id));

        checkTransactional(featureProvider);

        checkAuthorization(service.getData(), optionalUser);


        return commandHandler.postItemsResponse((FeatureTransactions) featureProvider, wfs3Request.getMediaType(), wfs3Request.getUriCustomizer()
                                                                                                                     .copy(), id, /*TODO*/null, /*TODO*/null, requestBody);
    }

    @Path("/{id}/items/{featureid}")
    @PUT
    @Consumes("application/geo+json")
    public Response putItem(@Auth Optional<User> optionalUser, @PathParam("id") String id,
                            @PathParam("featureid") final String featureId, @Context OgcApiApi service,
                            @Context OgcApiRequestContext wfs3Request, @Context HttpServletRequest request,
                            InputStream requestBody) {

        FeatureProvider2 featureProvider = providers.getFeatureProvider(service.getData(), service.getData().getCollections().get(id));

        checkTransactional(featureProvider);

        checkAuthorization(service.getData(), optionalUser);

        return commandHandler.putItemResponse((FeatureTransactions) featureProvider, wfs3Request.getMediaType(), id, featureId, /*TODO*/null, /*TODO*/null, requestBody);
    }

    @Path("/{id}/items/{featureid}")
    @DELETE
    public Response deleteItem(@Auth Optional<User> optionalUser, @Context OgcApiApi service,
                               @PathParam("id") String id, @PathParam("featureid") final String featureId) {

        FeatureProvider2 featureProvider = providers.getFeatureProvider(service.getData(), service.getData().getCollections().get(id));

        checkTransactional(featureProvider);

        checkAuthorization(service.getData(), optionalUser);

        return commandHandler.deleteItemResponse((FeatureTransactions) featureProvider, id, featureId);
    }

    private void checkTransactional(FeatureProvider2 featureProvider) {
        if (!(featureProvider instanceof FeatureTransactions)) {
            throw new NotAllowedException("GET");
        }
    }

    @Override
    public List<? extends FormatExtension> getFormats() {
        if (formats==null)
            formats = extensionRegistry.getExtensionsForType(OgcApiFeatureFormatExtension.class);
        return formats;
    }
}
