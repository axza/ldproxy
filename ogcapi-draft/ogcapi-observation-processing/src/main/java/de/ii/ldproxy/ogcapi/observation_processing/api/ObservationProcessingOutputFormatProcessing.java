/**
 * Copyright 2020 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.ogcapi.observation_processing.api;

import de.ii.ldproxy.ogcapi.domain.FormatExtension;
import de.ii.ldproxy.ogcapi.domain.OgcApiApi;
import de.ii.ldproxy.ogcapi.domain.OgcApiRequestContext;
import de.ii.ldproxy.ogcapi.feature_processing.api.FeatureProcessChain;
import de.ii.ldproxy.ogcapi.feature_processing.api.Processing;
import de.ii.ldproxy.ogcapi.observation_processing.application.ObservationProcessingConfiguration;
import de.ii.ldproxy.ogcapi.observation_processing.application.Variables;

import javax.ws.rs.core.Response;
import java.util.List;

public interface ObservationProcessingOutputFormatProcessing extends FormatExtension {

    default String getPathPattern() {
        String DAPA_PATH_ELEMENT = "dapa";
        return "^/collections/[\\w\\-]+/"+ DAPA_PATH_ELEMENT+"/?$";
    }

    Response getResponse(Processing processList, String collectionId, OgcApiApi api, OgcApiRequestContext requestContext);
}
