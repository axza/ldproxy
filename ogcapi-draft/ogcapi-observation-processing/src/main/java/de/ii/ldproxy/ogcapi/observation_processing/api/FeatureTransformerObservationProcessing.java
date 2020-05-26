/**
 * Copyright 2020 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.ogcapi.observation_processing.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import de.ii.ldproxy.ogcapi.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ldproxy.ogcapi.feature_processing.api.FeatureProcess;
import de.ii.ldproxy.ogcapi.feature_processing.api.FeatureProcessChain;
import de.ii.ldproxy.ogcapi.features.core.api.FeatureTransformations;
import de.ii.ldproxy.ogcapi.features.core.application.OgcApiFeaturesCoreConfiguration;
import de.ii.ldproxy.ogcapi.observation_processing.application.*;
import de.ii.ldproxy.ogcapi.observation_processing.data.*;
import de.ii.ldproxy.target.geojson.GeoJsonConfiguration;
import de.ii.xtraplatform.akka.http.HttpClient;
import de.ii.xtraplatform.codelists.Codelist;
import de.ii.xtraplatform.crs.domain.CrsTransformer;
import de.ii.xtraplatform.features.domain.FeatureProperty;
import de.ii.xtraplatform.features.domain.FeatureTransformer2;
import de.ii.xtraplatform.features.domain.FeatureType;
import de.ii.xtraplatform.features.domain.transform.FeaturePropertySchemaTransformer;
import de.ii.xtraplatform.features.domain.transform.FeaturePropertyValueTransformer;
import de.ii.xtraplatform.geometries.domain.ImmutableCoordinatesTransformer;
import de.ii.xtraplatform.geometries.domain.SimpleFeatureGeometry;
import io.dropwizard.views.ViewRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.time.temporal.Temporal;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class FeatureTransformerObservationProcessing implements FeatureTransformer2 {

    private static final Logger LOGGER = LoggerFactory.getLogger(FeatureTransformerObservationProcessing.class);

    private OutputStreamWriter outputStreamWriter;

    private final boolean isFeatureCollection;
    private final ViewRenderer mustacheRenderer;
    private final int pageSize;
    private CrsTransformer crsTransformer;
    private final Map<String, Codelist> codelists;
    private final Optional<FeatureTransformations> baseTransformations;
    private final Optional<FeatureTransformations> geojsonTransformations;
    private final String serviceUrl;
    private final FeatureTransformationContextObservationProcessing transformationContext;
    private final ObservationProcessingConfiguration configuration;
    private final FeatureProcessChain processes;
    private final Map<String, Object> processingParameters;
    private final TemporalInterval interval;

    private ImmutableCoordinatesTransformer.Builder coordinatesTransformerBuilder;
    private SimpleFeatureGeometry currentGeometryType;
    private ImmutableCoordinatesTransformer.Builder currentCoordinatesTransformerBuilder;
    private int currentGeometryNesting;

    private Object currentFeature;
    private StringBuilder currentValueBuilder = new StringBuilder();
    private String currentValue = null;
    private FeatureProperty currentProperty = null;
    private ArrayList<FeatureProperty> currentFeatureProperties = null;
    private Observations observations;
    private int observationCount = 0;
    private Float currentResult;
    private Float currentLon;
    private Float currentLat;
    private Temporal currentTime;
    private String currentVar;
    private String currentUom;
    private Integer currentVarIdx;
    private String currentLocationCode;
    private String currentLocationName;

    public FeatureTransformerObservationProcessing(FeatureTransformationContextObservationProcessing transformationContext, HttpClient httpClient) {
        this.outputStreamWriter = new OutputStreamWriter(transformationContext.getOutputStream());
        this.isFeatureCollection = transformationContext.isFeatureCollection();
        this.pageSize = transformationContext.getLimit();
        this.crsTransformer = transformationContext.getCrsTransformer()
                                                   .orElse(null);
        this.serviceUrl = transformationContext.getServiceUrl();
        this.codelists = transformationContext.getCodelists();
        this.mustacheRenderer = null; // TODO transformationContext.getMustacheRenderer();
        this.configuration = transformationContext.getConfiguration();
        this.transformationContext = transformationContext;
        this.processes = transformationContext.getProcesses();
        this.processingParameters = transformationContext.getProcessingParameters();
        this.interval = (TemporalInterval) processingParameters.get("interval");

        FeatureTypeConfigurationOgcApi featureType = transformationContext.getApiData()
                .getCollections()
                .get(transformationContext.getCollectionId());
        baseTransformations = Objects.isNull(featureType) ? Optional.empty() : featureType
                .getExtension(OgcApiFeaturesCoreConfiguration.class)
                .map(coreConfiguration -> coreConfiguration);
        geojsonTransformations = Objects.isNull(featureType) ? Optional.empty() : featureType
                .getExtension(GeoJsonConfiguration.class)
                .map(coreConfiguration -> coreConfiguration);
    }

    @Override
    public String getTargetFormat() {
        return OutputFormatGeoJson.MEDIA_TYPE.toString();
    }

    @Override
    public void onStart(OptionalLong numberReturned, OptionalLong numberMatched) {

        LOGGER.debug("START");

        if (numberReturned.isPresent()) {
            long returned = numberReturned.getAsLong();
            long matched = numberMatched.orElse(-1);
            LOGGER.debug("numberMatched {}", matched);
            LOGGER.debug("numberReturned {}", returned);
            observations = new Observations((int) returned);
            /* TODO
            transformationContext.getState().setObservations(observations);
             */
        } else {
            // TODO if numberReturned not present, abort or use the page size as default?
            observations = new Observations(pageSize);
            /* TODO
            transformationContext.getState().setObservations(observations);
             */
        }

        // TODO if numberMatched is the page size, abort?
    }

    @Override
    public void onEnd() {

        LOGGER.debug(observationCount + " observations received.");

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        ObjectNode featureCollection = mapper.createObjectNode();
        featureCollection.put("type", "FeatureCollection");
        ArrayNode features = featureCollection.putArray("features");

        Object data = observations;
        for (FeatureProcess process : processes.asList()) {
            data = process.execute(data, processingParameters);
        }

        if (data!=null) {
            if (data instanceof ObservationCollectionPointTimeSeries) {
                ObservationCollectionPointTimeSeries result = (ObservationCollectionPointTimeSeries) data;
                result.getValues().entrySet().stream().forEach(entry ->
                        addFeature(features.addObject(), result.getCode(), result.getName(), result.getGeometry(),
                                   entry.getKey(), entry.getKey(), entry.getValue()));
            } else if (data instanceof ObservationCollectionPointTimeSeriesList) {
                ObservationCollectionPointTimeSeriesList result = (ObservationCollectionPointTimeSeriesList) data;
                result.stream().forEach(pos -> pos.getValues().entrySet().stream().forEach(entry ->
                        addFeature(features.addObject(), pos.getCode(), pos.getName(), pos.getGeometry(),
                                   entry.getKey(), entry.getKey(), entry.getValue())));
            } else if (data instanceof ObservationCollectionAreaTimeSeries) {
                ObservationCollectionAreaTimeSeries result = (ObservationCollectionAreaTimeSeries) data;
                result.getValues().entrySet().stream().forEach(entry ->
                        addFeature(features.addObject(), Optional.empty(), Optional.empty(), result.getGeometry(),
                                entry.getKey(), entry.getKey(), entry.getValue()));
            } else if (data instanceof ObservationCollectionPoint) {
                ObservationCollectionPoint result = (ObservationCollectionPoint) data;
                addFeature(features.addObject(), result.getCode(), result.getName(), result.getGeometry(),
                        result.getInterval().getBegin(), result.getInterval().getEnd(), result.getValues());
            } else if (data instanceof ObservationCollectionPointList) {
                ObservationCollectionPointList result = (ObservationCollectionPointList) data;
                result.stream().forEach(pos -> pos.getValues().entrySet().stream().forEach(entry ->
                        addFeature(features.addObject(), pos.getCode(), pos.getName(), pos.getGeometry(),
                                pos.getInterval().getBegin(), pos.getInterval().getEnd(), pos.getValues())));
            } else if (data instanceof ObservationCollectionArea) {
                ObservationCollectionArea result = (ObservationCollectionArea) data;
                addFeature(features.addObject(), Optional.empty(), Optional.empty(), result.getGeometry(),
                        result.getInterval().getBegin(), result.getInterval().getEnd(), result.getValues());
            } else if (data instanceof DataArrayXyt) {
                DataArrayXyt result = (DataArrayXyt) data;
                Vector<String> vars = result.getVars();
                for (int i0=0; i0<result.getWidth(); i0++)
                    for (int i1=0; i1<result.getHeight(); i1++)
                        for (int i2=0; i2<result.getSteps(); i2++) {
                            Map<String, Number> map = new HashMap<>();
                            for (int i3 = 0; i3 < vars.size(); i3++)
                                if (!Float.isNaN(result.array[i2][i1][i0][i3]))
                                    map.put(vars.get(i3), result.array[i2][i1][i0][i3]);
                            if (!map.isEmpty())
                                addFeature(features.addObject(), Optional.empty(), Optional.empty(),
                                            new GeometryPoint(result.lon(i0), result.lat(i1)),
                                            result.date(i2), result.date(i2), map);
                        }
            } else if (data instanceof DataArrayXy) {
                DataArrayXy result = (DataArrayXy) data;
                Vector<String> vars = result.getVars();
                for (int i0=0; i0<result.getWidth(); i0++)
                    for (int i1=0; i1<result.getHeight(); i1++) {
                        Map<String, Number> map = new HashMap<>();
                        for (int i3 = 0; i3 < vars.size(); i3++)
                            if (!Float.isNaN(result.array[i1][i0][i3]))
                                map.put(vars.get(i3), result.array[i1][i0][i3]);
                        if (!map.isEmpty())
                            addFeature(features.addObject(), Optional.empty(), Optional.empty(),
                                    new GeometryPoint(result.lon(i0), result.lat(i1)),
                                    result.getInterval().getBegin(), result.getInterval().getEnd(), map);
                    }
            }
        }

        try {
            mapper.writeValue(outputStreamWriter, featureCollection);
        } catch (IOException e) {
            // TODO
            LOGGER.error("Error writing observations.");
        }

        LOGGER.debug("OgcApiResponse written.");
    }

    private void addFeature(ObjectNode feature, Optional<String> locationCode, Optional<String> locationName, Geometry geometry, Temporal timeBegin, Temporal timeEnd, Map<String, Number> values) {
        feature.put("type", "Feature");
        addGeometry(feature, geometry);
        ObjectNode properties = feature.putObject("properties");
        if (locationCode.isPresent())
            properties.put("locationCode", locationCode.get());
        if (locationName.isPresent())
            properties.put("locationName", locationName.get());
        if (timeBegin==timeEnd)
            properties.put("phenomenonTime", timeBegin.toString());
        else
            properties.put("phenomenonTime", timeBegin.toString()+"/"+timeEnd.toString());
        values.entrySet().parallelStream()
                .forEach(entry -> {
                    String variable = entry.getKey();
                    Number val = entry.getValue();
                    if (val instanceof Integer)
                        properties.put(variable, val.intValue());
                    else
                        properties.put(variable, val.floatValue());
                });
    }

    private void addGeometry(ObjectNode feature, Geometry geometry) {
        ArrayNode coord = feature.putObject("geometry")
                .put("type", geometry instanceof GeometryPoint ? "Point" : ((GeometryMultiPolygon) geometry).size()==1 ? "Polygon" : "MultiPolygon" )
                .putArray("coordinates");
        if (geometry instanceof GeometryPoint) {
            ((GeometryPoint) geometry).asList().stream().forEachOrdered(ord -> coord.add(ord));
        } else {
            GeometryMultiPolygon multiPolygon = (GeometryMultiPolygon) geometry;
            multiPolygon.asList().stream().forEachOrdered(polygon -> {
                ArrayNode coordPoly = multiPolygon.size()==1 ? coord : coord.addArray();
                polygon.stream().forEachOrdered(ring -> {
                    ArrayNode coordRing = coordPoly.addArray();
                    ring.stream().forEachOrdered(pos -> {
                        ArrayNode coordPos = coordRing.addArray();
                        pos.stream().forEach(ord -> coordPos.add(ord));
                    });
                });
            });
        }
    }

    @Override
    public void onFeatureStart(FeatureType featureType) {

        // TODO prepare feature object
        currentFeature = null;
        currentResult = null;
        currentLon = null;
        currentLat = null;
        currentTime = null;
        currentVar = null;
        currentUom = null;
        currentVarIdx = null;
        currentLocationCode = null;
        currentLocationName = null;
        currentFeatureProperties = new ArrayList<>(featureType.getProperties().values());
    }

    @Override
    public void onFeatureEnd() {

        if (Objects.nonNull(currentLon) && Objects.nonNull(currentLat) &&
            Objects.nonNull(currentTime) && Objects.nonNull(currentVarIdx) &&
            Objects.nonNull(currentResult)) {
            // TODO post-execute feature

            boolean added = observations.addValue(currentLon, currentLat, currentTime, currentVarIdx, currentResult,
                    currentLocationCode, currentLocationName);
            if (added)
                observationCount++;

        } else {
            // TODO incomplete information, throw error and ignore feature

        }

        currentFeature = null;
    }

    @Override
    public void onPropertyStart(FeatureProperty featureProperty, List<Integer> multiplicities) {
        // TODO current assumptions: no arrays, no object values,
        //      properties "observedProperty", "phenomenonTime", "result", "locationCode", "locationName";
        //      other properties are ignored

        FeatureProperty processedFeatureProperty = featureProperty;
        if (Objects.nonNull(processedFeatureProperty)) {

            List<FeaturePropertySchemaTransformer> schemaTransformations = getSchemaTransformations(processedFeatureProperty);
            for (FeaturePropertySchemaTransformer schemaTransformer : schemaTransformations) {
                processedFeatureProperty = schemaTransformer.transform(processedFeatureProperty);
            }

            /* TODO
            if (Objects.nonNull(processedFeatureProperty)) {
                transformationContext.getState()
                        .setCurrentFeatureProperty(Optional.ofNullable(processedFeatureProperty));
                transformationContext.getState()
                        .setCurrentMultiplicity(multiplicities);
            }
             */
        }

        switch (processedFeatureProperty.getName()) {
            default:
                currentProperty = null;
                break;
            case "observedProperty":
            case "phenomenonTime":
            case "result":
            case "locationCode":
            case "locationName":
                currentProperty = processedFeatureProperty;
        }
    }

    @Override
    public void onPropertyText(String text) {
        if (Objects.nonNull(currentProperty))
            currentValueBuilder.append(text);
    }

    @Override
    public void onPropertyEnd() throws Exception {
        if (currentValueBuilder.length() > 0) {
            String value = currentValueBuilder.toString();
            List<FeaturePropertyValueTransformer> valueTransformations = getValueTransformations(currentProperty);
            for (FeaturePropertyValueTransformer valueTransformer : valueTransformations) {
                value = valueTransformer.transform(value);
            }
            // skip, if the value has been transformed to null
            if (Objects.nonNull(value)) {
                switch (currentProperty.getName()) {
                    case "observedProperty":
                        currentVar = value;
                        currentVarIdx = observations.getOrAddVariable(value);
                        break;
                    case "phenomenonTime":
                        currentTime = interval.getTime(value);
                        break;
                    case "result":
                        currentResult = Float.valueOf(value);
                        break;
                    case "locationCode":
                        currentLocationCode = value;
                        break;
                    case "locationName":
                        currentLocationName = value;
                        break;
                }

                /* TODO
                transformationContext.getState()
                        .setCurrentValue(value);

                transformationContext.getState()
                        .setEvent(FeatureTransformationContext.Event.PROPERTY);
                 */
            }
            currentValueBuilder.setLength(0);
        }

        /* TODO
        transformationContext.getState()
                .setCurrentFeatureProperty(Optional.empty());
        transformationContext.getState()
                .setCurrentValue(Optional.empty());
         */
        this.currentProperty = null;

        // reset
        currentValueBuilder.setLength(0);
        currentValue = null;
        currentProperty = null;
    }

    @Override
    public void onGeometryStart(FeatureProperty featureProperty, SimpleFeatureGeometry type, Integer dimension) {
        if (Objects.nonNull(featureProperty)) {

            currentProperty = featureProperty;
            currentGeometryType = type;

            // TODO
            if (type!=SimpleFeatureGeometry.POINT) {
                // TODO throw error
            }

            /* TODO
            ImmutableCoordinatesTransformer.Builder coordinatesTransformerBuilder = ImmutableCoordinatesTransformer.builder();

            if (transformationContext.getCrsTransformer()
                    .isPresent()) {
                coordinatesTransformerBuilder.crsTransformer(transformationContext.getCrsTransformer()
                        .get());
            }

            //TODO: might set dimension in FromSql2?
            int fallbackDimension = Objects.nonNull(dimension) ? dimension : 2;
            coordinatesTransformerBuilder.sourceDimension(transformationContext.getCrsTransformer()
                    .map(CrsTransformer::getSourceDimension)
                    .orElse(fallbackDimension));
            coordinatesTransformerBuilder.targetDimension(transformationContext.getCrsTransformer()
                    .map(CrsTransformer::getTargetDimension)
                    .orElse(fallbackDimension));

            //TODO ext
            if (transformationContext.getMaxAllowableOffset() > 0) {
                int minPoints = currentGeometryType == SimpleFeatureGeometry.MULTI_POLYGON || currentGeometryType == SimpleFeatureGeometry.POLYGON ? 4 : 2;
                coordinatesTransformerBuilder.maxAllowableOffset(transformationContext.getMaxAllowableOffset());
                coordinatesTransformerBuilder.minNumberOfCoordinates(minPoints);
            }

            if (transformationContext.shouldSwapCoordinates()) {
                coordinatesTransformerBuilder.isSwapXY(true);
            }

            if (transformationContext.getGeometryPrecision() > 0) {
                coordinatesTransformerBuilder.precision(transformationContext.getGeometryPrecision());
            }

            if (Objects.equals(featureProperty.isForceReversePolygon(), true)) {
                coordinatesTransformerBuilder.isReverseOrder(true);
            }

            currentCoordinatesTransformerBuilder = coordinatesTransformerBuilder;
            currentGeometryNesting = 0;
             */
            currentValue = "";
        }
    }

    @Override
    public void onGeometryNestedStart() {
        if (Objects.isNull(currentGeometryType))
            return;

        // TODO throw error
        currentGeometryNesting++;
    }

    @Override
    public void onGeometryCoordinates(String text) {
        if (Objects.isNull(currentGeometryType))
            return;

        currentValue += text;
    }

    @Override
    public void onGeometryNestedEnd() {
        // TODO throw error
        currentGeometryNesting--;
    }

    @Override
    public void onGeometryEnd() {
        if (!currentValue.isEmpty()) {
            // TODO points only
            List<String> ords = Splitter.on(Pattern.compile("\\s"))
                    .trimResults()
                    .omitEmptyStrings()
                    .splitToList(Strings.nullToEmpty(currentValue));
            if (ords.size()>=2) {
                currentLon = Float.valueOf(ords.get(0));
                currentLat = Float.valueOf(ords.get(1));
            } else {
                // TODO report error
            }
        }

        currentProperty = null;
        currentGeometryType = null;
        currentValue = null;
    }

    // TODO move somewhere central for reuse?
    private List<FeaturePropertyValueTransformer> getValueTransformations(FeatureProperty featureProperty) {
        List<FeaturePropertyValueTransformer> valueTransformations = null;
        if (baseTransformations.isPresent()) {
            valueTransformations = baseTransformations.get()
                    .getValueTransformations(transformationContext.getCodelists(), transformationContext.getServiceUrl())
                    .get(featureProperty.getName()
                            .replaceAll("\\[[^\\]]+?\\]", "[]"));
        }
        if (geojsonTransformations.isPresent()) {
            if (Objects.nonNull(valueTransformations)) {
                List<FeaturePropertyValueTransformer> moreTransformations = geojsonTransformations.get()
                        .getValueTransformations(new HashMap<String, Codelist>(), transformationContext.getServiceUrl())
                        .get(featureProperty.getName()
                                .replaceAll("\\[[^\\]]+?\\]", "[]"));
                if (Objects.nonNull(moreTransformations)) {
                    valueTransformations = Stream
                            .of(valueTransformations, moreTransformations)
                            .flatMap(Collection::stream)
                            .collect(ImmutableList.toImmutableList());
                }
            } else {
                valueTransformations = geojsonTransformations.get()
                        .getValueTransformations(new HashMap<String, Codelist>(), transformationContext.getServiceUrl())
                        .get(featureProperty.getName()
                                .replaceAll("\\[[^\\]]+?\\]", "[]"));
            }
        }

        return Objects.nonNull(valueTransformations) ? valueTransformations : ImmutableList.of();
    }

    // TODO move somewhere central for reuse?
    private List<FeaturePropertySchemaTransformer> getSchemaTransformations(FeatureProperty featureProperty) {
        List<FeaturePropertySchemaTransformer> schemaTransformations = null;
        if (baseTransformations.isPresent()) {
            schemaTransformations = baseTransformations.get()
                    .getSchemaTransformations(false)
                    .get(featureProperty.getName()
                            .replaceAll("\\[[^\\]]+?\\]", "[]"));
        }
        if (geojsonTransformations.isPresent()) {
            if (Objects.nonNull(schemaTransformations)) {
                List<FeaturePropertySchemaTransformer> moreTransformations = geojsonTransformations.get()
                        .getSchemaTransformations(false)
                        .get(featureProperty.getName()
                                .replaceAll("\\[[^\\]]+?\\]", "[]"));
                if (Objects.nonNull(moreTransformations)) {
                    schemaTransformations = Stream
                            .of(schemaTransformations, moreTransformations)
                            .flatMap(Collection::stream)
                            .collect(ImmutableList.toImmutableList());
                }
            } else {
                schemaTransformations = geojsonTransformations.get()
                        .getSchemaTransformations(false)
                        .get(featureProperty.getName()
                                .replaceAll("\\[[^\\]]+?\\]", "[]"));
            }
        }

        return Objects.nonNull(schemaTransformations) ? schemaTransformations : ImmutableList.of();
    }
}
