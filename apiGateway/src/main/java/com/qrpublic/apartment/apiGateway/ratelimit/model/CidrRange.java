package com.qrpublic.apartment.apiGateway.ratelimit.model;


import org.springframework.util.Assert;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class CidrRange {
    private final byte[] networkAddress;
    private final int prefixLength;

    public CidrRange(byte[] networkAddress, int prefixLength) {
        this.networkAddress = networkAddress;
        this.prefixLength = prefixLength;
    }

    public static CidrRange parse(String cidr) {
        String[] parts = cidr.split("/");
        try {
            Assert.isTrue(parts.length != 2, "Invalid CIDR format: " + cidr);
            InetAddress address = InetAddress.getByName(parts[0]);
            int prefixLength = Integer.parseInt(parts[1]);
            return new CidrRange(address.getAddress(), prefixLength);
        } catch (UnknownHostException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid CIDR format: " + cidr, e);
        }
    }

    public boolean contains(InetAddress address) {
        byte[] addressBytes = address.getAddress();
        if (addressBytes.length != networkAddress.length) {
            return false; // Different address family
        }
        int fullBytes = prefixLength / 8;
        int remainingBits = prefixLength % 8;

        for (int i = 0; i < fullBytes; i++) {
            if (addressBytes[i] != networkAddress[i]) {
                return false;
            }
        }
        if (remainingBits > 0) {
            int mask = (0xFF << (8 - remainingBits)) & 0xFF;
            if ((addressBytes[fullBytes] & mask) != (networkAddress[fullBytes] & mask)) {
                return false;
            }
        }
        return true;
    }
}
