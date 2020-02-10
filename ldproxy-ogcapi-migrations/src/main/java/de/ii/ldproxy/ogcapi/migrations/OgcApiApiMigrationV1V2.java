/**
 * Copyright 2020 interactive instruments GmbH
 * <p>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.ogcapi.migrations;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import de.ii.ldproxy.ogcapi.domain.ExtensionConfiguration;
import de.ii.ldproxy.ogcapi.domain.FeatureTypeConfigurationOgcApi;
import de.ii.ldproxy.ogcapi.domain.ImmutableFeatureTypeConfigurationOgcApi;
import de.ii.ldproxy.ogcapi.domain.ImmutableOgcApiApiDataV1;
import de.ii.ldproxy.ogcapi.domain.ImmutableOgcApiApiDataV2;
import de.ii.ldproxy.ogcapi.domain.OgcApiApiDataV1;
import de.ii.ldproxy.ogcapi.domain.OgcApiApiDataV2;
import de.ii.ldproxy.ogcapi.domain.OgcApiFeaturesGenericMapping;
import de.ii.ldproxy.ogcapi.features.core.api.FeatureTypeMapping2;
import de.ii.ldproxy.ogcapi.features.core.api.ImmutableFeatureTypeMapping2;
import de.ii.ldproxy.ogcapi.features.core.api.ImmutableOgcApiFeaturesCollectionQueryables;
import de.ii.ldproxy.ogcapi.features.core.api.OgcApiFeaturesCollectionQueryables;
import de.ii.ldproxy.ogcapi.features.core.application.ImmutableOgcApiFeaturesCoreConfiguration;
import de.ii.ldproxy.ogcapi.features.core.application.OgcApiFeaturesCoreConfiguration;
import de.ii.ldproxy.target.geojson.GeoJsonMapping;
import de.ii.ldproxy.target.geojson.GeoJsonPropertyMapping;
import de.ii.ldproxy.target.html.HtmlConfiguration;
import de.ii.ldproxy.target.html.ImmutableHtmlConfiguration;
import de.ii.ldproxy.target.html.MicrodataMapping;
import de.ii.ldproxy.target.html.MicrodataPropertyMapping;
import de.ii.xtraplatform.entity.api.EntityData;
import de.ii.xtraplatform.event.store.EntityDataBuilder;
import de.ii.xtraplatform.event.store.EntityMigration;
import de.ii.xtraplatform.event.store.Identifier;
import de.ii.xtraplatform.feature.provider.sql.domain.ConnectionInfoSql;
import de.ii.xtraplatform.feature.provider.sql.domain.ImmutableConnectionInfoSql;
import de.ii.xtraplatform.feature.provider.wfs.domain.ConnectionInfoWfsHttp;
import de.ii.xtraplatform.feature.provider.wfs.domain.ImmutableConnectionInfoWfsHttp;
import de.ii.xtraplatform.feature.transformer.api.FeatureProviderDataTransformer;
import de.ii.xtraplatform.feature.transformer.api.FeatureTypeMapping;
import de.ii.xtraplatform.feature.transformer.api.SourcePathMapping;
import de.ii.xtraplatform.features.domain.ConnectionInfo;
import de.ii.xtraplatform.features.domain.FeatureProperty;
import de.ii.xtraplatform.features.domain.FeatureType;
import de.ii.xtraplatform.features.domain.ImmutableFeatureProperty;
import de.ii.xtraplatform.features.domain.ImmutableFeatureProviderDataV1;
import de.ii.xtraplatform.features.domain.ImmutableFeatureType;
import de.ii.xtraplatform.service.api.ServiceData;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Instantiate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.StaticServiceProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@Provides(properties = {
        //TODO: how to connect to entity
        @StaticServiceProperty(name = "entityType", type = "java.lang.String", value = "services")
})
@Instantiate
public class OgcApiApiMigrationV1V2 implements EntityMigration<OgcApiApiDataV1, OgcApiApiDataV2> {

    private static final Logger LOGGER = LoggerFactory.getLogger(OgcApiApiMigrationV1V2.class);

    @Override
    public long getSourceVersion() {
        return 1;
    }

    @Override
    public long getTargetVersion() {
        return 2;
    }

    @Override
    public EntityDataBuilder<OgcApiApiDataV1> getDataBuilder() {
        return new ImmutableOgcApiApiDataV1.Builder();
    }

    @Override
    public OgcApiApiDataV2 migrate(OgcApiApiDataV1 entityData) {

        List<ExtensionConfiguration> extensions = entityData.getExtensions()
                                                            .stream()
                                                            .map(extensionConfiguration -> {
                                                                if (extensionConfiguration instanceof OgcApiFeaturesCoreConfiguration) {
                                                                    return new ImmutableOgcApiFeaturesCoreConfiguration.Builder()
                                                                            .from(extensionConfiguration)
                                                                            .featureProvider(entityData.getId())
                                                                            .build();
                                                                }

                                                                return extensionConfiguration;
                                                            })
                                                            .collect(Collectors.toList());

        Map<String, FeatureTypeConfigurationOgcApi> collections = entityData.getFeatureTypes()
                                                                            .entrySet()
                                                                            .stream()
                                                                            .filter(entry -> entityData.getFeatureProvider()
                                                                                                       .getMappings()
                                                                                                       .containsKey(entry.getValue()
                                                                                                                         .getId()))
                                                                            .map(entry -> {

                                                                                FeatureTypeConfigurationOgcApi collection = entry.getValue();

                                                                                ImmutableFeatureTypeConfigurationOgcApi.Builder newCollection = new ImmutableFeatureTypeConfigurationOgcApi.Builder().from(collection);

                                                                                Optional<OgcApiFeaturesCoreConfiguration> coreConfiguration = collection.getExtension(OgcApiFeaturesCoreConfiguration.class);

                                                                                ImmutableOgcApiFeaturesCoreConfiguration.Builder newCoreConfiguration = new ImmutableOgcApiFeaturesCoreConfiguration.Builder();

                                                                                coreConfiguration.ifPresent(newCoreConfiguration::from);

                                                                                OgcApiFeaturesCollectionQueryables queryables = getQueryables(entityData.getFeatureProvider()
                                                                                                                                                        .getMappings()
                                                                                                                                                        .get(collection.getId()));
                                                                                newCoreConfiguration.featureType(collection.getId())
                                                                                                    .queryables(queryables);

                                                                                Map<String, FeatureTypeMapping2> coreTransformations = getCoreTransformations(entityData.getFeatureProvider()
                                                                                                                                                                        .getMappings()
                                                                                                                                                                        .get(collection.getId()));

                                                                                newCoreConfiguration.transformations(coreTransformations);


                                                                                Optional<HtmlConfiguration> htmlConfiguration = collection.getExtension(HtmlConfiguration.class);

                                                                                ImmutableHtmlConfiguration.Builder newHtmlConfiguration = new ImmutableHtmlConfiguration.Builder();

                                                                                htmlConfiguration.ifPresent(newHtmlConfiguration::from);


                                                                                Map<String, FeatureTypeMapping2> htmlTransformations = getHtmlTransformations(entityData.getFeatureProvider()
                                                                                                                                                                        .getMappings()
                                                                                                                                                                        .get(collection.getId()));

                                                                                newHtmlConfiguration.transformations(htmlTransformations);

                                                                                Optional<String> htmlName = getHtmlName(entityData.getFeatureProvider()
                                                                                                                                  .getMappings()
                                                                                                                                  .get(collection.getId()));

                                                                                htmlName.ifPresent(htmlName1 -> {
                                                                                    final String[] itemLabelFormat = {htmlName1};

                                                                                    htmlTransformations.forEach((key, value) -> {
                                                                                        if (value.getRename()
                                                                                                 .isPresent()) {
                                                                                            String rename = String.format("{{%s}}", value.getRename()
                                                                                                                                         .get());
                                                                                            if (itemLabelFormat[0].contains(rename)) {
                                                                                                itemLabelFormat[0] = itemLabelFormat[0].replace(rename, String.format("{{%s}}", key));
                                                                                            }
                                                                                        }
                                                                                    });

                                                                                    newHtmlConfiguration.itemLabelFormat(itemLabelFormat[0]);
                                                                                });


                                                                                List<ExtensionConfiguration> newExtensions = collection.getExtensions()
                                                                                                                                       .stream()
                                                                                                                                       .map(extension -> {

                                                                                                                                           if (extension instanceof OgcApiFeaturesCoreConfiguration) {
                                                                                                                                               return newCoreConfiguration.build();
                                                                                                                                           }
                                                                                                                                           if (extension instanceof HtmlConfiguration) {
                                                                                                                                               return newHtmlConfiguration.build();
                                                                                                                                           }

                                                                                                                                           return extension;
                                                                                                                                       })
                                                                                                                                       .collect(Collectors.toList());

                                                                                newCollection.extensions(newExtensions);

                                                                                if (newExtensions.stream()
                                                                                                 .noneMatch(extension -> extension instanceof OgcApiFeaturesCoreConfiguration)) {
                                                                                    newCollection.addExtensions(newCoreConfiguration.build());
                                                                                }
                                                                                if (newExtensions.stream()
                                                                                                 .noneMatch(extension -> extension instanceof HtmlConfiguration)) {
                                                                                    newCollection.addExtensions(newHtmlConfiguration.build());
                                                                                }

                                                                                return new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), newCollection.build());
                                                                            })
                                                                            .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));

        OgcApiApiDataV2 migrated = new ImmutableOgcApiApiDataV2.Builder().from((ServiceData) entityData)
                                                                         .entityStorageVersion(getTargetVersion())
                                                                         .serviceType("OGC_API")
                                                                         .metadata(Optional.ofNullable(entityData.getMetadata()))
                                                                         //TODO
                                                                         .collections(collections)
                                                                         .extensions(extensions)
                                                                         .build();

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Migrated schema for entity '{}' with type 'services': v{} -> v{}", entityData.getId(), getSourceVersion(), getTargetVersion());
        }

        return migrated;
    }

    @Override
    public Map<Identifier, EntityData> getAdditionalEntities(Identifier identifier, OgcApiApiDataV1 entityData) {

        FeatureProviderDataTransformer featureProvider = entityData.getFeatureProvider();
        String providerType = featureProvider.getProviderType();

        if (Objects.equals(providerType, "PGIS") || Objects.equals(providerType, "WFS")) {

            Map<String, FeatureType> featureTypes = featureProvider.getMappings()
                                                                   .entrySet()
                                                                   .stream()
                                                                   .map(entry -> {

                                                                       ImmutableFeatureType.Builder featureType = new ImmutableFeatureType.Builder()
                                                                               .name(entry.getKey());
                                                                       String[] featureTypeName = new String[1];

                                                                       entry.getValue()
                                                                            .getMappings()
                                                                            .entrySet()
                                                                            .stream()
                                                                            .sorted(OgcApiApiMigrationV1V2::compareSortPriority)
                                                                            .forEach(entry2 -> {
                                                                                String path = entry2.getKey();
                                                                                SourcePathMapping mappings = entry2.getValue();
                                                                                boolean isFeatureTypeMapping = isFeatureTypeMapping(providerType, path);
                                                                                if (isFeatureTypeMapping) {
                                                                                    featureTypeName[0] = path;
                                                                                }

                                                                                Optional<OgcApiFeaturesGenericMapping> general;
                                                                                try {
                                                                                    general = Optional.ofNullable((OgcApiFeaturesGenericMapping) mappings.getMappings()
                                                                                                                                                         .get("general"));
                                                                                } catch (Throwable e) {
                                                                                    general = Optional.empty();
                                                                                }
                                                                                //GeoJsonGeometryMapping
                                                                                Optional<GeoJsonPropertyMapping> json;
                                                                                try {
                                                                                    json = Optional.ofNullable((GeoJsonPropertyMapping) mappings.getMappings()
                                                                                                                                                .get("application/geo+json"));
                                                                                } catch (Throwable e) {
                                                                                    json = Optional.empty();
                                                                                }

                                                                                //MicrodataGeometryMapping
                                                                                Optional<MicrodataPropertyMapping> html;
                                                                                try {
                                                                                    html = Optional.ofNullable((MicrodataPropertyMapping) mappings.getMappings()
                                                                                                                                                  .get("text/html"));
                                                                                } catch (Throwable e) {
                                                                                    html = Optional.empty();
                                                                                }

                                                                                if (!isFeatureTypeMapping && general.isPresent()) {
                                                                                    String name = json.flatMap(jsonMapping -> Optional.ofNullable(jsonMapping.getName()))
                                                                                                      .orElse(Optional.ofNullable(general.get()
                                                                                                                                         .getName())
                                                                                                                      .orElse("TODO"));
                                                                                    OgcApiFeaturesGenericMapping.GENERIC_TYPE genericType = Optional.ofNullable(general.get()
                                                                                                                                                                       .getType())
                                                                                                                                                    .orElse(OgcApiFeaturesGenericMapping.GENERIC_TYPE.NONE);
                                                                                    GeoJsonMapping.GEO_JSON_TYPE jsonType = json.flatMap(jsonMapping -> Optional.ofNullable(jsonMapping.getType()))
                                                                                                                                .orElse(GeoJsonMapping.GEO_JSON_TYPE.STRING);

                                                                                    FeatureProperty.Type type = FeatureProperty.Type.STRING;
                                                                                    Optional<FeatureProperty.Role> role = Optional.empty();
                                                                                    switch (genericType) {

                                                                                        case TEMPORAL:
                                                                                            type = FeatureProperty.Type.DATETIME;
                                                                                            break;
                                                                                        case SPATIAL:
                                                                                            type = FeatureProperty.Type.GEOMETRY;
                                                                                            break;
                                                                                        case ID:
                                                                                            role = Optional.of(FeatureProperty.Role.ID);
                                                                                        case VALUE:
                                                                                        case REFERENCE:
                                                                                        case REFERENCE_EMBEDDED:
                                                                                        case NONE:
                                                                                            switch (jsonType) {

                                                                                                case INTEGER:
                                                                                                    type = FeatureProperty.Type.INTEGER;
                                                                                                    break;
                                                                                                case NUMBER:
                                                                                                case DOUBLE:
                                                                                                    type = FeatureProperty.Type.FLOAT;
                                                                                                    break;
                                                                                                case GEOMETRY:
                                                                                                    type = FeatureProperty.Type.GEOMETRY;
                                                                                                    break;
                                                                                                case BOOLEAN:
                                                                                                    type = FeatureProperty.Type.BOOLEAN;
                                                                                                    break;
                                                                                                case ID:
                                                                                                case STRING:
                                                                                                case NONE:
                                                                                                    type = FeatureProperty.Type.STRING;
                                                                                            }
                                                                                    }

                                                                                    FeatureProperty featureProperty = new ImmutableFeatureProperty.Builder()
                                                                                            .name(name)
                                                                                            .path(adjustPath(path, featureTypeName[0], providerType, featureProvider.getConnectionInfo()))
                                                                                            .type(type)
                                                                                            .role(role)
                                                                                            .build();

                                                                                    featureType.putProperties(name, featureProperty);
                                                                                }


                                                                            });

                                                                       return new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), featureType.build());
                                                                   })
                                                                   .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue));

            ImmutableFeatureProviderDataV1.Builder featureProviderData = new ImmutableFeatureProviderDataV1.Builder()
                    .id(entityData.getId())
                    .providerType("FEATURE")
                    .nativeCrs(featureProvider.getNativeCrs())
                    .types(featureTypes);

            if (Objects.equals(providerType, "PGIS")) {
                featureProviderData.featureProviderType("SQL")
                                   .connectionInfo(new ImmutableConnectionInfoSql.Builder()
                                           .from(featureProvider.getConnectionInfo())
                                           .connectorType("SLICK")
                                           .dialect(ConnectionInfoSql.Dialect.PGIS)
                                           .build());
            } else if (Objects.equals(providerType, "WFS")) {
                featureProviderData.featureProviderType("WFS")
                                   .connectionInfo(new ImmutableConnectionInfoWfsHttp.Builder()
                                           .from(featureProvider.getConnectionInfo())
                                           .connectorType("HTTP")
                                           .build());
            }

            List<String> path = ImmutableList.<String>builder()
                    .add("providers")
                    .addAll(identifier.path()
                                      .subList(1, identifier.path()
                                                            .size()))
                    .build();
            Identifier newIdentifier = Identifier.from(identifier.id(), path.toArray(new String[]{}));

            return ImmutableMap.of(newIdentifier, featureProviderData.build());
        }

        LOGGER.warn("Could not migrate feature provider for service '{}', providerType '{}' is not supported yet.", entityData.getId(), providerType);

        return ImmutableMap.of();

    }

    private String adjustPath(String path, String featureTypeName, String providerType, ConnectionInfo connectionInfo) {

        if (Objects.equals(providerType, "WFS") && connectionInfo instanceof ConnectionInfoWfsHttp) {
            Map<String, String> namespaces = ((ConnectionInfoWfsHttp) connectionInfo).getNamespaces();

            String resolvedPath = String.format("/%s/%s", featureTypeName, path);

            for (Map.Entry<String, String> entry : namespaces.entrySet()) {
                String prefix = entry.getKey();
                String uri = entry.getValue();
                resolvedPath = resolvedPath.replaceAll(uri, prefix);
            }

            return resolvedPath;
        }

        return path;
    }

    private OgcApiFeaturesCollectionQueryables getQueryables(
            FeatureTypeMapping featureTypeMapping) {
        ImmutableOgcApiFeaturesCollectionQueryables.Builder builder = new ImmutableOgcApiFeaturesCollectionQueryables.Builder();

        List<OgcApiFeaturesGenericMapping> filterables = featureTypeMapping.getMappings()
                                                                           .values()
                                                                           .stream()
                                                                           .sorted(OgcApiApiMigrationV1V2::compareSortPriority)
                                                                           .filter(sourcePathMapping -> sourcePathMapping.hasMappingForType("general"))
                                                                           .map(sourcePathMapping -> (OgcApiFeaturesGenericMapping) sourcePathMapping.getMappingForType("general"))
                                                                           .filter(OgcApiFeaturesGenericMapping::isFilterable)
                                                                           .collect(Collectors.toList());

        List<String> spatial = filterables.stream()
                                          .filter(mapping -> mapping.getType() == OgcApiFeaturesGenericMapping.GENERIC_TYPE.SPATIAL)
                                          .map(OgcApiFeaturesGenericMapping::getName)
                                          .collect(Collectors.toList());

        List<String> temporal = filterables.stream()
                                           .filter(mapping -> mapping.getType() == OgcApiFeaturesGenericMapping.GENERIC_TYPE.TEMPORAL)
                                           .map(OgcApiFeaturesGenericMapping::getName)
                                           .collect(Collectors.toList());

        List<String> other = filterables.stream()
                                        .filter(mapping -> mapping.getType() != OgcApiFeaturesGenericMapping.GENERIC_TYPE.SPATIAL && mapping.getType() != OgcApiFeaturesGenericMapping.GENERIC_TYPE.TEMPORAL)
                                        .map(OgcApiFeaturesGenericMapping::getName)
                                        .collect(Collectors.toList());

        return builder.spatial(spatial)
                      .temporal(temporal)
                      .other(other)
                      .build();
    }

    private Optional<String> getHtmlName(FeatureTypeMapping featureTypeMapping) {

        final String[] name = {null};

        featureTypeMapping.getMappings()
                          .values()
                          .stream()
                          .filter(sourcePathMapping -> sourcePathMapping.hasMappingForType("general") && sourcePathMapping.hasMappingForType("text/html"))
                          .forEach(sourcePathMapping -> {
                              OgcApiFeaturesGenericMapping general = (OgcApiFeaturesGenericMapping) sourcePathMapping.getMappingForType("general");
                              MicrodataPropertyMapping html = (MicrodataPropertyMapping) sourcePathMapping.getMappingForType("text/html");

                              if (Objects.isNull(general.getName()) && Objects.nonNull(html.getName())) {

                                  name[0] = html.getName();
                              }
                          });

        return Optional.ofNullable(name[0]);
    }

    private Map<String, FeatureTypeMapping2> getHtmlTransformations(FeatureTypeMapping featureTypeMapping) {

        Map<String, FeatureTypeMapping2> transformations = new LinkedHashMap<>();

        featureTypeMapping.getMappings()
                          .values()
                          .stream()
                          .filter(sourcePathMapping -> sourcePathMapping.hasMappingForType("general") && sourcePathMapping.hasMappingForType("text/html"))
                          .sorted(OgcApiApiMigrationV1V2::compareSortPriority)
                          .forEach(sourcePathMapping -> {
                              OgcApiFeaturesGenericMapping general = (OgcApiFeaturesGenericMapping) sourcePathMapping.getMappingForType("general");
                              MicrodataPropertyMapping html = (MicrodataPropertyMapping) sourcePathMapping.getMappingForType("text/html");

                              if (Objects.isNull(general.getName()) /*&& Objects.nonNull(html.getName())*/) {
                                  return;
                              }

                              //TODO: transactions? not yet in geoval
                              //TODO: mainWritePathPattern in FileSystemEvents

                              //TODO: connectorType + mappingStatus @ featureProvider
                              //TODO: api buildingBlocks as Object? i think does not work with deser
                              //TODO: ConnectionInfoV1 with custom deser
                              //TODO: foto[foto].hauptfoto -> foto[].hauptfoto

                              ImmutableFeatureTypeMapping2.Builder builder = new ImmutableFeatureTypeMapping2.Builder();
                              boolean hasTransformations = false;

                              if (Objects.isNull(html.isShowInCollection()) || !html.isShowInCollection()) {
                                  builder.remove("OVERVIEW");
                                  hasTransformations = true;
                              }

                              if (general.getType() != OgcApiFeaturesGenericMapping.GENERIC_TYPE.SPATIAL) {
                                  if (Objects.nonNull(html.getName()) && !Objects.equals(general.getName(), html.getName())) {
                                      builder.rename(html.getName());
                                      hasTransformations = true;
                                  }
                                  if (Objects.nonNull(html.getCodelist())) {
                                      builder.codelist(html.getCodelist());
                                      hasTransformations = true;
                                  }
                                  if (Objects.nonNull(html.getFormat())) {
                                      if (html.getType() == MicrodataMapping.MICRODATA_TYPE.DATE) {
                                          builder.dateFormat(html.getFormat());
                                      } else {
                                          builder.stringFormat(html.getFormat());
                                      }
                                      hasTransformations = true;
                                  }
                              }

                              if (hasTransformations) {
                                  transformations.put(general.getName()
                                                             .replaceAll("\\[.+?\\]", "[]"), builder.build());
                              }
                          });

        return transformations;
    }

    private Map<String, FeatureTypeMapping2> getCoreTransformations(FeatureTypeMapping featureTypeMapping) {

        Map<String, FeatureTypeMapping2> transformations = new LinkedHashMap<>();

        featureTypeMapping.getMappings()
                          .values()
                          .stream()
                          .filter(sourcePathMapping -> sourcePathMapping.hasMappingForType("general"))
                          .sorted(OgcApiApiMigrationV1V2::compareSortPriority)
                          .forEach(sourcePathMapping -> {
                              OgcApiFeaturesGenericMapping general = (OgcApiFeaturesGenericMapping) sourcePathMapping.getMappingForType("general");

                              ImmutableFeatureTypeMapping2.Builder builder = new ImmutableFeatureTypeMapping2.Builder();
                              boolean hasTransformations = false;

                              if (general.getType() != OgcApiFeaturesGenericMapping.GENERIC_TYPE.SPATIAL) {
                                  if (Objects.nonNull(general.getCodelist())) {
                                      builder.codelist(general.getCodelist());
                                      hasTransformations = true;
                                  }
                                  if (Objects.nonNull(general.getFormat())) {
                                      if (general.getType() == OgcApiFeaturesGenericMapping.GENERIC_TYPE.TEMPORAL) {
                                          builder.dateFormat(general.getFormat());
                                      } else {
                                          builder.stringFormat(general.getFormat());
                                      }
                                      hasTransformations = true;
                                  }
                              }

                              if (hasTransformations) {
                                  transformations.put(general.getName()
                                                             .replaceAll("\\[.+?\\]", "[]"), builder.build());
                              }
                          });

        return transformations;
    }

    private static int compareSortPriority(Map.Entry<String, SourcePathMapping> stringSourcePathMappingEntry,
                                           Map.Entry<String, SourcePathMapping> stringSourcePathMappingEntry1) {
        return compareSortPriority(stringSourcePathMappingEntry.getValue(), stringSourcePathMappingEntry1.getValue());
    }

    private static int compareSortPriority(SourcePathMapping sourcePathMapping, SourcePathMapping sourcePathMapping2) {
        OgcApiFeaturesGenericMapping general = (OgcApiFeaturesGenericMapping) sourcePathMapping.getMappingForType("general");
        OgcApiFeaturesGenericMapping general2 = (OgcApiFeaturesGenericMapping) sourcePathMapping2.getMappingForType("general");
        if (general.getSortPriority() == null) {
            return 1;
        }

        if (general2.getSortPriority() == null) {
            return -1;
        }

        return general.getSortPriority() - general2.getSortPriority();
    }

    private boolean isFeatureTypeMapping(String providerType, String path) {

        if (Objects.equals(providerType, "PGIS")) {
            return path.indexOf("/") == path.lastIndexOf("/");
        } else if (Objects.equals(providerType, "WFS")) {
            return Character.isUpperCase(path.charAt(path.lastIndexOf(":") + 1));
        }

        return false;
    }


}
