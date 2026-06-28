package com.zerodtree.gsad.config;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CidrRangeTest {

    @Test
    void contains_matchesNetBirdOverlay() throws Exception {
        CidrRange range = CidrRange.parse("100.67.0.0/16");
        InetAddress netbirdIp = InetAddress.getByName("100.67.167.35");

        assertThat(range.contains(netbirdIp)).isTrue();
        assertThat(range.contains(InetAddress.getByName("100.68.1.1"))).isFalse();
    }

    @Test
    void parseList_trimsAndSkipsBlanks() {
        List<CidrRange> ranges = CidrRange.parseList(" 100.67.0.0/16 , 10.0.0.0/8 ");

        assertThat(ranges).hasSize(2);
    }

    @Test
    void parse_rejectsInvalidPrefix() {
        assertThatThrownBy(() -> CidrRange.parse("10.0.0.0/33"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prefix");
    }

    @Test
    void parse_rejectsMissingSlash() {
        assertThatThrownBy(() -> CidrRange.parse("10.0.0.0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid CIDR");
    }
}
