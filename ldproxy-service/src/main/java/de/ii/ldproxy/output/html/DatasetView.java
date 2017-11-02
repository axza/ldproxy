/**
 * Copyright 2017 interactive instruments GmbH
 * <p>
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.output.html;

import com.github.mustachejava.util.DecoratedCollection;
import com.google.common.base.Splitter;
import de.ii.xsf.core.views.GenericView;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 *
 * @author zahnen
 */
public class DatasetView extends GenericView {

    public String name;
    public String title;
    public String description;
    public List<String> keywords;
    public String version;
    public String license;
    public String bbox;
    public String url;
    public String metadataUrl;
    public List<DatasetView> featureTypes;
    public String providerName;
    public String providerUrl;
    public String contactName;
    public String contactEmail;
    public String contactTelephone;
    public String contactFaxNumber;
    public String contactHoursOfService;
    public String contactUrl;
    public String contactStreetAddress;
    public String contactLocality;
    public String contactRegion;
    public String contactPostalCode;
    public String contactCountry;
    public List<NavigationDTO> breadCrumbs;
    public List<NavigationDTO> formats;

    public DatasetView(URI uri) {
        this("service", uri);
    }

    public DatasetView(String template, URI uri) {
        this(template, uri, (Object) null);
    }

    public DatasetView(String template, URI uri, Object data) {
        super(template, uri, data);
        this.keywords = new ArrayList<>();
        this.featureTypes = new ArrayList<>();
    }

    public DatasetView(String template, URI uri, String name) {
        this(template, uri, name, name);
    }

    public DatasetView(String template, URI uri, String name, String title) {
        this(template, uri);
        this.name = name;
        this.title = title;
    }

    public DecoratedCollection<String> getKeywordsDecorated() {
        return new DecoratedCollection<>(keywords);
    }

    public Function<String, String> getQueryWithout() {
        return without -> {
            List<String> ignore = Splitter.on(',').trimResults().omitEmptyStrings().splitToList(without);

            List<NameValuePair> query = URLEncodedUtils.parse(getQuery().substring(1), Charset.forName("utf-8")).stream()
                    .filter(kvp -> !ignore.contains(kvp.getName().toLowerCase()))
                    .collect(Collectors.toList());

            return '?' + URLEncodedUtils.format(query, '&', Charset.forName("utf-8")) + (!query.isEmpty() ? '&' : "");
        };
    }
}
