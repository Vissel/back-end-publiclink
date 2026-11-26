package com.qrpublic.apartment.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two mappings the whole plan split rests on: what a stored purpose means,
 * and which kind of plan a product type has to be paid for by.
 */
class PlanGroupTest {

    @Test
    void bookingProductTypesNeedBookingPlans() {
        assertThat(PlanGroup.fromProductType("YOGA_BOOKING")).isEqualTo(PlanGroup.BOOKING);
        assertThat(PlanGroup.fromProductType("BADMINTON_COURT")).isEqualTo(PlanGroup.BOOKING);
    }

    /**
     * An untyped product is what every environment created before the split is,
     * so defaulting it to anything but PRODUCT would retroactively break them.
     */
    @Test
    void standardAndUnknownProductTypesStayProduct() {
        assertThat(PlanGroup.fromProductType("STANDARD")).isEqualTo(PlanGroup.PRODUCT);
        assertThat(PlanGroup.fromProductType(null)).isEqualTo(PlanGroup.PRODUCT);
        assertThat(PlanGroup.fromProductType("")).isEqualTo(PlanGroup.PRODUCT);
        assertThat(PlanGroup.fromProductType("SOMETHING_NEW")).isEqualTo(PlanGroup.PRODUCT);
    }

    @Test
    void purposeIsCaseAndSpaceTolerant() {
        assertThat(PlanGroup.fromPurpose(" booking ")).isEqualTo(PlanGroup.BOOKING);
        assertThat(PlanGroup.fromPurpose("BOOKING")).isEqualTo(PlanGroup.BOOKING);
    }

    /** Legacy accounts have no stored purpose and must keep today's catalog. */
    @Test
    void missingPurposeReadsAsProduct() {
        assertThat(PlanGroup.fromPurpose(null)).isEqualTo(PlanGroup.PRODUCT);
        assertThat(PlanGroup.fromPurpose("")).isEqualTo(PlanGroup.PRODUCT);
        assertThat(PlanGroup.fromPurpose("garbage")).isEqualTo(PlanGroup.PRODUCT);
    }
}
