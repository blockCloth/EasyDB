package com.dyx.simpledb.util;

import org.springframework.web.socket.WebSocketSession;

import javax.servlet.http.HttpServletRequest;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public class IpUtil {
    public static String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && ip.length() != 0 && !"unknown".equalsIgnoreCase(ip)) {
            // 多次反向代理后会有多个IP值，第一个为真实IP
            if (ip.contains(",")) {
                ip = ip.split(",")[0].trim();
            }
        } else {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        // 检查是否为IPv6的本地地址，如果是则转换为IPv4的本地地址
        if ("0:0:0:0:0:0:0:1".equals(ip)) {
            ip = "127.0.0.1";
        }

        return ip;
    }

    public static String getClientIp(WebSocketSession session) {
        InetSocketAddress remoteAddress = session.getRemoteAddress();
        InetAddress inetAddress = remoteAddress.getAddress();
        String clientIp = inetAddress.getHostAddress();

        // Check if the address is IPv4
        if (inetAddress instanceof Inet4Address) {
            return clientIp;
        }

        // Handle IPv6 to IPv4 mapping
        if (inetAddress instanceof Inet6Address) {
            String ipv6 = clientIp;
            if (ipv6.startsWith("::ffff:")) {
                return ipv6.substring(7);
            }
        }

        return clientIp;
    }
}