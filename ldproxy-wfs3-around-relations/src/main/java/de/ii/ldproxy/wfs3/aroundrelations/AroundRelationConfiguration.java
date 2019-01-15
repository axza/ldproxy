/**
 * Copyright 2019 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.wfs3.aroundrelations;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import de.ii.ldproxy.wfs3.api.ExtensionConfiguration;
import org.immutables.value.Value;

import java.util.List;
import java.util.OptionalDouble;

/**
 * @author zahnen
 */
@Value.Immutable
@Value.Modifiable
@JsonDeserialize(as = ModifiableAroundRelationConfiguration.class)
public abstract class AroundRelationConfiguration implements ExtensionConfiguration {

    public static final String EXTENSION_KEY = "aroundRelations";
    public static final String EXTENSION_TYPE = "AROUND_RELATIONS";

    public abstract List<Relation> getRelations();

    @Value.Immutable
    @Value.Modifiable
    @JsonDeserialize(as = ModifiableRelation.class)
    public static abstract class Relation {

        public abstract String getId();

        public abstract String getLabel();

        public abstract String getResponseType();

        public abstract String getUrlTemplate();

        public abstract OptionalDouble getBufferInMeters();


    }
    @Override
    public ExtensionConfiguration mergeDefaults(ExtensionConfiguration extensionConfigurationDefault) {
        return ImmutableAroundRelationConfiguration.builder().from(extensionConfigurationDefault).from(this).build(); //TODO
    }


}
