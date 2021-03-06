/**
 * Copyright 2020 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.ogcapi.tiles;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import de.ii.ldproxy.ogcapi.domain.ApiRequestContext;
import de.ii.ldproxy.ogcapi.domain.ContentExtension;
import de.ii.ldproxy.ogcapi.domain.ExtensionConfiguration;
import de.ii.ldproxy.ogcapi.domain.ExtensionRegistry;
import de.ii.ldproxy.ogcapi.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ldproxy.ogcapi.domain.ImmutableRequestContext;
import de.ii.ldproxy.ogcapi.domain.OgcApi;
import de.ii.ldproxy.ogcapi.domain.OgcApiBackgroundTask;
import de.ii.ldproxy.ogcapi.domain.OgcApiDataV2;
import de.ii.ldproxy.ogcapi.domain.URICustomizer;
import de.ii.ldproxy.ogcapi.features.core.domain.FeaturesCoreConfiguration;
import de.ii.ldproxy.ogcapi.features.core.domain.FeaturesCoreProviders;
import de.ii.ldproxy.ogcapi.tiles.tileMatrixSet.TileMatrixSet;
import de.ii.ldproxy.ogcapi.tiles.tileMatrixSet.TileMatrixSetLimits;
import de.ii.ldproxy.ogcapi.tiles.tileMatrixSet.TileMatrixSetLimitsGenerator;
import de.ii.xtraplatform.crs.domain.CrsTransformerFactory;
import de.ii.xtraplatform.dropwizard.domain.XtraPlatform;
import de.ii.xtraplatform.feature.transformer.api.FeatureTypeConfiguration;
import de.ii.xtraplatform.features.domain.FeatureProvider2;
import de.ii.xtraplatform.features.domain.FeatureQuery;
import de.ii.xtraplatform.features.domain.ImmutableFeatureQuery;
import de.ii.xtraplatform.services.domain.TaskContext;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Context;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * This class is responsible for a automatic generation of the Tiles.
 * The range is specified in the config.
 * The automatic generation is executed, when the server is started/restarted.
 */
@Component
@Provides
@Instantiate
public class VectorTileSeeding implements OgcApiBackgroundTask {

    private static final Logger LOGGER = LoggerFactory.getLogger(VectorTileSeeding.class);

    private final CrsTransformerFactory crsTransformerFactory;
    private final ExtensionRegistry extensionRegistry;
    private final TileMatrixSetLimitsGenerator limitsGenerator;
    private final TilesCache tilesCache;
    private final XtraPlatform xtraPlatform;
    private final FeaturesCoreProviders providers;
    private final TilesQueriesHandler queryHandler;

    public VectorTileSeeding(@Requires CrsTransformerFactory crsTransformerFactory,
                             @Requires ExtensionRegistry extensionRegistry,
                             @Requires TileMatrixSetLimitsGenerator limitsGenerator,
                             @Requires TilesCache tilesCache,
                             @Requires XtraPlatform xtraPlatform,
                             @Requires FeaturesCoreProviders providers,
                             @Requires TilesQueriesHandler queryHandler,
                             @Context BundleContext bundleContext) {
        this.crsTransformerFactory = crsTransformerFactory;
        this.extensionRegistry = extensionRegistry;
        this.limitsGenerator = limitsGenerator;
        this.tilesCache = tilesCache;
        this.xtraPlatform = xtraPlatform;
        this.providers = providers;
        this.queryHandler = queryHandler;
    }

    @Override
    public boolean isEnabledForApi(OgcApiDataV2 apiData) {
        // currently no vector tiles support for WFS backends
        if (providers.getFeatureProvider(apiData)
                     .getData()
                     .getFeatureProviderType()
                     .equals("WFS")) {
            return false;
        }

        // no formats available
        if (extensionRegistry.getExtensionsForType(TileFormatExtension.class)
                             .isEmpty()) {
            return false;
        }

        return apiData.getExtension(TilesConfiguration.class)
                      .filter(TilesConfiguration::isEnabled)
                      .filter(config -> Objects.nonNull(config.getSeeding()) && !config.getSeeding()
                                                                                       .isEmpty())
                      .isPresent();
    }

    @Override
    public Class<? extends ExtensionConfiguration> getBuildingBlockConfigurationType() {
        return TilesConfiguration.class;
    }

    @Override
    public Class<OgcApi> getServiceType() {
        return OgcApi.class;
    }

    @Override
    public String getLabel() {
        return "Tile cache seeding";
    }

    @Override
    public boolean runOnStart(OgcApi api) {
        return isEnabledForApi(api.getData());
    }

    /**
     * Run the seeding
     *
     * @param api
     * @param taskContext
     */
    @Override
    public void run(OgcApi api, TaskContext taskContext) {
        List<TileFormatExtension> outputFormats = extensionRegistry.getExtensionsForType(TileFormatExtension.class);

        try {
            // first seed the multi-layer tiles, which also generates the necessary single-layer tiles
            if (!taskContext.isStopped())
                seedMultiLayerTiles(api, outputFormats, taskContext);

            // add any additional single-layer tiles
            if (!taskContext.isStopped())
                seedSingleLayerTiles(api, outputFormats, taskContext);

        } catch (IOException e) {
            if (!taskContext.isStopped()) {
                throw new RuntimeException("Error accessing the tile cache during seeding.", e);
            }
        } catch (Throwable e) {
            // in general, this should only happen on shutdown (as we cannot influence shutdown order, exceptions
            // during seeding on shutdown are currently inevitable), but for other situations we still add the error
            // to the log
            if (!taskContext.isStopped()) {
                throw new RuntimeException("An error occurred during seeding. Note that this may be a side-effect of a server shutdown.", e);
            }
        }
    }

    private void seedSingleLayerTiles(OgcApi api, List<TileFormatExtension> outputFormats, TaskContext taskContext) throws IOException {
        OgcApiDataV2 apiData = api.getData();
        FeatureProvider2 featureProvider = providers.getFeatureProvider(apiData);
        Map<String, Map<String, MinMax>> seedingMap = getSeedingConfig(apiData);

        long numberOfTiles = getNumberOfTiles2(api, outputFormats, seedingMap);
        final double[] currentTile = {0.0};

        walkCollectionsAndTiles(api, outputFormats, seedingMap, (api1, collectionId, outputFormat, tileMatrixSet, level, row, col) -> {
            TilesConfiguration tilesConfiguration = getTilesConfiguration(apiData, collectionId).get();
            Tile tile = new ImmutableTile.Builder()
                    .collectionIds(ImmutableList.of(collectionId))
                    .tileMatrixSet(tileMatrixSet)
                    .tileLevel(level)
                    .tileRow(row)
                    .tileCol(col)
                    .api(api)
                    .temporary(false)
                    .featureProvider(featureProvider)
                    .outputFormat(outputFormat)
                    .build();
            Path tileFile = tilesCache.getFile(tile);
            if (Files.exists(tileFile)) {
                // already there, nothing to create, but advance progress
                currentTile[0] += 1;
                return true;
            }

            URI uri;
            String uriString = String.format("%s/%s/collections/%s/tiles/%s/%s/%s/%s", xtraPlatform.getServicesUri(), apiData.getId(), collectionId, tileMatrixSet.getId(), level, row, col);
            try {
                uri = new URI(uriString);
            } catch (URISyntaxException e) {
                LOGGER.error("Stopping seeding. Invalid request URI during seeding: " + uriString);
                return false;
            }

            URICustomizer uriCustomizer = new URICustomizer(uri);
            ApiRequestContext requestContext = new ImmutableRequestContext.Builder()
                    .api(api)
                    .requestUri(uri)
                    .mediaType(outputFormat.getMediaType())
                    .build();

            FeatureQuery query = outputFormat.getQuery(tile, ImmutableList.of(), ImmutableMap.of(), tilesConfiguration, uriCustomizer);

            FeaturesCoreConfiguration coreConfiguration = apiData.getExtension(FeaturesCoreConfiguration.class)
                                                                 .get();

            TilesQueriesHandler.QueryInputTileSingleLayer queryInput = new ImmutableQueryInputTileSingleLayer.Builder()
                    .tile(tile)
                    .query(query)
                    .outputStream(new ByteArrayOutputStream())
                    .defaultCrs(coreConfiguration.getDefaultEpsgCrs())
                    .build();


            taskContext.setStatusMessage(String.format("currently processing -> %s, %s/%s/%s/%s, %s", collectionId, tileMatrixSet.getId(), level, row, col, outputFormat.getExtension()));

            queryHandler.handle(TilesQueriesHandler.Query.SINGLE_LAYER_TILE, queryInput, requestContext);

            currentTile[0] += 1;
            taskContext.setCompleteness(currentTile[0] / numberOfTiles);

            return !taskContext.isStopped();

        });
    }

    private void seedMultiLayerTiles(OgcApi api, List<TileFormatExtension> outputFormats, TaskContext taskContext) throws IOException {
        OgcApiDataV2 apiData = api.getData();
        FeatureProvider2 featureProvider = providers.getFeatureProvider(apiData);
        Map<String, MinMax> multiLayerTilesSeeding = ImmutableMap.of();
        Optional<TilesConfiguration> tilesConfiguration = apiData.getExtension(TilesConfiguration.class).filter(TilesConfiguration::getMultiCollectionEnabled);

        if (tilesConfiguration.isPresent()) {
            Map<String, MinMax> seedingConfig = tilesConfiguration.get()
                                                                  .getEffectiveSeeding();
            if (seedingConfig != null && !seedingConfig.isEmpty())
                multiLayerTilesSeeding = seedingConfig;
        }

        List<TileFormatExtension> multiLayerFormats = outputFormats.stream()
                                                                   .filter(TileFormatExtension::canMultiLayer)
                                                                   .collect(Collectors.toList());

        long numberOfTiles = getNumberOfTiles(api, multiLayerFormats, multiLayerTilesSeeding);
        final double[] currentTile = {0.0};

        walkTiles(api, "multi-layer", multiLayerFormats, multiLayerTilesSeeding, (api1, layerName, outputFormat, tileMatrixSet, level, row, col) -> {
            List<String> collectionIds = apiData.getCollections()
                                                .values()
                                                .stream()
                                                .filter(collection -> apiData.isCollectionEnabled(collection.getId()))
                                                .filter(collection -> {
                                                    Optional<TilesConfiguration> layerConfiguration = collection.getExtension(TilesConfiguration.class);
                                                    if (!layerConfiguration.isPresent() || !layerConfiguration.get().isEnabled() || !layerConfiguration.get().getMultiCollectionEnabled())
                                                        return false;
                                                    MinMax levels = layerConfiguration.get().getZoomLevels().get(tileMatrixSet.getId());
                                                    if (Objects.nonNull(levels) && (levels.getMax() < level || levels.getMin() > level))
                                                        return false;

                                                    return true;
                                                })
                                                .map(FeatureTypeConfiguration::getId)
                                                .collect(Collectors.toList());

            if (collectionIds.isEmpty()) {
                // nothing to generate, still advance progress
                currentTile[0] += 1;
                return true;
            }

            Tile multiLayerTile = new ImmutableTile.Builder()
                    .collectionIds(collectionIds)
                    .tileMatrixSet(tileMatrixSet)
                    .tileLevel(level)
                    .tileRow(row)
                    .tileCol(col)
                    .api(api)
                    .temporary(false)
                    .featureProvider(featureProvider)
                    .outputFormat(outputFormat)
                    .build();
            Path tileFile = tilesCache.getFile(multiLayerTile);
            if (Files.exists(tileFile)) {
                // already there, nothing to create, but still count for progress
                currentTile[0] += 1;
                return true;
            }

            URI uri;
            String uriString = String.format("%s/%s/tiles/%s/%s/%s/%s", xtraPlatform.getServicesUri(), apiData.getId(), tileMatrixSet.getId(), level, row, col);
            try {
                uri = new URI(uriString);
            } catch (URISyntaxException e) {
                LOGGER.error("Stopping seeding. Invalid request URI during seeding: " + uriString);
                return false;
            }

            URICustomizer uriCustomizer = new URICustomizer(uri);
            ApiRequestContext requestContext = new ImmutableRequestContext.Builder()
                    .api(api)
                    .requestUri(uri)
                    .mediaType(outputFormat.getMediaType())
                    .build();

            Map<String, Tile> singleLayerTileMap = collectionIds.stream()
                                                                .collect(ImmutableMap.toImmutableMap(collectionId -> collectionId, collectionId -> new ImmutableTile.Builder()
                                                                        .from(multiLayerTile)
                                                                        .collectionIds(ImmutableList.of(collectionId))
                                                                        .build()));

            Map<String, FeatureQuery> queryMap = collectionIds.stream()
                                                              .collect(ImmutableMap.toImmutableMap(collectionId -> collectionId, collectionId -> {
                                                                  String featureTypeId = apiData.getCollections()
                                                                                                .get(collectionId)
                                                                                                .getExtension(FeaturesCoreConfiguration.class)
                                                                                                .map(cfg -> cfg.getFeatureType()
                                                                                                               .orElse(collectionId))
                                                                                                .orElse(collectionId);
                                                                  TilesConfiguration layerConfiguration = apiData.getCollections()
                                                                                                                 .get(collectionId)
                                                                                                                 .getExtension(TilesConfiguration.class)
                                                                                                                 .orElse(tilesConfiguration.get());
                                                                  FeatureQuery query = outputFormat.getQuery(singleLayerTileMap.get(collectionId), ImmutableList.of(), ImmutableMap.of(), layerConfiguration, requestContext.getUriCustomizer());
                                                                  return ImmutableFeatureQuery.builder()
                                                                                              .from(query)
                                                                                              .type(featureTypeId)
                                                                                              .build();
                                                              }));

            FeaturesCoreConfiguration coreConfiguration = apiData.getExtension(FeaturesCoreConfiguration.class)
                                                                 .get();

            TilesQueriesHandler.QueryInputTileMultiLayer queryInput = new ImmutableQueryInputTileMultiLayer.Builder()
                    .tile(multiLayerTile)
                    .singleLayerTileMap(singleLayerTileMap)
                    .queryMap(queryMap)
                    .outputStream(new ByteArrayOutputStream())
                    .defaultCrs(coreConfiguration.getDefaultEpsgCrs())
                    .build();


            taskContext.setStatusMessage(String.format("currently processing -> %s, %s/%s/%s/%s, %s", layerName, tileMatrixSet.getId(), level, row, col, outputFormat.getExtension()));

            queryHandler.handle(TilesQueriesHandler.Query.MULTI_LAYER_TILE, queryInput, requestContext);

            currentTile[0] += 1;
            taskContext.setCompleteness(currentTile[0] / numberOfTiles);

            return !taskContext.isStopped();
        });
    }

    /**
     * checks if the tiles extension is available and returns a Map with entrys for each collection and their zoomLevel or seeding
     *
     * @param apiData the service data of the Wfs3 Service
     * @return a map with all collectionIds and the seeding configuration
     */
    private Map<String, Map<String, MinMax>> getSeedingConfig(OgcApiDataV2 apiData) {
        Map<String, Map<String, MinMax>> minMaxMap = new HashMap<>();

        for (FeatureTypeConfigurationOgcApi featureType : apiData.getCollections()
                                                                 .values()) {
            final Optional<TilesConfiguration> tilesConfiguration = featureType.getExtension(TilesConfiguration.class);

            if (tilesConfiguration.isPresent()) {
                Map<String, MinMax> seedingConfig = tilesConfiguration.get()
                                                                      .getEffectiveSeeding();
                if (seedingConfig != null && !seedingConfig.isEmpty())
                    minMaxMap.put(featureType.getId(), seedingConfig);
            }
        }
        return minMaxMap;
    }

    private long getNumberOfTiles2(OgcApi api, List<TileFormatExtension> outputFormats, Map<String, Map<String, MinMax>> seeding) {
        final long[] numberOfTiles = {0};

        try {
            walkCollectionsAndTiles(api, outputFormats, seeding, (ignore1, collectionId, ignore2, ignore3, ignore4, ignore5, ignore6) -> {
                numberOfTiles[0]++;
                return true;
            });
        } catch (IOException e) {
            //ignore
        }

        return numberOfTiles[0];
    }

    private long getNumberOfTiles(OgcApi api, List<TileFormatExtension> outputFormats, Map<String, MinMax> seeding) {
        final long[] numberOfTiles = {0};

        try {
            walkTiles(api, "", outputFormats, seeding, (ignore1, collectionId, ignore2, ignore3, ignore4, ignore5, ignore6) -> {
                numberOfTiles[0]++;
                return true;
            });
        } catch (IOException e) {
            //ignore
        }

        return numberOfTiles[0];
    }

    interface TileWalker {
        boolean visit(OgcApi api, String collectionId, TileFormatExtension outputFormat, TileMatrixSet tileMatrixSet, int level, int row, int col) throws IOException;
    }

    private void walkCollectionsAndTiles(OgcApi api, List<TileFormatExtension> outputFormats, Map<String, Map<String, MinMax>> seeding, TileWalker tileWalker) throws IOException {
        for (Map.Entry<String, Map<String, MinMax>> entry : seeding.entrySet()) {
            String collectionId = entry.getKey();
            Map<String, MinMax> seedingConfig = entry.getValue();
            Optional<TilesConfiguration> tilesConfiguration = getTilesConfiguration(api.getData(),collectionId);
            if (tilesConfiguration.isPresent()) {
                walkTiles(api, collectionId, outputFormats, seedingConfig, tileWalker);
            }
        }
    }

    private void walkTiles(OgcApi api, String collectionId, List<TileFormatExtension> outputFormats, Map<String, MinMax> seeding, TileWalker tileWalker) throws IOException {
        for (TileFormatExtension outputFormat : outputFormats) {
            for (Map.Entry<String, MinMax> entry : seeding.entrySet()) {
                TileMatrixSet tileMatrixSet = getTileMatrixSetById(entry.getKey());
                MinMax zoomLevels = entry.getValue();
                List<TileMatrixSetLimits> allLimits = limitsGenerator.getTileMatrixSetLimits(api.getData(), tileMatrixSet, zoomLevels, crsTransformerFactory);

                for (TileMatrixSetLimits limits : allLimits) {
                    int level = Integer.parseInt(limits.getTileMatrix());

                    for (int row = limits.getMinTileRow(); row <= limits.getMaxTileRow(); row++) {
                        for (int col = limits.getMinTileCol(); col <= limits.getMaxTileCol(); col++) {
                            boolean shouldContinue = tileWalker.visit(api, collectionId, outputFormat, tileMatrixSet, level, row, col);
                            if (!shouldContinue) {
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    private Optional<TilesConfiguration> getTilesConfiguration(OgcApiDataV2 apiData, String collectionId) {
        return Optional.ofNullable(apiData.getCollections()
                                          .get(collectionId))
                       .flatMap(featureType -> featureType.getExtension(TilesConfiguration.class))
                       .filter(TilesConfiguration::isEnabled);
    }

    private TileMatrixSet getTileMatrixSetById(String tileMatrixSetId) {
        TileMatrixSet tileMatrixSet = null;
        for (ContentExtension contentExtension : extensionRegistry.getExtensionsForType(ContentExtension.class)) {
            if (contentExtension instanceof TileMatrixSet && ((TileMatrixSet) contentExtension).getId()
                                                                                               .equals(tileMatrixSetId)) {
                tileMatrixSet = (TileMatrixSet) contentExtension;
                break;
            }
        }

        return tileMatrixSet;
    }
}
