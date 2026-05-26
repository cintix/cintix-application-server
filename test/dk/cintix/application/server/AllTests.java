package dk.cintix.application.server;

import dk.cintix.application.server.io.cache.CacheTest;
import dk.cintix.application.server.io.memory.ByteMemoryStreamTest;
import dk.cintix.application.server.infrastructure.modules.PluginSystemTest;
import dk.cintix.application.server.jdbc.EntityManagerInjectionTest;
import dk.cintix.application.server.jdbc.PooledDataSourceTest;
import dk.cintix.application.server.jdbc.TransactionableConnectionTest;
import dk.cintix.application.server.rest.RestActionCacheKeyTest;
import dk.cintix.application.server.rest.RestActionServiceMixedParamsTest;
import dk.cintix.application.server.rest.http.GraphQLEndpointTest;
import dk.cintix.application.server.rest.http.RestHttpRequestHeaderTest;
import dk.cintix.application.server.rest.http.ServerPagePropertyMergeTest;
import dk.cintix.application.server.rest.http.RestHttpServerPathTest;
import dk.cintix.application.server.rest.http.WebSocketQueryStringTest;
import dk.cintix.application.server.rest.http.utils.HttpUtilTest;
import dk.cintix.application.server.web.MimeTypesTest;

public class AllTests {

    public static void main(String[] args) throws Exception {
        new HttpUtilTest().runAll();
        new RestHttpRequestHeaderTest().runAll();
        new RestActionServiceMixedParamsTest().runAll();
        new WebSocketQueryStringTest().runAll();
        new ServerPagePropertyMergeTest().runAll();
        new RestHttpServerPathTest().runAll();
        new RestActionCacheKeyTest().runAll();
        new EntityManagerInjectionTest().runAll();
        new PooledDataSourceTest().runAll();
        new TransactionableConnectionTest().runAll();
        new MimeTypesTest().runAll();
        new ByteMemoryStreamTest().runAll();
        new CacheTest().runAll();
        new GraphQLEndpointTest().runAll();
        new PluginSystemTest().runAll();
        System.out.println("All tests passed.");
    }
}
