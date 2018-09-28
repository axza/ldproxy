/**
 * Copyright 2018 interactive instruments GmbH
 * <p>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.wfs3.vt;

import de.ii.ldproxy.wfs3.api.FeatureTypeConfigurationWfs3;
import de.ii.ldproxy.wfs3.api.Wfs3ServiceData;
import de.ii.ldproxy.wfs3.oas30.Wfs3OpenApiExtension;
import io.swagger.v3.oas.annotations.tags.Tags;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.tags.Tag;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;

import java.math.BigDecimal;
import java.util.*;

/**
 * extend API definition with tile resources
 *
 * @author portele
 */
@Component
@Provides
@Instantiate
public class Wfs3OpenApiVectorTiles implements Wfs3OpenApiExtension {
    @Override
    public int getSortPriority() {
        return 20;
    }

    @Override
    public OpenAPI process(OpenAPI openAPI, Wfs3ServiceData serviceData) {
        /*specify all new parameters. They are:
        * tilingSchemeId
        * zoomLevel
        * row
        * column
        * f2/f3/f4
        * collections
        * properties*/

        Parameter tilingSchemeId=new Parameter();
        tilingSchemeId.setName("tilingSchemeId");
        tilingSchemeId.in("path");
        tilingSchemeId.description("Local identifier of a specific tiling scheme");
        tilingSchemeId.setRequired(true);
        Schema tilingSchemeIdSchema=new Schema();
        tilingSchemeIdSchema.setType("string");
        tilingSchemeId.setSchema(tilingSchemeIdSchema);

        Parameter zoomLevel=new Parameter();
        zoomLevel.setName("zoomLevel");
        zoomLevel.in("path");
        zoomLevel.description("Zoom level of the tile");
        zoomLevel.setRequired(true);
        zoomLevel.setSchema(tilingSchemeIdSchema);

        Parameter row=new Parameter();
        row.setName("row");
        row.in("path");
        row.description("Row index of the tile on the selected zoom level");
        row.setRequired(true);
        row.setSchema(tilingSchemeIdSchema);

        Parameter column=new Parameter();
        column.setName("column");
        column.in("path");
        column.description("Column index of the tile on the selected zoom level");
        column.setRequired(true);
        column.setSchema(tilingSchemeIdSchema);


        Parameter f2=new Parameter();
        f2.setName("f");
        f2.in("query");
        f2.description("\\\n" +
                "        The format of the response. If no value is provided, the standard http rules apply, i.e., the accept header shall be used to determine the format.\\\n" +
                "        Pre-defined values are \"json\" and \"mvt\". The response to other values is determined by the server.");
        f2.setRequired(false);
        f2.setStyle(Parameter.StyleEnum.FORM);
        f2.setExplode(false);
        List<String> f2Enum=new ArrayList<String>();
        f2Enum.add("json");
        f2Enum.add("mvt");
        f2.setSchema(new StringSchema()._enum(f2Enum));
        f2.example("json");

        Parameter f3=new Parameter();
        f3.setName("f");
        f3.in("query");
        f3.description("\\\n" +
                "        The format of the response. If no value is provided, the standard http rules apply, i.e., the accept header shall be used to determine the format.\\\n" +
                "        The only pre-defined value is \"json\". The response to other values is determined by the server.");
        f3.setRequired(false);
        f3.setStyle(Parameter.StyleEnum.FORM);
        f3.setExplode(false);
        Schema f3Schema=new Schema();
        f3Schema.setType("string");
        List<String> f3Enum=new ArrayList<String>();
        f3Enum.add("json");
        f3.setSchema(new StringSchema()._enum(f3Enum));
        f3.example("json");

        Parameter f4=new Parameter();
        f4.setName("f");
        f4.in("query");
        f4.description("\\\n" +
                "        The format of the response. If no value is provided, the standard http rules apply, i.e., the accept header shall be used to determine the format.\\\n" +
                "        The only pre-defined value is \"mvt\". The response to other values is determined by the server.");
        f4.setRequired(false);
        f4.setStyle(Parameter.StyleEnum.FORM);
        f4.setExplode(false);
        List<String> f4Enum=new ArrayList<String>();
        f4Enum.add("mvt");
        f4.setSchema(new StringSchema()._enum(f4Enum));
        f4.example("json");

        Parameter collections=new Parameter();
        collections.setName("collections");
        collections.in("query");
        collections.description("The collections that should be included in the tile. The parameter value is a list of collection identifiers.");
        collections.setRequired(false);
        collections.setStyle(Parameter.StyleEnum.FORM);
        collections.setExplode(false);
        List<String> collectionsEnum=new ArrayList<String>();
        collectionsEnum.add("aircraft_hangar_s");
        collectionsEnum.add("amusement_park_s");
        collectionsEnum.add("allOtherCollectionIds");
        Schema collectionsArrayItems = new Schema().type("string");
        collectionsArrayItems.setEnum(collectionsEnum);
        Schema collectionsSchema=new ArraySchema().items(collectionsArrayItems);

        collections.setSchema(collectionsSchema);
        collections.setExample("aircraft_hangar_s,amusement_park_s");

        Parameter properties =new Parameter();
        properties.name("properties");
        properties.in("query");
        properties.description("The properties that should be included for each feature. The parameter value is a list of property names.");
        properties.setRequired(false);
        properties.setStyle(Parameter.StyleEnum.FORM);
        properties.setExplode(false);
        Schema propertiesSchema=new ArraySchema().items(new Schema().type("string"));
        properties.setSchema(propertiesSchema);
        properties.setExample("name, function, type");

        /*Add the parameters to definition*/
        openAPI.getComponents().addParameters("f2", f2);
        openAPI.getComponents().addParameters("f3", f3);
        openAPI.getComponents().addParameters("f4", f4);
        openAPI.getComponents().addParameters("tilingSchemeId", tilingSchemeId);
        openAPI.getComponents().addParameters("zoomLevel", zoomLevel);
        openAPI.getComponents().addParameters("row", row);
        openAPI.getComponents().addParameters("column", column);
        openAPI.getComponents().addParameters("collections", collections);
        openAPI.getComponents().addParameters("properties", properties);


        /*specify all new schemas. They are:
         * boundingBox
         * tileMatrix
         * tilingScheme
         * tilingSchemes
         * mvt*/

        List<String> modelRequirements=new LinkedList<String>();
        modelRequirements.add("type");

        Schema boundingBox= new Schema();
        boundingBox.setType("object");
        boundingBox.setRequired(modelRequirements);
        List<String> boundingBoxEnum=new ArrayList<String>();
        boundingBoxEnum.add("BoundingBox");
        boundingBox.addProperties("type",new StringSchema()._enum(boundingBoxEnum));
        boundingBox.addProperties("crs",new Schema().type("string").example("http://www.opengis.net/def/crs/EPSG/0/3857"));
        boundingBox.addProperties("lowerCorner",new Schema().type("string").example("-20037508.3427892 -20037508.342789"));
        boundingBox.addProperties("upperCorner",new Schema().type("string").example("20037508.3427892 20037508.3427892"));


        Schema matrix=new Schema();
        matrix.setType("object");
        matrix.setRequired(modelRequirements);
        List<String> matrixEnum=new ArrayList<String>();
        matrixEnum.add("TileMatrix");
        matrix.addProperties("type",new StringSchema()._enum(matrixEnum));
        matrix.addProperties("identifier",new Schema().type("string").example('0'));
        matrix.addProperties("MatrixHeight", new Schema().minimum(BigDecimal.valueOf(0)).type("integer").example(1));
        matrix.addProperties("MatrixWidth", new Schema().minimum(BigDecimal.valueOf(0)).type("integer").example(1));
        matrix.addProperties("TileHeight", new Schema().minimum(BigDecimal.valueOf(0)).type("integer").example(256));
        matrix.addProperties("TileWidth", new Schema().minimum(BigDecimal.valueOf(0)).type("integer").example(256));
        matrix.addProperties("scaleDenominator", new Schema().type("number").example(559082264.028717));
        matrix.addProperties("topLeftCorner", new Schema().type("string").example("-20037508.3427892 20037508.3427892"));

        List<String> tilingSchemeRequirements=new LinkedList<String>();
        tilingSchemeRequirements.add("type");
        tilingSchemeRequirements.add("identifier");

        Schema tilingScheme =new Schema();
        tilingScheme.setType("object");
        tilingScheme.setRequired(tilingSchemeRequirements);
        Map<String,Schema> tilingSchemeProperties= new HashMap<String,Schema>();
        List<String> tileMatrixSetEnum=new ArrayList<String>();
        tileMatrixSetEnum.add("TileMatrixSet");
        List<String> tilingSchemeSupportedCrsEnum=new ArrayList<String>();
        tilingSchemeSupportedCrsEnum.add("http://www.opengis.net/def/crs/EPSG/0/3857");
        List<String> tilingSchemeWellKnownEnum=new ArrayList<String>();
        tilingSchemeWellKnownEnum.add("http://www.opengis.net/def/wkss/OGC/1.0/GoogleMapsCompatible");
        tilingScheme.addProperties("type",new StringSchema()._enum(tileMatrixSetEnum));
        tilingScheme.addProperties("identifier", new Schema().type("string").example("default"));
        tilingScheme.addProperties("TileMatrix", new ArraySchema().items(new Schema().$ref("#/components/schemas/tileMatrix")));
        tilingScheme.addProperties("boundingBox", new ArraySchema().items(new Schema().$ref("#/components/schemas/boundingBox")));
        tilingScheme.addProperties("supportedCrs",new StringSchema()._enum(tilingSchemeSupportedCrsEnum).example("http://www.opengis.net/def/crs/EPSG/0/3857"));
        tilingScheme.addProperties("title",new Schema().type("string").example("Google Maps Compatible for the World"));
        tilingScheme.addProperties("wellKnownScaleSet",new StringSchema()._enum(tilingSchemeWellKnownEnum).example("http://www.opengis.net/def/wkss/OGC/1.0/GoogleMapsCompatible"));

        Schema tilingSchemes =new Schema();
        tilingSchemes.type("object");
        tilingSchemes.setRequired(modelRequirements);
        HashMap<String,Schema> tilingSchemesProperties=new HashMap<>();
        tilingSchemesProperties.put("tilingSchemes", new ArraySchema().items(new Schema().type("string").example("default")));
        tilingSchemes.setProperties(tilingSchemesProperties);

        Schema mvt=new Schema();
        mvt.type("string");
        mvt.format("binary");

        /*Add the schemas to definition*/
        openAPI.getComponents().addSchemas("tilingSchemes",tilingSchemes);
        openAPI.getComponents().addSchemas("tilingScheme",tilingScheme);
        openAPI.getComponents().addSchemas("tileMatrix",matrix);
        openAPI.getComponents().addSchemas("boundingBox",boundingBox);
        openAPI.getComponents().addSchemas("mvt",mvt);

        /*create a new tag and add it to definition*/
        openAPI.getTags().add(new Tag().name("Tiles").description("Access to data (features), partitioned into a hierarchy of tiles."));






        if (serviceData != null && serviceData.getFeatureProvider().supportsTransactions()) {

            //first new path - TilingSchemes
            openAPI.getPaths().addPathItem("/tilingSchemes",new PathItem().description("something"));  //create a new path
            PathItem pathItem = openAPI.getPaths().get("/tilingSchemes");
            ApiResponse success = new ApiResponse().description("A list of tiling schemes.")
                    .content(new Content()
                            .addMediaType("application/json", new MediaType().schema(new Schema().$ref("#/components/schemas/tilingSchemes")))
                            );
            ApiResponse exception = new ApiResponse().description("An error occured.")
                    .content(new Content()
                            .addMediaType("application/json", new MediaType().schema(new Schema().$ref("#/components/schemas/exception")))
                            );
            if (Objects.nonNull(pathItem)) {
                pathItem
                        .get(new Operation()
                                .addTagsItem("Tiles")
                                .summary("retrieve all available tiling schemes")
                                .operationId("getTilingSchemes")
                                .addParametersItem(new Parameter().$ref("#/components/parameters/f3"))
                                //.requestBody(requestBody)
                                .responses(new ApiResponses()
                                        .addApiResponse("200", success)
                                        .addApiResponse("default", exception))
                        );
            }
            openAPI.getPaths()
                    .addPathItem("/tilingSchemes", pathItem); //save to Path

            //second new path - TilingScheme
            openAPI.getPaths().addPathItem("/tilingSchemes/{tilingSchemeId}",new PathItem().description("something"));
            pathItem = openAPI.getPaths().get("/tilingSchemes/{tilingSchemeId}");
            success = new ApiResponse().description("A tiling scheme.")
                    .content(new Content()
                            .addMediaType("application/json", new MediaType().schema(new Schema().$ref("#/components/schemas/tilingScheme")))
                    );
            exception = new ApiResponse().description("An error occured.")
                    .content(new Content()
                            .addMediaType("application/json", new MediaType().schema(new Schema().$ref("#/components/schemas/exception")))
                    );
            if (Objects.nonNull(pathItem)) {
                pathItem
                        .get(new Operation()
                                .addTagsItem("Tiles")
                                .summary("retrieve a tiling scheme by id")
                                .operationId("getTilingScheme")
                                .addParametersItem(new Parameter().$ref("#/components/parameters/tilingSchemeId"))
                                .addParametersItem(new Parameter().$ref("#/components/parameters/f3"))
                                //.requestBody(requestBody)
                                .responses(new ApiResponses()
                                        .addApiResponse("200", success)
                                        .addApiResponse("default", exception))
                        );
            }
            openAPI.getPaths()
                    .addPathItem("/tilingSchemes/{tilingSchemeId}", pathItem);

            //third new path Tiles - TilingScheme
            openAPI.getPaths().addPathItem("/tiles/{tilingSchemeId}",new PathItem().description("something"));
            pathItem = openAPI.getPaths().get("/tiles/{tilingSchemeId}");
            success= new ApiResponse().description("A tiling scheme used to partition the dataset into tiles.")
                    .content(new Content()
                            .addMediaType("application/json", new MediaType().schema(new Schema().$ref("#/components/schemas/tilingScheme")))
                    );
            exception = new ApiResponse().description("An error occured.")
                    .content(new Content()
                            .addMediaType("application/json", new MediaType().schema(new Schema().$ref("#/components/schemas/exception")))
                    );
            if (Objects.nonNull(pathItem)) {
                pathItem
                        .get(new Operation()
                                .addTagsItem("Tiles")
                                .summary("retrieve a tiling scheme used to partition the dataset into tiles")
                                .operationId("getTilingSchemePartion")
                                .addParametersItem(new Parameter().$ref("#/components/parameters/tilingSchemeId"))
                                .addParametersItem(new Parameter().$ref("#/components/parameters/f3"))
                                //.requestBody(requestBody)
                                .responses(new ApiResponses()
                                        .addApiResponse("200", success)
                                        .addApiResponse("default", exception))
                        );
            }

            openAPI.getPaths()
                    .addPathItem("/tiles/{tilingSchemeId}", pathItem);


            //fourth new path -  All tiles
            openAPI.getPaths().addPathItem("/tiles/{tilingSchemeId}/{zoomLevel}/{row}/{column}",new PathItem().description("something"));
            pathItem = openAPI.getPaths().get("/tiles/{tilingSchemeId}/{zoomLevel}/{row}/{column}");
            success = new ApiResponse().description("A tile of the dataset.")
                    .content(new Content()
                            .addMediaType("application/vnd.mapbox-vector-tile", new MediaType().schema(new Schema().$ref("#/components/schemas/mvt")))
                    );
            exception = new ApiResponse().description("An error occured.")
                    .content(new Content()
                            .addMediaType("application/json", new MediaType().schema(new Schema().$ref("#/components/schemas/exception")))
                    );
            if (Objects.nonNull(pathItem)) {
                pathItem
                        .get(new Operation()
                                .addTagsItem("Tiles")
                                .summary("retrieve a tile of the dataset")
                                .description("The tile in the requested tiling scheme, on the requested zoom level in the tiling scheme, with the requested grid coordinates (row, column) is returned. " +
                                        "Each collection of the dataset is returned as a separate layer. The collections and the feature properties to include in the tile representation can be limited using query" +
                                        " parameters.")
                                .operationId("getTilesDataset")
                                .addParametersItem(new Parameter().$ref("#/components/parameters/tilingSchemeId"))
                                .addParametersItem(new Parameter().$ref("#/components/parameters/zoomLevel"))
                                .addParametersItem(new Parameter().$ref("#/components/parameters/row"))
                                .addParametersItem(new Parameter().$ref("#/components/parameters/column"))
                                .addParametersItem(new Parameter().$ref("#/components/parameters/collections"))
                                .addParametersItem(new Parameter().$ref("#/components/parameters/properties"))
                                .addParametersItem(new Parameter().$ref("#/components/parameters/f4"))
                                //.requestBody(requestBody)
                                .responses(new ApiResponses()
                                        .addApiResponse("200", success)
                                        .addApiResponse("default", exception))
                        );
            }

            openAPI.getPaths()
                    .addPathItem("/tiles/{tilingSchemeId}/{zoomLevel}/{row}/{column}", pathItem);

            //do for every feature type
            serviceData.getFeatureTypes()
                    .values()
                    .stream()
                    .sorted(Comparator.comparing(FeatureTypeConfigurationWfs3::getId))
                    .filter(ft -> serviceData.isFeatureTypeEnabled(ft.getId()))
                    .forEach(ft -> {

                        //fifth new path Tiles - TilingScheme (for every collection)
                        openAPI.getPaths().addPathItem("/collections/"+ ft.getId()+"/tiles/{tilingSchemeId}",new PathItem().description("something"));
                       PathItem pathItem2 = openAPI.getPaths().get("/collections/"+ ft.getId()+"/tiles/{tilingSchemeId}");
                        ApiResponse success2 = new ApiResponse().description("A tiling scheme used to partition the collection"+ft.getLabel()+" into tiles.")
                                .content(new Content()
                                        .addMediaType("application/json", new MediaType().schema(new Schema().$ref("#/components/schemas/tilingScheme")))
                                );
                        ApiResponse exception2 = new ApiResponse().description("An error occured.")
                                .content(new Content()
                                        .addMediaType("application/json", new MediaType().schema(new Schema().$ref("#/components/schemas/exception")))
                                );
                        if (Objects.nonNull(pathItem2)) {
                            pathItem2
                                    .get(new Operation()
                                            .addTagsItem("Tiles")
                                            .summary("retrieve a tiling scheme used to partition the collection" +ft.getLabel()+" into tiles")
                                            .operationId("getTilingSchemeCollection")
                                            .addParametersItem(new Parameter().$ref("#/components/parameters/tilingSchemeId"))
                                            .addParametersItem(new Parameter().$ref("#/components/parameters/f3"))
                                            //.requestBody(requestBody)
                                            .responses(new ApiResponses()
                                                    .addApiResponse("200", success2)
                                                    .addApiResponse("default", exception2))
                                    );
                        }

                        openAPI.getPaths()
                                .addPathItem("/collections/"+ ft.getId()+"/tiles/{tilingSchemeId}", pathItem2);


                        //sixth new path - All tiles (for every collection)
                        openAPI.getPaths().addPathItem("/collections/"+ ft.getId()+"/tiles/{tilingSchemeId}/{zoomLevel}/{row}/{column}",new PathItem().description("something"));
                        pathItem2 = openAPI.getPaths().get("/collections/"+ ft.getId()+"/tiles/{tilingSchemeId}/{zoomLevel}/{row}/{column}");
                        success2 = new ApiResponse().description("A tile of the collection "+ ft.getLabel()+".")
                                .content(new Content()
                                        .addMediaType("application/geo+json",new MediaType().schema(new Schema().$ref("#/components/schemas/featureCollectionGeoJSON")))
                                        .addMediaType("application/vnd.mapbox-vector-tile", new MediaType().schema(new Schema().$ref("#/components/schemas/mvt")))
                                );
                        exception2 = new ApiResponse().description("An error occured.")
                                .content(new Content()
                                        .addMediaType("application/json", new MediaType().schema(new Schema().$ref("#/components/schemas/exception")))
                                );
                        if (Objects.nonNull(pathItem2)) {
                            pathItem2
                                    .get(new Operation()
                                            .addTagsItem("Tiles")
                                            .summary("retrieve a tile of the collection " + ft.getLabel())
                                            .description("The tile in the requested tiling scheme, on the requested zoom level in the tiling scheme, with the requested grid coordinates (row, column) is returned. " +
                                                    "The tile has a single layer with all selected features in the bounding box of the tile. The feature properties to " +
                                                    "include in the tile representation can be limited using a query parameter.")
                                            .operationId("getTilesCollection")
                                            .addParametersItem(new Parameter().$ref("#/components/parameters/tilingSchemeId"))
                                            .addParametersItem(new Parameter().$ref("#/components/parameters/zoomLevel"))
                                            .addParametersItem(new Parameter().$ref("#/components/parameters/row"))
                                            .addParametersItem(new Parameter().$ref("#/components/parameters/column"))
                                            .addParametersItem(new Parameter().$ref("#/components/parameters/properties"))

                                            .addParametersItem(new Parameter().$ref("#/components/parameters/f2"))
                                            //.requestBody(requestBody)
                                            .responses(new ApiResponses()
                                                    .addApiResponse("200", success2)
                                                    .addApiResponse("default", exception2))
                                    );
                        }
                        openAPI.getPaths()
                                .addPathItem("/collections/"+ ft.getId()+"/tiles/{tilingSchemeId}/{zoomLevel}/{row}/{column}", pathItem2);
                    });
        }
        return openAPI;
    }
}