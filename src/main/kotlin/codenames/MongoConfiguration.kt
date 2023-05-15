package codenames

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.ServerApi
import com.mongodb.ServerApiVersion
import com.mongodb.WriteConcern
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.core.MongoTemplate

@Configuration
class MongoConfiguration {
    @Value("\${spring.data.mongodb.uri}")
    lateinit var connectionString: String

    @Value("\${spring.data.mongodb.database}")
    lateinit var databaseName: String

    @Bean
    fun mongoClient(): MongoClient {
        val serverApi = ServerApi.builder()
            .version(ServerApiVersion.V1)
            .build()

        val settings = MongoClientSettings.builder()
            .applyConnectionString(ConnectionString(connectionString+databaseName))
            .retryWrites(true)
            .writeConcern(WriteConcern.MAJORITY)
            .serverApi(serverApi)
            .build()

        // Create a new client and connect to the server
        return MongoClients.create(settings)
    }

    @Bean
    fun mongoTemplate(): MongoTemplate {
        return MongoTemplate(mongoClient(), databaseName)
    }
}
