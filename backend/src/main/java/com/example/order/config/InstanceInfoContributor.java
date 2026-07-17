package com.example.order.config;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Exposes the backing replica identity on {@code /actuator/info} so the
 * frontend "runtime status" page can show which order-svc replica answered a
 * given request. Because the nginx gateway round-robins, refreshing the page
 * across replicas makes the P4 load-balancing visible in the UI.
 */
@Component
public class InstanceInfoContributor implements InfoContributor {

    @Override
    public void contribute(Info.Builder builder) {
        String host = "unknown";
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ignored) {
            // fall through to "unknown"
        }
        builder.withDetail("instanceId", host);
        builder.withDetail("app", "order-platform");
    }
}
