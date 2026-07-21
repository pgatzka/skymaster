package io.github.pgatzka.skymaster.config;

import com.google.gson.annotations.Expose;
import io.github.notenoughupdates.moulconfig.Config;
import io.github.notenoughupdates.moulconfig.annotations.Accordion;
import io.github.notenoughupdates.moulconfig.annotations.Category;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider;
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorText;
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption;
import lombok.Getter;

@Getter
public class SkyMasterConfig extends Config {

    @Expose
    @Category(name = "Data Collection", desc = "Data Collection settings")
    public DataCollectionCategory dataCollection = new DataCollectionCategory();

    @Getter
    public static class DataCollectionCategory {

        @Expose
        @ConfigOption(
                name = "Enable SkyBlock data collection",
                desc = "Toggles collection of Hypixel SkyBlock game data")
        @ConfigEditorBoolean
        public boolean enabled = true;

        @Expose
        @Accordion
        @ConfigOption(
                name = "Data Collection server",
                desc = "Options for the data collection server, changing those options requires a restart of the game")
        public DataCollectionHostCategory dataCollectionHost = new DataCollectionHostCategory();

        @Expose
        @ConfigOption(
                name = "Handshake interval",
                desc = "Configures the interval of handshakes with the data collection server in seconds")
        @ConfigEditorSlider(minValue = 15, maxValue = 6000, minStep = 1)
        public int handshakeIntervalSeconds = 60;

        @Getter
        public static class DataCollectionHostCategory {

            @Expose
            @ConfigOption(name = "Host", desc = "Host where collected data is sent")
            @ConfigEditorText
            public String host = "localhost";

            @Expose
            @ConfigEditorSlider(minValue = 8080, maxValue = 9090, minStep = 1)
            @ConfigOption(name = "Port", desc = "Port of the data collection host")
            public int port = 8080;
        }
    }
}
