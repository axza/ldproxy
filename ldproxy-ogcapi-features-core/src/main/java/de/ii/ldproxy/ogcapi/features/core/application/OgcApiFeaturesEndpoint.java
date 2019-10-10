/**
 * Copyright 2019 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.ogcapi.features.core.application;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import de.ii.ldproxy.ogcapi.domain.*;
import de.ii.ldproxy.ogcapi.features.core.api.ImmutableOgcApiQueryInputFeature;
import de.ii.ldproxy.ogcapi.features.core.api.ImmutableOgcApiQueryInputFeatures;
import de.ii.ldproxy.ogcapi.features.core.api.OgcApiFeatureFormatExtension;
import de.ii.ldproxy.ogcapi.features.core.api.OgcApiFeaturesCoreQueriesHandler;
import de.ii.xtraplatform.auth.api.User;
import de.ii.xtraplatform.feature.provider.api.FeatureQuery;
import io.dropwizard.auth.Auth;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.ServerErrorException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Provides
@Instantiate
public class OgcApiFeaturesEndpoint implements OgcApiEndpointExtension {

    private static final Logger LOGGER = LoggerFactory.getLogger(OgcApiFeaturesEndpoint.class);
    private static final OgcApiContext API_CONTEXT = new ImmutableOgcApiContext.Builder()
            .apiEntrypoint("collections")
            .addMethods(OgcApiContext.HttpMethods.GET, OgcApiContext.HttpMethods.HEAD)
            .subPathPattern("^/\\w+/items(?:/\\w+)?/?$")
            .build();

    private final OgcApiExtensionRegistry extensionRegistry;

    @Requires
    private OgcApiFeaturesQuery ogcApiFeaturesQuery;

    @Requires
    private OgcApiFeaturesCoreQueriesHandler queryHandler;

    public OgcApiFeaturesEndpoint(@Requires OgcApiExtensionRegistry extensionRegistry) {
        this.extensionRegistry = extensionRegistry;
    }

    @Override
    public OgcApiContext getApiContext() {
        return API_CONTEXT;
    }

    @Override
    public ImmutableSet<OgcApiMediaType> getMediaTypes(OgcApiDatasetData dataset, String subPath) {
        if (subPath.matches("^/\\w+/items(?:/\\w+)?/?$"))
            return extensionRegistry.getExtensionsForType(OgcApiFeatureFormatExtension.class)
                                    .stream()
                                    .filter(outputFormatExtension -> outputFormatExtension.isEnabledForApi(dataset))
                                    .map(OgcApiFeatureFormatExtension::getMediaType)
                                    .collect(ImmutableSet.toImmutableSet());

        throw new ServerErrorException("Invalid sub path: "+subPath, 500);
    }

    @Override
    public ImmutableSet<String> getParameters(OgcApiDatasetData apiData, String subPath) {
        if (!isEnabledForApi(apiData))
            return ImmutableSet.of();

        ImmutableSet<String> parametersFromExtensions = new ImmutableSet.Builder<String>()
            .addAll(extensionRegistry.getExtensionsForType(OgcApiParameterExtension.class)
                .stream()
                .map(ext -> ext.getParameters(apiData, subPath))
                .flatMap(Collection::stream)
                .collect(Collectors.toSet()))
            .build();

        if (subPath.matches("^/\\w+/items/?$")) {
            // Features
            return new ImmutableSet.Builder<String>()
                    .addAll(OgcApiEndpointExtension.super.getParameters(apiData, subPath))
                    .add("datetime", "bbox", "limit", "offset")
                    .addAll(parametersFromExtensions)
                    .build();
        } else if (subPath.matches("^/\\w+/items/\\w+/?$")) {
            // Feature
            return new ImmutableSet.Builder<String>()
                    .addAll(OgcApiEndpointExtension.super.getParameters(apiData, subPath))
                    .addAll(parametersFromExtensions)
                    .build();
        }

        throw new ServerErrorException("Invalid sub path: "+subPath, 500);
    }

    @GET
    @Path("/{collectionId}/items")
    public Response getItems(@Auth Optional<User> optionalUser,
                             @Context OgcApiDataset api,
                             @Context OgcApiRequestContext requestContext,
                             @Context UriInfo uriInfo,
                             @PathParam("collectionId") String collectionId) {
        checkAuthorization(api.getData(), optionalUser);

        int minimumPageSize = getExtensionConfiguration(api.getData(), OgcApiFeaturesCoreConfiguration.class)
                .map(OgcApiFeaturesCoreConfiguration::getMinimumPageSize)
                .orElse(OgcApiFeaturesCoreConfiguration.MINIMUM_PAGE_SIZE);
        int defaultPageSize = getExtensionConfiguration(api.getData(), OgcApiFeaturesCoreConfiguration.class)
                .map(OgcApiFeaturesCoreConfiguration::getDefaultPageSize)
                .orElse(OgcApiFeaturesCoreConfiguration.DEFAULT_PAGE_SIZE);
        int maxPageSize = getExtensionConfiguration(api.getData(), OgcApiFeaturesCoreConfiguration.class)
                .map(OgcApiFeaturesCoreConfiguration::getMaxPageSize)
                .orElse(OgcApiFeaturesCoreConfiguration.MAX_PAGE_SIZE);
        boolean showsFeatureSelfLink = getExtensionConfiguration(api.getData(), OgcApiFeaturesCoreConfiguration.class)
                .map(OgcApiFeaturesCoreConfiguration::getShowsFeatureSelfLink)
                .orElse(false);
        boolean includeHomeLink = getExtensionConfiguration(api.getData(), OgcApiCommonConfiguration.class)
                .map(OgcApiCommonConfiguration::getIncludeHomeLink)
                .orElse(false);
        boolean includeLinkHeader = getExtensionConfiguration(api.getData(), OgcApiCommonConfiguration.class)
                .map(OgcApiCommonConfiguration::getIncludeLinkHeader)
                .orElse(false);

        FeatureQuery query = ogcApiFeaturesQuery.requestToFeatureQuery(api, collectionId, minimumPageSize, defaultPageSize, maxPageSize, toFlatMap(uriInfo.getQueryParameters()));

        OgcApiFeaturesCoreQueriesHandler.OgcApiQueryInputFeatures queryInput = new ImmutableOgcApiQueryInputFeatures.Builder()
                .collectionId(collectionId)
                .query(query)
                .defaultPageSize(Optional.of(defaultPageSize))
                .showsFeatureSelfLink(showsFeatureSelfLink)
                .includeHomeLink(includeHomeLink)
                .includeLinkHeader(includeLinkHeader)
                .build();

        return queryHandler.handle(OgcApiFeaturesCoreQueriesHandler.Query.FEATURES, queryInput, requestContext);
    }

    @GET
    @Path("/{collectionId}/items/{featureId}")
    public Response getItem(@Auth Optional<User> optionalUser,
                            @Context OgcApiDataset api,
                            @Context OgcApiRequestContext requestContext,
                            @Context UriInfo uriInfo,
                            @PathParam("collectionId") String collectionId,
                            @PathParam("featureId") String featureId) {
        checkAuthorization(api.getData(), optionalUser);

        boolean includeHomeLink = getExtensionConfiguration(api.getData(), OgcApiCommonConfiguration.class)
                .map(OgcApiCommonConfiguration::getIncludeHomeLink)
                .orElse(false);
        boolean includeLinkHeader = getExtensionConfiguration(api.getData(), OgcApiCommonConfiguration.class)
                .map(OgcApiCommonConfiguration::getIncludeLinkHeader)
                .orElse(false);

        FeatureQuery query = ogcApiFeaturesQuery.requestToFeatureQuery(api, collectionId, toFlatMap(uriInfo.getQueryParameters()), featureId);

        OgcApiFeaturesCoreQueriesHandler.OgcApiQueryInputFeature queryInput = new ImmutableOgcApiQueryInputFeature.Builder()
                .collectionId(collectionId)
                .featureId(featureId)
                .query(query)
                .includeHomeLink(includeHomeLink)
                .includeLinkHeader(includeLinkHeader)
                .build();

        return queryHandler.handle(OgcApiFeaturesCoreQueriesHandler.Query.FEATURE, queryInput, requestContext);
    }

    public static Map<String, String> toFlatMap(MultivaluedMap<String, String> queryParameters) {
        return toFlatMap(queryParameters, false);
    }

    public static Map<String, String> toFlatMap(MultivaluedMap<String, String> queryParameters,
                                                boolean keysToLowerCase) {
        return queryParameters.entrySet()
                .stream()
                .map(entry -> {
                    String key = keysToLowerCase ? entry.getKey()
                            .toLowerCase() : entry.getKey();
                    return new AbstractMap.SimpleImmutableEntry<>(key, entry.getValue()
                            .isEmpty() ? "" : entry.getValue()
                            .get(0));
                })
                .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public static Map<String, String> getFiltersFromQuery(Map<String, String> query,
                                                          Map<String, String> filterableFields) {

        Map<String, String> filters = new LinkedHashMap<>();

        for (String filterKey : query.keySet()) {
            if (filterableFields.containsKey(filterKey.toLowerCase())) {
                String filterValue = query.get(filterKey);
                filters.put(filterKey.toLowerCase(), filterValue);
            }
        }

        return filters;
    }
}