package shanpark.cloud.gateway.filter;

import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Filter를 통과하는 요청들을 로그에 남긴다.
 * <p>
 * 아래와 예시와 같은 형태로 간단한 로그를 남긴다.
 * - Request 로그 예
 * => dfa28fbc-43: /rest/hello
 * - Response 로그 예
 * <= dfa28fbc-43: /rest/hello [200 OK]
 */
@Component
class LoggerGatewayFilterFactory extends AbstractGatewayFilterFactory<LoggerGatewayFilterFactory.Config> {

    private static final Logger log = LoggerFactory.getLogger("C"); // Logger를 짧게 쓰려고 @Slf4j 사용안함.

    public LoggerGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String requestId = request.getId();
            String requestPath = request.getPath().toString();

            log.info("=> {}: {}", requestId, requestPath);

            //Custom Post Filter
            return chain.filter(exchange)
                    .then(Mono.fromRunnable(() ->
                            log.info("<= {}: {} [{}]", requestId, requestPath, exchange.getResponse().getStatusCode())
                    ));
        };
    }

    @Data
    public static class Config {
    }
}