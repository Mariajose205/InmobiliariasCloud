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
   (Entra ID)          CORS + JWT nativo                    matriz de roles + auditoría
```

Lo que hace cada capa:

- **API Gateway**: CORS, validación del access token de Azure Entra ID, throttling,
  logs de acceso, y reenvío de la identidad del usuario en cabeceras.
- **`inmobiliaria-service`**: decide qué puede hacer cada rol y registra la auditoría.

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
2. **Security Group del EC2 de backend**: permitir entrada al puerto **8081**
   únicamente desde el CIDR de la VPC. **Nunca** `0.0.0.0/0` en 8081.
3. **Dos subnets en AZ distintas** para las ENIs del VPC Link.

### El NLB no es opcional: lo exige API Gateway

Una integración privada de **HTTP API** no acepta una IP ni un DNS "crudo" como
destino. Segun la documentacion de AWS, `IntegrationUri` debe ser el ARN de
un listener de NLB o ALB, o de un servicio de AWS Cloud Map:

> For an HTTP API private integration, specify the ARN of an Application Load
> Balancer listener, Network Load Balancer listener, or AWS Cloud Map service.

Por eso la plantilla **crea el NLB, el target group y el listener**, y las
integraciones usan `!Ref BackendListener`. Solo hay que entregarle el
`BackendInstanceId` del EC2.

```
API Gateway ──VPC Link──> NLB interno (TCP 8081) ──> EC2:8081
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
   desde el VPC Link o el NLB. Verifica que no haya una regla `0.0.0.0/0`.
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

   > El scope es lo que impide que un ID Token (que no trae `scp`) o un token de
   > Microsoft Graph se usen como credencial de esta API.

6. **Integrations** — una por método (GET, POST, PUT, DELETE), tipo
   **HTTP proxy**, VPC link `InmobiliariaVpcLink`, *Payload format* `1.0`, y:

   - *Target service*: **ALB/NLB** → el listener del NLB interno en el 8081.
     No se puede escribir una IP: la API exige el ARN del listener.
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
  --stack-name inmobiliarias-api \
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

## 7. Problemas frecuentes

| Síntoma | Causa probable | Solución |
|---|---|---|
| `401` en todo, incluso con token bueno | `iss` distinto al esperado | Los **access tokens** de Entra ID usan `iss = https://sts.windows.net/<tenant>/` (con barra final); los ID tokens usan `.../v2.0`. Ajusta el parámetro `AzureIssuer` a la URL de `sts.windows.net`. |
| `403` con `"Missing authentication token"` | `AuthorizationScopes` no calza con el `scp` del token | El scope en el token viaja en forma corta (`access_as_user`). Confirma `AuthorizationScopeName` = `access_as_user` (NO la URI `api://.../access_as_user`). |
| `504` / `Integration timeout` | El NLB no alcanza al backend | Revisa el SG del EC2 (TCP 8081 desde `VpcCidr`) y, en **Target Groups**, que el target esté `healthy`. |
| Target `unhealthy` en el NLB | El contenedor no escucha en el 8081 o el SG lo bloquea | `docker compose logs inmobiliaria-service`. Verifica que en el compose el puerto siga publicado como `8081:8081`. |
| `Unsupported property [VpcLinkId]` | Se uso la propiedad vieja | En `AWS::ApiGatewayV2::Integration` la propiedad se llama `ConnectionId`, no `VpcLinkId`. |
| `Invalid template parameter property 'MinItems'` | `MinItems`/`MaxItems` no existen para parámetros | Solo aplican a propiedades de recursos. En un `List<...>` el requisito de "2 subnets" lo valida el propio VPC Link al crearse. |
| `500` en toda llamada | El microservicio no arrancó | `docker compose logs inmobiliaria-service`. Ojo: en producción rechaza peticiones sin `X-User` con un 403 explícito, no un 500. |
| Error CORS en el navegador pero `curl` funciona | Origen no listado, o falta declarar `Content-Type` | Revisa `AllowOrigins` y `AllowHeaders` en la configuración de la API. |
| La auditoría dice `desconocido` | El token no trae `preferred_username` | Falls back al `oid`. Revisa `X-User-Oid` en el mapeo de la integración. |
| `Network Link for VPC Link ... not found` | Subnets en VPC equivocada | `VpcLinkSubnetIds` debe ser de la VPC del EC2 de backend. |

---

## 8. Costo aproximado

Una HTTP API cobra **USD 1,00 por millón de peticiones** (el free tier cubre
1 millón al mes durante los primeros 12 meses) más la transferencia de salida. No
hay cargos por el VPC Link.

Para el volumen de este proyecto queda dentro del free tier. El throttling del
stage (100 rps sostenidos, 200 en ráfaga) protege contra abuso accidental.

---

## 9. Archivos relacionados

| Archivo | Rol |
|---|---|
| `backend/aws/api-gateway.yaml` | Plantilla de la API completa |
| `backend/inmobiliaria-service/.../security/IdentidadInterceptor.java` | Matriz de roles |
| `backend/inmobiliaria-service/.../security/SeguridadProperties.java` | Emails y oids de ADMIN y CORREDOR |
| `backend/inmobiliaria-service/.../controller/MeController.java` | `GET /api/me` |
| `backend/inmobiliaria-service/.../config/ApiGatewayWebConfig.java` | Registro del interceptor (sin CORS) |
| `DIAGRAMA_ARQUITECTURA.md` | Diagramas y tabla de responsabilidades |
