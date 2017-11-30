/**
 * Copyright 2017 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.output.geojson;

import de.ii.ogc.wfs.proxy.TargetMapping;
import de.ii.ogc.wfs.proxy.WfsProxyFeatureTypeAnalyzer.GML_TYPE;

/**
 * @author zahnen
 */
public interface GeoJsonMapping extends TargetMapping {
    GEO_JSON_TYPE getType();

    enum GEO_JSON_TYPE {

        ID(GML_TYPE.ID),
        STRING(GML_TYPE.STRING, GML_TYPE.DATE, GML_TYPE.DATE_TIME, GML_TYPE.URI),
        NUMBER(GML_TYPE.INT, GML_TYPE.INTEGER, GML_TYPE.LONG, GML_TYPE.SHORT, GML_TYPE.DECIMAL, GML_TYPE.DOUBLE, GML_TYPE.FLOAT),
        GEOMETRY(),
        NONE(GML_TYPE.NONE);

        private GML_TYPE[] gmlTypes;

        GEO_JSON_TYPE(GML_TYPE... gmlType) {
            this.gmlTypes = gmlType;
        }

        public static GEO_JSON_TYPE forGmlType(GML_TYPE gmlType) {
            for (GEO_JSON_TYPE geoJsonType : GEO_JSON_TYPE.values()) {
                for (GML_TYPE v2: geoJsonType.gmlTypes) {
                    if (v2 == gmlType) {
                        return geoJsonType;
                    }
                }
            }

            return NONE;
        }

        public boolean isValid() {
            return this != NONE;
        }
    }
}
