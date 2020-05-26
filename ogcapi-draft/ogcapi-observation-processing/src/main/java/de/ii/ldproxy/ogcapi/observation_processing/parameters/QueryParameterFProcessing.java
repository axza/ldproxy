package de.ii.ldproxy.ogcapi.observation_processing.parameters;

import de.ii.ldproxy.ogcapi.domain.*;
import de.ii.ldproxy.ogcapi.feature_processing.api.FeatureProcessInfo;
import de.ii.ldproxy.ogcapi.observation_processing.api.ObservationProcessingOutputFormatProcessing;
import de.ii.ldproxy.ogcapi.observation_processing.api.ObservationProcessingOutputFormatVariables;
import de.ii.ldproxy.ogcapi.observation_processing.application.ObservationProcessingConfiguration;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;

import java.util.ArrayList;
import java.util.List;

@Component
@Provides
@Instantiate
public class QueryParameterFProcessing extends QueryParameterF {

    private static final String DAPA_PATH_ELEMENT = "dapa";

    protected QueryParameterFProcessing(@Requires OgcApiExtensionRegistry extensionRegistry) {
        super(extensionRegistry);
    }

    @Override
    public String getId() {
        return "fProcessing";
    }

    @Override
    public boolean isApplicable(OgcApiApiDataV2 apiData, String definitionPath, OgcApiContext.HttpMethods method) {
        return super.isApplicable(apiData, definitionPath, method) &&
                definitionPath.equals("/collections/{collectionId}/"+DAPA_PATH_ELEMENT);
    }

    @Override
    protected Class<? extends FormatExtension> getFormatClass() {
        return ObservationProcessingOutputFormatProcessing.class;
    }

    @Override
    public boolean isEnabledForApi(OgcApiApiDataV2 apiData) {
        return isExtensionEnabled(apiData, ObservationProcessingConfiguration.class);
    }

}
