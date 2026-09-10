package co.za.kandkmedia.payroll.domain;

/** Mirrors the payroll approval pipeline from the system spec. */
public enum PayrollStatus {
    DRAFT,
    REVIEWED,
    APPROVED,
    FINALIZED,
    PUBLISHED,
    SENT
}
