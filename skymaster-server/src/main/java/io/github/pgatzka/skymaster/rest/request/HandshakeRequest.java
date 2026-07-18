package io.github.pgatzka.skymaster.rest.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HandshakeRequest {

    @NotEmpty
    @Pattern(regexp = "^\\w{8}-\\w{4}-\\w{4}-\\w{4}-\\w{12}$")
    private String uuid;

    @NotEmpty
    private String username;

    @NotEmpty
    @Pattern(regexp = "^\\d+\\.\\d+\\.\\d+(-SNAPSHOT)?")
    private String version;

}
