/**
 * Copyright 2018 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.wfs3.aroundrelations;

import com.google.common.collect.ImmutableMap;
import de.ii.ldproxy.wfs3.api.FeatureTypeConfigurationWfs3;
import de.ii.ldproxy.wfs3.api.Wfs3ServiceData;
import de.ii.ldproxy.wfs3.oas30.Wfs3OpenApiExtension;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;

import static de.ii.ldproxy.wfs3.aroundrelations.AroundRelationConfiguration.EXTENSION_KEY;

/**
 * @author zahnen
 */
@Component
@Provides
@Instantiate
public class Wfs3OpenApiAroundRelations implements Wfs3OpenApiExtension {
    @Override
    public int getSortPriority() {
        return 200;
    }

    @Override
    public OpenAPI process(OpenAPI openAPI, Wfs3ServiceData serviceData) {

        ObjectSchema collectionInfo = (ObjectSchema) openAPI.getComponents()
                                                            .getSchemas()
                                                            .get("collectionInfo");

        collectionInfo.getProperties()
                      .put("relations", new ObjectSchema().description("Related collections that may be retrieved for this collection")
                                                          .example("{\"id\": \"label\"}"));


        Parameter limitList = new Parameter().name("limit")
                                             .in("query")
                                             .required(false)
                                             .style(Parameter.StyleEnum.FORM)
                                             .explode(false)
                                             .description("Comma-separated list of limits for related collections")
                                             .schema(new ArraySchema().items(new IntegerSchema()._default(5)
                                                                                                .minimum(BigDecimal.valueOf(0))
                                                                                                .maximum(BigDecimal.valueOf(10000))));

        Parameter offsetList = new Parameter().name("offset")
                                              .in("query")
                                              .required(false)
                                              .style(Parameter.StyleEnum.FORM)
                                              .explode(false)
                                              .description("Comma-separated list of offsets for related collections")
                                              .schema(new ArraySchema().items(new IntegerSchema()._default(0)
                                                                                                 .minimum(BigDecimal.valueOf(0))));

        Parameter relations = new Parameter().name("relations")
                                             .in("query")
                                             .required(false)
                                             .style(Parameter.StyleEnum.FORM)
                                             .explode(false)
                                             .description("Comma-separated list of related collections that should be shown for this feature")
                                             .schema(new ArraySchema().items(new StringSchema()));

        Parameter resolve = new Parameter().name("resolve")
                                           .in("query")
                                           .required(false)
                                           .style(Parameter.StyleEnum.FORM)
                                           .explode(false)
                                           .description("Only provide links to related collections by default, resolve the links when true")
                                           .schema(new BooleanSchema()._default(false));

        openAPI.getComponents()
               .addParameters(relations.getName(), relations)
               .addParameters(resolve.getName(), resolve)
               .addParameters("limitList", limitList)
               .addParameters("offsetList", offsetList);

        serviceData.getFeatureTypes()
                   .values()
                   .stream()
                   .sorted(Comparator.comparing(FeatureTypeConfigurationWfs3::getId))
                   .filter(ft -> serviceData.isFeatureTypeEnabled(ft.getId()) && ft.getExtensions()
                                                                                   .containsKey(EXTENSION_KEY))
                   .forEach(ft -> {

                       final AroundRelationConfiguration aroundRelationConfiguration = (AroundRelationConfiguration) ft.getExtensions()
                                                                                                                       .get(EXTENSION_KEY);
                       if (!aroundRelationConfiguration.getRelations()
                                                       .isEmpty()) {


                           PathItem pathItem2 = openAPI.getPaths()
                                                       .get(String.format("/collections/%s/items/{featureId}", ft.getId()));

                           if (Objects.nonNull(pathItem2)) {
                               pathItem2.getGet()
                                        .addParametersItem(new Parameter().$ref(relations.getName()))
                                        .addParametersItem(new Parameter().$ref(resolve.getName()))
                                        .addParametersItem(new Parameter().$ref("limitList"))
                                        .addParametersItem(new Parameter().$ref("offsetList"));
                           }
                       }

                   });

        return openAPI;
    }
}