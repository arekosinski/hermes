package pl.allegro.tech.hermes.message.tracker.elasticsearch.frontend;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeBuilder;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import pl.allegro.tech.hermes.api.PublishedMessageTraceStatus;
import pl.allegro.tech.hermes.message.tracker.elasticsearch.LogSchemaAware;
import pl.allegro.tech.hermes.message.tracker.frontend.AbstractLogRepositoryTest;
import pl.allegro.tech.hermes.message.tracker.frontend.LogRepository;

import java.io.File;
import java.nio.file.Files;

import static com.jayway.awaitility.Awaitility.await;
import static com.jayway.awaitility.Duration.FIVE_SECONDS;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchQuery;

public class ElasticsearchLogRepositoryTest extends AbstractLogRepositoryTest implements LogSchemaAware {

    public static final String CLUSTER_NAME = "primary";
    private static Node elastic;
    private static Client client;

    @BeforeClass
    public static void setupSpec() throws Exception {
        File dataDir = Files.createTempDirectory("elasticsearch_data_").toFile();
        Settings settings = ImmutableSettings.settingsBuilder()
                .put("path.data", dataDir)
                .put("cluster.name", "hermes").build();
        elastic = NodeBuilder.nodeBuilder().local(true).settings(settings).build();
        elastic.start();
        client = elastic.client();
        client.admin().indices().prepareCreate(PUBLISHED_INDEX).execute().actionGet();
        client.admin().cluster().prepareHealth(PUBLISHED_INDEX).setWaitForActiveShards(1).execute().actionGet();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        elastic.stop();
    }

    @Override
    protected LogRepository createRepository() {
        return new ElasticsearchLogRepository(client, CLUSTER_NAME);
    }

    @Override
    protected void awaitUntilMessageIsPersisted(String topic, String id, PublishedMessageTraceStatus status, String reason) throws Exception {
        awaitUntilMessageIsIndexed(
                boolQuery()
                        .should(matchQuery(TOPIC_NAME, topic))
                        .should(matchQuery(MESSAGE_ID, id))
                        .should(matchQuery(STATUS, status.toString()))
                        .should(matchQuery(REASON, reason))
                        .should(matchQuery(CLUSTER, CLUSTER_NAME)));
    }

    @Override
    protected void awaitUntilMessageIsPersisted(String topic, String id, PublishedMessageTraceStatus status) throws Exception {
        awaitUntilMessageIsIndexed(
                boolQuery()
                        .should(matchQuery(TOPIC_NAME, topic))
                        .should(matchQuery(MESSAGE_ID, id))
                        .should(matchQuery(STATUS, status.toString()))
                        .should(matchQuery(CLUSTER, CLUSTER_NAME)));
    }

    private void awaitUntilMessageIsIndexed(QueryBuilder query) {
        await().atMost(FIVE_SECONDS).until(() -> {
            SearchResponse response = client.prepareSearch(PUBLISHED_INDEX)
                    .setTypes(PUBLISHED_TYPE)
                    .setQuery(query)
                    .execute().get();
            return response.getHits().getTotalHits() == 1;
        });
    }

}