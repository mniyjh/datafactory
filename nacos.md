# nacos

下载地址：https://nacos.io/download/nacos-server/?spm=5238cd80.2ef5001f.0.0.3f613b7cqN4qh0

## 部署
1. create schema nacos collate utf8mb4_general_ci;
2. 初始化数据库表
   [mysql-schema.sql](../../nacos/conf/mysql-schema.sql)
3.修改[application.properties](../../nacos/conf/application.properties)
```properties
nacos.console.port=8840

spring.sql.init.platform=mysql

db.num=1
db.url.0=jdbc:mysql://${mysql_host}:${mysql_port}/${nacos_database}?characterEncoding=utf8&connectTimeout=1000&socketTimeout=3000&autoReconnect=true
db.user=${mysql_user}
db.password=${mysql_password}

nacos.core.auth.plugin.nacos.token.secret.key=VGhpc0lzTXlDdXN0b21TZWNyZXRLZXkwMTIzNDU2Nzg=
nacos.core.auth.server.identity.key=nacos
nacos.core.auth.server.identity.value=nacos

```
4. 启动nacos
```shell
# lunux /mac
sh startup.sh -m standalone
# windows
startup.cmd -m standalone
```