/**
 * Copyright 2019 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.ogcapi.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import javax.ws.rs.core.Link;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;


@Value.Immutable
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonDeserialize(as = ImmutableOgcApiLink.class)
@XmlType(propOrder={"rel","type","title","href","hreflang","length","templated"})
public abstract class OgcApiLink {

    @Nullable
    @XmlAttribute
    public abstract String getRel();

    @Nullable
    @XmlAttribute
    public abstract String getType();

    @Nullable
    @XmlAttribute
    public abstract String getTitle();

    @XmlAttribute
    public abstract String getHref();

    @Nullable
    @XmlAttribute
    public abstract String getHreflang();

    @Nullable
    @XmlAttribute
    public abstract Integer getLength();

    @Nullable
    @XmlAttribute
    public abstract String getTemplated();

    @JsonIgnore
    @XmlTransient
    public Link getLink() {
        Link.Builder link = Link.fromUri(getHref());

        if (getRel()!=null && !getRel().isEmpty())
            link.rel(getRel());
        if (getTitle()!=null && !getTitle().isEmpty())
            link.title(getTitle());
        if (getType()!=null && !getType().isEmpty())
            link.type(getType());

        return link.build();
    };

    @JsonIgnore
    @XmlTransient
    @Value.Derived
    public String getTypeLabel() {
        String mediaType = getType();
        if (mediaType == null)
            return "";
        else if (mediaType.toLowerCase().startsWith("application/gml+xml"))
            return "GML";
        else if (mediaType.toLowerCase().startsWith("application/geo+json"))
            return "GeoJSON";
        else if (mediaType.toLowerCase().startsWith("application/json"))
            return "JSON";
        else if (mediaType.toLowerCase().startsWith("application/xml"))
            return "XML";

        return mediaType;
    }
}
