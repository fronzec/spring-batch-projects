package com.fronzec.singlethreaded.group;

/**
 * Because we are using the ordinal mapping for the entity for rapid development
 * is crucial to add new status bellow the already created
 * Other more robust approach is use custom Attribute Converter see https://thorben-janssen.com/hibernate-enum-mappings/
 */
public enum GroupedDispatchStatuses {
    PENDING,
    SENT
}