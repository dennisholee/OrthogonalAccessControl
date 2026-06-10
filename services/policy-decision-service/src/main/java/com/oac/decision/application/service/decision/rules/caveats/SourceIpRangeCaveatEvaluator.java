package com.oac.decision.application.service.decision.rules.caveats;

import com.oac.decision.application.service.decision.CaveatEvaluator;
import com.oac.decision.application.service.decision.DecisionContext;
import com.oac.decision.model.AttributeAccessMap;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Evaluates SOURCE_IP_RANGE caveats. A policy with this caveat only applies
 * when the request originates from within a specified CIDR range or explicit IP.
 * Supports multiple allowed ranges/addresses.
 */
public class SourceIpRangeCaveatEvaluator implements CaveatEvaluator {

    @Override
    public boolean evaluate(DecisionContext context, Map<String, Object> caveatParams) {
        // Extract source IP from runtime context
        String clientIp = stringParam(context.resolvedRuntimeContext(), "clientIp");
        if (clientIp == null) {
            return false;
        }

        Object allowedRanges = caveatParams.get("allowedRanges");
        if (allowedRanges instanceof List<?> rangeList) {
            for (Object range : rangeList) {
                if (isIpInRange(clientIp, range.toString())) {
                    return true;
                }
            }
        }

        // Also check single CIDR or IP parameter
        String cidrStr = stringParam(caveatParams, "cidr");
        if (cidrStr != null && isIpInRange(clientIp, cidrStr)) {
            return true;
        }

        return false;
    }

    private boolean isIpInRange(String ip, String cidr) {
        try {
            InetAddress address = InetAddress.getByName(ip);
            byte[] addrBytes = address.getAddress();

            if (cidr.contains("/")) {
                String[] parts = cidr.split("/");
                InetAddress subnet = InetAddress.getByName(parts[0]);
                int prefixLength = Integer.parseInt(parts[1]);
                byte[] subnetBytes = subnet.getAddress();

                if (addrBytes.length != subnetBytes.length) {
                    return false;
                }

                int fullBytes = prefixLength / 8;
                int remainingBits = prefixLength % 8;

                for (int i = 0; i < fullBytes; i++) {
                    if (addrBytes[i] != subnetBytes[i]) {
                        return false;
                    }
                }

                if (remainingBits > 0) {
                    int mask = (0xFF << (8 - remainingBits)) & 0xFF;
                    return (addrBytes[fullBytes] & mask) == (subnetBytes[fullBytes] & mask);
                }

                return true;
            } else {
                return ip.equals(cidr);
            }
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private String stringParam(Map<String, Object> params, String key) {
        Object val = params.get(key);
        return val == null ? null : val.toString();
    }
}