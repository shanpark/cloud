package shanpark.cloud.gateway.filter;

import lombok.Data;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Gateway의 Health check용 filter 구현.
 * <p>
 * 다른 서버로 전달이 되지 않기때문에 application.yml 파일에서 filters 설정의 uri 필드는 사용되지 않는다.
 * 하지만 uri 필드가 필수이기 때문에 자기 자신을 가리키도록 설정해준다.
 * 일부 하드웨어 스위치가 healthcheck를 위해 필요로 하는 경우가 있다.
 */
@Component
public class HealthCheckGatewayFilterFactory extends AbstractGatewayFilterFactory<HealthCheckGatewayFilterFactory.Config> {

    @Data
    public static class Status {
        private String status = "UP";
    }

    private static final Jackson2JsonEncoder JSON_ENCODER = new Jackson2JsonEncoder();
    private static final Status STAT_UP = new Status(); // Response body object.
    private static final ResolvableType RESP_OBJ_TYPE = ResolvableType.forInstance(STAT_UP);

    public HealthCheckGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerWebExchangeUtils.setResponseStatus(exchange, HttpStatus.OK); // 200 OK
            ServerWebExchangeUtils.setAlreadyRouted(exchange);
            return chain.filter(exchange).then(Mono.defer(() -> {
                final ServerHttpResponse response = exchange.getResponse();
                response.getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString());
                return response.writeWith(JSON_ENCODER.encode(
                        Mono.just(STAT_UP),
                        response.bufferFactory(),
                        RESP_OBJ_TYPE,
                        MediaType.APPLICATION_JSON,
                        null
                ));
            }));
        };
    }

    @Data
    public static class Config {
    }
}
