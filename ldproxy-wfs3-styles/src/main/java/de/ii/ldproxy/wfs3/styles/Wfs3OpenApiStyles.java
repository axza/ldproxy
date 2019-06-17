/**
 * Copyright 2019 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.wfs3.styles;

import de.ii.ldproxy.wfs3.api.FeatureTypeConfigurationWfs3;
import de.ii.ldproxy.wfs3.api.Wfs3ServiceData;
import de.ii.ldproxy.wfs3.oas30.Wfs3OpenApiExtension;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.tags.Tag;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;


/**
 * extend API definition with styles
 */
@Component
@Provides
@Instantiate
public class Wfs3OpenApiStyles implements Wfs3OpenApiExtension {
    @Override
    public int getSortPriority() {
        return 25;
    }


    @Override
    public OpenAPI process(OpenAPI openAPI, Wfs3ServiceData serviceData) {

        if (serviceData != null && isExtensionEnabled(serviceData, StylesConfiguration.class)) {


            Parameter styleId = new Parameter();
            styleId.setName("styleId");
            styleId.in("path");
            styleId.description("Local identifier of a specific style of the collection. A list of all available styles for the collection can be found under the /styles path.");
            styleId.setRequired(true);
            Schema styleIdSchema = new Schema();
            styleIdSchema.setType("string");
            styleId.setSchema(styleIdSchema);


            Parameter styleIdDataset = new Parameter();
            styleIdDataset.setName("styleId");
            styleIdDataset.in("path");
            styleIdDataset.description("Local identifier of a specific style of the dataset. A list of all available styles for the dataset can be found under the /styles path.");
            styleIdDataset.setRequired(true);
            styleIdDataset.setSchema(styleIdSchema);

            openAPI.getComponents()
                   .addParameters("styleIdentifier", styleId);
            openAPI.getComponents()
                   .addParameters("styleIdentifierDataset", styleIdDataset);


            List<String> requirementsId = new LinkedList<String>();
            requirementsId.add("id");
            List<String> typeRequirements = new LinkedList<String>();
            typeRequirements.add("type");

            List<String> requirementsLayers = new LinkedList<String>();
            requirementsLayers.add("id");
            requirementsLayers.add("type");

            List<String> requirementsStyle = new LinkedList<String>();
            requirementsStyle.add("version");
            requirementsStyle.add("sources");
            requirementsStyle.add("layers");

            Schema stylesArray = new Schema();
            stylesArray.setType("object");
            stylesArray.setRequired(requirementsId);
            stylesArray.addProperties("identifier", new Schema().type("string")
                                                                .example("default"));
            stylesArray.addProperties("links", new ArraySchema().items(new Schema().$ref("#/components/schemas/link")));

            Schema styles = new Schema();
            styles.setType("object");
            styles.setRequired(typeRequirements);
            styles.addProperties("styles", new ArraySchema().items(new Schema().$ref("#/components/schemas/stylesArray")));


            Schema layersArray = new Schema();
            layersArray.setType("object");
            layersArray.setRequired(requirementsLayers);
            List<String> typeEnum = new ArrayList<String>();
            typeEnum.add("fill");
            typeEnum.add("line");
            typeEnum.add("symbol");
            typeEnum.add("circle");
            typeEnum.add("heatmap");
            typeEnum.add("fill-extrusion");
            typeEnum.add("raster");
            typeEnum.add("hillshade");
            typeEnum.add("background");

            layersArray.addProperties("id", new Schema().type("string")
                                                        .example("1"));
            layersArray.addProperties("type", new StringSchema()._enum(typeEnum)
                                                                .example("fill"));
            layersArray.addProperties("source", new Schema().type("string")
                                                            .example("default"));
            layersArray.addProperties("source-layer", new Schema().type("string")
                                                                  .example("collectionId"));
            layersArray.addProperties("layout", new Schema().type("object"));
            layersArray.addProperties("paint", new Schema().type("object")
                                                           .addProperties("fill-color", new StringSchema().example("#11083b")));

            Schema style = new Schema();
            style.setType("object");
            style.setRequired(requirementsStyle);
            style.addProperties("version", new Schema().type("number")
                                                       .example(8));
            style.addProperties("name", new Schema().type("string")
                                                    .example("default"));
            style.addProperties("sources",
                    new Schema().type("object")
                                .addProperties("default",
                                        new Schema().type("object")
                                                    .addProperties("type", new Schema().type("String")
                                                                                       .example("vector"))
                                                    .addProperties("url", new Schema().type("String")
                                                                                      .example("sourceUrl"))));
            style.addProperties("sprite", new Schema().type("string")
                                                      .example("mapbox://sprites/mapbox/bright-v8"));
            style.addProperties("layers", new ArraySchema().items(new Schema().$ref("#/components/schemas/layersArray")));


            openAPI.getComponents()
                   .addSchemas("stylesArray", stylesArray);
            openAPI.getComponents()
                   .addSchemas("styles", styles);
            openAPI.getComponents()
                   .addSchemas("layersArray", layersArray);
            openAPI.getComponents()
                   .addSchemas("style", style);

            openAPI.getTags()
                   .add(new Tag().name("Styles")
                                 .description("Access to styles."));

            if (serviceData != null) {

                openAPI.getPaths()
                       .addPathItem("/styles", new PathItem().description("something"));  //create a new path
                PathItem pathItem = openAPI.getPaths()
                                           .get("/styles");
                ApiResponse success = new ApiResponse().description("A list of styles for the dataset")
                                                       .content(new Content()
                                                               .addMediaType("application/json", new MediaType().schema(new Schema().$ref("#/components/schemas/styles")))
                                                       );
                ApiResponse exception = new ApiResponse().description("An error occured.")
                                                         .content(new Content()
                                                                 .addMediaType("application/json", new MediaType().schema(new Schema().$ref("#/components/schemas/exception")))
                                                         );
                if (Objects.nonNull(pathItem)) {
                    pathItem
                            .get(new Operation()
                                    .addTagsItem("Styles")
                                    .summary("retrieve all available styles from the dataset")
                                    .operationId("getStyles")
                                    .addParametersItem(new Parameter().$ref("#/components/parameters/f3"))
                                    //.requestBody(requestBody)
                                    .responses(new ApiResponses()
                                            .addApiResponse("200", success)
                                            .addApiResponse("default", exception))
                            );
                }
                openAPI.getPaths()
                       .addPathItem("/styles", pathItem); //save to Path

                openAPI.getPaths()
                       .addPathItem("/styles/{styleId}", new PathItem().description("something"));
                pathItem = openAPI.getPaths()
                                  .get("/styles/{styleId}");
                success = new ApiResponse().description("A style of the dataset")
                                           .content(new Content()
                                                   .addMediaType("application/json", new MediaType().schema(new Schema().$ref("#/components/schemas/style")))
                                           );
                exception = new ApiResponse().description("An error occured.")
                                             .content(new Content()
                                                     .addMediaType("application/json", new MediaType().schema(new Schema().$ref("#/components/schemas/exception")))
                                             );
                if (Objects.nonNull(pathItem)) {
                    pathItem
                            .get(new Operation()
                                    .addTagsItem("Styles")
                                    .summary("retrieve a style of the dataset by id")
                                    .operationId("getStyle")
                                    .addParametersItem(new Parameter().$ref("#/components/parameters/styleIdentifierDataset"))
                                    .addParametersItem(new Parameter().$ref("#/components/parameters/f3"))

                                    //.requestBody(requestBody)
                                    .responses(new ApiResponses()
                                            .addApiResponse("200", success)
                                            .addApiResponse("default", exception))
                            );
                }

                openAPI.getPaths()
                       .addPathItem("/styles/{styleId}", pathItem);


                serviceData.getFeatureTypes()
                           .values()
                           .stream()
                           .sorted(Comparator.comparing(FeatureTypeConfigurationWfs3::getId))
                           .filter(ft -> serviceData.isFeatureTypeEnabled(ft.getId()))
                           .forEach(ft -> {

                               openAPI.getPaths()
                                      .addPathItem("/collections/" + ft.getId() + "/styles", new PathItem().description("something"));
                               PathItem pathItem2 = openAPI.getPaths()
                                                           .get("/collections/" + ft.getId() + "/styles");
                               ApiResponse success2 = new ApiResponse().description("A list of styles for the collection " + ft.getLabel())
                                                                       .content(new Content()
                                                                               .addMediaType("application/json", new MediaType().schema(new Schema().$ref("#/components/schemas/styles")))
                                                                       );
                               ApiResponse exception2 = new ApiResponse().description("An error occured.")
                                                                         .content(new Content()
                                                                                 .addMediaType("application/json", new MediaType().schema(new Schema().$ref("#/components/schemas/exception")))
                                                                         );
                               if (Objects.nonNull(pathItem2)) {
                                   pathItem2
                                           .get(new Operation()
                                                   .addTagsItem("Styles")
                                                   .summary("retrieve all available styles from the collection " + ft.getLabel())
                                                   .operationId("getStyles" + ft.getId())
                                                   .addParametersItem(new Parameter().$ref("#/components/parameters/f3"))
                                                   //.requestBody(requestBody)
                                                   .responses(new ApiResponses()
                                                           .addApiResponse("200", success2)
                                                           .addApiResponse("default", exception2))
                                           );
                               }

                               openAPI.getPaths()
                                      .addPathItem("/collections/" + ft.getId() + "/styles/{styleId}", pathItem2);

                               openAPI.getPaths()
                                      .addPathItem("/collections/" + ft.getId() + "/styles/{styleId}", new PathItem().description("something"));
                               pathItem2 = openAPI.getPaths()
                                                  .get("/collections/" + ft.getId() + "/styles/{styleId}");
                               success2 = new ApiResponse().description("A style of the collection " + ft.getLabel())
                                                           .content(new Content()
                                                                   .addMediaType("application/json", new MediaType().schema(new Schema().$ref("#/components/schemas/style")))
                                                           );
                               exception2 = new ApiResponse().description("An error occured.")
                                                             .content(new Content()
                                                                     .addMediaType("application/json", new MediaType().schema(new Schema().$ref("#/components/schemas/exception")))
                                                             );
                               if (Objects.nonNull(pathItem2)) {
                                   pathItem2
                                           .get(new Operation()
                                                   .addTagsItem("Styles")
                                                   .summary("retrieve a style of the collection " + ft.getLabel() + " by id")
                                                   .operationId("getStyle" + ft.getId())
                                                   .addParametersItem(new Parameter().$ref("#/components/parameters/styleIdentifier"))
                                                   .addParametersItem(new Parameter().$ref("#/components/parameters/f3"))

                                                   //.requestBody(requestBody)
                                                   .responses(new ApiResponses()
                                                           .addApiResponse("200", success2)
                                                           .addApiResponse("default", exception2))
                                           );
                               }

                               openAPI.getPaths()
                                      .addPathItem("/collections/" + ft.getId() + "/styles/{styleId}", pathItem2);

                           });
            }
        }
        return openAPI;
    }
}
