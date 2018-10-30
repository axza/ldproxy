/**
 * Copyright 2018 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
/*

package de.ii.ldproxy.wfs3.vt;

import de.ii.ldproxy.wfs3.Wfs3MediaTypes;
import de.ii.ldproxy.wfs3.Wfs3Service;
import de.ii.ldproxy.wfs3.api.*;
import de.ii.ldproxy.wfs3.vt.VectorTilesCache;
import de.ii.ldproxy.wfs3.vt.Wfs3EndpointTiles;
import de.ii.xtraplatform.crs.api.BoundingBox;
import de.ii.xtraplatform.crs.api.CrsTransformation;
import de.ii.xtraplatform.crs.api.CrsTransformationException;
import de.ii.xtraplatform.feature.transformer.api.TransformingFeatureProvider;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.osgi.framework.BundleContext;
import org.slf4j.LoggerFactory;

import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import javax.xml.crypto.dsig.Transform;
import java.io.File;
import java.io.FileNotFoundException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import static de.ii.ldproxy.wfs3.api.Wfs3ServiceData.DEFAULT_CRS;
import static de.ii.xtraplatform.runtime.FelixRuntime.DATA_DIR_KEY;

@Component
@Provides
@Instantiate
public class VectorTileSeeding implements Wfs3StartupTask {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Wfs3EndpointTiles.class);
    private final VectorTilesCache cache;



    @Requires
    private CrsTransformation crsTransformation;

    public VectorTileSeeding(@org.apache.felix.ipojo.annotations.Context BundleContext bundleContext){
        String dataDirectory = bundleContext.getProperty(DATA_DIR_KEY);
        cache = new VectorTilesCache(dataDirectory);
    }
    @Override
    public Runnable getTask(Wfs3ServiceData wfs3ServiceData, TransformingFeatureProvider featureProvider) {


        Runnable startSeeding=()->{

            //service, bundleContext, crsTransformation
            Set<String> collectionIdsDataset = Wfs3EndpointTiles.getCollectionIdsDataset(wfs3ServiceData);
            boolean tilesEnabled=true;
            boolean seedingEnabled=true;

            for (String collectionId : collectionIdsDataset) {
                tilesEnabled=true;
                seedingEnabled=true;
                try{
                    wfs3ServiceData.getFeatureTypes().get(collectionId).getTiles();
                }catch(NullPointerException e) {
                    tilesEnabled = false;
                }
                if(tilesEnabled) {
                    try {
                        wfs3ServiceData.getFeatureTypes().get(collectionId).getTiles().getSeeding();
                    } catch (NullPointerException e) {
                        seedingEnabled = false;
                    }
                }

                if(tilesEnabled && seedingEnabled) {
                    Set<String> tilingSchemeIdsCollection=null;
                    try{
                        tilingSchemeIdsCollection = wfs3ServiceData.getFeatureTypes().get(collectionId).getTiles().getSeeding().keySet();
                    }catch(NullPointerException e){
                    }
                    for (String tilingSchemeId : tilingSchemeIdsCollection) {
                        try {
                            LOGGER.debug("Seeding - Service: " + wfs3ServiceData.getId() + " Collection: " + collectionId + " TilingScheme: " + tilingSchemeId);
                            seeding(wfs3ServiceData, collectionId, tilingSchemeId, cache, crsTransformation, featureProvider);
                        } catch (Exception e) {
                        }
                    }
                }
            }
            try {
                boolean tilesDatasetEnabled=true;
                boolean seedingDatasetEnabled=true;


                Optional<FeatureTypeConfigurationWfs3> firstTiles = wfs3ServiceData
                        .getFeatureTypes()
                        .values()
                        .stream()
                        .filter(ft -> { try{ return ft.getTiles().getEnabled(); } catch (IllegalStateException ignored){return false;} })
                        .findFirst();

                if(!firstTiles.isPresent())
                    tilesDatasetEnabled = false;

                if(tilesDatasetEnabled) {
                    Optional<FeatureTypeConfigurationWfs3> firstSeeding = wfs3ServiceData
                            .getFeatureTypes()
                            .values()
                            .stream()
                            .filter(ft -> {
                                try {
                                    ft.getTiles().getSeeding();
                                    return true;
                                } catch (IllegalStateException ignored) {
                                    return false;
                                }
                            })
                            .findFirst();
                    if(!firstSeeding.isPresent())
                        seedingDatasetEnabled= false;
                }


                if(tilesDatasetEnabled && seedingDatasetEnabled) {
                    seedingDataset(wfs3ServiceData.getId(), collectionIdsDataset, wfs3ServiceData, crsTransformation, cache, featureProvider);
                }
            } catch (FileNotFoundException e) { e.printStackTrace(); }
        };
        new Thread(startSeeding).start();
        return startSeeding;
    }

    public static void seeding(Wfs3ServiceData serviceData, String collectionId, String tilingSchemeId, VectorTilesCache cache, CrsTransformation crsTransformation,TransformingFeatureProvider featureProvider) throws CrsTransformationException, NullPointerException {
        int maxZoom = 0;
        int minZoom = 0;
        double xMin = 0;
        double xMax = 0;
        double yMin = 0;
        double yMax = 0;
        int rowMin=0;
        int rowMax=0;
        int colMin=0;
        int colMax=0;
        boolean enabled = false;
        List<String> formats = null;
        Map<String, FeatureTypeTiles.MinMax> seeding = null;
        BoundingBox spatial =null;
        try {
            enabled = serviceData.getFeatureTypes().get(collectionId).getTiles().getEnabled();
            formats = serviceData.getFeatureTypes().get(collectionId).getTiles().getFormats();
            seeding = serviceData.getFeatureTypes().get(collectionId).getTiles().getSeeding();
            spatial = serviceData.getFeatureTypes().get(collectionId).getExtent().getSpatial();
        }catch (Exception e){}

        //only do seeding if tiles enabled and mvt supported there are values for it
        if (enabled && (formats.contains("application/vnd.mapbox-vector-tile") || formats.size() == 0 ) && seeding !=null && spatial!= null) {
            maxZoom = seeding.get(tilingSchemeId).getMax();
            minZoom = seeding.get(tilingSchemeId).getMin();
            xMin =  spatial.getXmin();
            xMax = spatial.getXmax();
            yMin = spatial.getYmin();
            yMax = spatial.getYmax();
            for(int z =minZoom; z<=maxZoom;z++) {
                Map<String,Integer>minMax=computeMinMax(z,new DefaultTilingScheme(),crsTransformation,xMin,xMax,yMin,yMax);
                rowMin=minMax.get("rowMin");
                rowMax=minMax.get("rowMax");
                colMin=minMax.get("colMin");
                colMax=minMax.get("colMax");
                for (int x = rowMin; x <= rowMax; x++) {
                    for (int y = colMin; y <= colMax; y++) {
                        generateSeedingMVT(serviceData, collectionId,tilingSchemeId,z,x,y,cache,crsTransformation,featureProvider);
                    }
                }
            }
        }
    }

    private static void generateSeedingMVT(Wfs3ServiceData serviceData, String collectionId, String tilingSchemeId, int z, int x, int y, VectorTilesCache cache, CrsTransformation crsTransformation, TransformingFeatureProvider featureProvider){

        try {
            LOGGER.debug("seeding - ZoomLevel: " + Integer.toString(z) + " row: " + Integer.toString(x) +" col: " + Integer.toString(y));
            VectorTile tile = new VectorTile(collectionId, tilingSchemeId, Integer.toString(z), Integer.toString(x), Integer.toString(y), serviceData, false, cache,featureProvider);
            File tileFileMvt = tile.getFile(cache, "pbf");
            if (!tileFileMvt.exists()) {
                File tileFileJson=generateSeedingJSON(serviceData, collectionId,tilingSchemeId,z,x,y,cache,crsTransformation,featureProvider);
                Map<String, File> layers = new HashMap<>();
                layers.put(collectionId, tileFileJson);
                boolean success = tile.generateTileMvt(tileFileMvt, layers, null, crsTransformation);
                if (!success) {
                    String msg = "Internal server error: could not generate protocol buffers for a tile.";
                    LOGGER.error(msg);
                    throw new InternalServerErrorException(msg);
                }
            }
        }catch (FileNotFoundException e){
            e.printStackTrace();
        }
    }

    private static File generateSeedingJSON(Wfs3ServiceData serviceData, String collectionId, String tilingSchemeId, int z, int x, int y, VectorTilesCache cache, CrsTransformation crsTransformation,TransformingFeatureProvider featureProvider){
        try{
            VectorTile tile = new VectorTile(collectionId, tilingSchemeId, Integer.toString(z), Integer.toString(x), Integer.toString(y), serviceData, false, cache,featureProvider);
            File tileFileJson = tile.getFile(cache, "json");

            String prefix = "https://services.interactive-instruments.de/vtp/"; //TODO dynamic URI
            String uriString = prefix + "/" + serviceData.getId() + "/" + "collections" + "/"
                    + collectionId + "/tiles/" + tilingSchemeId + "/" + z +"/" + y + "/" + x;

            URI uri= null;
            try {
                uri = new URI(uriString);
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
            URICustomizer uriCustomizer  = new URICustomizer(uri);

            Wfs3MediaType mediaType;
            mediaType = ImmutableWfs3MediaType.builder()
                    .main(new MediaType("application", "json"))
                    .label("JSON")
                    .build();
            if (!tileFileJson.exists()) {
                tile.generateTileJson(tileFileJson, crsTransformation,null, null,null,uriCustomizer,mediaType,true);
            }
            return tileFileJson;
        }catch (FileNotFoundException e){
            e.printStackTrace();
        }
        return null;
    }
    public static Map<String, Integer> computeMinMax(int zoomLevel, TilingScheme tilingScheme, CrsTransformation crsTransformation, double xMin, double xMax, double yMin, double yMax) throws CrsTransformationException {
        int row=0;
        int col=0;
        Map<String, Integer> minMax=new HashMap<>();
        double getXMax=crsTransformation.getTransformer(tilingScheme.getBoundingBox(zoomLevel,col,row).getEpsgCrs(),DEFAULT_CRS).transformBoundingBox(tilingScheme.getBoundingBox(zoomLevel,col,row)).getXmin();
        double getXMin=crsTransformation.getTransformer(tilingScheme.getBoundingBox(zoomLevel,col,row).getEpsgCrs(),DEFAULT_CRS).transformBoundingBox(tilingScheme.getBoundingBox(zoomLevel,col,row)).getXmax();
        double getYMin=crsTransformation.getTransformer(tilingScheme.getBoundingBox(zoomLevel,col,row).getEpsgCrs(),DEFAULT_CRS).transformBoundingBox(tilingScheme.getBoundingBox(zoomLevel,col,row)).getYmin();
        double getYMax=crsTransformation.getTransformer(tilingScheme.getBoundingBox(zoomLevel,col,row).getEpsgCrs(),DEFAULT_CRS).transformBoundingBox(tilingScheme.getBoundingBox(zoomLevel,col,row)).getYmax();

        while(getXMin<xMin){
            col++;
            getXMin=crsTransformation.getTransformer(tilingScheme.getBoundingBox(zoomLevel,col,row).getEpsgCrs(),DEFAULT_CRS).transformBoundingBox(tilingScheme.getBoundingBox(zoomLevel,col,row)).getXmin();
        }
        minMax.put("colMin",col);

        getXMax=getXMin;
        while(getXMax<xMax){
            col++;
            getXMax=crsTransformation.getTransformer(tilingScheme.getBoundingBox(zoomLevel,col,row).getEpsgCrs(),DEFAULT_CRS).transformBoundingBox(tilingScheme.getBoundingBox(zoomLevel,col,row)).getXmax();
        }

        minMax.put("colMax",col);

        while(getYMax>yMax){
            row++;
            getYMax=crsTransformation.getTransformer(tilingScheme.getBoundingBox(zoomLevel,col,row).getEpsgCrs(),DEFAULT_CRS).transformBoundingBox(tilingScheme.getBoundingBox(zoomLevel,col,row)).getYmax();
        }
        minMax.put("rowMin",row);

        getYMin=getYMax;
        while(getYMin>yMin){
            row++;
            getYMin=crsTransformation.getTransformer(tilingScheme.getBoundingBox(zoomLevel,col,row).getEpsgCrs(),DEFAULT_CRS).transformBoundingBox(tilingScheme.getBoundingBox(zoomLevel,col,row)).getYmin();
        }
        minMax.put("rowMax",row);

        return minMax;
    }
    public static void seedingDataset(String datasetId, Set<String>collectionIdsDataset, Wfs3ServiceData wfs3ServiceData,CrsTransformation crsTransformation,VectorTilesCache cache, TransformingFeatureProvider featureProvider) throws FileNotFoundException {
        //TODO Max/MinZOOM / spatial for datatset in config?
        List<Double> xMinList=new ArrayList<>();
        List<Double> xMaxList=new ArrayList<>();
        List<Double> yMinList=new ArrayList<>();
        List<Double> yMaxList=new ArrayList<>();
        List<Integer> minZoomList=new ArrayList<>();
        List<Integer> maxZoomList=new ArrayList<>();
        Set<String> tilingSchemeIdsCollection=null;

        for (String collectionId : collectionIdsDataset) {
            tilingSchemeIdsCollection = wfs3ServiceData.getFeatureTypes().get(collectionId).getTiles().getSeeding().keySet();
            for (String tilingSchemeId : tilingSchemeIdsCollection) {
                try {
                    Map<String, FeatureTypeTiles.MinMax> seeding = wfs3ServiceData.getFeatureTypes().get(collectionId).getTiles().getSeeding();
                    BoundingBox spatial = wfs3ServiceData.getFeatureTypes().get(collectionId).getExtent().getSpatial();

                    if(spatial==null){

                    }
                    //only do seeding if there are values for it
                    if (seeding.size() != 0 && spatial!= null) {
                        int maxZoom = seeding.get(tilingSchemeId).getMax();
                        int minZoom = seeding.get(tilingSchemeId).getMin();
                        double xMin = spatial.getXmin();
                        double xMax = spatial.getXmax();
                        double yMin = spatial.getYmin();
                        double yMax = spatial.getYmax();

                        maxZoomList.add(maxZoom);
                        minZoomList.add(minZoom);
                        if(xMin!=-180)
                            xMinList.add(xMin);
                        if(xMax!=180)
                            xMaxList.add(xMax);
                        if(yMin!=-90)
                            yMinList.add(yMin);
                        if(yMax!=90)
                            yMaxList.add(yMax);
                    }
                } catch (Exception e) { }
            }
        }
        int minZoomDataset=minZoomList.stream().min(Comparator.comparing(Integer::intValue)).orElseThrow(NoSuchElementException::new);
        int maxZoomDataset=maxZoomList.stream().max(Comparator.comparing(Integer::intValue)).orElseThrow(NoSuchElementException::new);
        double xMinDataset=xMinList.stream().min(Comparator.comparing(Double::doubleValue)).orElseThrow(NoSuchElementException::new);
        double xMaxDataset=xMaxList.stream().max(Comparator.comparing(Double::doubleValue)).orElseThrow(NoSuchElementException::new);
        double yMinDataset=yMinList.stream().min(Comparator.comparing(Double::doubleValue)).orElseThrow(NoSuchElementException::new);
        double yMaxDataset=yMaxList.stream().max(Comparator.comparing(Double::doubleValue)).orElseThrow(NoSuchElementException::new);

        for(int z =minZoomDataset; z<=maxZoomDataset;z++) {

            Map<String,Integer>minMax= null;
            try {
                minMax = computeMinMax(z,new DefaultTilingScheme(),crsTransformation,xMinDataset,xMaxDataset,yMinDataset,yMaxDataset);
            } catch (CrsTransformationException e) {
                e.printStackTrace();
            }

            int rowMin=minMax.get("rowMin");
            int rowMax=minMax.get("rowMax");
            int colMin=minMax.get("colMin");
            int colMax=minMax.get("colMax");

            for (int x = rowMin; x <= rowMax; x++) {
                for (int y = colMin; y <= colMax; y++) {
                    for(String tilingSchemeId:tilingSchemeIdsCollection) {
                        VectorTile tile = new VectorTile(null, tilingSchemeId, Integer.toString(z), Integer.toString(x), Integer.toString(y), wfs3ServiceData, false, cache, featureProvider);

                        // generate tile
                        File tileFileMvt = tile.getFile(cache, "pbf");
                        if (!tileFileMvt.exists()) {

                            Map<String, File> layers = new HashMap<String, File>(); //TODO
                            Set<String> collectionIds = Wfs3EndpointTiles.getCollectionIdsDataset(wfs3ServiceData);


                            for (String collectionId : collectionIds) {
                                // include only the requested layers / collections
                                File tileFileJson = generateSeedingJSON(wfs3ServiceData, collectionId,tilingSchemeId,z,x,y,cache,crsTransformation,featureProvider);
                                layers.put(collectionId, tileFileJson);
                            }
                            boolean success = tile.generateTileMvt(tileFileMvt, layers, null, crsTransformation);
                            if (!success) {
                                String msg = "Internal server error: could not generate protocol buffers for a tile.";
                                LOGGER.error(msg);
                                throw new InternalServerErrorException(msg);
                            }
                        }
                    }
                }
            }
        }
    }
}
*/