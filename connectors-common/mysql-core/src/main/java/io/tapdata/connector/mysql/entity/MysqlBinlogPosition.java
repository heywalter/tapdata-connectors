package io.tapdata.connector.mysql.entity;

import java.io.Serializable;

/**
 * @author samuel
 * @Description
 * @create 2022-05-23 21:04
 **/
public class MysqlBinlogPosition implements Serializable {
    private String filename;

    private long position;

    private String gtidSet;

    public MysqlBinlogPosition() {
    }

    public MysqlBinlogPosition(String filename, long position) {
        this.filename = filename;
        this.position = position;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public long getPosition() {
        return position;
    }

    public void setPosition(long position) {
        this.position = position;
    }

    public String getGtidSet() {
        return gtidSet;
    }

    public void setGtidSet(String gtidSet) {
        this.gtidSet = gtidSet;
    }

    public MysqlBinlogPosition clone() {
        MysqlBinlogPosition mysqlBinlogPosition = new MysqlBinlogPosition(filename, position);
        mysqlBinlogPosition.setGtidSet(gtidSet);
        return mysqlBinlogPosition;
    }
}
