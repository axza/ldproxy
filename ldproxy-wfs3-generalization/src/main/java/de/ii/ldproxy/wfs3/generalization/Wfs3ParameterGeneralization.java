package de.ii.ldproxy.wfs3.generalization;

import de.ii.ldproxy.wfs3.api.FeatureTypeConfigurationWfs3;
import de.ii.ldproxy.wfs3.api.Wfs3ParameterExtension;
import de.ii.xtraplatform.crs.api.EpsgCrs;
import de.ii.xtraplatform.feature.query.api.ImmutableFeatureQuery;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;

import java.util.Map;
import java.util.Objects;

import static de.ii.ldproxy.wfs3.api.Wfs3ServiceData.DEFAULT_CRS_URI;

/**
 * @author zahnen
 */
@Component
@Provides
@Instantiate
public class Wfs3ParameterGeneralization implements Wfs3ParameterExtension {

    @Override
    public ImmutableFeatureQuery.Builder transformQuery(FeatureTypeConfigurationWfs3 featureTypeConfigurationWfs3, ImmutableFeatureQuery.Builder queryBuilder, Map<String, String> parameters) {

        if (parameters.containsKey("maxAllowableOffset")) {
            try {
                queryBuilder.maxAllowableOffset(Double.valueOf(parameters.get("maxAllowableOffset")));
            } catch (NumberFormatException e) {
                //ignore
            }
        }

        return queryBuilder;
    }
}
