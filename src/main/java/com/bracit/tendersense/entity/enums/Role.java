package com.bracit.tendersense.entity.enums;

/**
 * What an account may do. A company's tender team is USER; TenderSense staff are ADMIN.
 * An account belonging to a company that is also ADMIN gets both screens.
 */
public enum Role {
    USER,
    ADMIN
}
