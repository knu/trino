/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.tests.product.deltalake;

import org.testng.annotations.Test;

import static io.trino.tempto.assertions.QueryAssert.Row.row;
import static io.trino.tempto.assertions.QueryAssert.assertQueryFailure;
import static io.trino.tempto.assertions.QueryAssert.assertThat;
import static io.trino.testing.TestingNames.randomNameSuffix;
import static io.trino.tests.product.TestGroups.DELTA_LAKE_DATABRICKS;
import static io.trino.tests.product.TestGroups.DELTA_LAKE_EXCLUDE_73;
import static io.trino.tests.product.TestGroups.PROFILE_SPECIFIC_TESTS;
import static io.trino.tests.product.utils.QueryExecutors.onDelta;
import static io.trino.tests.product.utils.QueryExecutors.onTrino;
import static java.lang.String.format;

public class TestDeltaLakeDatabricksChangeDataFeedCompatibility
        extends BaseTestDeltaLakeS3Storage
{
    @Test(groups = {DELTA_LAKE_DATABRICKS, DELTA_LAKE_EXCLUDE_73, PROFILE_SPECIFIC_TESTS})
    public void testUpdateTableWithCdf()
    {
        String tableName = "test_updates_to_table_with_cdf_" + randomNameSuffix();
        try {
            onDelta().executeQuery("CREATE TABLE default." + tableName + " (col1 STRING, updated_column INT) " +
                    "USING DELTA " +
                    "LOCATION 's3://" + bucketName + "/databricks-compatibility-test-" + tableName + "'" +
                    "TBLPROPERTIES (delta.enableChangeDataFeed = true)");

            onDelta().executeQuery("INSERT INTO default." + tableName + " VALUES ('testValue1', 1)");
            onDelta().executeQuery("INSERT INTO default." + tableName + " VALUES ('testValue2', 2)");
            onDelta().executeQuery("INSERT INTO default." + tableName + " VALUES ('testValue3', 3)");
            onTrino().executeQuery("UPDATE delta.default." + tableName +
                    " SET updated_column = 5 WHERE col1 = 'testValue3'");
            onTrino().executeQuery("UPDATE delta.default." + tableName +
                    " SET updated_column = 4, col1 = 'testValue4' WHERE col1 = 'testValue3'");

            assertThat(onDelta().executeQuery("SELECT col1, updated_column, _change_type, _commit_version " +
                    "FROM table_changes('default." + tableName + "', 0)"))
                    .containsOnly(
                            row("testValue1", 1, "insert", 1L),
                            row("testValue2", 2, "insert", 2L),
                            row("testValue3", 3, "insert", 3L),
                            row("testValue3", 3, "update_preimage", 4L),
                            row("testValue3", 5, "update_postimage", 4L),
                            row("testValue3", 5, "update_preimage", 5L),
                            row("testValue4", 4, "update_postimage", 5L));
        }
        finally {
            onDelta().executeQuery("DROP TABLE IF EXISTS default." + tableName);
        }
    }

    @Test(groups = {DELTA_LAKE_DATABRICKS, DELTA_LAKE_EXCLUDE_73, PROFILE_SPECIFIC_TESTS})
    public void testUpdatePartitionedTableWithCdf()
    {
        String tableName = "test_updates_to_partitioned_table_with_cdf_" + randomNameSuffix();
        try {
            onDelta().executeQuery("CREATE TABLE default." + tableName + " (updated_column STRING, partitioning_column_1 INT, partitioning_column_2 STRING) " +
                    "USING DELTA " +
                    "PARTITIONED BY (partitioning_column_1, partitioning_column_2) " +
                    "LOCATION 's3://" + bucketName + "/databricks-compatibility-test-" + tableName + "'" +
                    "TBLPROPERTIES (delta.enableChangeDataFeed = true)");

            onDelta().executeQuery("INSERT INTO default." + tableName + " VALUES ('testValue1', 1, 'partition1')");
            onDelta().executeQuery("INSERT INTO default." + tableName + " VALUES ('testValue2', 2, 'partition2')");
            onDelta().executeQuery("INSERT INTO default." + tableName + " VALUES ('testValue3', 3, 'partition3')");
            onTrino().executeQuery("UPDATE delta.default." + tableName + " SET updated_column = 'testValue5' WHERE partitioning_column_1 = 3");

            assertThat(onDelta().executeQuery(
                    "SELECT updated_column, partitioning_column_1, partitioning_column_2, _change_type, _commit_version " +
                            "FROM table_changes('default." + tableName + "', 0)"))
                    .containsOnly(
                            row("testValue1", 1, "partition1", "insert", 1L),
                            row("testValue2", 2, "partition2", "insert", 2L),
                            row("testValue3", 3, "partition3", "insert", 3L),
                            row("testValue3", 3, "partition3", "update_preimage", 4L),
                            row("testValue5", 3, "partition3", "update_postimage", 4L));
        }
        finally {
            onDelta().executeQuery("DROP TABLE IF EXISTS default." + tableName);
        }
    }

    @Test(groups = {DELTA_LAKE_DATABRICKS, DELTA_LAKE_EXCLUDE_73, PROFILE_SPECIFIC_TESTS})
    public void testUpdateTableWithManyRowsInsertedInTheSameQueryAndCdfEnabled()
    {
        String tableName = "test_updates_to_table_with_many_rows_inserted_in_one_query_cdf_" + randomNameSuffix();
        try {
            onDelta().executeQuery("CREATE TABLE default." + tableName + " (col1 STRING, updated_column INT) " +
                    "USING DELTA " +
                    "LOCATION 's3://" + bucketName + "/databricks-compatibility-test-" + tableName + "'" +
                    "TBLPROPERTIES (delta.enableChangeDataFeed = true)");

            onDelta().executeQuery("INSERT INTO default." + tableName + " VALUES ('testValue1', 1), ('testValue2', 2), ('testValue3', 3)");
            onTrino().executeQuery("UPDATE delta.default." + tableName + " SET updated_column = 5 WHERE col1 = 'testValue3'");

            assertThat(onDelta().executeQuery("SELECT col1, updated_column, _change_type, _commit_version " +
                    "FROM table_changes('default." + tableName + "', 0)"))
                    .containsOnly(
                            row("testValue1", 1, "insert", 1L),
                            row("testValue2", 2, "insert", 1L),
                            row("testValue3", 3, "insert", 1L),
                            row("testValue3", 3, "update_preimage", 2L),
                            row("testValue3", 5, "update_postimage", 2L));
        }
        finally {
            onDelta().executeQuery("DROP TABLE IF EXISTS default." + tableName);
        }
    }

    @Test(groups = {DELTA_LAKE_DATABRICKS, DELTA_LAKE_EXCLUDE_73, PROFILE_SPECIFIC_TESTS})
    public void testUpdatePartitionedTableWithManyRowsInsertedInTheSameRequestAndCdfEnabled()
    {
        String tableName = "test_updates_to_partitioned_table_with_many_rows_inserted_in_one_query_cdf_" + randomNameSuffix();
        try {
            onDelta().executeQuery("CREATE TABLE default." + tableName + " (updated_column STRING, partitioning_column_1 INT, partitioning_column_2 STRING) " +
                    "USING DELTA " +
                    "PARTITIONED BY (partitioning_column_1, partitioning_column_2) " +
                    "LOCATION 's3://" + bucketName + "/databricks-compatibility-test-" + tableName + "'" +
                    "TBLPROPERTIES (delta.enableChangeDataFeed = true)");

            onDelta().executeQuery("INSERT INTO default." + tableName + " VALUES " +
                    "('testValue1', 1, 'partition1'), " +
                    "('testValue2', 2, 'partition2'), " +
                    "('testValue3', 3, 'partition3')");
            onTrino().executeQuery("UPDATE delta.default." + tableName + " SET updated_column = 'testValue5' WHERE partitioning_column_1 = 3");

            assertThat(onDelta().executeQuery(
                    "SELECT updated_column, partitioning_column_1, partitioning_column_2, _change_type, _commit_version " +
                            "FROM table_changes('default." + tableName + "', 0)"))
                    .containsOnly(
                            row("testValue1", 1, "partition1", "insert", 1L),
                            row("testValue2", 2, "partition2", "insert", 1L),
                            row("testValue3", 3, "partition3", "insert", 1L),
                            row("testValue3", 3, "partition3", "update_preimage", 2L),
                            row("testValue5", 3, "partition3", "update_postimage", 2L));
        }
        finally {
            onDelta().executeQuery("DROP TABLE IF EXISTS default." + tableName);
        }
    }

    @Test(groups = {DELTA_LAKE_DATABRICKS, DELTA_LAKE_EXCLUDE_73, PROFILE_SPECIFIC_TESTS})
    public void testUpdatePartitionedTableCdfEnabledAndPartitioningColumnUpdated()
    {
        String tableName = "test_updates_partitioning_column_in_table_with_cdf_" + randomNameSuffix();
        try {
            onDelta().executeQuery("CREATE TABLE default." + tableName + " (updated_column STRING, partitioning_column_1 INT, partitioning_column_2 STRING) " +
                    "USING DELTA " +
                    "PARTITIONED BY (partitioning_column_1, partitioning_column_2) " +
                    "LOCATION 's3://" + bucketName + "/databricks-compatibility-test-" + tableName + "'" +
                    "TBLPROPERTIES (delta.enableChangeDataFeed = true)");

            onDelta().executeQuery("INSERT INTO default." + tableName + " VALUES " +
                    "('testValue1', 1, 'partition1'), " +
                    "('testValue2', 2, 'partition2'), " +
                    "('testValue3', 3, 'partition3')");
            onTrino().executeQuery("UPDATE delta.default." + tableName +
                    " SET partitioning_column_1 = 5 WHERE partitioning_column_2 = 'partition1'");
            onTrino().executeQuery("UPDATE delta.default." + tableName +
                    " SET partitioning_column_1 = 4, updated_column = 'testValue4' WHERE partitioning_column_2 = 'partition2'");

            assertThat(onDelta().executeQuery(
                    "SELECT updated_column, partitioning_column_1, partitioning_column_2, _change_type, _commit_version " +
                            "FROM table_changes('default." + tableName + "', 0)"))
                    .containsOnly(
                            row("testValue1", 1, "partition1", "insert", 1L),
                            row("testValue2", 2, "partition2", "insert", 1L),
                            row("testValue3", 3, "partition3", "insert", 1L),
                            row("testValue1", 1, "partition1", "update_preimage", 2L),
                            row("testValue1", 5, "partition1", "update_postimage", 2L),
                            row("testValue2", 2, "partition2", "update_preimage", 3L),
                            row("testValue4", 4, "partition2", "update_postimage", 3L));
        }
        finally {
            onDelta().executeQuery("DROP TABLE IF EXISTS default." + tableName);
        }
    }

    @Test(groups = {DELTA_LAKE_DATABRICKS, DELTA_LAKE_EXCLUDE_73, PROFILE_SPECIFIC_TESTS})
    public void testUpdateTableWithCdfEnabledAfterTableIsAlreadyCreated()
    {
        String tableName = "test_updates_to_table_with_cdf_enabled_later_" + randomNameSuffix();
        try {
            onDelta().executeQuery("CREATE TABLE default." + tableName + " (col1 STRING, updated_column INT) " +
                    "USING DELTA " +
                    "LOCATION 's3://" + bucketName + "/databricks-compatibility-test-" + tableName + "'");

            onDelta().executeQuery("INSERT INTO default." + tableName + " VALUES ('testValue1', 1)");
            onDelta().executeQuery("INSERT INTO default." + tableName + " VALUES ('testValue2', 2)");
            onDelta().executeQuery("INSERT INTO default." + tableName + " VALUES ('testValue3', 3)");
            onTrino().executeQuery("UPDATE delta.default." + tableName + " SET updated_column = 5 WHERE col1 = 'testValue3'");
            onDelta().executeQuery("ALTER TABLE default." + tableName + " SET TBLPROPERTIES (delta.enableChangeDataFeed = true)");
            long versionWithCdfEnabled = (long) onDelta().executeQuery("DESCRIBE HISTORY default." + tableName + " LIMIT 1")
                    .row(0)
                    .get(0);
            onTrino().executeQuery("UPDATE delta.default." + tableName + " SET updated_column = 4 WHERE col1 = 'testValue3'");

            assertQueryFailure(() -> onDelta().executeQuery("SELECT col1, updated_column, _change_type, _commit_version " +
                    "FROM table_changes('default." + tableName + "', 0)"))
                    .hasMessageMatching("(?s)(.*Error getting change data for range \\[0 , 6] as change data was not\nrecorded for version \\[0].*)");

            onTrino().executeQuery("INSERT INTO delta.default." + tableName + " VALUES ('testValue6', 6)");
            assertThat(onDelta().executeQuery(
                    format("SELECT col1, updated_column, _change_type, _commit_version FROM table_changes('default.%s', %d)",
                            tableName,
                            versionWithCdfEnabled)))
                    .containsOnly(
                            row("testValue3", 5, "update_preimage", 6L),
                            row("testValue3", 4, "update_postimage", 6L),
                            row("testValue6", 6, "insert", 7L));

            long lastVersionWithCdf = (long) onDelta().executeQuery("DESCRIBE HISTORY default." + tableName + " LIMIT 1")
                    .row(0)
                    .get(0);
            onDelta().executeQuery("ALTER TABLE default." + tableName + " SET TBLPROPERTIES (delta.enableChangeDataFeed = false)");
            onTrino().executeQuery("INSERT INTO delta.default." + tableName + " VALUES ('testValue7', 7)");

            assertThat(onDelta().executeQuery("SELECT col1, updated_column, _change_type, _commit_version " +
                    format("FROM table_changes('default.%s', %d, %d)", tableName, versionWithCdfEnabled, lastVersionWithCdf)))
                    .containsOnly(
                            row("testValue3", 5, "update_preimage", 6L),
                            row("testValue3", 4, "update_postimage", 6L),
                            row("testValue6", 6, "insert", 7L));
        }
        finally {
            onDelta().executeQuery("DROP TABLE IF EXISTS default." + tableName);
        }
    }

    @Test(groups = {DELTA_LAKE_DATABRICKS, DELTA_LAKE_EXCLUDE_73, PROFILE_SPECIFIC_TESTS})
    public void testDeleteFromTableWithCdf()
    {
        String tableName = "test_deletes_from_table_with_cdf_" + randomNameSuffix();
        try {
            onDelta().executeQuery("CREATE TABLE default." + tableName + " (col1 STRING, updated_column INT) " +
                    "USING DELTA " +
                    "LOCATION 's3://" + bucketName + "/databricks-compatibility-test-" + tableName + "'" +
                    "TBLPROPERTIES (delta.enableChangeDataFeed = true)");

            onDelta().executeQuery("INSERT INTO default." + tableName + " VALUES('testValue1', 1)");
            onDelta().executeQuery("INSERT INTO default." + tableName + " VALUES('testValue2', 2)");
            onDelta().executeQuery("INSERT INTO default." + tableName + " VALUES('testValue3', 3)");
            onTrino().executeQuery("DELETE FROM delta.default." + tableName + " WHERE col1 = 'testValue3'");

            assertThat(onDelta().executeQuery("SELECT col1, updated_column, _change_type, _commit_version " +
                    "FROM table_changes('default." + tableName + "', 0)"))
                    .containsOnly(
                            row("testValue1", 1, "insert", 1L),
                            row("testValue2", 2, "insert", 2L),
                            row("testValue3", 3, "insert", 3L),
                            row("testValue3", 3, "delete", 4L));
        }
        finally {
            onDelta().executeQuery("DROP TABLE IF EXISTS default." + tableName);
        }
    }

    @Test(groups = {DELTA_LAKE_DATABRICKS, DELTA_LAKE_EXCLUDE_73, PROFILE_SPECIFIC_TESTS})
    public void testMergeUpdateIntoTableWithCdfEnabled()
    {
        String tableName1 = "test_merge_update_into_table_with_cdf_" + randomNameSuffix();
        String tableName2 = "test_merge_update_into_table_with_cdf_data_table_" + randomNameSuffix();
        try {
            onDelta().executeQuery("CREATE TABLE default." + tableName1 + " (nationkey INT, name STRING, regionkey INT) " +
                    "USING DELTA " +
                    "LOCATION 's3://" + bucketName + "/databricks-compatibility-test-" + tableName1 + "'" +
                    "TBLPROPERTIES (delta.enableChangeDataFeed = true)");
            onDelta().executeQuery("CREATE TABLE default." + tableName2 + " (nationkey INT, name STRING, regionkey INT) " +
                    "USING DELTA " +
                    "LOCATION 's3://" + bucketName + "/databricks-compatibility-test-" + tableName2 + "'" +
                    "TBLPROPERTIES (delta.enableChangeDataFeed = true)");

            onDelta().executeQuery("INSERT INTO default." + tableName1 + " VALUES (1, 'nation1', 100)");
            onDelta().executeQuery("INSERT INTO default." + tableName1 + " VALUES (2, 'nation2', 200)");
            onDelta().executeQuery("INSERT INTO default." + tableName1 + " VALUES (3, 'nation3', 300)");

            onDelta().executeQuery("INSERT INTO default." + tableName2 + " VALUES (1000, 'nation1000', 1000)");
            onDelta().executeQuery("INSERT INTO default." + tableName2 + " VALUES (2, 'nation2', 20000)");
            onDelta().executeQuery("INSERT INTO default." + tableName2 + " VALUES (3000, 'nation3000', 3000)");

            onTrino().executeQuery("MERGE INTO delta.default." + tableName1 + " cdf USING delta.default." + tableName2 + " n " +
                    "ON (cdf.nationkey = n.nationkey) " +
                    "WHEN MATCHED " +
                    "THEN UPDATE SET nationkey = (cdf.nationkey + n.nationkey + n.regionkey) " +
                    "WHEN NOT MATCHED " +
                    "THEN INSERT (nationkey, name, regionkey) VALUES (n.nationkey, n.name, n.regionkey)");

            assertThat(onDelta().executeQuery("SELECT * FROM " + tableName1))
                    .containsOnly(
                            row(1000, "nation1000", 1000),
                            row(3000, "nation3000", 3000),
                            row(1, "nation1", 100),
                            row(3, "nation3", 300),
                            row(20004, "nation2", 200));

            assertThat(onDelta().executeQuery(
                    "SELECT nationkey, name, regionkey, _change_type, _commit_version " +
                            "FROM table_changes('default." + tableName1 + "', 0)"))
                    .containsOnly(
                            row(1, "nation1", 100, "insert", 1),
                            row(2, "nation2", 200, "insert", 2),
                            row(3, "nation3", 300, "insert", 3),
                            row(1000, "nation1000", 1000, "insert", 4),
                            row(3000, "nation3000", 3000, "insert", 4),
                            row(2, "nation2", 200, "update_preimage", 4),
                            row(20004, "nation2", 200, "update_postimage", 4));
        }
        finally {
            onDelta().executeQuery("DROP TABLE IF EXISTS default." + tableName1);
            onDelta().executeQuery("DROP TABLE IF EXISTS default." + tableName2);
        }
    }

    @Test(groups = {DELTA_LAKE_DATABRICKS, DELTA_LAKE_EXCLUDE_73, PROFILE_SPECIFIC_TESTS})
    public void testMergeDeleteIntoTableWithCdfEnabled()
    {
        String tableName1 = "test_merge_delete_into_table_with_cdf_" + randomNameSuffix();
        String tableName2 = "test_merge_delete_into_table_with_cdf_data_table_" + randomNameSuffix();
        try {
            onDelta().executeQuery("CREATE TABLE default." + tableName1 + " (nationkey INT, name STRING, regionkey INT) " +
                    "USING DELTA " +
                    "LOCATION 's3://" + bucketName + "/databricks-compatibility-test-" + tableName1 + "'" +
                    "TBLPROPERTIES (delta.enableChangeDataFeed = true)");
            onDelta().executeQuery("CREATE TABLE default." + tableName2 + " (nationkey INT, name STRING, regionkey INT) " +
                    "USING DELTA " +
                    "LOCATION 's3://" + bucketName + "/databricks-compatibility-test-" + tableName2 + "'" +
                    "TBLPROPERTIES (delta.enableChangeDataFeed = true)");

            onDelta().executeQuery("INSERT INTO default." + tableName1 + " VALUES (1, 'nation1', 100)");
            onDelta().executeQuery("INSERT INTO default." + tableName1 + " VALUES (2, 'nation2', 200)");
            onDelta().executeQuery("INSERT INTO default." + tableName1 + " VALUES (3, 'nation3', 300)");

            onDelta().executeQuery("INSERT INTO default." + tableName2 + " VALUES (1000, 'nation1000', 1000)");
            onDelta().executeQuery("INSERT INTO default." + tableName2 + " VALUES (2, 'nation2', 20000)");
            onDelta().executeQuery("INSERT INTO default." + tableName2 + " VALUES (3000, 'nation3000', 3000)");

            onTrino().executeQuery("MERGE INTO delta.default." + tableName1 + " cdf USING delta.default." + tableName2 + " n " +
                    "ON (cdf.nationkey = n.nationkey) " +
                    "WHEN MATCHED " +
                    "THEN DELETE " +
                    "WHEN NOT MATCHED " +
                    "THEN INSERT (nationkey, name, regionkey) VALUES (n.nationkey, n.name, n.regionkey)");

            assertThat(onDelta().executeQuery("SELECT * FROM " + tableName1))
                    .containsOnly(
                            row(1000, "nation1000", 1000),
                            row(3000, "nation3000", 3000),
                            row(1, "nation1", 100),
                            row(3, "nation3", 300));

            assertThat(onDelta().executeQuery(
                    "SELECT nationkey, name, regionkey, _change_type, _commit_version " +
                            "FROM table_changes('default." + tableName1 + "', 0)"))
                    .containsOnly(
                            row(1, "nation1", 100, "insert", 1),
                            row(2, "nation2", 200, "insert", 2),
                            row(3, "nation3", 300, "insert", 3),
                            row(1000, "nation1000", 1000, "insert", 4),
                            row(3000, "nation3000", 3000, "insert", 4),
                            row(2, "nation2", 200, "delete", 4));
        }
        finally {
            onDelta().executeQuery("DROP TABLE IF EXISTS default." + tableName1);
            onDelta().executeQuery("DROP TABLE IF EXISTS default." + tableName2);
        }
    }

    @Test(groups = {DELTA_LAKE_DATABRICKS, DELTA_LAKE_EXCLUDE_73, PROFILE_SPECIFIC_TESTS})
    public void testMergeMixedDeleteAndUpdateIntoTableWithCdfEnabled()
    {
        String targetTableName = "test_merge_mixed_delete_and_update_into_table_with_cdf_" + randomNameSuffix();
        String sourceTableName = "test_merge_mixed_delete_and_update_into_table_with_cdf_data_table_" + randomNameSuffix();
        try {
            onDelta().executeQuery("CREATE TABLE default." + targetTableName + " (page_id INT, page_url STRING, views INT) " +
                    "USING DELTA " +
                    "LOCATION 's3://" + bucketName + "/databricks-compatibility-test-" + targetTableName + "'" +
                    "TBLPROPERTIES (delta.enableChangeDataFeed = true)");
            onDelta().executeQuery("CREATE TABLE default." + sourceTableName + " (page_id INT, page_url STRING, views INT) " +
                    "USING DELTA " +
                    "LOCATION 's3://" + bucketName + "/databricks-compatibility-test-" + sourceTableName + "'");

            onDelta().executeQuery("INSERT INTO default." + targetTableName + " VALUES (1, 'pageUrl1', 100)");
            onDelta().executeQuery("INSERT INTO default." + targetTableName + " VALUES (2, 'pageUrl2', 200)");
            onDelta().executeQuery("INSERT INTO default." + targetTableName + " VALUES (3, 'pageUrl3', 300)");
            onDelta().executeQuery("INSERT INTO default." + targetTableName + " VALUES (4, 'pageUrl4', 400)");

            onDelta().executeQuery("INSERT INTO default." + sourceTableName + " VALUES (1000, 'pageUrl1000', 1000)");
            onDelta().executeQuery("INSERT INTO default." + sourceTableName + " VALUES (2, 'pageUrl2', 20000)");
            onDelta().executeQuery("INSERT INTO default." + sourceTableName + " VALUES (3000, 'pageUrl3000', 3000)");
            onDelta().executeQuery("INSERT INTO default." + sourceTableName + " VALUES (4, 'pageUrl4000', 4000)");

            onTrino().executeQuery("MERGE INTO delta.default." + targetTableName + " targetTable USING delta.default." + sourceTableName + " sourceTable " +
                    "ON (targetTable.page_id = sourceTable.page_id) " +
                    "WHEN MATCHED AND targetTable.page_id = 2 " +
                    "THEN DELETE " +
                    "WHEN MATCHED AND targetTable.page_id > 2 " +
                    "THEN UPDATE SET views = (targetTable.views + sourceTable.views) " +
                    "WHEN NOT MATCHED " +
                    "THEN INSERT (page_id, page_url, views) VALUES (sourceTable.page_id, sourceTable.page_url, sourceTable.views)");

            assertThat(onDelta().executeQuery("SELECT * FROM " + targetTableName))
                    .containsOnly(
                            row(1000, "pageUrl1000", 1000),
                            row(3000, "pageUrl3000", 3000),
                            row(4, "pageUrl4", 4400),
                            row(1, "pageUrl1", 100),
                            row(3, "pageUrl3", 300));

            assertThat(onDelta().executeQuery(
                    "SELECT page_id, page_url, views, _change_type, _commit_version " +
                            "FROM table_changes('default." + targetTableName + "', 0)"))
                    .containsOnly(
                            row(1, "pageUrl1", 100, "insert", 1),
                            row(2, "pageUrl2", 200, "insert", 2),
                            row(3, "pageUrl3", 300, "insert", 3),
                            row(4, "pageUrl4", 400, "insert", 4),
                            row(1000, "pageUrl1000", 1000, "insert", 5),
                            row(3000, "pageUrl3000", 3000, "insert", 5),
                            row(2, "pageUrl2", 200, "delete", 5),
                            row(4, "pageUrl4", 4400, "update_postimage", 5),
                            row(4, "pageUrl4", 400, "update_preimage", 5));
        }
        finally {
            onDelta().executeQuery("DROP TABLE IF EXISTS default." + targetTableName);
            onDelta().executeQuery("DROP TABLE IF EXISTS default." + sourceTableName);
        }
    }

    @Test(groups = {DELTA_LAKE_DATABRICKS, DELTA_LAKE_EXCLUDE_73, PROFILE_SPECIFIC_TESTS})
    public void testDeleteFromNullPartitionWithCdfEnabled()
    {
        String tableName = "test_delete_from_null_partition_with_cdf_enabled" + randomNameSuffix();
        try {
            onDelta().executeQuery("CREATE TABLE default." + tableName + " (updated_column STRING, partitioning_column_1 INT, partitioning_column_2 STRING) " +
                    "USING DELTA " +
                    "PARTITIONED BY (partitioning_column_1, partitioning_column_2) " +
                    "LOCATION 's3://" + bucketName + "/databricks-compatibility-test-" + tableName + "'" +
                    "TBLPROPERTIES (delta.enableChangeDataFeed = true)");

            onDelta().executeQuery("INSERT INTO default." + tableName + " VALUES " +
                    "('testValue1', 1, 'partition1'), " +
                    "('testValue2', 2, 'partition2'), " +
                    "('testValue3', 3, NULL)");
            onTrino().executeQuery("DELETE FROM delta.default." + tableName +
                    " WHERE partitioning_column_2 IS NULL");

            assertThat(onTrino().executeQuery(
                    "SELECT * FROM delta.default." + tableName
            )).containsOnly(
                    row("testValue1", 1, "partition1"),
                    row("testValue2", 2, "partition2"));

            assertThat(onDelta().executeQuery(
                    "SELECT updated_column, partitioning_column_1, partitioning_column_2, _change_type, _commit_version " +
                            "FROM table_changes('default." + tableName + "', 0)"))
                    .containsOnly(
                            row("testValue1", 1, "partition1", "insert", 1L),
                            row("testValue2", 2, "partition2", "insert", 1L),
                            row("testValue3", 3, null, "insert", 1L),
                            row("testValue3", 3, null, "delete", 2L));
        }
        finally {
            onDelta().executeQuery("DROP TABLE IF EXISTS default." + tableName);
        }
    }
}
