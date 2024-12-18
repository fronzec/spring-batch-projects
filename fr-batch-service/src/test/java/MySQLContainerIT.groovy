import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import spock.lang.Shared
import spock.lang.Specification

import java.sql.ResultSet
import java.sql.Statement

@Testcontainers
class MySQLContainerIT extends Specification {

    @Shared
    MySQLContainer mySQLContainer = new MySQLContainer(DockerImageName.parse("mysql:8.2.0"))
            .withDatabaseName("frbatchservicedb")
            .withUsername("theuser")
            .withPassword("thepassword")

    def "waits until database accepts jdbc connections"() {

        given: "a jdbc connection"
        HikariConfig hikariConfig = new HikariConfig()
        hikariConfig.setJdbcUrl(mySQLContainer.jdbcUrl)
        hikariConfig.setUsername("theuser")
        hikariConfig.setPassword("thepassword")
        HikariDataSource ds = new HikariDataSource(hikariConfig)

        when: "querying the database"
        Statement statement = ds.getConnection().createStatement()
        statement.execute("SELECT 1")
        ResultSet resultSet = statement.getResultSet()
        resultSet.next()

        then: "result is returned"
        int resultSetInt = resultSet.getInt(1)
        resultSetInt == 1

        cleanup:
        ds.close()
    }
}
