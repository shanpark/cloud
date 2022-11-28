package shanpark.cloud.gateway.consts;

/**
 * Gateway가 응답을 반환하는 경우 사용하는 응답 코드 선언 enum 클래스.
 * <p>
 * 현재는 JwtAuthGatewayFilter 밖에 사용하지 않지만 추후에 다른 Filter 에서도 사용하도록 한다.
 */
public enum ResultCode {
    COOKE_NOT_FOUND(-1),
    INVALID_COOKIE(-2),
    NOT_ALLOWED_ROLE(-3),
    JWT_SERIALIZATION_ERR(-4),
    UNKNOWN_ERR(-99);

    private final int value;

    ResultCode(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
