/**
 * Copyright 2020 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.ogcapi.observation_processing.application;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import de.ii.ldproxy.ogcapi.application.DefaultLinksGenerator;
import de.ii.ldproxy.ogcapi.application.I18n;
import de.ii.ldproxy.ogcapi.domain.*;
import de.ii.ldproxy.ogcapi.feature_processing.api.FeatureProcessChain;
import de.ii.ldproxy.ogcapi.feature_processing.api.ImmutableProcessing;
import de.ii.ldproxy.ogcapi.feature_processing.api.Processing;
import de.ii.ldproxy.ogcapi.observation_processing.api.*;
import de.ii.xtraplatform.codelists.CodelistRegistry;
import de.ii.xtraplatform.crs.domain.CrsTransformer;
import de.ii.xtraplatform.crs.domain.CrsTransformerFactory;
import de.ii.xtraplatform.crs.domain.EpsgCrs;
import de.ii.xtraplatform.dropwizard.api.Dropwizard;
import de.ii.xtraplatform.features.domain.*;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;

import javax.ws.rs.NotAcceptableException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.function.Function;

import static com.codahale.metrics.MetricRegistry.name;

@Component
@Instantiate
@Provides
public class ObservationProcessingQueriesHandlerImpl implements ObservationProcessingQueriesHandler {

    private static final String DAPA_PATH_ELEMENT = "dapa";
    private final I18n i18n;
    private final CrsTransformerFactory crsTransformerFactory;
    private final Map<Query, OgcApiQueryHandler<? extends OgcApiQueryInput>> queryHandlers;
    private final MetricRegistry metricRegistry;
    private CodelistRegistry codelistRegistry;

    public ObservationProcessingQueriesHandlerImpl(@Requires I18n i18n,
                                                   @Requires CrsTransformerFactory crsTransformerFactory,
                                                   @Requires Dropwizard dropwizard,
                                                   @Requires CodelistRegistry codelistRegistry) {
        this.i18n = i18n;
        this.crsTransformerFactory = crsTransformerFactory;
        this.codelistRegistry = codelistRegistry;

        this.metricRegistry = dropwizard.getEnvironment()
                                        .metrics();

        this.queryHandlers = ImmutableMap.of(
                Query.PROCESS,
                OgcApiQueryHandler.with(OgcApiQueryInputObservationProcessing.class, this::getProcessResponse),
                Query.VARIABLES,
                OgcApiQueryHandler.with(OgcApiQueryInputVariables.class, this::getVariablesResponse),
                Query.LIST,
                OgcApiQueryHandler.with(OgcApiQueryInputProcessing.class, this::getProcessingResponse)
        );
    }

    @Override
    public Map<Query, OgcApiQueryHandler<? extends OgcApiQueryInput>> getQueryHandlers() {
        return queryHandlers;
    }

    public static void ensureCollectionIdExists(OgcApiApiDataV2 apiData, String collectionId) {
        if (!apiData.isCollectionEnabled(collectionId)) {
            throw new NotFoundException();
        }
    }

    private static void ensureFeatureProviderSupportsQueries(FeatureProvider2 featureProvider) {
        if (!featureProvider.supportsQueries()) {
            throw new IllegalStateException("feature provider does not support queries");
        }
    }

    private Response getVariablesResponse(OgcApiQueryInputVariables queryInput, OgcApiRequestContext requestContext) {
        OgcApiApi api = requestContext.getApi();
        OgcApiApiDataV2 apiData = api.getData();
        String collectionId = queryInput.getCollectionId();
        if (!apiData.isCollectionEnabled(collectionId))
            throw new NotFoundException();

        ObservationProcessingOutputFormatVariables outputFormat = api.getOutputFormat(
                ObservationProcessingOutputFormatVariables.class,
                requestContext.getMediaType(),
                "/collections/"+collectionId+"/"+DAPA_PATH_ELEMENT+"/variables")
                .orElseThrow(NotAcceptableException::new);

        ensureCollectionIdExists(api.getData(), collectionId);

        List<OgcApiMediaType> alternateMediaTypes = requestContext.getAlternateMediaTypes();
        List<OgcApiLink> links =
                new DefaultLinksGenerator().generateLinks(requestContext.getUriCustomizer(), requestContext.getMediaType(), alternateMediaTypes, i18n, requestContext.getLanguage());

        Variables variables = ImmutableVariables.builder()
                .variables(queryInput.getVariables())
                .links(links)
                .build();

        Response variablesResponse = outputFormat.getResponse(variables, collectionId, api, requestContext);

        Response.ResponseBuilder response = Response.ok()
                .entity(variablesResponse.getEntity())
                .type(requestContext
                        .getMediaType()
                        .type());

        Optional<Locale> language = requestContext.getLanguage();
        if (language.isPresent())
            response.language(language.get());

        if (queryInput.getIncludeLinkHeader() && links != null)
            links.stream()
                    .forEach(link -> response.links(link.getLink()));

        return response.build();
    }

    private Response getProcessingResponse(OgcApiQueryInputProcessing queryInput, OgcApiRequestContext requestContext) {
        OgcApiApi api = requestContext.getApi();
        OgcApiApiDataV2 apiData = api.getData();
        String collectionId = queryInput.getCollectionId();
        if (!apiData.isCollectionEnabled(collectionId))
            throw new NotFoundException();

        ObservationProcessingOutputFormatProcessing outputFormat = api.getOutputFormat(
                ObservationProcessingOutputFormatProcessing.class,
                requestContext.getMediaType(),
                "/collections/"+collectionId+"/"+DAPA_PATH_ELEMENT)
                .orElseThrow(NotAcceptableException::new);

        ensureCollectionIdExists(api.getData(), collectionId);

        List<OgcApiMediaType> alternateMediaTypes = requestContext.getAlternateMediaTypes();
        List<OgcApiLink> links =
                new DefaultLinksGenerator().generateLinks(requestContext.getUriCustomizer(), requestContext.getMediaType(), alternateMediaTypes, i18n, requestContext.getLanguage());

        Processing processing = ImmutableProcessing.builder()
                .from(queryInput.getProcessing())
                .links(links)
                .build();

        Response processingResponse = outputFormat.getResponse(processing, collectionId, api, requestContext);

        Response.ResponseBuilder response = Response.ok()
                .entity(processingResponse.getEntity())
                .type(requestContext
                        .getMediaType()
                        .type());

        Optional<Locale> language = requestContext.getLanguage();
        if (language.isPresent())
            response.language(language.get());

        if (queryInput.getIncludeLinkHeader() && links != null)
            links.stream()
                    .forEach(link -> response.links(link.getLink()));

        return response.build();
    }

    private Response getProcessResponse(OgcApiQueryInputObservationProcessing queryInput, OgcApiRequestContext requestContext) {
        OgcApiApi api = requestContext.getApi();
        OgcApiApiDataV2 apiData = api.getData();
        String collectionId = queryInput.getCollectionId();
        FeatureQuery query = queryInput.getQuery();
        FeatureProvider2 featureProvider = queryInput.getFeatureProvider();
        EpsgCrs defaultCrs = queryInput.getDefaultCrs();
        boolean includeLinkHeader = queryInput.getIncludeLinkHeader();
        Map<String, Object> processingParameters = queryInput.getProcessingParameters();
        FeatureProcessChain processes = queryInput.getProcesses();

        ObservationProcessingOutputFormat outputFormat = api.getOutputFormat(
                ObservationProcessingOutputFormat.class,
                requestContext.getMediaType(),
                "/collections/" + collectionId + processes.getSubSubPath())
                .orElseThrow(NotAcceptableException::new);

        ensureCollectionIdExists(api.getData(), collectionId);
        ensureFeatureProviderSupportsQueries(featureProvider);

        Optional<CrsTransformer> crsTransformer = Optional.empty();
        boolean swapCoordinates = false;

        EpsgCrs targetCrs = query.getCrs().orElse(defaultCrs);
        if (featureProvider.supportsCrs()) {
            EpsgCrs sourceCrs = featureProvider.crs().getNativeCrs();
            //TODO: warmup on service start
            crsTransformer = crsTransformerFactory.getTransformer(sourceCrs, targetCrs);
            swapCoordinates = crsTransformer.isPresent() ? crsTransformer.get()
                    .needsCoordinateSwap() : query.getCrs().isPresent() && featureProvider.crs().shouldSwapCoordinates(query.getCrs().get());
        }

        List<OgcApiMediaType> alternateMediaTypes = requestContext.getAlternateMediaTypes();

        // TODO add links
        List<OgcApiLink> links = ImmutableList.of();

        // TODO update
        ImmutableFeatureTransformationContextObservationProcessing.Builder transformationContext = new ImmutableFeatureTransformationContextObservationProcessing.Builder()
                .apiData(api.getData())
                .collectionId(collectionId)
                .ogcApiRequest(requestContext)
                .crsTransformer(crsTransformer)
                .codelists(codelistRegistry.getCodelists())
                .defaultCrs(defaultCrs)
                .links(links)
                .isFeatureCollection(true)
                .isHitsOnly(query.hitsOnly())
                .isPropertyOnly(query.propertyOnly())
                .processes(queryInput.getProcesses())
                .processingParameters(queryInput.getProcessingParameters())
                .fields(query.getFields())
                .limit(query.getLimit())
                .offset(query.getOffset())
                .maxAllowableOffset(query.getMaxAllowableOffset())
                .geometryPrecision(query.getGeometryPrecision())
                .shouldSwapCoordinates(swapCoordinates);

        StreamingOutput streamingOutput;

        if (outputFormat.canTransformFeatures()) {
            FeatureStream2 featureStream = featureProvider.queries()
                    .getFeatureStream2(query);

            streamingOutput = stream(featureStream, outputStream -> outputFormat.getFeatureTransformer(transformationContext.outputStream(outputStream)
                    .build(), requestContext.getLanguage())
                    .get());
        } else {
            throw new NotAcceptableException();
        }

        return response(streamingOutput,
                requestContext.getMediaType(),
                requestContext.getLanguage(),
                includeLinkHeader ? links : null,
                targetCrs);
    }

    private Response response(Object entity, OgcApiMediaType mediaType, Optional<Locale> language,
                              List<OgcApiLink> links, EpsgCrs crs) {
        Response.ResponseBuilder response = Response.ok()
                                                    .entity(entity);

        if (mediaType != null)
            response.type(mediaType.type()
                                   .toString());

        if (language.isPresent())
            response.language(language.get());

        if (links != null)
            links.stream()
                 .forEach(link -> response.links(link.getLink()));

        if (crs != null)
            response.header("Content-Crs", "<"+crs.toUriString()+">");

        return response.build();
    }

    private StreamingOutput stream(FeatureStream2 featureTransformStream,
                                   final Function<OutputStream, FeatureTransformer2> featureTransformer) {
        Timer.Context timer = metricRegistry.timer(name(ObservationProcessingQueriesHandlerImpl.class, "stream"))
                                            .time();
        return outputStream -> {
            try {
                featureTransformStream.runWith(featureTransformer.apply(outputStream))
                                      .toCompletableFuture()
                                      .join();
                timer.stop();
            } catch (CompletionException e) {
                if (e.getCause() instanceof WebApplicationException) {
                    throw (WebApplicationException) e.getCause();
                }
                throw new IllegalStateException("Feature stream error", e.getCause());
            }
        };
    }
}