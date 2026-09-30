package com.ferrocompras.ordenescompra.dto;

import java.util.List;

public record ListEnvelope<T>(String object, List<T> data, boolean hasMore) {

    public static <T> ListEnvelope<T> of(List<T> data, boolean hasMore) {
        return new ListEnvelope<>("list", data, hasMore);
    }
}
