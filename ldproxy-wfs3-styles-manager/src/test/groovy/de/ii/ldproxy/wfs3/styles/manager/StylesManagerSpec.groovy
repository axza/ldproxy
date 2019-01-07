package de.ii.ldproxy.wfs3.styles.manager

import spock.lang.Specification

class StylesManagerSpec extends Specification{


    def'validate no request body'(){

        given: 'request body is null'

        def requestBody=null

        when: "validateRequestBody is called"

        def result = Wfs3EndpointStylesManager.validateRequestBody(requestBody)

        then:'it should return false'

        !result

    }

    def'validate empty request body'(){

        given: 'request body is empty'

        def requestBody=""

        when: "validateRequestBody is called"

        def result = Wfs3EndpointStylesManager.validateRequestBody(requestBody)

        then:'it should return false'

        !result


    }

    def'validate request body with invalid json syntax '(){

        given: 'request body with invalid json'

        def requestBody="{\"id\": \"default}"

        when: "validateRequestBody is called"

        def result = Wfs3EndpointStylesManager.validateRequestBody(requestBody)

        then:'it should return false'

        !result

    }

    def'validate request body with no version number '(){

        given: 'request body with no version number '

        def requestBody="{\n" +
                "  \"name\": \"default\",\n" +
                "  \"sources\": {\n" +
                "    \"default\": {\n" +
                "      \"type\": \"vector\"\n" +
                "    }\n" +
                "  },\n" +
                "  \"sprite\": \"mapbox://sprites/mapbox/bright-v8\",\n" +
                "  \"layers\": [\n" +
                "    {\n" +
                "      \"id\": \"1\",\n" +
                "      \"type\": \"fill\",\n" +
                "      \"source\": \"default\",\n" +
                "      \"source-layer\": \"collectionId \",\n" +
                "      \"layout\": \"string\",\n" +
                "      \"paint\": {\n" +
                "        \"fill-color\": \"#11083b\"\n" +
                "      }\n" +
                "    }" +
                "  ]\n" +
                "}"

        when: "validateRequestBody is called"

        def result = Wfs3EndpointStylesManager.validateRequestBody(requestBody)

        then:'it should return false'

        !result

    }

    def'validate request body with invalid version number '(){

        given: 'request body with a version number of 7'

        def requestBody="{\n" +
                "  \"version\": 7,\n" +
                "  \"name\": \"default\",\n" +
                "  \"sources\": {\n" +
                "    \"default\": {\n" +
                "      \"type\": \"vector\"\n" +
                "    }\n" +
                "  },\n" +
                "  \"sprite\": \"mapbox://sprites/mapbox/bright-v8\",\n" +
                "  \"layers\": [\n" +
                "    {\n" +
                "      \"id\": \"1\",\n" +
                "      \"type\": \"fill\",\n" +
                "      \"source\": \"default\",\n" +
                "      \"source-layer\": \"collectionId \",\n" +
                "      \"layout\": \"string\",\n" +
                "      \"paint\": {\n" +
                "        \"fill-color\": \"#11083b\"\n" +
                "      }\n" +
                "    }" +
                "  ]\n" +
                "}"

        when: "validateRequestBody is called"

        def result = Wfs3EndpointStylesManager.validateRequestBody(requestBody)

        then:'it should return false'

        !result

    }

    def'validate request body with no sources'(){

        given: 'request body with no sources'

        def requestBody="{\n" +
                "  \"version\": 8,\n" +
                "  \"name\": \"default\",\n" +
                "  \"sprite\": \"mapbox://sprites/mapbox/bright-v8\",\n" +
                "  \"layers\": [\n" +
                "    {\n" +
                "      \"id\": \"1\",\n" +
                "      \"type\": \"fill\",\n" +
                "      \"source\": \"default\",\n" +
                "      \"source-layer\": \"collectionId \",\n" +
                "      \"layout\": \"string\",\n" +
                "      \"paint\": {\n" +
                "        \"fill-color\": \"#11083b\"\n" +
                "      }\n" +
                "    }" +
                "  ]\n" +
                "}"

        when: "validateRequestBody is called"

        def result = Wfs3EndpointStylesManager.validateRequestBody(requestBody)

        then:'it should return false'

        !result

    }

    def'validate request body with no layers'(){

        given: 'request body with no layer'

        def requestBody="{\n" +
                "  \"version\": 8,\n" +
                "  \"name\": \"default\",\n" +
                "  \"sources\": {\n" +
                "    \"default\": {\n" +
                "      \"type\": \"vector\"\n" +
                "    }\n" +
                "  },\n" +
                "  \"sprite\": \"mapbox://sprites/mapbox/bright-v8\",\n" +
                "}"

        when: "validateRequestBody is called"

        def result = Wfs3EndpointStylesManager.validateRequestBody(requestBody)

        then:'it should return false'

        !result

    }

    def'validate request body with layer - missing id'(){

        given: 'request body with a layer and the layer has no id'

        def requestBody="{\n" +
                "  \"version\": 8,\n" +
                "  \"name\": \"default\",\n" +
                "  \"sources\": {\n" +
                "    \"default\": {\n" +
                "      \"type\": \"vector\"\n" +
                "    }\n" +
                "  },\n" +
                "  \"sprite\": \"mapbox://sprites/mapbox/bright-v8\",\n" +
                "  \"layers\": [\n" +
                "    {\n" +
                "      \"type\": \"fill\",\n" +
                "      \"source\": \"default\",\n" +
                "      \"source-layer\": \"collectionId \",\n" +
                "      \"layout\": \"string\",\n" +
                "      \"paint\": {\n" +
                "        \"fill-color\": \"#11083b\"\n" +
                "      }\n" +
                "    }" +
                "  ]\n" +
                "}"

        when: "validateRequestBody is called"

        def result = Wfs3EndpointStylesManager.validateRequestBody(requestBody)

        then:'it should return false'

        !result
    }

    def'validate request body with layer - type incorrect'(){
        given: 'request body with a layer and the layer has an incorrect type'

        def requestBody="{\n" +
                "  \"version\": 8,\n" +
                "  \"name\": \"default\",\n" +
                "  \"sources\": {\n" +
                "    \"default\": {\n" +
                "      \"type\": \"vector\"\n" +
                "    }\n" +
                "  },\n" +
                "  \"sprite\": \"mapbox://sprites/mapbox/bright-v8\",\n" +
                "  \"layers\": [\n" +
                "    {\n" +
                "      \"id\": \"1\",\n" +
                "      \"type\": \"rectangle\",\n" +
                "      \"source\": \"default\",\n" +
                "      \"source-layer\": \"collectionId \",\n" +
                "      \"layout\": \"string\",\n" +
                "      \"paint\": {\n" +
                "        \"fill-color\": \"#11083b\"\n" +
                "      }\n" +
                "    }" +
                "  ]\n" +
                "}"

        when: "validateRequestBody is called"

        def result = Wfs3EndpointStylesManager.validateRequestBody(requestBody)

        then:'it should return false'

        !result
    }

    def'validate request body with layers - not unique ids'(){

        given: 'request body with two layers with the same id'

        def requestBody="{\n" +
                "  \"version\": 8,\n" +
                "  \"name\": \"default\",\n" +
                "  \"sources\": {\n" +
                "    \"default\": {\n" +
                "      \"type\": \"vector\"\n" +
                "    }\n" +
                "  },\n" +
                "  \"sprite\": \"mapbox://sprites/mapbox/bright-v8\",\n" +
                "  \"layers\": [\n" +
                "    {\n" +
                "      \"id\": \"1\",\n" +
                "      \"type\": \"fill\",\n" +
                "      \"source\": \"default\",\n" +
                "      \"source-layer\": \"collectionId \",\n" +
                "      \"layout\": \"string\",\n" +
                "      \"paint\": {\n" +
                "        \"fill-color\": \"#11083b\"\n" +
                "      }\n" +
                "    }," +
                "    {\n" +
                "      \"id\": \"1\",\n" +
                "      \"type\": \"fill\",\n" +
                "      \"source\": \"default\",\n" +
                "      \"source-layer\": \"collectionId \",\n" +
                "      \"layout\": \"string\",\n" +
                "      \"paint\": {\n" +
                "        \"fill-color\": \"#11083b\"\n" +
                "      }\n" +
                "    }" +
                "  ]\n" +
                "}"

        when: "validateRequestBody is called"

        def result = Wfs3EndpointStylesManager.validateRequestBody(requestBody)

        then:'it should return false'

        !result
    }


    def'validate request body with valid syntax and content '(){


        given: 'request body with valid json, a version number of 8, sources  and two layers with unique ids and a correct type'

        def requestBody="{\n" +
                "  \"version\": 8,\n" +
                "  \"name\": \"default\",\n" +
                "  \"sources\": {\n" +
                "    \"default\": {\n" +
                "      \"type\": \"vector\"\n" +
                "    }\n" +
                "  },\n" +
                "  \"sprite\": \"mapbox://sprites/mapbox/bright-v8\",\n" +
                "  \"layers\": [\n" +
                "    {\n" +
                "      \"id\": \"1\",\n" +
                "      \"type\": \"fill\",\n" +
                "      \"source\": \"default\",\n" +
                "      \"source-layer\": \"collectionId \",\n" +
                "      \"layout\": \"string\",\n" +
                "      \"paint\": {\n" +
                "        \"fill-color\": \"#11083b\"\n" +
                "      }\n" +
                "    }," +
                "    {\n" +
                "      \"id\": \"2\",\n" +
                "      \"type\": \"fill\",\n" +
                "      \"source\": \"default\",\n" +
                "      \"source-layer\": \"collectionId \",\n" +
                "      \"layout\": \"string\",\n" +
                "      \"paint\": {\n" +
                "        \"fill-color\": \"#11083b\"\n" +
                "      }\n" +
                "    }" +
                "  ]\n" +
                "}"

        when: "validateRequestBody is called"

        def result = Wfs3EndpointStylesManager.validateRequestBody(requestBody)

        then:'it should return true'

        result


    }


}
