package de.ii.ldproxy.wfs3.sitemaps;

import de.ii.xtraplatform.feature.query.api.SimpleFeatureGeometry;
import de.ii.xtraplatform.feature.query.api.TargetMapping;
import de.ii.xtraplatform.feature.transformer.api.FeatureTransformer;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

/**
 * @author zahnen
 */
public class ItemSitesReader implements FeatureTransformer {

    private final String baseUrl;
    private List<Site> sites;

    public ItemSitesReader(String baseUrl) {
        this.baseUrl = baseUrl;
        this.sites = new ArrayList<>();
    }

    public List<Site> getSites() {
        return sites;
    }

    @Override
    public String getTargetFormat() {
        return null;
    }

    @Override
    public void onStart(OptionalLong optionalLong, OptionalLong optionalLong1) throws Exception {

    }

    @Override
    public void onEnd() throws Exception {

    }

    @Override
    public void onFeatureStart(TargetMapping targetMapping) throws Exception {

    }

    @Override
    public void onFeatureEnd() throws Exception {

    }

    @Override
    public void onPropertyStart(TargetMapping targetMapping, List<Integer> list) throws Exception {

    }

    @Override
    public void onPropertyText(String id) throws Exception {
        this.sites.add(new Site(baseUrl + "/" + id));
    }

    @Override
    public void onPropertyEnd() throws Exception {

    }

    @Override
    public void onGeometryStart(TargetMapping targetMapping, SimpleFeatureGeometry simpleFeatureGeometry, Integer integer) throws Exception {

    }

    @Override
    public void onGeometryNestedStart() throws Exception {

    }

    @Override
    public void onGeometryCoordinates(String s) throws Exception {

    }

    @Override
    public void onGeometryNestedEnd() throws Exception {

    }

    @Override
    public void onGeometryEnd() throws Exception {

    }
}
