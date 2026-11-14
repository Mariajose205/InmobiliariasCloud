# API Gateway en AWS — Inmobiliarias Duoc

La API ya no es una aplicación propia. Es un **API Gateway (HTTP API) de AWS** que
hace de puerta de entrada entre el frontend React y el microservicio
`inmobiliaria-service`.

Se eliminaron dos piezas:

| Pieza eliminada | Por qué ya no hace falta |
|---|---|
| `backend/api-gateway/` (Spring Boot, puerto 8080) | API Gateway reenvía las peticiones y valida el token de Microsoft |
| `location /api/` en `frontend-react/nginx.conf` | El navegador llama directo a la URL de AWS, así que no hay proxy ni mismo-origen |

Y se agregó una pieza: la **matriz de roles** se movió al microservicio
(`IdentidadInterceptor`), porque API Gateway no conoce los roles del negocio.

---

## 1. Arquitectura resultante

```
Navegador ──HTTPS──> API Gateway (HTTP API) ──VPC Link──> inmobiliaria-service :8081 ──> PostgreSQL
   (Entra ID)          CORS + JWT nativo          │
                                                   └────> wishlist-service :8082 ──────> PostgreSQL + Kafka
                                                          (favoritos, alertas, novedades)
```

Lo que hace cada capa:

- **API Gateway**: CORS, validación del JWT (Entra ID para el staff, Cognito
  para los clientes), throttling, logs de acceso, y reenvío de la identidad del
  usuario en cabeceras.
- **`inmobiliaria-service`**: decide qué puede hacer cada rol y registra la auditoría.
- **`wishlist-service`**: favoritos, alertas por comuna y novedades; consume Kafka
  y comparte PostgreSQL con el servicio de propiedades.

---

## 2. CORS: configuración y por qué es segura

Está en `CorsConfiguration` de `api-gateway.yaml`. Este es el punto que más se
revisa en una evaluación, así que conviene entender cada decisión:

| Propiedad | Valor | Razón |
|---|---|---|
| `AllowOrigins` | `https://inmobiliariasduoc.duckdns.org`<br>(+ `http://localhost:5173` solo si `IncludeDevOrigin=true`) | Lista explícita, **nunca `*`**. Un `*` dejaría que cualquier sitio de internet llamara a la API usando el token de un usuario que haya iniciado sesión allá. |
| `AllowMethods` | `GET, POST, PUT, DELETE, OPTIONS` | Solo los verbos que usa el catálogo. `OPTIONS` es el método del preflight. |
| `AllowHeaders` | `Authorization, Content-Type, Accept` | Solo las que el frontend envía. `Content-Type` debe declararse porque `application/json` no es un valor *safelisted* de CORS, así que sin esta línea el navegador bloquearía los POST/PUT con `Failed to fetch`. |
| `AllowCredentials` | `false` | La autenticación es un token Bearer en la cabecera `Authorization`, no cookies. Con `true` el navegador exigiría un origen concreto (correcto) pero además adjuntaría cookies en cada llamada sin que la app las use, ampliando el impacto de un eventual CSRF. |
| `ExposeHeaders` | *(omitido)* | El frontend solo lee el código de estado y el cuerpo JSON. No se le permite leer ninguna cabecera propia de la respuesta. |
| `MaxAge` | `3600` | El navegador cachea el preflight una hora y no lo repite antes de cada llamada autenticada. |

Dos detalles deliberados:

- **`X-User` y `X-User-Rol` NO están en `AllowHeaders`.** El navegador no debe poder
  enviarlas. API Gateway las escribe él mismo con los claims del token ya
  validado, pisando cualquier valor del cliente, y por eso la auditoría no se
  puede falsear.
- **El microservicio no declara CORS.** El único cliente legítimo es API Gateway.
  Si los dos declararan CORS habría dos capas compitiendo por las mismas
  cabeceras y los errores serían muy difíciles de depurar.

> Con `AllowCredentials: false` y orígenes explícitos, la especificación exige que
> el navegador acepte la respuesta solo si `Access-Control-Allow-Origin` coincide
> exactamente con el origen de la petición. Un origen no listado recibe la
> respuesta pero el navegador no la entrega al JavaScript.

---

## 3. Requisitos previos (red)

API Gateway es un servicio de AWS, así que para alcanzar el microservicio que
corre en un EC2 hay que habilitar la red privada. Esta es la parte que más trabajo
da y la que conviene tener clara antes de desplegar.

1. **Ambos EC2 en la misma VPC.** Si están en VPC distintas, no hay VPC Link posible.
2. **Security Group del EC2 de backend**: permitir entrada a los puertos **8081
   y 8082** únicamente desde el CIDR de la VPC. **Nunca** `0.0.0.0/0` en 8081 ni
   8082. El 8082 corresponde a `wishlist-service`; si falta, su target group queda
   *unhealthy* y API Gateway responde `503` en todo `/api/wishlist/*`.
3. **Dos subnets en AZ distintas** para las ENIs del VPC Link.

### El NLB no es opcional: lo exige API Gateway

Una integración privada de **HTTP API** no acepta una IP ni un DNS "crudo" como
destino. Segun la documentacion de AWS, `IntegrationUri` debe ser el ARN de
un listener de NLB o ALB, o de un servicio de AWS Cloud Map:

> For an HTTP API private integration, specify the ARN of an Application Load
> Balancer listener, Network Load Balancer listener, or AWS Cloud Map service.

Por eso la plantilla **crea el NLB, el target group y el listener**, y las
integraciones usan `!Ref BackendListener`. Solo hay que entregarle el
`BackendInstanceId` del EC2. Para `wishlist-service` la plantilla crea un
segundo NLB/target group/listener en el puerto 8082 (`WishlistListener`).

```
API Gateway ──VPC Link──> NLB interno (TCP 8081) ──> EC2:8081 inmobiliaria-service
             └──────────> NLB interno (TCP 8082) ──> EC2:8082 wishlist-service
                            target group + health check TCP
```

Aparte de ser un requisito de la API, el balanceador mejora el diseño: con
*health checks*, API Gateway deja de enviar tráfico si el contenedor cae, y el
deregistro automático evita que queden peticiones apuntando a un puerto muerto.
**Un NLB no tiene cargo horario**: solo se cobra el procesamiento de datos, así que
no agrega costo fijo al proyecto.

---

## 4. Despliegue

### Opción A — Consola de AWS

1. **EC2 → Security Groups** → edita el SG del EC2 de backend → entrada TCP 8081
   y 8082 desde el VPC Link o el NLB. Verifica que no haya una regla `0.0.0.0/0`.
2. **VPC → VPC Links** → *Create VPC link*
   - Name: `InmobiliariaVpcLink`
   - Subnets: las dos subnets privadas de AZ distintas
   - Security groups: crea una con salida TCP 8081 hacia el CIDR de la VPC
3. **API Gateway → APIs (HTTP) → Create**
   - Name: `inmobiliarias-duoc-api`
   - En *CORS configuration* pon los orígenes, métodos y cabeceras de la tabla de
     la sección 2. **No actives "Enable CORS"** si ya definiste `CorsConfiguration`.
4. **Authorization → Manage authorizers → Create**
   - Type: **JWT**
   - Name: `azure-entra-jwt`
   - Identity source: `$request.header.Authorization`
   - Issuer: `https://login.microsoftonline.com/680da6eb-42e0-4147-bda4-8c06e4819411/v2.0`
   - Audience: `api://f345fdc4-1687-49d0-b0cc-e0d45eb85731` y
     `f345fdc4-1687-49d0-b0cc-e0d45eb85731`
5. **Routes** — crea una por cada endpoint:

   | Route key | Authorizer | Authorization scopes |
   |---|---|---|
   | `GET /api/propiedades` | **None** | — |
   | `GET /api/propiedades/{id}` | **None** | — |
   | `POST /api/propiedades` | `azure-entra-jwt` | `api://f345fdc4-1687-49d0-b0cc-e0d45eb85731/access_as_user` |
   | `PUT /api/propiedades/{id}` | `azure-entra-jwt` | el mismo scope |
   | `DELETE /api/propiedades/{id}` | `azure-entra-jwt` | el mismo scope |
   | `GET /api/auditoria` | `azure-entra-jwt` | el mismo scope |
   | `GET /api/me` | `azure-entra-jwt` | el mismo scope |
   | `POST /api/informes/{proxy+}` | `azure-entra-jwt` | el mismo scope |
   | `GET /api/admin/{proxy+}` | `azure-entra-jwt` | el mismo scope |
   | `POST /api/admin/{proxy+}` | `azure-entra-jwt` | el mismo scope |
   | `DELETE /api/admin/{proxy+}` | `azure-entra-jwt` | el mismo scope |
   | `GET /api/wishlist/comunas` | **None** | — |
   | `GET /api/wishlist/favoritos` | `azure-entra-jwt` | el mismo scope |
   | `POST /api/wishlist/favoritos` | `azure-entra-jwt` | el mismo scope |
   | `DELETE /api/wishlist/favoritos/{propiedadId}` | `azure-entra-jwt` | el mismo scope |
   | `GET /api/wishlist/alertas` | `azure-entra-jwt` | el mismo scope |
   | `POST /api/wishlist/alertas` | `azure-entra-jwt` | el mismo scope |
   | `DELETE /api/wishlist/alertas/{comuna}` | `azure-entra-jwt` | el mismo scope |
   | `GET /api/wishlist/novedades` | `azure-entra-jwt` | el mismo scope |
   | `DELETE /api/wishlist/novedades/{id}` | `azure-entra-jwt` | el mismo scope |
   | `GET /api/admin/kafka/{proxy+}` | `azure-entra-jwt` | el mismo scope |
   | `POST /api/admin/kafka/{proxy+}` | `azure-entra-jwt` | el mismo scope |
   | `DELETE /api/admin/kafka/{proxy+}` | `azure-entra-jwt` | el mismo scope |
   | `POST /api/admin/notificaciones/{proxy+}` | `azure-entra-jwt` | el mismo scope |

   > Las rutas `{proxy+}` cubren el informe de propiedades
   > (`POST /api/informes/propiedades/pdf`), la administracion del broker
   > (`/api/admin/rabbitmq/...`) y el barrido manual del resumen de novedades
   > (`POST /api/admin/notificaciones/resumen`). Siguen exigiendo el access token
   > de Entra ID; el microservicio aplica ademas el rol (ADMIN/CORREDOR).

   > El scope es lo que impide que un ID Token (que no trae `scp`) o un token de
   > Microsoft Graph se usen como credencial de esta API.

6. **Integrations** — una por método (GET, POST, PUT, DELETE), tipo
   **HTTP proxy**, VPC link `InmobiliariaVpcLink`, *Payload format* `1.0`, y:

   - *Target service*: **ALB/NLB** → el listener del NLB interno en el 8081
     (`BackendListener`) para `/api/propiedades*`, `/api/informes*` y
     `/api/admin/*`; y el listener del NLB en el 8082 (`WishlistListener`) para
     `/api/wishlist/*`, `/api/admin/kafka/*` y `/api/admin/notificaciones/*`.
     No se puede escribir una IP: la API exige el ARN del listener.

     La ruta `POST /api/admin/notificaciones/{proxy+}` es **mas especifica** que
     `POST /api/admin/{proxy+}`, asi que API Gateway la prefiere y el barrido
     manual del resumen (`/api/admin/notificaciones/resumen`) llega al
     wishlist-service (8082) en vez de a inmobiliaria-service (8081).
   - *Request parameters*, con el formato `<accion>:<ubicacion>.<nombre>`:

   | Clave | Valor |
   |---|---|
   | `overwrite:header.X-User` | `$context.authorizer.claims.preferred_username` |
   | `overwrite:header.X-User-Oid` | `$context.authorizer.claims.oid` |
   | `overwrite:header.X-User-Name` | `$context.authorizer.claims.name` |

   `overwrite` es lo que descarta cualquier valor que mande el navegador y lo
   reemplaza por el claim del token validado. Si usaras `append`, el valor del
   cliente quedaria en la cabecera y la auditoria seria falseable.

   Las dos rutas publicas del catalogo usan una integracion **sin** request
   parameters: al no haber autorizador, `$context.authorizer` no existe y mapear
   claims ahi deja variables sin resolver (error 500).

7. **Stages → Create** con stage name `$default` y auto deploy activado.

### Opción B — CloudFormation (recomendada, es lo que hay en el repo)

```bash
aws cloudformation deploy \
  --stack-name api-inmobiliarias \
  --template-file backend/aws/api-gateway.yaml \
  --parameter-overrides \
      VpcId=vpc-12345678 \
      VpcLinkSubnetIds=subnet-aaaa,subnet-bbbb \
      BackendInstanceId=i-1234567890abcdef0 \
      VpcCidr=172.31.0.0/16
```

El NLB, su target group y el listener los crea el propio stack, asi que no hay
que armarlos a mano. Devuelve la salida `ApiInvokeUrl`, que es el valor de
`VITE_API_BASE_URL`.

#### Actualizar un stack existente (change set)

Para un cambio aditivo, como agregar la ruta del resumen de novedades, no hace
falta recrear nada: se reutilizan los parametros que ya tiene el stack y se
revisa el change set **antes** de ejecutarlo. El stack desplegado se llama
`api-inmobiliarias` y vive en `us-east-1`.

```bash
S=api-inmobiliarias; R=us-east-1
cd /tmp
curl -fsSL -o api-gateway.yaml \
  https://raw.githubusercontent.com/Mariajose205/InmobiliariasCloud/main/backend/aws/api-gateway.yaml
grep -c RutaWishlistAdminNotificacionesPost api-gateway.yaml   # debe ser 1

# Parametros actuales del stack (VpcId, subredes, instancia, etc.)
aws cloudformation describe-stacks --stack-name $S --region $R \
  --query 'Stacks[0].Parameters' > params.json

python3 - <<'PY'
import json
inp = {
  "StackName": "api-inmobiliarias",
  "ChangeSetName": "agregar-ruta-notificaciones",
  "ChangeSetType": "UPDATE",
  "TemplateBody": open('/tmp/api-gateway.yaml').read(),
  "Parameters": json.load(open('/tmp/params.json')),
  "Capabilities": ["CAPABILITY_IAM", "CAPABILITY_NAMED_IAM"],
}
json.dump(inp, open('/tmp/changeset.json','w'))
PY

aws cloudformation create-change-set --region $R --cli-input-json file:///tmp/changeset.json
sleep 15
aws cloudformation describe-change-set --stack-name $S --region $R \
  --change-set-name agregar-ruta-notificaciones \
  --query 'Changes[].ResourceChange.[Action,LogicalResourceId,ResourceType]' --output text
```

Con `UPDATE_COMPLETE` + `AVAILABLE` y un unico `Add RutaWishlistAdminNotificacionesPost`,
se aplica y se espera el cierre:

```bash
aws cloudformation execute-change-set --stack-name $S --region $R \
  --change-set-name agregar-ruta-notificaciones
aws cloudformation wait stack-update-complete --stack-name $S --region $R
```

Los stages de HTTP API con `AutoDeploy: true` toman la ruta al instante; no hay
que publicar una version nueva ni tocar el frontend.

Para comprobar que API Gateway enruto por la ruta nueva, el log de acceso
`/aws/apigateway/<api-id>` registra el `routeKey` que hizo match:

```bash
aws logs tail /aws/apigateway/qe8w2nla3a --since 5m --format short \
  | grep -i notificaciones | tail -3
# "routeKey":"POST /api/admin/notificaciones/{proxy+}"  ... "status":"401" (sin token)
```

---

## 5. Conectar el frontend

En `frontend-react/.env` y `.env.production`:

```bash
VITE_API_BASE_URL=https://<api-id>.execute-api.<region>.amazonaws.com
```

Sin barra final. Después hay que reconstruir la imagen del frontend, porque Vite
incorpora esa variable en el bundle en tiempo de build.

**No hay que cambiar nada en Azure Entra ID.** El redirect URI sigue siendo el del
frontend (`https://inmobiliariasduoc.duckdns.org/`); CORS no se registra en
Entra ID, se configura en la API.

---

## 6. Verificación

```bash
API=https://<api-id>.execute-api.<region>.amazonaws.com

# 1. Preflight: debe devolver 204/200 con Access-Control-Allow-Origin exacto
curl -i -X OPTIONS "$API/api/propiedades" \
  -H "Origin: https://inmobiliariasduoc.duckdns.org" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: authorization,content-type"

# 2. Origen NO permitido: la respuesta llega, pero sin el header de permiso,
#    asi que el navegador la descarta.
curl -i "$API/api/propiedades" -H "Origin: https://sitio-malicioso.example"

# 3. Lectura publica: 200 con el catalogo
curl -i "$API/api/propiedades"

# 4. Escritura sin token: 401 de API Gateway (nunca llega al microservicio)
curl -i -X POST "$API/api/propiedades" \
  -H "Content-Type: application/json" -d '{}'
```

Con token real, en la consola del navegador:

| Prueba | Resultado esperado |
|---|---|
| `GET /api/propiedades` sin sesión | 200, se ve el catálogo |
| `POST /api/propiedades` sin sesión | 401 de API Gateway |
| `POST` como ADMIN | 201 y fila en la auditoría con tu correo |
| `POST` como CORREDOR | 201 |
| `PUT` como CORREDOR | 403 con *"…solo puede publicar y eliminar propiedades, no editarlas."* |
| `GET /api/auditoria` como CORREDOR | 403 con *"Solo el administrador puede ver la auditoria."* |
| `GET /api/auditoria` como ADMIN | 200 con el registro |

---

## 7. Clientes públicos con Amazon Cognito

El staff (ADMIN y CORREDOR) entra con Microsoft Entra ID, que es el IDaaS
corporativo. Los clientes del portal no tienen cuenta corporativa, así que se
registran solos en un **User Pool de Amazon Cognito**. Cada público se
autentica contra el proveedor que le corresponde y la API tiene **un authorizer
JWT por proveedor**, aplicado según el prefijo de la ruta:

```
Navegador ──/api/cliente/**──> authorizer Cognito ──┐
   (clientes)                                        ├──> wishlist-service :8082
Navegador ──/api/** (staff)──> authorizer Entra ID ─┘
```

| Recurso | Valor |
|---|---|
| User Pool | `us-east-1_VOjUlX3N7` (`inmobiliarias-clientes`, region `us-east-1`) |
| App client | `5c7ke1lrusghqkkio0682f6qvb` — sin secreto, porque es una SPA |
| Issuer | `https://cognito-idp.us-east-1.amazonaws.com/us-east-1_VOjUlX3N7` |
| Plan del pool | `ESSENTIALS` |
| Authorizer | `ClienteCognitoAuthorizer` (JWT; `Issuer` + `Audience` = app client) |
| Rutas protegidas | las 9 rutas de `/api/cliente/**` |
| Salidas del stack | `ClienteUserPoolId`, `ClienteUserPoolClientId`, `ClienteCognitoIssuer` |

El authorizer valida **issuer y audience**, no solo la firma: un token de otro
User Pool (o de otro app client) se rechaza aunque su firma sea válida.

### 7.1 Por qué el pool es `ESSENTIALS`

El authorizer valida el **access token**, y para agregarle claims propios hace
falta el evento **`V2_0`** del trigger `PreTokenGeneration`. Ese evento solo
existe en los planes Essentials y Plus: `LITE` únicamente soporta `V1_0`, que
personaliza el ID token y nada más. Es el error que se ve como "el login
funciona pero las cabeceras `X-User*` llegan vacías".

Dos detalles de la plantilla que cuestan un change set fallido si se cambian:

- `UserPoolTier: ESSENTIALS` — el enum es **sensible a mayúsculas**;
  `Essentials` lo rechaza la validación temprana de CloudFormation
  (`AWS::EarlyValidation::PropertyValidation`).
- El plan `ESSENTIALS` incluye 10.000 MAU gratis al mes, de sobra para el
  laboratorio.

### 7.2 Flujos del cliente

Sin *hosted UI*: el registro y el login son pantallas propias de la SPA
(`frontend-react/src/pages/Cliente.jsx`) y hablan con Cognito mediante
`amazon-cognito-identity-js` (SRP, la contraseña nunca viaja al servidor).

| Flujo | Llamada de Cognito | Dónde está |
|---|---|---|
| Registro con nombre, apellido y email | `SignUp` → envía código de 6 dígitos y guarda `name`, `given_name`, `family_name` | `registrarCliente()` en `src/cognito/auth.js` |
| Confirmación | `ConfirmSignUp` con el código | `confirmarRegistroCliente()` |
| Login | `InitiateAuth` (`USER_SRP_AUTH`) | `iniciarSesionCliente()` |
| Refresh | `InitiateAuth` (`REFRESH_TOKEN_AUTH`) | `renovarTokenCliente()` (`ensureSesion` interno) |
| Recuperar contraseña | `ForgotPassword` + `ConfirmForgotPassword` | `solicitarRecuperacionPassword()` / `confirmarNuevaPassword()` |

Configuración del app client en la plantilla:

- `GenerateSecret: false` (una SPA no puede guardar un secreto).
- `ExplicitAuthFlows`: `ALLOW_USER_SRP_AUTH`, `ALLOW_REFRESH_TOKEN_AUTH` y
  `ALLOW_USER_PASSWORD_AUTH`. La última está **solo para poder probar el flujo
  con AWS CLI** (`initiate-auth`); el frontend nunca la usa.
- `AccessTokenValidity: 60` minutos, `RefreshTokenValidity: 30` días,
  `EnableTokenRevocation: true`.
- `PreventUserExistenceErrors: ENABLED`: "usuario inexistente" y "contraseña
  incorrecta" devuelven el mismo error, así el formulario no sirve para
  enumerar correos registrados.
- `UsernameAttributes: [email]`: el usuario se identifica por correo. Cognito
  guarda internamente un `sub` (UUID) como *username*, así que los comandos
  administrativos usan ese UUID, no el correo.

### 7.3 El rol vive en el claim `rol`

El `ClienteRolFunction` (trigger `PreTokenGeneration` con `V2_0`) agrega a los
**dos** tokens:

```json
"rol": "CLIENTE", "email": "...", "name": "..."
```

No se puede falsificar desde el navegador: el claim lo escribe Cognito al
emitir el token, y API Gateway copia el claim del token ya validado a la
cabecera `X-User-Rol` con `overwrite:`, es decir **pisa** cualquier valor que
mande el cliente. Por eso `X-User` y `X-User-Rol` no están en `AllowHeaders`
del CORS.

El backend (`IdentidadInterceptor`) normaliza `OPERADOR` → `CORREDOR` y aplica
la matriz por prefijo:

| Prefijo | Rol exigido | Un CLIENTE de Cognito |
|---|---|---|
| `/api/cliente/**` | `CLIENTE` | 200 |
| `/api/admin/**` | `ADMIN` | 401 (lo corta el authorizer de Entra) |
| resto autenticado | `ADMIN` o `CORREDOR` | 401 |

Un cliente que intente `/api/propiedades` (POST) o `/api/auditoria` no llega
ni al microservicio: el authorizer de Entra rechaza su token de Cognito. Y si
llegara, el interceptor responde 403 porque su rol no alcanza.

Cada ruta de `/api/cliente/**` declara además `AuthorizationScopes` con
`aws.cognito.signin.user.admin`. Ese `scope` viaja **solo** en el access token
(claim `scope`), así que un ID token de Cognito —que comparte issuer y
`aud`/`client_id` con el access token— recibe **403** (token válido pero sin el
scope exigido). Sin esa línea el authorizer los aceptaría a los dos, porque no
hay nada en el JWT que indique `token_use` de forma estándar.

### 7.4 Mapeo de cabeceras: `$context.authorizer.claims.*`

En las integraciones de cliente:

```yaml
RequestParameters:
  overwrite:header.X-User: $context.authorizer.claims.email
  overwrite:header.X-User-Oid: $context.authorizer.claims.sub
  overwrite:header.X-User-Name: $context.authorizer.claims.name
  overwrite:header.X-User-Rol: $context.authorizer.claims.rol
```

`$context.authorizer.claims.<claim>` es la variable de contexto de los
authorizers JWT en **HTTP APIs**. La variante `$context.authorizer.jwt.claims.
<claim>` pertenece al evento de una **integración Lambda**: en el mapeo de
parámetros resuelve vacía y las cabeceras llegan sin valor, con lo que el
microservicio responde:

```json
{"error": "Acceso denegado: la peticion no fue validada por el API Gateway."}
```

Las integraciones de staff usan los claims de Entra (`preferred_username`,
`oid`, `name`) y las dos rutas públicas del catálogo no mapean nada: sin
authorizer, `$context.authorizer` no existe y las variables quedarían sin
resolver.

### 7.5 Pruebas con AWS CLI (T02-T05)

```bash
REGION=us-east-1
API=https://qe8w2nla3a.execute-api.us-east-1.amazonaws.com
CLIENT=5c7ke1lrusghqkkio0682f6qvb
POOL=us-east-1_VOjUlX3N7
EMAIL=cliente.demo.1794611290@example.com
PASS='Cliente1234'

# T02 - registro (devuelve CodeDeliveryDetails: el codigo va al correo)
aws cognito-idp sign-up --region $REGION --client-id $CLIENT \
  --username "$EMAIL" --password "$PASS" \
  --user-attributes Name=name,Value="Cliente Demo"

# Confirmacion (admin, equivalente a ConfirmSignUp con el codigo del correo)
aws cognito-idp admin-confirm-sign-up --region $REGION \
  --user-pool-id $POOL --username "$EMAIL"
aws cognito-idp admin-update-user-attributes --region $REGION \
  --user-pool-id $POOL --username "$EMAIL" \
  --user-attributes Name=email_verified,Value=true

# T02 - login y refresh
AUTH=$(aws cognito-idp initiate-auth --region $REGION --client-id $CLIENT \
  --auth-flow USER_PASSWORD_AUTH \
  --auth-parameters "USERNAME=$EMAIL,PASSWORD=$PASS" \
  --query AuthenticationResult --output json)
ACCESS=$(echo "$AUTH" | jq -r .AccessToken)
REFRESH=$(echo "$AUTH" | jq -r .RefreshToken)

aws cognito-idp initiate-auth --region $REGION --client-id $CLIENT \
  --auth-flow REFRESH_TOKEN_AUTH \
  --auth-parameters "REFRESH_TOKEN=$REFRESH" \
  --query 'AuthenticationResult.{Access:AccessToken,Expira:ExpiresIn}'

# T02 - forgot-password
aws cognito-idp forgot-password --region $REGION --client-id $CLIENT \
  --username "$EMAIL"

# T05 - el rol viaja en el claim (no hace falta decodificar a mano)
echo "$ACCESS" | cut -d. -f2 | base64 -d 2>/dev/null | jq .

# T03 - rutas protegidas con access_token
curl -s -o /dev/null -w "%{http_code}\n" \
  -H "Authorization: Bearer $ACCESS" "$API/api/cliente/perfil"      # 200
curl -s -o /dev/null -w "%{http_code}\n" \
  -H "Authorization: Bearer $ACCESS" "$API/api/cliente/favoritos"   # 200

# T04 - sin token
curl -s -o /dev/null -w "%{http_code}\n" "$API/api/cliente/perfil"  # 401

# T05 - el token de cliente NO sirve para el staff
curl -s -o /dev/null -w "%{http_code}\n" \
  -H "Authorization: Bearer $ACCESS" "$API/api/admin/kafka/topicos" # 401

# T05 - el ID token tampoco sirve: no trae el scope del access token
IDT=$(echo "$AUTH" | jq -r .IdToken)
curl -s -o /dev/null -w "%{http_code}\n" \
  -H "Authorization: Bearer $IDT" "$API/api/cliente/perfil"       # 403
```

> Códigos distintos a propósito: **401** es "token ausente, vencido o de otro
> emisor"; **403** es "token válido pero sin el scope que exige la ruta".

Respuesta esperada de `GET /api/cliente/perfil`:

```json
{
  "email": "cliente.demo.1794611290@example.com",
  "nombre": "Cliente Demo",
  "oid": "7438b458-70a1-7054-069e-3583a61141b9",
  "rol": "CLIENTE"
}
```

### 7.6 Rol de IAM del trigger

En una cuenta normal la plantilla crea el rol `ClienteRolFunctionRole`. En
cuentas de laboratorio donde `iam:CreateRole` está denegado, se pasa el ARN de
un rol existente:

```bash
--parameter-overrides ClienteRolLambdaRoleArn=arn:aws:iam::<cuenta>:role/LabRole
```

Con ese parámetro la condicion `CrearRolLambda` es falsa y el trigger usa el rol
prestado. El permiso de invocación (`ClienteRolFunctionPermission`, con
`SourceArn` = ARN del pool) se crea igual.

### 7.7 Frontend

| Archivo | Rol |
|---|---|
| `frontend-react/src/cognito/config.js` | Lee `VITE_COGNITO_*` y crea el `CognitoUserPool` (tokens en `sessionStorage`) |
| `frontend-react/src/cognito/auth.js` | Registro, confirmación, login SRP, refresh, forgot-password y cierre con revocación (tokens en `sessionStorage`) |
| `frontend-react/src/api/apiCliente.js` | Llama a `/api/cliente/**` con `Authorization: Bearer <access_token>` |
| `frontend-react/src/components/PropertyCatalog.jsx` | Botón "Guardar": usa `/api/cliente/favoritos` si hay sesión de cliente y `/api/wishlist/favoritos` si es staff |
| `frontend-react/src/pages/Cliente.jsx` | Formularios y panel del cliente |
| `frontend-react/.env.production` | `VITE_COGNITO_REGION`, `VITE_COGNITO_USER_POOL_ID`, `VITE_COGNITO_CLIENT_ID` |

> **Tokens en el navegador.** El SDK los guarda en `sessionStorage` (no en
> `localStorage`): viven solo en esa pestaña y se borran al cerrarla. Al cerrar
> sesión, `cerrarSesionCliente()` usa `signOut(callback)`, que llama al endpoint
> **Revoke** con el refresh token (habilitado por `EnableTokenRevocation: true`)
> y recién después limpia el storage; así un refresh token robado no sirve
> durante los 30 días de vigencia.

> El ID token **no** se usa para llamar a la API: el authorizer espera un
> access token. Las rutas de `/api/cliente/**` exigen el scope
> `aws.cognito.signin.user.admin`, que solo aparece en el claim `scope` del
> access token; un ID token (aunque comparte issuer y `aud`) no lo trae y
> recibe **403**.

---

## 8. Dashboard del administrador con datos reales

`frontend-react/src/pages/Dashboard.jsx` ya no usa cifras fijas: combina tres
fuentes que se piden en paralelo con `Promise.allSettled` (si una falla, el
resto se sigue mostrando y aparece un aviso).

| Fuente | Qué aporta |
|---|---|
| `GET /api/propiedades` (pública) | Total del catálogo, venta/arriendo, categorías, comunas (`ubicacion`), precios y publicaciones por mes (`fechaCreacion`). |
| `GET /api/admin/estadisticas` (ADMIN) | Favoritos totales, clientes distintos, alertas por comuna, resúmenes de novedades y rankings de propiedades/comunas más guardadas. |
| `GET /api/auditoria` (ADMIN) | Actividad reciente (quién creó/editó/eliminó y cuándo). |

### 8.1 Métricas que calcula el wishlist-service

`EstadisticasService` lee las tablas de favoritos, alertas y resúmenes y
devuelve:

```json
{
  "totalFavoritos": 12,
  "clientesConFavoritos": 3,
  "totalAlertas": 7,
  "clientesConAlertas": 2,
  "totalResumenes": 4,
  "resumenesEnviados": 4,
  "topPropiedadesGuardadas": [{ "propiedadId": 5, "titulo": "Casa en Quillota", "guardados": 4 }],
  "topComunasFavoritos": [{ "comuna": "Quillota", "guardados": 6 }],
  "topComunasAlertas": [{ "comuna": "Quillota", "seguidores": 3 }]
}
```

El cálculo se hace en memoria porque el volumen del laboratorio es chico; con
más datos cada cifra debería pasar a un `count`/`group by` en el repositorio.

### 8.2 Ruta en API Gateway

La ruta exacta gana sobre el comodín `GET /api/admin/{proxy+}` (que apunta a
inmobiliaria-service), así que la petición llega al wishlist-service:

```yaml
RutaAdminEstadisticas:
  Type: AWS::ApiGatewayV2::Route
  Properties:
    ApiId: !Ref InmobiliariaApi
    RouteKey: GET /api/admin/estadisticas
    Target: !Sub 'integrations/${WishlistGetIntegration}'
    AuthorizationType: JWT
    AuthorizerId: !Ref AzureEntraAuthorizer
    AuthorizationScopes:
      - !Ref AuthorizationScopeName
```

`IdentidadInterceptor` vuelve a exigir el rol ADMIN con `X-User-Rol`; un
CORREDOR recibe 403 y sin token la respuesta es 401 del authorizer.

### 8.3 Criterios de las cifras

- **Venta / Arriendo**: comparación sin distinguir mayúsculas sobre
  `tipoOperacion`.
- **Comunas**: se usa `extraerComunaLocal` (misma normalización que el
  catálogo). Las ubicaciones que no calzan se informan aparte, no se agrupan
  como si fueran una comuna.
- **Publicaciones por mes**: últimos 12 meses calendario según
  `fechaCreacion`; el mes actual se resalta.
- **Comisión estimada**: 3 % del valor de las propiedades en venta con precio
  informado. Es una estimación declarada en la tarjeta, no una cifra contable.
- **Precios**: se excluyen precios nulos o cero al promediar; se muestran
  promedios separados para venta y arriendo.

> Para desplegarlo: subir el cambio de `api-gateway.yaml` como change set en
> CloudShell, esperar `CREATE_COMPLETE` y verificar
> `GET /api/admin/estadisticas` con token de staff (200) y sin token (401).
> El frontend se publica solo con el workflow de GitHub Actions.

---

## 9. Problemas frecuentes

| Síntoma | Causa probable | Solución |
|---|---|---|
| `401` en todo, incluso con token bueno | `iss` distinto al esperado | Los **access tokens** de Entra ID usan `iss = https://sts.windows.net/<tenant>/` (con barra final); los ID tokens usan `.../v2.0`. Ajusta el parámetro `AzureIssuer` a la URL de `sts.windows.net`. |
| `403` con `"Missing authentication token"` | `AuthorizationScopes` no calza con el `scp` del token | El scope en el token viaja en forma corta (`access_as_user`). Confirma `AuthorizationScopeName` = `access_as_user` (NO la URI `api://.../access_as_user`). |
| `504` / `Integration timeout` | El NLB no alcanza al backend | Revisa el SG del EC2 (TCP 8081 desde `VpcCidr`) y, en **Target Groups**, que el target esté `healthy`. |
| `503 Service Unavailable` en `/api/wishlist/*` (incluido `/comunas`, que es público) | El NLB de wishlist no tiene targets sanos | Verifica que el contenedor `wishlist-service` exista y esté corriendo en el EC2 (`docker compose -p backend ps`), que escuche en `8082` y que el SG permita TCP 8082 desde `VpcCidr`. En **Target Groups → inmob-wishlist-tg-v1 → Targets** el target debe estar `healthy`. |
| Target `unhealthy` en el NLB de wishlist | La imagen `wishlist-service` no existe, el contenedor no arrancó o el SG bloquea el 8082 | El workflow `backend.yml` debe construir y subir `mariacarrero/wishlist-service:latest`; el compose de infra debe declarar el servicio (Kafka + wishlist). Revisa `docker compose -p backend logs wishlist-service`. |
| Target `unhealthy` en el NLB | El contenedor no escucha en el 8081 o el SG lo bloquea | `docker compose logs inmobiliaria-service`. Verifica que en el compose el puerto siga publicado como `8081:8081`. |
| `Unsupported property [VpcLinkId]` | Se uso la propiedad vieja | En `AWS::ApiGatewayV2::Integration` la propiedad se llama `ConnectionId`, no `VpcLinkId`. |
| `Invalid template parameter property 'MinItems'` | `MinItems`/`MaxItems` no existen para parámetros | Solo aplican a propiedades de recursos. En un `List<...>` el requisito de "2 subnets" lo valida el propio VPC Link al crearse. |
| `500` en toda llamada | El microservicio no arrancó | `docker compose logs inmobiliaria-service`. Ojo: en producción rechaza peticiones sin `X-User` con un 403 explícito, no un 500. |
| Error CORS en el navegador pero `curl` funciona | Origen no listado, o falta declarar `Content-Type` | Revisa `AllowOrigins` y `AllowHeaders` en la configuración de la API. |
| La auditoría dice `desconocido` | El token no trae `preferred_username` | Falls back al `oid`. Revisa `X-User-Oid` en el mapeo de la integración. |
| `Network Link for VPC Link ... not found` | Subnets en VPC equivocada | `VpcLinkSubnetIds` debe ser de la VPC del EC2 de backend. |
| `403` `"Acceso denegado: la peticion no fue validada por el API Gateway."` en `/api/cliente/**` con token válido | Las integraciones mapean `$context.authorizer.jwt.claims.*` (vacío en HTTP APIs) | Usar `$context.authorizer.claims.<claim>`. Verificar con `aws apigatewayv2 get-integrations --api-id <id> --query "Items[?RequestParameters]"`. |
| El login de Cognito funciona pero el access token no trae `rol` | El trigger `PreTokenGeneration` está en `V1_0` | Cambiar a `LambdaVersion: V2_0` (requiere `UserPoolTier: ESSENTIALS`) y volver a iniciar sesión: el claim se escribe al emitir el token, los tokens ya emitidos no cambian. |
| `Essentials is not a valid enum value` al crear el change set | El enum del `UserPoolTier` es sensible a mayúsculas | `UserPoolTier: ESSENTIALS`. El detalle de la validación se ve con `aws cloudformation describe-events --stack-name <stack> --change-set-name <cs> --filters FailedEvents=true`. |
| `AlreadyExists` / `NotAuthorizedException` en `sign-up` | El correo ya está registrado o se usó `admin-create-user` | Usar un correo nuevo (`cliente.demo.<epoch>@example.com`). Con `UsernameAttributes: email`, el *username* interno es un UUID: los comandos admin necesitan ese UUID o el correo, no el nombre. |
| `InvalidParameterException: Missing required parameter USERNAME` en `admin-confirm-sign-up` | Se pasó el UUID como `--username` cuando el pool identifica por email | Pasar el correo en `--username`. |
| La SPA dice que la sesión expiró al cambiar de página | Se leyó `getSignInUserSession()`, que es `null` en el `CognitoUser` nuevo que devuelve `getCurrentUser()` en cada navegación | Usar `usuario.getSession(callback)`: reconstruye la sesión desde `sessionStorage` y refresca solo si el access token venció. |
| Tras cerrar sesión, un `POST` a `cognito-idp` responde `400` y la UI sigue pidiendo datos del cliente | El refresh token fue revocado, pero quedaron los tokens del SDK en `sessionStorage`; `getCurrentUser()` seguía devolviendo usuario y cada llamada reintentaba el refresh | `asegurarSesion()` y `cerrarSesionCliente()` limpian las claves `CognitoIdentityServiceProvider.<clientId>.*`; el error viaja con `code = SESSION_EXPIRED` y el panel vuelve al formulario de ingreso. |
| El login responde `No se pudo guardar la sesión en este navegador`, o entra y vuelve al login | Los `CognitoUser` creados a mano no heredan `Storage` del pool: el SDK guarda los tokens en `localStorage` y `getCurrentUser()` los busca en `sessionStorage` | Crear los usuarios con `Storage: userPool.storage` (helper `crearUsuario` de `auth.js`). Al cargar `auth.js` se borran los tokens heredados de `localStorage`. |

---

## 10. Costo aproximado

Una HTTP API cobra **USD 1,00 por millón de peticiones** (el free tier cubre
1 millón al mes durante los primeros 12 meses) más la transferencia de salida. No
hay cargos por el VPC Link.

Para el volumen de este proyecto queda dentro del free tier. El throttling del
stage (100 rps sostenidos, 200 en ráfaga) protege contra abuso accidental.

---

## 11. Archivos relacionados

| Archivo | Rol |
|---|---|
| `backend/aws/api-gateway.yaml` | Plantilla de la API completa |
| `backend/inmobiliaria-service/.../security/IdentidadInterceptor.java` | Matriz de roles |
| `backend/inmobiliaria-service/.../security/SeguridadProperties.java` | Emails y oids de ADMIN y CORREDOR |
| `backend/inmobiliaria-service/.../controller/MeController.java` | `GET /api/me` |
| `backend/inmobiliaria-service/.../config/ApiGatewayWebConfig.java` | Registro del interceptor (sin CORS) |
| `backend/wishlist-service/.../security/IdentidadInterceptor.java` | Misma matriz, aplicada a `/api/cliente/**` |
| `backend/wishlist-service/.../controller/PerfilClienteController.java` | `GET /api/cliente/perfil` |
| `backend/wishlist-service/.../config/ApiGatewayWebConfig.java` | Registro del interceptor para `/api/cliente/**` |
| `backend/wishlist-service/.../service/EstadisticasService.java` | Cálculo de favoritos, alertas y resúmenes para el dashboard |
| `backend/wishlist-service/.../controller/admin/EstadisticasAdminController.java` | `GET /api/admin/estadisticas` |
| `frontend-react/src/pages/Dashboard.jsx` | Dashboard del admin con datos reales (catálogo + wishlist + auditoría) |
| `frontend-react/src/cognito/auth.js` | Flujos de Cognito (SRP, refresh, forgot-password) |
| `DIAGRAMA_ARQUITECTURA.md` | Diagramas y tabla de responsabilidades |
