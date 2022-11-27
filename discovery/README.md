# discovery

default 설정은 아래와 같다.

- 기본 default 설정은 standalone 으로 실행했을 때 정상동작 하는 값으로 설정되어있다.

```yml
spring:
  application:
    name: CLOUD-DISCOVERY

eureka:
  instance:
    hostname: localhost
    prefer-ip-address: true
    lease-renewal-interval-in-seconds: 10 # discovery한테 10초마다 하트비트 전송
    lease-expiration-duration-in-seconds: 20 # discovery는 서비스를 해제 하기 전에 20초까지 기다림
  client:
    register-with-eureka: false
    fetch-registry: false
    service-url:
      # 'default-zone'으로 지정하면 안됨.
      defaultZone: http://${eureka.instance.hostname}:${server.port}/eureka/

server:
  port: 8761

management:
  endpoints:
    web:
      exposure:
        include: info, health, metrics

logging:
  config: classpath:logback.xml
```

- 만약 2대 이상의 instance 를 구동하도록 설정한다면 각 instance 의 설정은 아래와 같이 해준다.
- jar 파일과 같은 위치에 application.yml 파일을 추가하여 기본 설정값을 overwrite 하도록 한다.
- 각 instance 의 설정은 아래 설정에서 hostname 를 자신의 값으로 바꿔준다.
- defaultZone 에는 모든 instance 의 uri 를 우선순위에 따라 차례로 지정한다.
- 'defaultZone' 키값 naming convention 을 반드시 camel 케이스로 할 것.

```yml
eureka:
  instance:
    hostname: 192.168.1.156
  client:
    register-with-eureka: true
    fetch-registry: true
    service-url:
      # 'default-zone' 으로 설정하면 안됨.
      defaultZone: http://192.168.1.156:8761/eureka/, http://192.168.1.103:8761/eureka/
```