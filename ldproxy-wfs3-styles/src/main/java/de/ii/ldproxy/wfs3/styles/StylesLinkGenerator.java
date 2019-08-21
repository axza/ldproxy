/**
 * Copyright 2019 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.wfs3.styles;

import com.google.common.collect.ImmutableList;
import de.ii.ldproxy.ogcapi.domain.ImmutableWfs3Link;
import de.ii.ldproxy.ogcapi.domain.URICustomizer;
import de.ii.ldproxy.ogcapi.domain.Wfs3Link;

import java.util.List;

/**
 * This class is responsible for generating the links to the styles.
 */
public class StylesLinkGenerator {


    /**
     * generates the Links on the page /serviceId?f=json and /serviceId/collections?f=json
     *
     * @param uriBuilder the URI, split in host, path and query
     * @return a List with links
     */
    public List<Wfs3Link> generateDatasetLinks(URICustomizer uriBuilder) {

        return ImmutableList.<Wfs3Link>builder()
                .add(new ImmutableWfs3Link.Builder()
                        .href(uriBuilder.copy()
                                        .removeLastPathSegment("collections")
                                        .ensureLastPathSegment("styles")
                                        .ensureParameter("f", "json")
                                        .toString())
                        .rel("styles")
                        .type("application/json")
                        .description("the list of available styles")
                        .build())
                .build();
    }

    /**
     * generates one link of a style on the page /serviceId/styles
     *
     * @param uriBuilder the URI, split in host, path and query
     * @param styleId    the ids of the styles
     * @return a list with links
     */
    public List<Wfs3Link> generateStylesLinksDataset(URICustomizer uriBuilder, String styleId) {

        return ImmutableList.<Wfs3Link>builder()
                .add(new ImmutableWfs3Link.Builder()
                        .href(uriBuilder.copy()
                                        .ensureLastPathSegment(styleId)
                                        .setParameter("f", "json")
                                        .toString()
                        )
                        .rel("style")
                        .type("application/json")
                        .build())
                .build();
    }

    /**
     * generates the collections styles link at /serviceid/collections and /servideid/collections/{collectionId}
     *
     * @param uriBuilder   the URI, split in host, path and query
     * @param collectionId the id of the collection the style is part of
     * @param styleId      the ids of the styles
     * @return a list with links
     */
    public List<Wfs3Link> generateStylesLinksCollectionMetadata(URICustomizer uriBuilder, String collectionId,
                                                                String styleId) {
        String link = "";
        if (!uriBuilder.copy()
                       .isLastPathSegment(collectionId)) {
            link = uriBuilder.copy()
                             .ensureLastPathSegments("collections", collectionId, "styles", styleId)
                             .setParameter("f", "json")
                             .toString();
        } else {
            link = uriBuilder.copy()
                             .ensureLastPathSegments("styles", styleId)
                             .setParameter("f", "json")
                             .toString();
        }

        return ImmutableList.<Wfs3Link>builder()
                .add(new ImmutableWfs3Link.Builder()
                        .href(link)
                        .rel("style")
                        .type("application/json")
                        .build())
                .build();
    }


    /**
     * generates one link of a style on the page /serviceId/collections/{collectionId}/styles
     *
     * @param uriBuilder the URI, split in host, path and query
     * @param styleId    the ids of the styles
     * @return a list with links
     */
    public List<Wfs3Link> generateStylesLinksCollection(URICustomizer uriBuilder, String styleId) {

        return ImmutableList.<Wfs3Link>builder()
                .add(new ImmutableWfs3Link.Builder()
                        .href(uriBuilder.copy()
                                        .ensureLastPathSegment(styleId)
                                        .setParameter("f", "json")
                                        .toString()
                        )
                        .rel("style")
                        .type("application/json")
                        .build())
                .build();
    }
}
