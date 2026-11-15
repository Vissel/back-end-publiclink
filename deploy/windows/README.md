# PublicLink QA on Windows (Tomcat machine)

QA layout: **apiGateway runs as a WAR inside Tomcat** (context path
`/apiGateway` — Tomcat strips it exactly like nginx strips the prefix in
production), while **user and chat-app run as executable JARs** — the same
Netty runtime they use in production. **publiclink-app QA stays on the Mac**
and joins this stack over the LAN.

| Service | Artifact | Runs | Port | URL |
|---|---|---|---|---|
| apiGateway | `apiGateway.war` | Tomcat `webapps\apiGateway.war` | 8080 | `http://WIN_IP:8080/apiGateway` |
| user | `user-0.0.1-SNAPSHOT.jar` | `start-user.bat` | 8082 | `http://WIN_IP:8082` |
| chat-app | `chat-exec.jar` | `start-chat.bat` | 9090 | `http://WIN_IP:9090` |
| publiclink-app | `publiclink-exec.jar` | Mac (`deploy/mac/run-publiclink-qa.sh`) | 9080 | `http://MAC_IP:9080` |

`WIN_IP` = this Windows machine's LAN IP, `MAC_IP` = the Mac's LAN IP. MySQL,
RabbitMQ and Redis live on this Windows machine; `publiclink-db` stays on the
Mac.

## 0. Prerequisites

- **JDK 25** — the modules compile with `java.version=25`, so the bytecode
  will not run on an older JRE. `java -version` must report 25.
- **Tomcat 10.1+ or 11** (Jakarta EE) — the WAR is built against
  `jakarta.servlet`; Tomcat 9 or older will fail to deploy it.
- MySQL, RabbitMQ and Redis already installed on this machine.

## 1. Build on the Mac

```bash
cd VietQrApplication
mvn -pl apiGateway,user -am package -DskipTests
mvn -Pqa -pl chat-app,publiclink-app -am package -DskipTests
```

Artifacts:

- `apiGateway/target/apiGateway.war`
- `user/target/user-0.0.1-SNAPSHOT.jar`
- `chat-app/target/chat-exec.jar` — qa profile baked in at build time
- `publiclink-app/target/publiclink-exec.jar` — stays on the Mac

## 2. Layout on this machine

```
C:\publiclink\qa-env.bat                     (renamed from qa-env.bat.example, fill in)
C:\publiclink\keys\auth260222.pem            (from apiGateway/src/main/resources/auth-service/)
C:\publiclink\keys\pubauth.pem               (from apiGateway/src/main/resources/auth-service/)
C:\publiclink\user-0.0.1-SNAPSHOT.jar
C:\publiclink\chat-exec.jar
C:\publiclink\qa-setup.sql
C:\publiclink\start-user.bat
C:\publiclink\start-chat.bat
```

`qa-env.bat` must exist before anything is started: Tomcat's `setenv.bat`,
`start-user.bat` and `start-chat.bat` all call it. Fill in `JWT_KEY` (same
value the Mac uses), `RABBITMQ_PASSWORD` and the two LAN IPs.

## 3. MySQL schemas

```
mysql -u root -p < C:\publiclink\qa-setup.sql
```

Creates `user_schema` + `chat_schema` and the shared `newuser03` account that
both services' `application-qa.properties` log in with. Each service migrates
its own schema on first start (chat via Liquibase, user via
Hibernate `ddl-auto=update`).

## 4. RabbitMQ QA account

The built-in `guest` user may only connect from localhost, and
**publiclink-app on the Mac joins the same broker remotely** — payment and
notification events cross between the two services. Create a dedicated
account and use it in `qa-env.bat` and in the Mac runner script:

```
rabbitmqctl add_user publiclink <password>
rabbitmqctl set_permissions -p / publiclink ".*" ".*" ".*"
```

## 5. Tomcat + gateway WAR

1. Copy `apiGateway.war` into `%CATALINA_HOME%\webapps\` — Tomcat derives the
   context path from the file name, so the gateway is served at `/apiGateway`
   while `webapps\ROOT` keeps whatever lives there.
2. Install the environment hook:
   `copy setenv.bat.example %CATALINA_HOME%\bin\setenv.bat` — the gateway WAR
   then receives every `qa-env.bat` variable (JWT key, RSA key paths, service
   URLs, RabbitMQ, Redis).
3. The default `server.xml` connector (port 8080, all interfaces) is correct.
4. Start Tomcat: `%CATALINA_HOME%\bin\startup.bat`.

## 6. Firewall

Allow the Mac to reach the three ports (once, from an admin cmd):

```
netsh advfirewall firewall add rule name="PublicLink QA 8080" dir=in action=allow protocol=TCP localport=8080
netsh advfirewall firewall add rule name="PublicLink QA 8082" dir=in action=allow protocol=TCP localport=8082
netsh advfirewall firewall add rule name="PublicLink QA 9090" dir=in action=allow protocol=TCP localport=9090
```

## 7. Start order

1. Tomcat (gateway) — `startup.bat`
2. `start-user.bat`
3. `start-chat.bat`

The three services are independent; order only matters for the first smoke
test. On the Mac, start publiclink-app with
`deploy/mac/run-publiclink-qa.sh` after the Windows services are up.

## 8. Smoke tests (from the Mac)

```bash
curl http://WIN_IP:8082/actuator/health                 # user JAR up
curl http://WIN_IP:9090/chat/api/v1/chat/start -X POST  # chat JAR up
curl http://WIN_IP:8080/apiGateway/actuator/health      # gateway WAR up
curl http://WIN_IP:8080/apiGateway/api/v1/auth/public-key   # RSA keys load
```

Then open the QA frontend and log in — the login exercises
browser → gateway → user → publiclink-app end to end.

## 9. Fallback: gateway as standalone JAR

Spring Cloud Gateway officially targets Netty. Plain HTTP routing on Tomcat
is what this stack needs, but if the WAR ever misbehaves, run it standalone:

```
java -jar C:\publiclink\apiGateway.war
```

The executable WAR carries the provided Tomcat jars, so it boots Netty on
port 8080 with context path `/`. Update `SECURITY_SERVICE_URL` on the Mac
(drop the `/apiGateway` suffix) and the frontend base URL accordingly.

## 10. Notes

- `server.port` / `server.address` are ignored inside the WAR — the Tomcat
  connector (8080) applies. user and chat-app keep 8082 / 9090 through their
  qa profile files.
- Everything restarts manually: close the console window and rerun the bat
  script, Tomcat via `shutdown.bat` / `startup.bat`. No auto-restart services.
