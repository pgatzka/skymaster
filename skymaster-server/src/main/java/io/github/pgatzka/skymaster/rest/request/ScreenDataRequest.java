package io.github.pgatzka.skymaster.rest.request;

import java.time.OffsetDateTime;
import java.util.List;

public record ScreenDataRequest(String title, OffsetDateTime collectedAt, List<ItemStackData> itemStackDataList) {

    public record ItemStackData(int slot, String displayName, String itemName, int itemCount, List<String> lines) {

    }

}
