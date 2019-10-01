/**
 * Copyright 2019 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.wfs3.vt;

import de.ii.ldproxy.ogcapi.domain.*;
import de.ii.ldproxy.target.geojson.Wfs3OutputFormatGeoJson;
import de.ii.ldproxy.wfs3.api.Wfs3FeatureFormatExtension;
import de.ii.xtraplatform.crs.api.*;
import de.ii.xtraplatform.feature.transformer.api.TransformingFeatureProvider;
import de.ii.xtraplatform.server.CoreServerConfig;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.osgi.framework.BundleContext;
import org.slf4j.LoggerFactory;

import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.core.MediaType;
import java.io.File;
import java.io.FileNotFoundException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import static de.ii.xtraplatform.runtime.FelixRuntime.DATA_DIR_KEY;

/**
 * This class is responsible for a automatic generation of the Tiles.
 * The range is specified in the config.
 * The automatic generation is executed, when the server is started/restarted.
 */
@Component
@Provides
@Instantiate
public class VectorTileSeeding implements OgcApiStartupTask {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(VectorTileSeeding.class);
    private final VectorTilesCache cache;
    private Thread t = null;
    private Map<Thread, String> threadMap = new HashMap<>();


    @Requires
    private CrsTransformation crsTransformation;

    @Requires
    private CoreServerConfig coreServerConfig;

    @Requires
    private OgcApiExtensionRegistry extensionRegistry;

    private final VectorTileMapGenerator vectorTileMapGenerator = new VectorTileMapGenerator();

    public VectorTileSeeding(@org.apache.felix.ipojo.annotations.Context BundleContext bundleContext) {
        String dataDirectory = bundleContext.getProperty(DATA_DIR_KEY);
        cache = new VectorTilesCache(dataDirectory);
    }

    @Override
    public boolean isEnabledForApi(OgcApiDatasetData apiData) {
        return isExtensionEnabled(apiData, TilesConfiguration.class);
    }


    /**
     * The runnable Task which starts the seeding.
     *
     * @param service               the Wfs3 Service
     * @param featureProvider   the featureProvider
     * @return the runnable process
     */
    @Override
    public Runnable getTask(OgcApiDataset service, TransformingFeatureProvider featureProvider) {

        Optional<Wfs3FeatureFormatExtension> wfs3OutputFormatGeoJson = getOutputFormatForType(Wfs3OutputFormatGeoJson.MEDIA_TYPE);
        OgcApiDatasetData apiData = service.getData();

        if (!wfs3OutputFormatGeoJson.isPresent()) {
            return () -> {
            };
        }


        Runnable startSeeding = () -> {

            Set<String> collectionIdsDataset = Wfs3EndpointTiles.getCollectionIdsDataset(vectorTileMapGenerator.getAllCollectionIdsWithTileExtension(apiData), vectorTileMapGenerator.getEnabledMap(apiData),
                    vectorTileMapGenerator.getFormatsMap(apiData), vectorTileMapGenerator.getMinMaxMap(apiData, true), false, false, true);
            try {
                boolean tilesDatasetEnabled = false;
                boolean seedingDatasetEnabled = false;

                if (!collectionIdsDataset.isEmpty())
                    tilesDatasetEnabled = true;


                if (tilesDatasetEnabled) {
                    for (String collectionId : collectionIdsDataset) {
                        if (isExtensionEnabled(apiData, apiData.getFeatureTypes()
                                                                       .get(collectionId), TilesConfiguration.class)) {

                            final TilesConfiguration tilesConfiguration = getExtensionConfiguration(apiData, apiData.getFeatureTypes()
                                                                                                                            .get(collectionId), TilesConfiguration.class).get();

                            Map<String, TilesConfiguration.MinMax> seedingCollection = tilesConfiguration.getSeeding();

                            if (seedingCollection != null) {
                                seedingDatasetEnabled = true;
                                break;
                            }
                        }


                    }
                }


                if (tilesDatasetEnabled && seedingDatasetEnabled) {
                    seedingDataset(collectionIdsDataset, service, crsTransformation, cache, featureProvider, coreServerConfig, wfs3OutputFormatGeoJson.get());
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }


        };
        t = new Thread(startSeeding);
        t.setDaemon(true);
        t.start();
        threadMap.put(t, apiData.getId());
        return startSeeding;

    }

    /**
     * @return a Map with all ongoing threads
     */
    public Map<Thread, String> getThreadMap() {
        return threadMap;
    }

    /**
     * removes a specific thread from the threadMap.
     *
     * @param t the thread which should be removed
     */
    public void removeThreadMapEntry(Thread t) {
        threadMap.remove(t);
    }

    /**
     * Computes the minimum and maximum row and col values.
     * <p>
     * Generates all JSON Tiles and MVT (if the format is enabled) for each collection (MVT support has to be enabled)
     * for every tiling Scheme in the specified seeding range
     *
     * @param collectionIdsDataset all ids of feature Types which have the tiles support and seeding enabled
     * @param service              the Wfs3 service
     * @param crsTransformation    the coordinate reference system transformation object to transform coordinates
     * @param cache                the vector tile cache
     * @param featureProvider      the feature Provider
     * @param coreServerConfig     the core server config with the external url
     * @throws FileNotFoundException
     */
    private void seedingDataset(Set<String> collectionIdsDataset, OgcApiDataset service,
                                CrsTransformation crsTransformation, VectorTilesCache cache,
                                TransformingFeatureProvider featureProvider, CoreServerConfig coreServerConfig,
                                Wfs3FeatureFormatExtension wfs3OutputFormatGeoJson) throws FileNotFoundException {

        /*Computation of the minimum and maximum values for x and y from the minimum/maximum spatial extent
         * TODO: Maybe a spatial extent for the whole dataset in the config?*/
        List<Double> xMinList = new ArrayList<>();
        List<Double> xMaxList = new ArrayList<>();
        List<Double> yMinList = new ArrayList<>();
        List<Double> yMaxList = new ArrayList<>();
        List<Integer> minZoomList = new ArrayList<>();
        List<Integer> maxZoomList = new ArrayList<>();
        Set<String> tilingSchemeIdsCollection = null;
        OgcApiDatasetData datasetData = service.getData();
        Map<String, Map<String, TilesConfiguration.MinMax>> seedingMap = vectorTileMapGenerator.getMinMaxMap(datasetData, true);
        for (String collectionId : collectionIdsDataset) {

            if (!Objects.isNull(seedingMap) && seedingMap.containsKey(collectionId)) {


                Map<String, TilesConfiguration.MinMax> seeding = seedingMap.get(collectionId);


                tilingSchemeIdsCollection = seeding.keySet();

                for (String tilingSchemeId : tilingSchemeIdsCollection) {
                    try {
                        BoundingBox spatial = datasetData.getFeatureTypes()
                                                         .get(collectionId)
                                                         .getExtent()
                                                         .getSpatial();
                        if (spatial == null) {
                        }
                        if (seeding.size() != 0 && spatial != null) {
                            int maxZoom = seeding.get(tilingSchemeId)
                                                 .getMax();
                            int minZoom = seeding.get(tilingSchemeId)
                                                 .getMin();
                            double xMin = spatial.getXmin();
                            double xMax = spatial.getXmax();
                            double yMin = spatial.getYmin();
                            double yMax = spatial.getYmax();
                            maxZoomList.add(maxZoom);
                            minZoomList.add(minZoom);
                            if (xMin != -180)
                                xMinList.add(xMin);
                            if (xMax != 180)
                                xMaxList.add(xMax);
                            if (yMin != -90)
                                yMinList.add(yMin);
                            if (yMax != 90)
                                yMaxList.add(yMax);
                        }
                    } catch (Exception e) {
                    }
                }
            }
        }
        int minZoomDataset = minZoomList.stream()
                                        .min(Comparator.comparing(Integer::intValue))
                                        .orElseThrow(NoSuchElementException::new);
        int maxZoomDataset = maxZoomList.stream()
                                        .max(Comparator.comparing(Integer::intValue))
                                        .orElseThrow(NoSuchElementException::new);
        double xMinDataset = xMinList.stream()
                                     .min(Comparator.comparing(Double::doubleValue))
                                     .orElseThrow(NoSuchElementException::new);
        double xMaxDataset = xMaxList.stream()
                                     .max(Comparator.comparing(Double::doubleValue))
                                     .orElseThrow(NoSuchElementException::new);
        double yMinDataset = yMinList.stream()
                                     .min(Comparator.comparing(Double::doubleValue))
                                     .orElseThrow(NoSuchElementException::new);
        double yMaxDataset = yMaxList.stream()
                                     .max(Comparator.comparing(Double::doubleValue))
                                     .orElseThrow(NoSuchElementException::new);
        /*Comupation end*/

        /*Begin seeding*/
        for (int z = minZoomDataset; z <= maxZoomDataset; z++) {

            Map<String, Integer> minMax = null;
            try {
                minMax = computeMinMax(z, new DefaultTileMatrixSet(), crsTransformation, xMinDataset, xMaxDataset, yMinDataset, yMaxDataset, OgcApiDatasetData.DEFAULT_CRS);
            } catch (CrsTransformationException e) {
                e.printStackTrace();
            }

            int rowMin = minMax.get("rowMin");
            int rowMax = minMax.get("rowMax");
            int colMin = minMax.get("colMin");
            int colMax = minMax.get("colMax");

            for (int x = rowMin; x <= rowMax; x++) {
                for (int y = colMin; y <= colMax; y++) {
                    for (String tilingSchemeId : tilingSchemeIdsCollection) {

                        VectorTile tile = new VectorTile(null, tilingSchemeId, Integer.toString(z), Integer.toString(x), Integer.toString(y), service, false, cache, featureProvider, wfs3OutputFormatGeoJson);

                        // generate tile
                        File tileFileMvt = tile.getFile(cache, "pbf");
                        if (!tileFileMvt.exists()) {

                            Map<String, File> layers = new HashMap<String, File>();

                            Set<String> collectionIdsMVTEnabled = Wfs3EndpointTiles.getCollectionIdsDataset(vectorTileMapGenerator.getAllCollectionIdsWithTileExtension(datasetData), vectorTileMapGenerator.getEnabledMap(datasetData),
                                    vectorTileMapGenerator.getFormatsMap(datasetData), vectorTileMapGenerator.getMinMaxMap(datasetData, true), true, false, false);


                            for (String collectionId : collectionIdsMVTEnabled) {
                                Map<String, TilesConfiguration.MinMax> seeding = seedingMap.get(collectionId);
                                if (!Objects.isNull(seeding)) {
                                    int collectionMax = seeding.get(tilingSchemeId)
                                                               .getMax();
                                    int collectionMin = seeding.get(tilingSchemeId)
                                                               .getMin();
                                    if (collectionMin <= z && z <= collectionMax) {
                                        File tileFileMvtCollection = generateMVT(service, collectionId, tilingSchemeId, z, x, y, cache, crsTransformation, featureProvider, coreServerConfig, wfs3OutputFormatGeoJson);
                                        layers.put(collectionId, tileFileMvtCollection);
                                    }
                                }
                            }

                            boolean success = TileGeneratorMvt.generateTileMvt(tileFileMvt, layers, null, crsTransformation, tile);
                            if (!success) {
                                String msg = "Internal server error: could not generate protocol buffers for a tile.";
                                LOGGER.error(msg);
                                throw new InternalServerErrorException(msg);
                            }

                            Set<String> collectionIdsOnlyJSON = Wfs3EndpointTiles.getCollectionIdsDataset(vectorTileMapGenerator.getAllCollectionIdsWithTileExtension(datasetData), vectorTileMapGenerator.getEnabledMap(datasetData),
                                    vectorTileMapGenerator.getFormatsMap(datasetData), vectorTileMapGenerator.getMinMaxMap(datasetData, true), false, true, false);

                            for (String collectionId : collectionIdsOnlyJSON) {
                                Map<String, TilesConfiguration.MinMax> seeding = seedingMap.get(collectionId);
                                if (!Objects.isNull(seeding)) {
                                    int collectionMax = seeding.get(tilingSchemeId)
                                                               .getMax();
                                    int collectionMin = seeding.get(tilingSchemeId)
                                                               .getMin();
                                    if (collectionMin <= z && z <= collectionMax) {
                                        generateJSON(service, collectionId, tilingSchemeId, z, x, y, cache, crsTransformation, featureProvider, coreServerConfig, wfs3OutputFormatGeoJson);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * generates the MVT for the specified parameters
     *
     * @param service           the service data of the Wfs3 Service
     * @param collectionId      the id of the collection of the tile
     * @param tilingSchemeId    the id of the tiling scheme of the tile
     * @param z                 the zoom level of the tile
     * @param x                 the row of the tile
     * @param y                 the col of the tile
     * @param cache             the vector tile cache
     * @param crsTransformation the coordinate reference system transformation object to transform coordinates
     * @param featureProvider   the feature Provider
     * @param coreServerConfig  the core server config with the external url
     * @return the Json File. If the mvt already exists, return null
     */
    private File generateMVT(OgcApiDataset service, String collectionId, String tilingSchemeId, int z, int x,
                             int y, VectorTilesCache cache, CrsTransformation crsTransformation,
                             TransformingFeatureProvider featureProvider, CoreServerConfig coreServerConfig,
                             Wfs3FeatureFormatExtension wfs3OutputFormatGeoJson) {

        try {
            LOGGER.debug("seeding - ZoomLevel: " + Integer.toString(z) + " row: " + Integer.toString(x) + " col: " + Integer.toString(y));
            VectorTile tile = new VectorTile(collectionId, tilingSchemeId, Integer.toString(z), Integer.toString(x), Integer.toString(y), service, false, cache, featureProvider, wfs3OutputFormatGeoJson);
            File tileFileMvt = tile.getFile(cache, "pbf");
            if (!tileFileMvt.exists()) {
                File tileFileJson = generateJSON(service, collectionId, tilingSchemeId, z, x, y, cache, crsTransformation, featureProvider, coreServerConfig, wfs3OutputFormatGeoJson);
                Map<String, File> layers = new HashMap<>();
                layers.put(collectionId, tileFileJson);
                boolean success = TileGeneratorMvt.generateTileMvt(tileFileMvt, layers, null, crsTransformation, tile);
                if (!success) {
                    String msg = "Internal server error: could not generate protocol buffers for a tile.";
                    LOGGER.error(msg);
                    throw new InternalServerErrorException(msg);
                }
                return tileFileJson;
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * generates the JSON Tile for the specified parameters
     *
     * @param service           the service data of the Wfs3 Service
     * @param collectionId      the id of the collection of the tile
     * @param tilingSchemeId    the id of the tiling scheme of the tile
     * @param z                 the zoom level of the tile
     * @param x                 the row of the tile
     * @param y                 the col of the tile
     * @param cache             the vector tile cache
     * @param crsTransformation the coordinate reference system transformation object to transform coordinates
     * @param featureProvider   the feature Provider
     * @param coreServerConfig  the core server config with the external url
     * @return the json File, if it already exists return null
     */
    private File generateJSON(OgcApiDataset service, String collectionId, String tilingSchemeId, int z, int x,
                              int y, VectorTilesCache cache, CrsTransformation crsTransformation,
                              TransformingFeatureProvider featureProvider, CoreServerConfig coreServerConfig,
                              Wfs3FeatureFormatExtension wfs3OutputFormatGeoJson) {

        try {
            VectorTile tile = new VectorTile(collectionId, tilingSchemeId, Integer.toString(z), Integer.toString(x), Integer.toString(y), service, false, cache, featureProvider, wfs3OutputFormatGeoJson);
            File tileFileJson = tile.getFile(cache, "json");

            if (!tileFileJson.exists()) {
                String prefix = coreServerConfig.getExternalUrl();
                String uriString = prefix + "/" + service.getData().getId() + "/" + "collections" + "/"
                        + collectionId + "/tiles/" + tilingSchemeId + "/" + z + "/" + y + "/" + x;

                URI uri = null;
                try {
                    uri = new URI(uriString);
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }
                URICustomizer uriCustomizer = new URICustomizer(uri);

                OgcApiMediaType mediaType;
                mediaType = new ImmutableOgcApiMediaType.Builder()
                        .type(new MediaType("application", "json"))
                        .label("JSON")
                        .build();
                TileGeneratorJson.generateTileJson(tileFileJson, crsTransformation, null, null, null, uriCustomizer, mediaType, true, tile);
            }
            return tileFileJson;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * compute the min and max row/col for the zoom level with the given spatial extent.
     *
     * @param zoomLevel         the zoom level you want to compute the values for
     * @param tileMatrixSet      the id of the tiling Scheme
     * @param crsTransformation the coordinate reference system transformation object to transform coordinates
     * @param xMin              the x coordinate of a the lower left corner of a bounding box, including the whole dataset you want to use the seeding for
     * @param xMax              the x coordinate of a the lower upper right corner of a bounding box, including the whole dataset you want to use the seeding for
     * @param yMin              the y coordinate of a the lower left corner of a bounding box, including the whole dataset you want to use the seeding for
     * @param yMax              the y coordinate of a the lower upper right corner of a bounding box, including the whole dataset you want to use the seeding for
     * @return a map with minimum and maximum values for row and col
     * @throws CrsTransformationException
     */
    public static Map<String, Integer> computeMinMax(int zoomLevel, TileMatrixSet tileMatrixSet,
                                                     CrsTransformation crsTransformation, double xMin, double xMax,
                                                     double yMin, double yMax,
                                                     EpsgCrs targetCrs) throws CrsTransformationException {
        int row = 0;
        int col = 0;
        Map<String, Integer> minMax = new HashMap<>();
        double getXMax;
        double getXMin = getBoundingBoxCornerValue(crsTransformation, tileMatrixSet, zoomLevel, col, row, true, false, targetCrs);
        double getYMin;
        double getYMax = getBoundingBoxCornerValue(crsTransformation, tileMatrixSet, zoomLevel, col, row, false, false, targetCrs);

        while (getXMin < xMin) {
            col++;
            getXMin = getBoundingBoxCornerValue(crsTransformation, tileMatrixSet, zoomLevel, col, row, true, true, targetCrs);
        }
        //leads to a "buffer" around the desired area, so a a little bit more tiles are created than needed, col mustn't be -1
        if (col != 0)
            col--;
        minMax.put("colMin", col);

        getXMax = getXMin;
        while (getXMax < xMax) {
            col++;
            getXMax = getBoundingBoxCornerValue(crsTransformation, tileMatrixSet, zoomLevel, col, row, true, false, targetCrs);
        }
        //at the edge case of maximum col number for the zoom level the col is not valid anymore
        if (col == Math.pow(2, zoomLevel)) {
            col--;
        }
        minMax.put("colMax", col);

        while (getYMax > yMax) {
            row++;
            getYMax = getBoundingBoxCornerValue(crsTransformation, tileMatrixSet, zoomLevel, col, row, false, false, targetCrs);
        }
        //leads to a "buffer" around the desired area, so a a little bit more tiles are created than needed, row mustn't be -1

        if (row != 0)
            row--;
        minMax.put("rowMin", row);

        getYMin = getYMax;
        while (getYMin > yMin) {
            row++;
            getYMin = getBoundingBoxCornerValue(crsTransformation, tileMatrixSet, zoomLevel, col, row, false, true, targetCrs);
        }
        //at the edge case of maximum row number for the zoom level the col is not valid anymore
        if (row == Math.pow(2, zoomLevel)) {
            row--;
        }

        minMax.put("rowMax", row);

        return minMax;
    }

    /**
     * Method to convert a specific tile into a bounding box in a desired CRS
     *
     * @param crsTransformation the coordinate reference system transformation object to transform coordinates
     * @param tileMatrixSet      the id of the tiling Scheme of the tile you want to convert
     * @param zoomLevel         the zoom level of the tile you want to convert
     * @param col               the zoom col of the tile you want to convert
     * @param row               the zoom row of the tile you want to convert
     * @param x                 x value or y value
     * @param min               min is bottom left corner, max is upper right corner
     * @param targetCrs         the desired crs the values should be in
     * @return a x or y value of a bounding box corner in the desired CRS
     * @throws CrsTransformationException
     */
    public static double getBoundingBoxCornerValue(CrsTransformation crsTransformation, TileMatrixSet tileMatrixSet,
                                                   int zoomLevel, int col, int row, boolean x, boolean min,
                                                   EpsgCrs targetCrs) throws CrsTransformationException {
        double cornerValue = 0;

        if (crsTransformation == null || targetCrs.equals(tileMatrixSet.getBoundingBox(zoomLevel, col, row)
                                                                      .getEpsgCrs())) {
            if (x && min)
                cornerValue = tileMatrixSet.getBoundingBox(zoomLevel, col, row)
                                          .getXmin();
            if (x && !min)
                cornerValue = tileMatrixSet.getBoundingBox(zoomLevel, col, row)
                                          .getXmax();
            if (!x && min)
                cornerValue = tileMatrixSet.getBoundingBox(zoomLevel, col, row)
                                          .getYmin();
            if (!x && !min)
                cornerValue = tileMatrixSet.getBoundingBox(zoomLevel, col, row)
                                          .getYmax();

        } else {
            CrsTransformer crsTransformer = crsTransformation.getTransformer(tileMatrixSet.getBoundingBox(zoomLevel, col, row)
                                                                                         .getEpsgCrs(), targetCrs);
            BoundingBox bbox = tileMatrixSet.getBoundingBox(zoomLevel, col, row);
            BoundingBox boundingBox = crsTransformer.transformBoundingBox(bbox);

            if (x && min)
                cornerValue = boundingBox.getXmin();
            if (x && !min)
                cornerValue = boundingBox.getXmax();
            if (!x && min)
                cornerValue = boundingBox.getYmin();
            if (!x && !min)
                cornerValue = boundingBox.getYmax();
        }


        return cornerValue;
    }


    private Optional<Wfs3FeatureFormatExtension> getOutputFormatForType(OgcApiMediaType mediaType) {
        return extensionRegistry.getExtensionsForType(Wfs3FeatureFormatExtension.class)
                                .stream()
                                .filter(wfs3OutputFormatExtension -> wfs3OutputFormatExtension.getMediaType()
                                                                                              .equals(mediaType))
                                .findFirst();
    }
}
