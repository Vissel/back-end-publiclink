# PublicLink QA on Windows (Tomcat machine)

QA layout: **all three backend services run standalone** — apiGateway as an
executable WAR (`java -jar`, with `--spring.webflux.base-path=/apiGateway` so
the URL prefix matches production), user and chat-app as executable JARs.
Tomcat serves only the frontend SPA. **publiclink-app QA stays on the Mac**
and joins this stack over the LAN.

| Service | Artifact | Runs | Port | URL |
|---|---|---|---|---|
| apiGateway | `apiGateway.war` | `start-gateway.bat` (standalone) | 8080 | `http://WIN_IP:8080/apiGateway` |
| user | `user-0.0.1-SNAPSHOT.jar` | `start-user.bat` | 8082 | `http://WIN_IP:8082` |
| chat-app | `chat-exec.jar` | `start-chat.bat` | 9090 | `http://WIN_IP:9090` |
| publiclink-app | `publiclink-exec.jar` | Mac (`deploy/mac/run-publiclink-qa.sh`) | 9080 | `http://jade.local:9080` |

`WIN_IP` = this Windows machine's LAN IP. The Mac is addressed **by hostname**
(`jade.local`, its Bonjour name) so the config survives the Mac's IP changing.
MySQL, RabbitMQ and Redis live on this Windows machine; `publiclink-db` stays
on the Mac.

### Resolving the Mac's hostname from Windows

`PUBLICLINK_SERVICE_URL=http://jade.local:9080` only works if Windows can
resolve `jade.local`. This LAN has **no local DNS zone** — the router hands out
ISP DNS servers and no domain option — so `jade`, `jade.lan` and `jade.home`
do not resolve. Pick one:

- **Bonjour for Windows** (recommended, no IP anywhere): install Apple's
  *Bonjour Print Services*, or the Bonjour that ships with iTunes / Apple
  Devices. It adds mDNS to the OS, so `*.local` resolves exactly as on the Mac.
- **hosts entry** (no extra software): add to
  `C:\Windows\System32\drivers\etc\hosts` as administrator —
  `192.168.1.13   jade.local`. Update that one line if the Mac's IP changes.

Verify before starting the gateway:

```bat
ping jade.local
curl http://jade.local:9080/publiclink/api/v1/publish/getTimeZone
```

`start-gateway.bat` runs this reachability check at startup and prints a
warning when the name does not resolve or the Mac app is down. Without it,
every `/publiclink/**` request fails while `/api/v1/auth/**` keeps working —
a routing problem that used to be reported as a misleading
`500 AUTH_001 "Invalid username or password"`.

## 0. Prerequisites

- **JDK 25** — the modules compile with `java.version=25`, so the bytecode
  will not run on an older JRE. `java -version` must report 25.
- **Tomcat 10.1+ or 11** (Jakarta EE) — only needed to serve the frontend SPA
  from `webapps\ROOT`; no backend WAR is deployed into it (see section 9).
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
C:\publiclink\apiGateway.war
C:\publiclink\user-0.0.1-SNAPSHOT.jar
C:\publiclink\chat-exec.jar
C:\publiclink\qa-setup.sql
C:\publiclink\start-gateway.bat
C:\publiclink\start-user.bat
C:\publiclink\start-chat.bat
```

`qa-env.bat` must exist before anything is started: `start-gateway.bat`,
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

## 5. Gateway (standalone, not in Tomcat)

1. If `apiGateway.war` was previously copied into `%CATALINA_HOME%\webapps\`,
   **remove it** (and the exploded `webapps\apiGateway\` folder) — inside
   Tomcat it can only answer 404 (section 9 explains why).
2. Run `C:\publiclink\start-gateway.bat`. It launches the executable WAR with
   `--spring.webflux.base-path=/apiGateway`, so the gateway listens on 8080
   and every URL keeps the `/apiGateway` prefix, exactly like behind nginx in
   production. `SECURITY_SERVICE_URL` on the Mac and the frontend base URL
   need no change.
3. Tomcat is started separately (`startup.bat`) only when you want the
   frontend SPA served from `webapps\ROOT`.

## 6. Firewall

Allow the Mac to reach the three ports (once, from an admin cmd):

```
netsh advfirewall firewall add rule name="PublicLink QA 8080" dir=in action=allow protocol=TCP localport=8080
netsh advfirewall firewall add rule name="PublicLink QA 8082" dir=in action=allow protocol=TCP localport=8082
netsh advfirewall firewall add rule name="PublicLink QA 9090" dir=in action=allow protocol=TCP localport=9090
```

## 7. Start order

1. `start-gateway.bat`
2. `start-user.bat`
3. `start-chat.bat`

The three services are independent; order only matters for the first smoke
test. On the Mac, start publiclink-app with
`deploy/mac/run-publiclink-qa.sh` after the Windows services are up.

## 8. Smoke tests (from the Mac)

```bash
curl http://$WIN_IP:8082/actuator/health                 # user JAR up
curl http://$WIN_IP:9090/chat/api/v1/chat/start -X POST  # chat JAR up
curl http://$WIN_IP:8080/apiGateway/actuator/health      # gateway WAR up
curl http://$WIN_IP:8080/apiGateway/api/v1/auth/public-key   # RSA keys load
```

Then open the QA frontend and log in — the login exercises
browser → gateway → user → publiclink-app end to end.

### The one test that separates routing from auth (from Windows)

```bat
curl http://localhost:8080/apiGateway/actuator/health
curl http://localhost:8080/apiGateway/publiclink/api/v1/publish/getTimeZone
```

- Both answer → the whole chain works.
- Health `200` but the second one `500`/`503` → the gateway cannot reach
  publiclink-app: hostname not resolved, wrong `PUBLICLINK_SERVICE_URL`, or the
  Mac app is down. Nothing to do with credentials. The gateway console prints
  `Global exception handler caught:` naming the real cause
  (`UnknownHostException`, `ConnectException`).
- Since the error-handling fix, an unreachable target answers
  `503 SVC_001 "A downstream service is unreachable"` instead of the old
  misleading `500 AUTH_001 "Invalid username or password"`.

## 9. Why the gateway must not run inside Tomcat

Spring Cloud Gateway is a WebFlux (reactive) application. Spring Boot's
`SpringBootServletInitializer` — the entry point Tomcat uses for WARs — always
creates a **servlet** application context and never registers the reactive
`ServletHttpHandlerAdapter`. The Spring context then starts without errors,
but no WebFlux request mapping, no gateway routes and no actuator web
endpoints load: **every URL under the context answers 404**, including
`/actuator/health`. This is the officially documented limitation ("Spring
Cloud Gateway does not work in a traditional Servlet Container or when built
as a WAR").

Running `java -jar apiGateway.war` avoids the servlet initializer entirely:
SpringApplication creates a proper reactive context and boots an embedded
server from the jars in `WEB-INF/lib-provided`. The
`--spring.webflux.base-path=/apiGateway` argument reproduces the prefix that
nginx strips in production, so all route/security matchers and client URLs
stay unchanged.

## 10. Notes

- The gateway binds 8080 itself (`server.port` + `SERVER_ADDRESS` from
  `qa-env.bat`); user and chat-app keep 8082 / 9090 through their qa profile
  files. Tomcat's connector port is irrelevant to the backends — pick
  anything free for the SPA (e.g. 8081) to avoid clashing with the gateway.
- Everything restarts manually: close the console window and rerun the bat
  script. No auto-restart services.
- Debian GNU/Linux: debianqa/debianqa_password01