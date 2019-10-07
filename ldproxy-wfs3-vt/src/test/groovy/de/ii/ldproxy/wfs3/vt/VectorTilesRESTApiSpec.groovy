/*
 * Copyright 2019 interactive instruments GmbH
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package de.ii.ldproxy.wfs3.vt

import groovyx.net.http.ContentType
import groovyx.net.http.Method
import groovyx.net.http.RESTClient
import spock.lang.Requires
import spock.lang.Specification

@Requires({env['SUT_URL'] != null})
class VectorTilesRESTApiSpec extends Specification{

    static final String SUT_URL = System.getenv('SUT_URL')
    static final String SUT_PATH = "/rest/services/daraa"
    static final String SUT_TILE_MATRIX_SET_ID = "WebMercatorQuad"
    static final String SUT_COLLECTION = "aeronauticcrv"

    RESTClient restClient = new RESTClient(SUT_URL)


    def 'GET Request for the /tileMatrixSets Page'(){

        when:
        def response = restClient.get( path: SUT_PATH + '/tileMatrixSets')

        then:
        response.status == 200

        and:
        response.responseData.containsKey("tileMatrixSetLinks")
        response.responseData.get("tileMatrixSetLinks").get(0).get("id") == "WebMercatorQuad"
    }

    def 'GET Request for the tile matrix set Page from tileMatrixSets'(){

        when:
        def response = restClient.get( path: SUT_PATH + '/tileMatrixSets/'+ SUT_TILE_MATRIX_SET_ID)

        then:
        response.status == 200

        and:
        response.responseData.containsKey("tileMatrix")
        response.responseData.containsKey("boundingBox")
        response.responseData.containsKey("identifier")
        response.responseData.containsKey("supportedCRS")
        response.responseData.containsKey("title")
        response.responseData.containsKey("type")
        response.responseData.containsKey("wellKnownScaleSet")
        response.responseData.get("identifier") == "WebMercatorQuad"
    }

    def 'GET Request for the tiles Page'(){

        when:
        def response = restClient.get( path: SUT_PATH + '/tiles')

        then:
        response.status == 200

        and:
        response.responseData.containsKey("tileMatrixSetLinks")
        response.responseData.get("tileMatrixSetLinks").get(0).get("tileMatrixSet") == "WebMercatorQuad"
    }

    def 'GET Request for the tile matrix set Page from tiles'(){

        when:
        def response = restClient.get( path: SUT_PATH + '/tiles/'+ SUT_TILE_MATRIX_SET_ID)

        then:
        response.status == 200

        and:
        response.responseData.containsKey("tileMatrix")
        response.responseData.containsKey("boundingBox")
        response.responseData.containsKey("identifier")
        response.responseData.containsKey("supportedCRS")
        response.responseData.containsKey("title")
        response.responseData.containsKey("type")
        response.responseData.containsKey("wellKnownScaleSet")
        response.responseData.get("identifier") == "WebMercatorQuad"
    }

    def 'GET Request for a empty tile of the dataset'(){

        when:
        def response=restClient.request(SUT_URL, Method.GET, ContentType.JSON,{ req ->
            uri.path = SUT_PATH + '/tiles/'+ SUT_TILE_MATRIX_SET_ID + '/10/413/618'
            headers.Accept = 'application/vnd.mapbox-vector-tile'
        })

        then:
        response.status == 200

        and:
        response.responseData == null
    }

    def 'GET Request for a non-empty tile of the dataset'(){

        def status = 404

        when:
        restClient.request(SUT_URL, Method.GET, ContentType.JSON,{ req ->
            uri.path = SUT_PATH + '/tiles/'+ SUT_TILE_MATRIX_SET_ID + '/10/413/614'
            headers.Accept = 'application/vnd.mapbox-vector-tile'

            response.success = { resp ->
                println 'request was successful'
                status = resp.status
            }
        })

        then:
        status == 200
    }

    def 'GET Request for a tile matrix set Page from a collection'(){

        when:
        def response = restClient.get( path: SUT_PATH + '/collections/'+ SUT_COLLECTION +"/tiles")

        then:
        response.status == 200

        and:
        response.responseData.containsKey("tileMatrixSetLinks")
        response.responseData.get("tileMatrixSetLinks").get(0).get("tileMatrixSet") == "WebMercatorQuad"
    }

    def 'GET Request for a tile of a collection in json format'(){

        when:
        def response = restClient.request(SUT_URL, Method.GET, ContentType.JSON, { req ->
            uri.path = SUT_PATH + '/collections/' + SUT_COLLECTION + "/tiles/" + SUT_TILE_MATRIX_SET_ID + "/10/413/614"
            headers.Accept = 'application/json'
        })

        then:
        response.status == 200

        and:
        response.responseData.containsKey("type")
        response.responseData.get("type") == "FeatureCollection"
        response.responseData.containsKey("links")
        response.responseData.containsKey("numberReturned")
        response.responseData.containsKey("numberMatched")
        response.responseData.containsKey("timeStamp")
        response.responseData.containsKey("features")
    }

    def 'GET Request for a tile of a collection in mvt format'() {

        def status = 404

        when:
        restClient.request(SUT_URL, Method.GET, ContentType.JSON, { req ->
            uri.path = SUT_PATH + '/collections/' + SUT_COLLECTION + "/tiles/" + SUT_TILE_MATRIX_SET_ID + "/10/413/614"
            headers.Accept = 'application/vnd.mapbox-vector-tile'

            response.success = { resp ->
                println 'request was successful'
                status = resp.status
            }
        })

        then:
        status == 200
    }

    def 'Vector tiles conformance classes'() {
        when: "request to the conformance page"
        def response = restClient.request(SUT_URL, Method.GET, ContentType.JSON, {req ->
            uri.path = SUT_PATH + '/conformance'
            headers.Accept = 'application/json'
        })

        then: "check conformance classes"
        response.status == 200
        response.responseData.containsKey("conformsTo")
        response.responseData.get("conformsTo").any { it == 'http://www.opengis.net/spec/ogcapi-tiles-1/1.0/conf/core' }
        response.responseData.get("conformsTo").any { it == 'http://www.opengis.net/spec/ogcapi-tiles-1/1.0/conf/collections' }
        response.responseData.get("conformsTo").any { it == 'http://www.opengis.net/spec/ogcapi-tiles-1/1.0/conf/tmxs' }
    }
}
