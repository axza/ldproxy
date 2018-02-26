package de.ii.ldproxy.target.geojson;

import de.ii.ogc.wfs.proxy.TargetMapping;

/**
 * @author zahnen
 */
public class TargetMappingMock implements TargetMapping {
    String name;
    boolean enabled;
    boolean geometry;
    String type;
    String itemType;
    boolean showInCollection;
    String format;
    String geometryType;
    boolean filterable;

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isGeometry() {
        return type.equals("GEOMETRY");
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setGeometry(boolean geometry) {
        this.geometry = geometry;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getItemType() {
        return itemType;
    }

    public void setItemType(String itemType) {
        this.itemType = itemType;
    }

    public boolean isShowInCollection() {
        return showInCollection;
    }

    public void setShowInCollection(boolean showInCollection) {
        this.showInCollection = showInCollection;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getGeometryType() {
        return geometryType;
    }

    public void setGeometryType(String geometryType) {
        this.geometryType = geometryType;
    }

    public boolean isFilterable() {
        return filterable;
    }

    public void setFilterable(boolean filterable) {
        this.filterable = filterable;
    }

    @Override
    public TargetMapping mergeCopyWithBase(TargetMapping targetMapping) {
        if (this.name == null)
            this.name = targetMapping.getName();
        this.enabled = this.enabled || targetMapping.isEnabled();
        return this;
    }

    @Override
    public String toString() {
        return "TargetMappingMock{" +
                "name='" + name + '\'' +
                ", type='" + type + '\'' +
                '}';
    }
}
