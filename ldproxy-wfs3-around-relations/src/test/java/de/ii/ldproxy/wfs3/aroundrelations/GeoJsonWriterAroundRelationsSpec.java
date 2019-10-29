/**
 * Copyright 2019 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.wfs3.aroundrelations;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.greghaskins.spectrum.Spectrum;
import de.ii.ldproxy.ogcapi.domain.*;
import de.ii.ldproxy.target.geojson.*;
import de.ii.xtraplatform.crs.api.EpsgCrs;
import de.ii.xtraplatform.feature.provider.api.SimpleFeatureGeometry;
import de.ii.xtraplatform.feature.provider.wfs.ConnectionInfoWfsHttp;
import de.ii.xtraplatform.feature.provider.wfs.ImmutableConnectionInfoWfsHttp;
import de.ii.xtraplatform.feature.transformer.api.ImmutableFeatureProviderDataTransformer;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Consumer;

import static com.greghaskins.spectrum.dsl.specification.Specification.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author zahnen
 */
@RunWith(Spectrum.class)
public class GeoJsonWriterAroundRelationsSpec {

    static final GeoJsonGeometryMapping geometryMapping = new GeoJsonGeometryMapping();

    static final String coordinates = "10 50, 10 51, 11 51, 11 50, 10 50";

    static {
        geometryMapping.setGeometryType(GeoJsonGeometryMapping.GEO_JSON_GEOMETRY_TYPE.POLYGON);
    }

    static String expectedCollection = "{" + System.lineSeparator() +
            "        \"type\" : \"FeatureCollection\"," + System.lineSeparator() +
            "        \"links\" : []," + System.lineSeparator() +
            "        \"numberMatched\" : 8," + System.lineSeparator() +
            "        \"numberReturned\" : 5," + System.lineSeparator() +
            "        \"timeStamp\" : \"2018-08-30T15:36:08Z\"," + System.lineSeparator() +
            "        \"features\" : [ { \"links\" : [ {\"rel\": \"self\", \"href\": \"RELATION\"} ] } ]" + System.lineSeparator() +
            "      }";

    {

        describe("GeoJsonWriterAroundRelations middleware", () -> {

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            beforeEach(outputStream::reset);

            context("matching config and query", () -> {

                context("resolve=false", () -> {
                    //runTransformer(outputStream, true, OTHER_CRS);

                    it("it should add links and nothing else", () -> {

                        URI query = new URI("http://test?relations=test1&resolve=false");

                        FeatureTransformationContextGeoJson transformationContext = createTransformationContext(outputStream, query);

                        final FeatureTransformationContextGeoJson[] nextTransformationContext = new FeatureTransformationContextGeoJson[1];

                        runTransformer(transformationContext, new GeoJsonWriter() {
                            @Override
                            public GeoJsonWriter create() {
                                return this;
                            }

                            @Override
                            public int getSortPriority() {
                                return 10000;
                            }

                            @Override
                            public void onFeatureEnd(FeatureTransformationContextGeoJson transformationContext, Consumer<FeatureTransformationContextGeoJson> next) throws IOException {
                                nextTransformationContext[0] = transformationContext;
                            }
                        }, new AroundRelationResolver() {
                            @Override
                            public String resolve(AroundRelationsQuery.AroundRelationQuery aroundRelationQuery) {

                                assertThat(aroundRelationQuery.name).isEqualTo("test1");

                                assertThat(aroundRelationQuery.limit).isEqualTo(5);

                                assertThat(aroundRelationQuery.offset).isEqualTo(0);

                                assertThat(aroundRelationQuery.getBbox()).isEqualTo("10.000000,50.000000,11.000000,51.000000");


                                return expectedCollection;
                            }

                            @Override
                            public String getUrl(AroundRelationsQuery.AroundRelationQuery aroundRelationQuery, String additionalParameters) {
                                assertThat(aroundRelationQuery.name).isEqualTo("test1");

                                assertThat(aroundRelationQuery.limit).isEqualTo(5);

                                assertThat(aroundRelationQuery.offset).isEqualTo(0);

                                assertThat(aroundRelationQuery.getBbox()).isEqualTo("10.000000,50.000000,11.000000,51.000000");


                                return "RELATION";
                            }

                            @Override
                            public String resolve(AroundRelationsQuery.AroundRelationQuery aroundRelationQuery, String additionalParameters) {
                                return null;
                            }
                        });

                        int expectedSize = transformationContext.getLinks()
                                                                .size() + 1;

                        OgcApiLink expectedLink = new ImmutableOgcApiLink.Builder()
                                                                 .rel("test1")
                                                                 .title("test1")
                                                                 .type("application/geo+json")
                                                                 .href("RELATION")
                                                                 .build();

                        assertThat(nextTransformationContext[0].getLinks()).hasSize(expectedSize)
                                                                           .contains(expectedLink);

                        String actual = new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
                        String expected = "{ }";

                        assertThat(actual).isEqualTo(expected);


                    });

                });

                context("resolve=true", () -> {
                    //runTransformer(outputStream, true, OTHER_CRS);

                    it("it should additionalFeatures and nothing else", () -> {

                        URI query = new URI("http://test?relations=test1&resolve=true");

                        FeatureTransformationContextGeoJson transformationContext = createTransformationContext(outputStream, query);

                        final FeatureTransformationContextGeoJson[] nextTransformationContext = new FeatureTransformationContextGeoJson[1];

                        runTransformer(transformationContext, new GeoJsonWriter() {
                            @Override
                            public GeoJsonWriter create() {
                                return this;
                            }

                            @Override
                            public int getSortPriority() {
                                return 10000;
                            }

                            @Override
                            public void onFeatureEnd(FeatureTransformationContextGeoJson transformationContext, Consumer<FeatureTransformationContextGeoJson> next) throws IOException {
                                nextTransformationContext[0] = transformationContext;
                            }
                        }, new AroundRelationResolver() {
                            @Override
                            public String resolve(AroundRelationsQuery.AroundRelationQuery aroundRelationQuery) {
                                return expectedCollection;
                            }

                            @Override
                            public String getUrl(AroundRelationsQuery.AroundRelationQuery aroundRelationQuery, String additionalParameters) {
                                return null;
                            }

                            @Override
                            public String resolve(AroundRelationsQuery.AroundRelationQuery aroundRelationQuery, String additionalParameters) {
                                return expectedCollection;
                            }
                        });

                        assertThat(nextTransformationContext[0]).isEqualTo(transformationContext);

                        String actual = new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
                        String expected = "{" + System.lineSeparator() +
                                "  \"additionalFeatures\" : {" + System.lineSeparator() +
                                "    \"test1\" : {" + System.lineSeparator() +
                                "        \"type\" : \"FeatureCollection\"," + System.lineSeparator() +
                                "        \"links\" : []," + System.lineSeparator() +
                                "        \"numberMatched\" : 8," + System.lineSeparator() +
                                "        \"numberReturned\" : 5," + System.lineSeparator() +
                                "        \"timeStamp\" : \"2018-08-30T15:36:08Z\"," + System.lineSeparator() +
                                "        \"features\" : [ { \"links\" : [ {\"rel\": \"self\", \"href\": \"RELATION\"} ] } ]" + System.lineSeparator() +
                                "      }" + System.lineSeparator() +
                                "  }" + System.lineSeparator() +
                                "}";

                        assertThat(actual).isEqualTo(expected);


                    });

                });

            });

        });

    }

    private void runTransformer(FeatureTransformationContextGeoJson transformationContext, GeoJsonWriter chainedWriter, AroundRelationResolver aroundRelationResolver) throws IOException, URISyntaxException {


        FeatureTransformerGeoJson transformer = new FeatureTransformerGeoJson(transformationContext, ImmutableList.of(new GeoJsonWriterAroundRelations().create(aroundRelationResolver), chainedWriter));

        transformationContext.getJson()
                             .writeStartObject();

        transformer.onStart(OptionalLong.empty(), OptionalLong.empty());
        transformer.onFeatureStart(null);
        transformer.onGeometryStart(geometryMapping, SimpleFeatureGeometry.POLYGON, null);
        transformer.onGeometryNestedStart();
        transformer.onGeometryCoordinates(coordinates);
        transformer.onGeometryNestedEnd();
        transformer.onGeometryEnd();
        transformer.onFeatureEnd();

        transformationContext.getJson()
                             .writeEndObject();
        transformer.onEnd();
    }

    private FeatureTransformationContextGeoJson createTransformationContext(OutputStream outputStream, URI query) throws URISyntaxException {
        return ImmutableFeatureTransformationContextGeoJson.builder()
                                                           .crsTransformer(Optional.empty())
                                                           .apiData(new ImmutableOgcApiDatasetData.Builder()
                                                                                                .id("s")
                                                                                                .serviceType("WFS3")
                                                                                                .featureProvider(new ImmutableFeatureProviderDataTransformer.Builder()
                                                                                                        .providerType("WFS")
                                                                                                        .connectorType("HTTP")
                                                                                                                                                .connectionInfo(new ImmutableConnectionInfoWfsHttp.Builder()
                                                                                                                                                                                              .uri(new URI("http://localhost"))
                                                                                                                                                                                              .method(ConnectionInfoWfsHttp.METHOD.GET)
                                                                                                                                                                                              .version("2.0.0")
                                                                                                                                                                                              .gmlVersion("3.2.1")
                                                                                                                                                                                              .build())
                                                                                                                                                .nativeCrs(new EpsgCrs())
                                                                                                                                                .build())
                                                                                                .featureTypes(ImmutableMap.of("ft", new ImmutableFeatureTypeConfigurationOgcApi.Builder()
                                                                                                                                                                         .id("ft")
                                                                                                                                                                         .label("ft")
                                                                                                                                                                         .extent(new ImmutableCollectionExtent.Builder()
                                                                                                                                                                                                           .temporal(new ImmutableTemporalExtent.Builder().build())
                                                                                                                                                                                                           .build())
                                                                                                                                                                         .addCapabilities(new ImmutableAroundRelationsConfiguration.Builder()
                                                                                                                                                                                                                                    .enabled(true)
                                                                                                                                                                                                                                    .addRelations(new ImmutableRelation.Builder()
                                                                                                                                                                                                                                                                   .id("test1")
                                                                                                                                                                                                                                                                   .label("test1")
                                                                                                                                                                                                                                                                   .responseType("application/geo+json")
                                                                                                                                                                                                                                                                   .urlTemplate("")
                                                                                                                                                                                                                                                                   .build())
                                                                                                                                                                                                                                    .enabled(true)
                                                                                                                                                                                                                                    .build())
                                                                                                                                                                         .build()))
                                                                                                .build())
                                                           .collectionId("ft")
                                                           .outputStream(outputStream)
                                                           .links(ImmutableList.of(new ImmutableOgcApiLink.Builder()
                                                                                                    .href("TEST")
                                                                                                    .build()))
                                                           .isFeatureCollection(false)
                                                           .ogcApiRequest(new OgcApiRequestContext() {
                                                               @Override
                                                               public OgcApiMediaType getMediaType() {
                                                                   return null;
                                                               }

                                                               @Override
                                                               public List<OgcApiMediaType> getAlternateMediaTypes() {
                                                                   return null;
                                                               }

                                                               @Override
                                                               public Optional<Locale> getLanguage() {
                                                                   return Optional.empty();
                                                               }

                                                               @Override
                                                               public OgcApiDataset getApi() {
                                                                   return null;
                                                               }

                                                               @Override
                                                               public URICustomizer getUriCustomizer() {
                                                                   return new URICustomizer(query);
                                                               }

                                                               @Override
                                                               public String getStaticUrlPrefix() {
                                                                   return null;
                                                               }
                                                           })
                                                           .limit(10)
                                                           .offset(20)
                                                           .maxAllowableOffset(0)
                                                           .isHitsOnly(false)
                                                           .state(ModifiableStateGeoJson.create())
                                                           .geoJsonConfig(ImmutableGeoJsonConfig.builder().isEnabled(true).nestedObjectStrategy(FeatureTransformerGeoJson.NESTED_OBJECTS.NEST).multiplicityStrategy(FeatureTransformerGeoJson.MULTIPLICITY.ARRAY).useFormattedJsonOutput(true).build())
                                                           .build();

    }
}