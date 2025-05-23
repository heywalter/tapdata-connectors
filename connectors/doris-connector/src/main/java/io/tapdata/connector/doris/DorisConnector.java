package io.tapdata.connector.doris;

import io.tapdata.common.CommonDbConnector;
import io.tapdata.common.SqlExecuteCommandFunction;
import io.tapdata.connector.doris.bean.DorisConfig;
import io.tapdata.connector.doris.ddl.DorisDDLSqlGenerator;
import io.tapdata.connector.doris.streamload.DorisStreamLoader;
import io.tapdata.connector.doris.streamload.DorisTableType;
import io.tapdata.connector.doris.streamload.HttpUtil;
import io.tapdata.connector.doris.streamload.exception.DorisRetryableException;
import io.tapdata.connector.mysql.bean.MysqlColumn;
import io.tapdata.entity.codec.TapCodecsRegistry;
import io.tapdata.entity.event.ddl.table.*;
import io.tapdata.entity.event.dml.TapRecordEvent;
import io.tapdata.entity.schema.TapField;
import io.tapdata.entity.schema.TapTable;
import io.tapdata.entity.schema.value.*;
import io.tapdata.entity.simplify.TapSimplify;
import io.tapdata.entity.simplify.pretty.BiClassHandlers;
import io.tapdata.entity.utils.DataMap;
import io.tapdata.kit.EmptyKit;
import io.tapdata.kit.ErrorKit;
import io.tapdata.pdk.apis.annotations.TapConnectorClass;
import io.tapdata.pdk.apis.context.TapConnectionContext;
import io.tapdata.pdk.apis.context.TapConnectorContext;
import io.tapdata.pdk.apis.entity.ConnectionOptions;
import io.tapdata.pdk.apis.entity.TestItem;
import io.tapdata.pdk.apis.entity.WriteListResult;
import io.tapdata.pdk.apis.functions.ConnectorFunctions;
import io.tapdata.pdk.apis.functions.PDKMethod;
import io.tapdata.pdk.apis.functions.connection.RetryOptions;
import io.tapdata.pdk.apis.functions.connection.TableInfo;
import io.tapdata.pdk.apis.functions.connector.target.CreateTableOptions;
import org.apache.http.impl.client.CloseableHttpClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * @author jarad
 * @date 7/14/22
 */
@TapConnectorClass("spec_doris.json")
public class DorisConnector extends CommonDbConnector {

    private DorisJdbcContext dorisJdbcContext;
    private DorisConfig dorisConfig;
    private final Map<String, DorisStreamLoader> dorisStreamLoaderMap = new ConcurrentHashMap<>();


    @Override
    public void onStart(TapConnectionContext tapConnectionContext) {
        this.dorisConfig = new DorisConfig().load(tapConnectionContext.getConnectionConfig());
        isConnectorStarted(tapConnectionContext, connectorContext -> dorisConfig.load(connectorContext.getNodeConfig()));
        dorisJdbcContext = new DorisJdbcContext(dorisConfig);
//        if (!dorisJdbcContext.queryVersion().contains("2.")) {
//            dorisConfig.setUpdateSpecific(false);
//            dorisConfig.setUniqueKeyType("Unique");
//        }
        if (tapConnectionContext instanceof TapConnectorContext) {
            ddlSqlGenerator = new DorisDDLSqlGenerator();
        }
        tapLogger = tapConnectionContext.getLog();
        commonDbConfig = dorisConfig;
        jdbcContext = dorisJdbcContext;
        commonSqlMaker = new DorisSqlMaker();
        commonSqlMaker.applyDefault(commonDbConfig.getApplyDefault());
        exceptionCollector = new DorisExceptionCollector();
        fieldDDLHandlers = new BiClassHandlers<>();
        fieldDDLHandlers.register(TapNewFieldEvent.class, this::newField);
        fieldDDLHandlers.register(TapAlterFieldAttributesEvent.class, this::alterFieldAttr);
        fieldDDLHandlers.register(TapAlterFieldNameEvent.class, this::alterFieldName);
        fieldDDLHandlers.register(TapDropFieldEvent.class, this::dropField);
    }


    @Override
    public ConnectionOptions connectionTest(TapConnectionContext connectionContext, Consumer<TestItem> consumer) {
        dorisConfig = new DorisConfig().load(connectionContext.getConnectionConfig());
        ConnectionOptions connectionOptions = ConnectionOptions.create();
        connectionOptions.connectionString(dorisConfig.getConnectionString());
        try (
                DorisTest dorisTest = new DorisTest(dorisConfig, consumer)
        ) {
            dorisTest.testOneByOne();
            return connectionOptions;
        }
    }

    @Override
    public void registerCapabilities(ConnectorFunctions connectorFunctions, TapCodecsRegistry codecRegistry) {

        connectorFunctions.supportBatchCount(this::batchCount);
        connectorFunctions.supportBatchRead(this::batchReadWithoutOffset);
        connectorFunctions.supportQueryByAdvanceFilter(this::queryByAdvanceFilterWithOffset);
        connectorFunctions.supportCountByPartitionFilterFunction(this::countByAdvanceFilter);
        connectorFunctions.supportWriteRecord(this::writeRecord);
        connectorFunctions.supportCreateTableV2(this::createDorisTable);
        connectorFunctions.supportClearTable(this::clearTable);
        connectorFunctions.supportDropTable(this::dropTable);
        connectorFunctions.supportQueryByFilter(this::queryByFilter);
        connectorFunctions.supportExecuteCommandFunction((a, b, c) -> SqlExecuteCommandFunction.executeCommand(a, b, () -> dorisJdbcContext.getConnection(), this::isAlive, c));

        codecRegistry.registerFromTapValue(TapRawValue.class, "text", tapRawValue -> {
            if (tapRawValue != null && tapRawValue.getValue() != null)
                return toJson(tapRawValue.getValue());
            return "null";
        });
        codecRegistry.registerFromTapValue(TapMapValue.class, "text", tapMapValue -> {
            if (tapMapValue != null && tapMapValue.getValue() != null)
                return toJson(tapMapValue.getValue());
            return "null";
        });
        codecRegistry.registerFromTapValue(TapArrayValue.class, "text", tapValue -> {
            if (tapValue != null && tapValue.getValue() != null)
                return toJson(tapValue.getValue());
            return "null";
        });
        codecRegistry.registerFromTapValue(TapBooleanValue.class, "boolean", tapValue -> {
            if (tapValue != null) {
                Boolean value = tapValue.getValue();
                if (value != null && value) {
                    return 1;
                }
            }
            return 0;
        });
        codecRegistry.registerFromTapValue(TapBinaryValue.class, "text", tapValue -> {
            if (tapValue != null && tapValue.getValue() != null && tapValue.getValue().getValue() != null)
                return toJson(tapValue.getValue().getValue());
            return "null";
        });

        //TapTimeValue, TapDateTimeValue and TapDateValue's value is DateTime, need convert into Date object.
        codecRegistry.registerFromTapValue(TapTimeValue.class, "varchar(10)", tapValue -> {
            if (tapValue != null && tapValue.getValue() != null) {
                return tapValue.getValue().toTimeStr();
            }
            return "null";
        });
        codecRegistry.registerFromTapValue(TapYearValue.class, TapValue::getOriginValue);
        codecRegistry.registerFromTapValue(TapDateTimeValue.class, tapDateTimeValue -> {
            if (dorisConfig.getOldVersionTimezone()) {
                return tapDateTimeValue.getValue().toTimestamp();
            } else {
                return tapDateTimeValue.getValue().toInstant().atZone(dorisConfig.getZoneId()).toLocalDateTime();
            }
        });
        codecRegistry.registerFromTapValue(TapDateValue.class, tapDateValue -> tapDateValue.getValue().toSqlDate());
        connectorFunctions.supportErrorHandleFunction(this::errorHandle);
        connectorFunctions.supportGetTableInfoFunction(this::getTableInfo);
        connectorFunctions.supportNewFieldFunction(this::fieldDDLHandler);
        connectorFunctions.supportAlterFieldNameFunction(this::fieldDDLHandler);
        connectorFunctions.supportAlterFieldAttributesFunction(this::fieldDDLHandler);
        connectorFunctions.supportDropFieldFunction(this::fieldDDLHandler);

    }

    protected RetryOptions errorHandle(TapConnectionContext tapConnectionContext, PDKMethod pdkMethod, Throwable throwable) {
        RetryOptions retryOptions = RetryOptions.create();
        if (null != matchThrowable(throwable, DorisRetryableException.class)
                || null != matchThrowable(throwable, IOException.class)) {
            retryOptions.needRetry(true);
            return retryOptions;
        }
        return retryOptions;
    }

    public DorisStreamLoader getDorisStreamLoader() {
        String threadName = Thread.currentThread().getName();
        if (!dorisStreamLoaderMap.containsKey(threadName)) {
            DorisJdbcContext context = new DorisJdbcContext(dorisConfig);
            CloseableHttpClient httpClient;
            if (Boolean.TRUE.equals(dorisConfig.getUseHTTPS())) {
                httpClient = HttpUtil.generationHttpClient();
            } else {
                httpClient = new HttpUtil().getHttpClient();
            }
            DorisStreamLoader dorisStreamLoader = new DorisStreamLoader(context, httpClient);
            dorisStreamLoaderMap.put(threadName, dorisStreamLoader);
        }
        return dorisStreamLoaderMap.get(threadName);
    }

    private void writeRecord(TapConnectorContext connectorContext, List<TapRecordEvent> tapRecordEvents, TapTable tapTable, Consumer<WriteListResult<TapRecordEvent>> writeListResultConsumer) throws Throwable {
        try {
            if (checkStreamLoad()) {
                getDorisStreamLoader().writeRecord(tapRecordEvents, tapTable, writeListResultConsumer);
            } else {
                // TODO: 2023/4/28 jdbc writeRecord
            }
        } catch (Throwable t) {
            dorisStreamLoaderMap.computeIfPresent(Thread.currentThread().getName(), (key, value) -> {
                value.shutdown();
                return null;
            });
            exceptionCollector.collectWritePrivileges("writeRecord", Collections.emptyList(), t);
            throw t;
        }
    }

    protected CreateTableOptions createDorisTable(TapConnectorContext connectorContext, TapCreateTableEvent createTableEvent) throws SQLException {
        TapTable tapTable = createTableEvent.getTable();
        CreateTableOptions createTableOptions = new CreateTableOptions();
        if (jdbcContext.queryAllTables(Collections.singletonList(tapTable.getId())).size() > 0) {
            createTableOptions.setTableExists(true);
            return createTableOptions;
        }
        Collection<String> primaryKeys = tapTable.primaryKeys(true);
        DorisTableType uniqueType = DorisTableType.valueOf(dorisConfig.getUniqueKeyType());
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("CREATE TABLE IF NOT EXISTS ").append(getSchemaAndTable(tapTable.getId())).append("(");
        //generate column definition
        if (uniqueType == DorisTableType.Duplicate) {
            if (EmptyKit.isEmpty(primaryKeys)) {
                if (EmptyKit.isEmpty(dorisConfig.getDuplicateKey())) {
                    stringBuilder.append(commonSqlMaker.buildColumnDefinition(tapTable, true));
                } else {
                    stringBuilder.append(((DorisSqlMaker) commonSqlMaker).buildColumnDefinitionByOrder(tapTable, dorisConfig.getDuplicateKey(), false));
                }
            } else {
                stringBuilder.append(((DorisSqlMaker) commonSqlMaker).buildColumnDefinitionByOrder(tapTable, primaryKeys, false));
            }
        } else {
            if (EmptyKit.isEmpty(primaryKeys)) {
                stringBuilder.append(commonSqlMaker.buildColumnDefinition(tapTable, true));
            } else {
                stringBuilder.append(((DorisSqlMaker) commonSqlMaker).buildColumnDefinitionByOrder(tapTable, primaryKeys, uniqueType == DorisTableType.Aggregate));
            }
        }
        //generate key definition
        stringBuilder.append(") ").append(uniqueType).append(" KEY (`");
        if (EmptyKit.isEmpty(primaryKeys)) {
            if (EmptyKit.isEmpty(dorisConfig.getDuplicateKey())) {
                stringBuilder.append(String.join("`,`", tapTable.getNameFieldMap().keySet()));
            } else {
                stringBuilder.append(String.join("`,`", dorisConfig.getDuplicateKey()));
            }
        } else {
            stringBuilder.append(String.join("`,`", primaryKeys));
        }
        stringBuilder.append("`) DISTRIBUTED BY HASH(`");
        //generate distributed key
        if (EmptyKit.isEmpty(dorisConfig.getDistributedKey())) {
            if (EmptyKit.isEmpty(primaryKeys)) {
                stringBuilder.append(String.join("`,`", tapTable.getNameFieldMap().keySet()));
            } else {
                stringBuilder.append(String.join("`,`", primaryKeys));
            }
        } else {
            stringBuilder.append(String.join("`,`", dorisConfig.getDistributedKey()));
        }
        //generate bucket
        stringBuilder.append("`) BUCKETS ").append(dorisConfig.getBucket()).append(" PROPERTIES(");
        //generate properties
        stringBuilder.append(dorisConfig.getTableProperties().stream().map(v -> "\"" + v.get("propKey") + "\"=\"" + v.get("propValue") + "\"").collect(Collectors.joining(", ")));
        stringBuilder.append(")");
        createTableOptions.setTableExists(false);
        try {
            dorisJdbcContext.execute(stringBuilder.toString());
            return createTableOptions;
        } catch (Exception e) {
            exceptionCollector.collectWritePrivileges("createTable", Collections.emptyList(), e);
            throw new RuntimeException("Create Table " + tapTable.getId() + " Failed | Error: " + e.getMessage() + " | Sql: " + stringBuilder, e);
        }
    }

//    @Override
//    protected CreateTableOptions createTableV2(TapConnectorContext connectorContext, TapCreateTableEvent createTableEvent) throws SQLException {
//        TapTable tapTable = createTableEvent.getTable();
//        CreateTableOptions createTableOptions = new CreateTableOptions();
//        if (jdbcContext.queryAllTables(Collections.singletonList(tapTable.getId())).size() > 0) {
//            createTableOptions.setTableExists(true);
//            return createTableOptions;
//        }
//        String sql;
//        Collection<String> primaryKeys = tapTable.primaryKeys(true);
//        DorisTableType uniqueType = Boolean.TRUE.equals(dorisConfig.getUpdateSpecific()) && DorisTableType.Aggregate.toString().equals(dorisConfig.getUniqueKeyType()) ? DorisTableType.Aggregate : DorisTableType.Unique;
//        long bucket = (EmptyKit.isNotNull(tapTable.getTableAttr()) && EmptyKit.isNotNull((tapTable.getTableAttr().get("capacity")))) ?
//                (Math.min(Long.parseLong(tapTable.getTableAttr().get("capacity").toString()) / 1000000L, 14) + 2) : 16;
//        if (CollectionUtils.isEmpty(primaryKeys)) {
//            //append mode
//            if (EmptyKit.isEmpty(dorisConfig.getDuplicateKey())) {
//                Collection<String> allColumns = tapTable.getNameFieldMap().keySet();
//                sql = "CREATE TABLE IF NOT EXISTS " + getSchemaAndTable(tapTable.getId()) +
//                        "(" + commonSqlMaker.buildColumnDefinition(tapTable, true) + ") " +
//                        uniqueType + " KEY (`" + String.join("`,`", allColumns) + "`) " +
//                        "DISTRIBUTED BY HASH(`" + String.join("`,`", allColumns) + "`) BUCKETS " + bucket + " " +
//                        "PROPERTIES(\"replication_num\" = \"" +
//                        dorisConfig.getReplicationNum().toString() +
//                        "\"" + (uniqueType == DorisTableType.Unique ? ", \"enable_unique_key_merge_on_write\" = \"true\", \"store_row_column\" = \"true\"" : "") + ")";
//            } else {
//                sql = "CREATE TABLE IF NOT EXISTS " + getSchemaAndTable(tapTable.getId()) +
//                        "(" + ((DorisSqlMaker) commonSqlMaker).buildColumnDefinitionByOrder(tapTable, dorisConfig.getDuplicateKey(), false) + ") " +
//                        "DUPLICATE KEY (`" + String.join("`,`", dorisConfig.getDuplicateKey()) + "`) " +
//                        "DISTRIBUTED BY HASH(`" + String.join("`,`", dorisConfig.getDistributedKey()) + "`) BUCKETS " + bucket + " " +
//                        "PROPERTIES(\"replication_num\" = \"" +
//                        dorisConfig.getReplicationNum().toString() +
//                        "\")";
//            }
//        } else {
//            sql = "CREATE TABLE IF NOT EXISTS " + getSchemaAndTable(tapTable.getId()) +
//                    "(" + ((DorisSqlMaker) commonSqlMaker).buildColumnDefinitionByOrder(tapTable, primaryKeys, uniqueType == DorisTableType.Aggregate) + ") " +
//                    uniqueType + " KEY (`" + String.join("`,`", primaryKeys) + "`) " +
//                    "DISTRIBUTED BY HASH(`" + String.join("`,`", primaryKeys) + "`) BUCKETS " + bucket + " " +
//                    "PROPERTIES(\"replication_num\" = \"" +
//                    dorisConfig.getReplicationNum().toString() +
//                    "\"" + (uniqueType == DorisTableType.Unique ? ", \"enable_unique_key_merge_on_write\" = \"true\", \"store_row_column\" = \"true\"" : "") + ")";
//        }
//        createTableOptions.setTableExists(false);
//        try {
//            dorisJdbcContext.execute(sql);
//            return createTableOptions;
//        } catch (Exception e) {
//            exceptionCollector.collectWritePrivileges("createTable", Collections.emptyList(), e);
//            throw new RuntimeException("Create Table " + tapTable.getId() + " Failed | Error: " + e.getMessage() + " | Sql: " + sql, e);
//        }
//    }

    //the second method to load schema instead of mysql
    @Override
    protected void singleThreadDiscoverSchema(List<DataMap> subList, Consumer<List<TapTable>> consumer) throws SQLException {
        List<TapTable> tapTableList = dorisJdbcContext.queryTablesDesc(subList.stream().map(v -> v.getString("tableName")).collect(Collectors.toList()));
        syncSchemaSubmit(tapTableList, consumer);
    }

    protected TapField makeTapField(DataMap dataMap) {
        return new MysqlColumn(dataMap).getTapField();
    }

    @Override
    public void onStop(TapConnectionContext connectionContext) {
        ErrorKit.ignoreAnyError(() -> {
            for (DorisStreamLoader dorisStreamLoader : dorisStreamLoaderMap.values()) {
                if (EmptyKit.isNotNull(dorisStreamLoader)) {
                    dorisStreamLoader.shutdown();
                }
            }
        });
        EmptyKit.closeQuietly(dorisJdbcContext);
    }

    private boolean checkStreamLoad() {
        // TODO: 2023/4/28 check stream load
        return true;
    }

    private TableInfo getTableInfo(TapConnectionContext tapConnectorContext, String tableName) {
        DataMap dataMap = dorisJdbcContext.getTableInfo(tableName);
        TableInfo tableInfo = TableInfo.create();
        tableInfo.setNumOfRows(Long.valueOf(dataMap.getString("TABLE_ROWS")));
        tableInfo.setStorageSize(Long.valueOf(dataMap.getString("DATA_LENGTH")));
        return tableInfo;
    }

    protected void fieldDDLHandler(TapConnectorContext tapConnectorContext, TapFieldBaseEvent tapFieldBaseEvent) throws SQLException {
        List<String> sqlList = fieldDDLHandlers.handle(tapFieldBaseEvent, tapConnectorContext);
        if (null == sqlList) {
            return;
        }
        try {
            jdbcContext.batchExecute(sqlList);
        } catch (SQLException e) {
            if (e.getErrorCode() == 1105 && e.getMessage().contains("Nothing is changed")) {
                return;
            }
            exceptionCollector.collectWritePrivileges("execute sqls: " + TapSimplify.toJson(sqlList), Collections.emptyList(), e);
            throw e;
        }
    }

    @Override
    protected void processDataMap(DataMap dataMap, TapTable tapTable) {
        if (!dorisConfig.getOldVersionTimezone()) {
            for (Map.Entry<String, Object> entry : dataMap.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof LocalDateTime) {
                    if (!tapTable.getNameFieldMap().containsKey(entry.getKey())) {
                        continue;
                    }
                    entry.setValue(((LocalDateTime) value).minusHours(dorisConfig.getZoneOffsetHour()));
                } else if (value instanceof java.sql.Date) {
                    entry.setValue(Instant.ofEpochMilli(((Date) value).getTime()).atZone(ZoneId.systemDefault()).toLocalDateTime());
                } else if (value instanceof String && tapTable.getNameFieldMap().get(entry.getKey()).getDataType().equals("largeint")) {
                    entry.setValue(new BigDecimal((String) value));
                }
            }
        }
    }
}
