package io.github.pgatzka.skymaster;

public interface IModule {

    default void onInitializeClient() {
        // empty by default
    }
}
