package com.dyx.simpledb.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.beans.factory.annotation.Autowired;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    // WebSocket处理器，用于处理WebSocket消息
    private final TerminalWebSocketHandler terminalWebSocketHandler;
    // 握手拦截器，用于在WebSocket握手时进行一些操作
    private final HttpSessionHandshakeInterceptor httpSessionHandshakeInterceptor;

    // 使用@Autowired注解，让Spring自动注入我们需要的bean
    @Autowired
    public WebSocketConfig(TerminalWebSocketHandler terminalWebSocketHandler,
                           HttpSessionHandshakeInterceptor httpSessionHandshakeInterceptor) {
        this.terminalWebSocketHandler = terminalWebSocketHandler;
        this.httpSessionHandshakeInterceptor = httpSessionHandshakeInterceptor;
    }

    // 重写registerWebSocketHandlers方法，以便我们可以自定义WebSocket的配置
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 添加一个WebSocket处理器，并设置其路径为"/terminal"
        registry.addHandler(terminalWebSocketHandler, "/terminal")
                .addInterceptors(httpSessionHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}