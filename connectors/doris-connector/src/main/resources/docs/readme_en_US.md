## **Connection configuration help**
### **1.  Doris installation instructions**
Please follow the instructions below to ensure that the Doris database is successfully added and used in Tapdata.
### **2.  Supported versions**
Doris 1.x、Doris 2.x
### **3.  Prerequisites**
#### **3.1 Create Doris account**
```
//Create user
create user 'username'@'localhost' identified with mysql_native_password by 'password';
//Change password
alter user 'username'@'localhost' identified with mysql_native_password by 'password';
```
#### **3.2 Authorization of tapdata account**
Assign select permission to a database
```
GRANT SELECT, SHOW VIEW, CREATE ROUTINE, LOCK TABLES ON <DATABASE_NAME>.< TABLE_NAME> TO 'tapdata' IDENTIFIED BY 'password';
```
Permissions for global
```
GRANT RELOAD, SHOW DATABASES, REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO 'tapdata' IDENTIFIED BY 'password';
```
#### **3.3 Constraint description**
```
When synchronizing from Doris to other heterogeneous databases, if the source Doris has a table cascade setting, the data updates and deletions generated by the cascade trigger will not be delivered to the target. If you need to build a cascade processing capability at the target end, you can achieve this type of data synchronization through triggers and other means depending on the target situation.
```
###  **4.  Prerequisites (as targets)**
Assign all permissions to a database
```
GRANT ALL PRIVILEGES ON <DATABASE_NAME>.< TABLE_NAME> TO 'tapdata' IDENTIFIED BY 'password';
```
Permissions for global
```
GRANT PROCESS ON *.* TO 'tapdata' IDENTIFIED BY 'password';
```
### **5. Notes on table building**
```
Due to Doris being written as the target using the Stream Load method, the following are some precautions when creating a table
```
#### **5.1 Duplicate**
```
The duplicate method of creating a table represents a repeatable pattern. In non append write mode, the default sorting key is the update condition field. In append write mode, if there is no update condition field, it is manually configured

Updating an event in this mode will add a new record and will not overwrite the original record. Deleting an event will delete all identical records that meet the conditions. Therefore, it is recommended to use it in append write mode
```
#### **5.2 Aggregate**
```
The Aggregate method for creating tables represents the aggregation mode. By default, the update condition field is used as the aggregation key, while non aggregation keys use Replace If Not null

In this mode, the performance of updating a large number of events is better, but the drawback is that it cannot take effect on events with Set Nulls. Additionally, deleting events is not supported. Therefore, it is recommended to use scenarios without physical deletion and update events without Set Nulls, as well as scenarios with incomplete data fields on the source side
```
#### **5.3 Unique**
```
The Unique method of creating a table represents a unique key pattern, with the default setting of updating the condition field as the unique key. The usage method is consistent with the vast majority of relational data sources

If there are a large number of update events and the updated fields are constantly changing, it is recommended that the source end use full field completion to ensure considerable performance
```
###  **6.  Common errors**
Unknown error 1044
If the permission has been granted, but the connection cannot be tested through tapdata, you can check and repair it through the following steps
```
SELECT host,user,Grant_ priv,Super_ priv FROM Doris.user where user='username';
//View Grant_ Whether the value of priv field is Y
//If not, execute the following command
UPDATE Doris.user SET Grant_ priv='Y' WHERE user='username';
FLUSH PRIVILEGES;
```
