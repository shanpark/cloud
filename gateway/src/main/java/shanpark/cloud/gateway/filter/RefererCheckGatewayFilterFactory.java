package shanpark.cloud.gateway.filter;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * Referer 체크를 통해서 현재 서비스 범위 내에서 온 요청인 경우 통과 시키고 그렇지 않은 경우 통과 시키지 않는다.
 * 주소창에서 직접 입력한 요청은 refer가 없으므로 통과하지 못하고 페이지 내에서 요청된 이미지 같은 경우 통과될 것이다.
 */
@Slf4j
@Component
public class RefererCheckGatewayFilterFactory extends AbstractGatewayFilterFactory<RefererCheckGatewayFilterFactory.Config> {

    public RefererCheckGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            if (config.referers == null) // referers가 지정되지 않으면 검사를 하지 않는다.
                return chain.filter(exchange); // 무조건 통과.

            ServerHttpRequest request = exchange.getRequest();
            List<String> referers = request.getHeaders().get("referer");
            if (!CollectionUtils.isEmpty(referers)) { // referer 헤더가 없으면 통과 불가.
                for (String referer : referers) {
                    for (String possibleReferer : config.referers) {
                        if (referer.startsWith(possibleReferer))
                            return chain.filter(exchange); // config의 referer값으로 시작되는 값이면 통과.
                    }
                }
            }

            // 여기까지 왔으면 통과 불가. warning 로그 기록.
            String reqPath = request.getPath().toString();
            log.warn("Unknown referer. ([ref:{}] => [req:{}])", referers, reqPath);

            // 여기까지 왔으면 통과 불가. Status 404를 보낸다. referer는 auth필터와 달리 따로 Body는 없다.
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.NOT_FOUND);
            return response.setComplete();
        };
    }

    @Data
    public static class Config {
        private List<String> referers = null; // null 이면 검사를 하지 않는다.
    }
}
