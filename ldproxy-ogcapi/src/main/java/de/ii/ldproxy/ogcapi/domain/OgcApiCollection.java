/**
 * Copyright 2019 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.ogcapi.domain;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Value.Immutable
@Value.Style(deepImmutablesDetection = true)
@JsonDeserialize(builder = ImmutableOgcApiCollection.Builder.class)
public abstract class OgcApiCollection extends PageRepresentation {

    public abstract String getId();

    public abstract OgcApiExtent getExtent();

    public abstract List<String> getCrs();

    public abstract Optional<String> getStorageCrs();

    public abstract Optional<String> getItemType();

    //@JsonIgnore
    //public abstract String getPrefixedName();

    @JsonAnyGetter
    public abstract Map<String, Object> getExtensions();
}
