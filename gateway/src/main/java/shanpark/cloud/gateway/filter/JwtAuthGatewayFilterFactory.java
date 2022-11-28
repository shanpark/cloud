package shanpark.cloud.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import shanpark.cloud.gateway.consts.ResultCode;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * JWT 인증 토큰을 이용하여 인증을 수행하는 Filter.
 * <p>
 * 정상적인 JWT 토큰이 확인된 경우 요청을 통과시키고 그렇지않은 경우 통과시키지 않는다.
 * <p>
 *  - JWT 토큰에서 claims 정보를 가져와서 JSON 포맷으로 serialize 한 후 base64 encoding 하여 request 헤더에 추가하여
 *    downstream 으로 보낸다.
 *  - application.yml 에서 설정하는 configuration property는 다음과 같다.
 *    - cookieName: JWT가 들어있는 쿠키의 키값. (default: "jwt")
 *    - secretKey: JWT를 parsing 하기 위해 필요한 secret key 문자열. (default: "Ke5r!tW*teiif3xm6d#nh_afeijfv39ru!68h-r!ewijfsajif8ewjh5dw2m2dwk")
 *    - headerName: downstream으로 인증 정보를 보내기위한 header name. null이면 header를 보내지 않는다. (default: null)
 *    - claims: 위 header를 생성할 때 담아낼 JWT의 claims 정보로부터 뽑아낼 필드 목록. (default: sub)
 *  - 아래는 filter 설정이다.
 *    - roles: filter를 통과하기 위해서 필요한 role list. null 이거나 empty 이면 검사하지 않는다. (default: null)
 *             이 값을 사용하려면 JWT의 claims에는 'roles' 필드가 반드시 포함되어야 한다.
 *    - unauthorizable: 미인증 상태로 요청을 하더라도 로그에 남기지 않는다. 원래 인증 상태를 점검하기 위한 요청들을 여기에 등록한다. (default: null)
 */
@Slf4j
@Component
public class JwtAuthGatewayFilterFactory extends AbstractGatewayFilterFactory<JwtAuthGatewayFilterFactory.Config> {

    @Value("${gateway.filter.jwt-auth.cookie-name: jwt}")
    private String cookieName;
    @Value("${gateway.filter.jwt-auth.secret-key}")
    private String secretKey;
    @Value("${gateway.filter.jwt-auth.header-name: X-Auth}")
    private String headerName;
    @Value("${gateway.filter.jwt-auth.jwt-claims: sub}")
    private List<String> jwtClaims;

    @Data
    @AllArgsConstructor
    public static class ResponseDto {
        private int code;
        private String message;
    }

    private static final Jackson2JsonEncoder JSON_ENCODER = new Jackson2JsonEncoder();
    private static final ResponseDto UNKNOWN_ERR = new ResponseDto(ResultCode.UNKNOWN_ERR.value(), "Unknown error.");
    private static final ResolvableType RESP_OBJ_TYPE = ResolvableType.forInstance(UNKNOWN_ERR);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static SecretKey KEY = null; // postConstruct에서 초기화.

    public JwtAuthGatewayFilterFactory() {
        super(Config.class);
    }

    @PostConstruct
    private void postConstruct() {
        KEY = Keys.hmacShaKeyFor(secretKey.getBytes());
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ResponseDto responseDto = null;
            ServerHttpRequest request = exchange.getRequest();
            List<HttpCookie> httpCookies = request.getCookies().get(cookieName);
            if (!CollectionUtils.isEmpty(httpCookies)) {
                for (HttpCookie cookie : httpCookies) {
                    Claims claims = validateJwt(cookie.getValue());
                    if (claims != null) { // 비어있더라도 claims를 반환했으면 일단 jwt는 정상이다.
                        if (checkRole(config, claims)) { // 권한 검사 통과하면 헤더 작성.
                            try {
                                if (StringUtils.hasText(headerName)){
                                    String authHeader = makeAuthHeader(claims);
                                    ServerHttpRequest newRequest = request.mutate().header(headerName, authHeader).build();
                                    ServerWebExchange newExchange = exchange.mutate().request(newRequest).build();
                                    return chain.filter(newExchange); // 통과.
                                } else {
                                    return chain.filter(exchange); // downstream으로 header를 보낼 필요 없다면 그냥 통과.
                                }
                            } catch (JsonProcessingException ignored) { // serialization 실패로 인한 헤더 작성 실패.
                                responseDto = new ResponseDto(ResultCode.JWT_SERIALIZATION_ERR.value(), "JWT claims serialization failed.");
                            }
                        } else {
                            responseDto = new ResponseDto(ResultCode.NOT_ALLOWED_ROLE.value(), "Access with invalid role.");
                        }
                    }
                }
                if (responseDto == null)
                    responseDto = new ResponseDto(ResultCode.INVALID_COOKIE.value(), "'" + cookieName + "'Cookie exists but invalid.");
            } else {
                responseDto = new ResponseDto(ResultCode.COOKE_NOT_FOUND.value(), "No '" + cookieName + "' cookie found.");
            }

            // 여기까지 왔으면 통과 불가. warning 로그 기록.
            String reqPath = request.getPath().toString();
            if (CollectionUtils.isEmpty(config.unauthorizable) || !config.unauthorizable.contains(reqPath))
                log.warn("Unauthorized request. [{}: {}: {}]", reqPath, responseDto.code, responseDto.message);

            // 여기까지 왔으면 통과 불가. 401과 실패 ResponseDto를 Body로 보내준다.
            ServerWebExchangeUtils.setResponseStatus(exchange, HttpStatus.UNAUTHORIZED); // 401 OK
            ServerWebExchangeUtils.setAlreadyRouted(exchange);
            final ServerHttpResponse response = exchange.getResponse();
            response.getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString());
            return response.writeWith(JSON_ENCODER.encode(
                    Mono.just(responseDto),
                    response.bufferFactory(),
                    RESP_OBJ_TYPE,
                    MediaType.APPLICATION_JSON,
                    null
            ));
        };
    }

    /**
     * JWT를 체크해서 valid한 토큰이라면 claims 정보를 map으로 반환한다. 뽑아낼 정보가 없더라도 빈 map을 반환해야 한다.
     * invalid한 토큰이라면 바로 null을 반환한다.
     *
     * @param jwt JWT 토큰 문자열
     * @return 유효한 토큰이면 claims 정보를 담은 map객체. 유효하지 않으면 null
     */
    private Claims validateJwt(String jwt) {
        try {
            if (StringUtils.hasText(jwt)) {
                // JWT parsing
                return Jwts.parserBuilder()
                        .setSigningKey(KEY)
                        .build()
                        .parseClaimsJws(jwt)
                        .getBody(); // 비정상적이거나 만료되었다면 그에 해당하는 Exception이 발생한다.
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * JWT 에서 뽑아낸 claims 정보와 Config 에서 가져온 role 정보를 비교하여 접근 권한이 있는 지 검사한다.
     *
     * @param config JwtAuthGatewayFilterFactory.Config 객체
     * @param claims JWT 에서 뽑아낸 claims 정보.
     * @return 접근 권한이 있으면 true, 아니면 false
     */
    private boolean checkRole(Config config, Map<String, Object> claims) {
        List<String> configRoles = config.getRoles();
        if (!CollectionUtils.isEmpty(configRoles)) { // 권한 검사가 필요.
            @SuppressWarnings("unchecked") Collection<String> roles = (Collection<String>) claims.get("roles");
            if (!CollectionUtils.isEmpty(roles)) { // JWT에 role 정보가 없으면 통과 불가.
                for (String role : roles) {
                    if (configRoles.contains(role))
                        return true;
                }
            }
            return false; // 권한 검사가 필요한데 여기까지 왔으면 권한 없음.
        } else {
            return true; // 권한 검사가 필여없으면
        }
    }

    /**
     * downstream 으로 보내는 헤더를 작성하여 반환한다. 작성된 헤더는 Config 에서 설정한 claims 필드들을 JSON 포맷으로
     * serialize 한 후 base64로 encoding 한 값이다.
     *
     * @param claims JWT 에서 뽑아낸 claims 정보.
     * @return Config에 설정된 claims 정보를 JSON 포맷으로 serialize해서 base64로 encoding한 값.
     * @throws JsonProcessingException serialize 중에 발생 가능.
     */
    private String makeAuthHeader(Claims claims) throws JsonProcessingException {
        Map<String, Object> resultClaims = new HashMap<>();
        if (!CollectionUtils.isEmpty(jwtClaims)) {
            for (String claim : jwtClaims) {
                Object value = claims.get(claim);
                if (value != null)
                    resultClaims.put(claim, value);
            }
        }

        String serialized = OBJECT_MAPPER.writeValueAsString(resultClaims);
        return Base64.getEncoder().encodeToString(serialized.getBytes(StandardCharsets.UTF_8));
    }

    @Data
    public static class Config {
        private List<String> roles = null; // null 이면 권한 검사를 하지 않는다.
        private List<String> unauthorizable = null; // 인증이 안된상태로 요청을 해도 log를 남기지 않는다.
    }
}
