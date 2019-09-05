/**
 * Copyright 2019 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.wfs3.oas30;

import de.ii.ldproxy.ogcapi.domain.ConformanceClass;
import de.ii.ldproxy.ogcapi.domain.OgcApiDatasetData;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@Provides
@Instantiate
public class Wfs3EndpointOpenApi implements ConformanceClass {

    private static Logger LOGGER = LoggerFactory.getLogger(Wfs3EndpointOpenApi.class);

    @Override
    public String getConformanceClass() {
        return "http://www.opengis.net/spec/ogcapi-features-1/1.0/conf/oas30";
    }

    @Override
    public boolean isEnabledForApi(OgcApiDatasetData apiData) {
        return isExtensionEnabled(apiData, Oas30Configuration.class);
    }
}
