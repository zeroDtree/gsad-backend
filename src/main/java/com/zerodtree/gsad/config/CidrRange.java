package com.zerodtree.gsad.config;

import org.springframework.util.StringUtils;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * IPv4 CIDR range for agent bind validation.
 */
public final class CidrRange {

    private final int networkAddress;
    private final int prefixLength;

    private CidrRange(int networkAddress, int prefixLength) {
        this.networkAddress = networkAddress;
        this.prefixLength = prefixLength;
    }

    public static CidrRange parse(String cidr) {
        if (!StringUtils.hasText(cidr)) {
            throw new IllegalArgumentException("CIDR must not be blank");
        }
        String trimmed = cidr.trim();
        int slash = trimmed.indexOf('/');
        if (slash <= 0 || slash == trimmed.length() - 1) {
            throw new IllegalArgumentException("Invalid CIDR (expected a.b.c.d/prefix): " + trimmed);
        }

        String hostPart = trimmed.substring(0, slash);
        int prefix;
        try {
            prefix = Integer.parseInt(trimmed.substring(slash + 1));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid CIDR prefix: " + trimmed, ex);
        }
        if (prefix < 0 || prefix > 32) {
            throw new IllegalArgumentException("CIDR prefix must be 0–32: " + trimmed);
        }

        int address = parseIpv4(hostPart);
        int mask = prefix == 0 ? 0 : 0xFFFFFFFF << (32 - prefix);
        return new CidrRange(address & mask, prefix);
    }

    public static List<CidrRange> parseList(String commaSeparated) {
        if (!StringUtils.hasText(commaSeparated)) {
            return List.of();
        }
        List<CidrRange> ranges = new ArrayList<>();
        for (String part : commaSeparated.split(",")) {
            if (StringUtils.hasText(part)) {
                ranges.add(parse(part.trim()));
            }
        }
        return ranges;
    }

    public boolean contains(InetAddress address) {
        if (!(address instanceof Inet4Address inet4)) {
            return false;
        }
        int host = toInt(inet4);
        if (prefixLength == 0) {
            return true;
        }
        int mask = 0xFFFFFFFF << (32 - prefixLength);
        return (host & mask) == networkAddress;
    }

    private static int parseIpv4(String host) {
        try {
            InetAddress address = InetAddress.getByName(host);
            if (!(address instanceof Inet4Address)) {
                throw new IllegalArgumentException("IPv4 CIDR required, got: " + host);
            }
            return toInt((Inet4Address) address);
        } catch (UnknownHostException ex) {
            throw new IllegalArgumentException("Invalid IPv4 address in CIDR: " + host, ex);
        }
    }

    private static int toInt(Inet4Address address) {
        byte[] octets = address.getAddress();
        return ((octets[0] & 0xFF) << 24)
                | ((octets[1] & 0xFF) << 16)
                | ((octets[2] & 0xFF) << 8)
                | (octets[3] & 0xFF);
    }
}
