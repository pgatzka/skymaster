package io.github.pgatzka.skymaster.rest.request;

import java.time.OffsetDateTime;
import java.util.List;

public record ScreenDataRequest(String title, OffsetDateTime collectedAt, List<ItemStackData> itemStackDataList,
                                Collector collector) {

    public record ItemStackData(int slot, String displayName, String itemName, Integer itemCount, List<String> lines) {

    }

    public record Collector(String id, String name) {

    }
}
