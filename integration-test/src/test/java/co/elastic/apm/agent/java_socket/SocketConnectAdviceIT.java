/*
   Copyright 2021 Tobias Stadler

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package co.elastic.apm.agent.java_socket;

import co.elastic.apm.api.ElasticApm;
import co.elastic.apm.api.Scope;
import co.elastic.apm.api.Transaction;
import co.elastic.apm.attach.ElasticApmAttacher;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.model.ClearType;
import org.mockserver.model.Format;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;

@ExtendWith(MockServerExtension.class)
class SocketConnectAdviceIT {

    private static MockServerClient MOCK_SERVER_CLIENT;

    @BeforeAll
    static void setUp(MockServerClient mockServerClient) {
        MOCK_SERVER_CLIENT = mockServerClient;
        MOCK_SERVER_CLIENT.when(request("/")).respond(response().withStatusCode(200).withBody(json("{\"version\": \"7.13.0\"}")));
        MOCK_SERVER_CLIENT.when(request("/config/v1/agents")).respond(response().withStatusCode(403));
        MOCK_SERVER_CLIENT.when(request("/intake/v2/events")).respond(response().withStatusCode(200));

        Map<String, String> configuration = new HashMap<>();
        configuration.put("server_url", "http://localhost:" + mockServerClient.getPort());
        configuration.put("report_sync", "true");
        configuration.put("disable_metrics", "*");
        configuration.put("plugins_dir", "target/apm-plugins");
        configuration.put("application_packages", "co.elastic.apm.agent.java_socket");

        ElasticApmAttacher.attach(configuration);
    }

    @BeforeEach
    void clear() {
        MOCK_SERVER_CLIENT.clear(request("/intake/v2/events"), ClearType.LOG);
    }

    @Test
    public void testConnect() throws Exception {
        Transaction transaction = ElasticApm.startTransaction();
        try (Scope scope = transaction.activate()) {
            new Socket().connect(new InetSocketAddress("localhost", MOCK_SERVER_CLIENT.getPort()));
        } finally {
            transaction.end();
        }

        Map<String, Object> span = getSpan();

        assertEquals("connect to 127.0.0.1:" + MOCK_SERVER_CLIENT.getPort(), JsonPath.read(span, "$.name"));
        assertEquals("127.0.0.1", JsonPath.read(span, "$.context.destination.address"));
        assertEquals(MOCK_SERVER_CLIENT.getPort(), (int) JsonPath.read(span, "$.context.destination.port"));
    }

    private static Map<String, Object> getSpan() {
        return getEvents()
                .flatMap(dc -> ((List<Map<String, Object>>) dc.read("$[?(@.span)].span")).stream())
                .findAny()
                .get();
    }

    private static Stream<DocumentContext> getEvents() {
        return ((List<String>) JsonPath.read(MOCK_SERVER_CLIENT.retrieveRecordedRequests(request("/intake/v2/events"), Format.JAVA), "$..body.rawBytes"))
                .stream()
                .map(Base64.getDecoder()::decode)
                .map(String::new)
                .flatMap(s -> Arrays.stream(s.split("\r?\n")))
                .map(JsonPath::parse);
    }
}