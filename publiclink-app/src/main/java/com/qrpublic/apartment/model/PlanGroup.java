package com.qrpublic.apartment.model;

import lombok.Getter;

/**
 * Purpose group of a payment plan. The catalog is split in two because a yoga
 * or badminton booking link and a normal product sale link cost different
 * amounts for the same duration, and an account that bought the wrong kind
 * would otherwise sit on money it cannot spend.
 *
 * <p>
 * The group is decided once, when the visitor registers, and it is enforced
 * when an environment is created: the plan that supplies the link slot has to
 * belong to the group the product type belongs to.
 */
@Getter
public enum PlanGroup {
    /** Plain product sale links. */
    PRODUCT("Normal product links"),
    /** Yoga class and badminton court booking links. */
    BOOKING("Yoga & badminton booking links");

    /**
     * Product types that count as bookings. Mirrors the constants on
     * {@code CoreYogaEnvironmentService}; repeated here so the model layer does
     * not have to reach up into a core service.
     */
    private static final String TYPE_YOGA = "YOGA_BOOKING";
    private static final String TYPE_BADMINTON = "BADMINTON_COURT";

    private final String label;

    PlanGroup(String label) {
        this.label = label;
    }

    /**
     * The group an account's registration purpose maps to. Lenient on purpose:
     * every account created before the split exists with no purpose at all, and
     * it must keep seeing exactly the product catalog it saw before.
     */
    public static PlanGroup fromPurpose(String purpose) {
        return BOOKING.name().equalsIgnoreCase(trim(purpose)) ? BOOKING : PRODUCT;
    }

    /**
     * The group a product type has to be paid for by. An absent product is a
     * plain product link, which is what every legacy environment is.
     */
    public static PlanGroup fromProductType(String productType) {
        String type = trim(productType);
        if (TYPE_YOGA.equalsIgnoreCase(type) || TYPE_BADMINTON.equalsIgnoreCase(type)) {
            return BOOKING;
        }
        return PRODUCT;
    }

    /** Catalog name of a product type's group, blank-safe for stored rows. */
    public static String nameOf(String purpose) {
        return fromPurpose(purpose).name();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
