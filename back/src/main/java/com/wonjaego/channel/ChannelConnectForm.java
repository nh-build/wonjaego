package com.wonjaego.channel;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChannelConnectForm {

    @NotNull
    private ChannelType channelType = ChannelType.ZIGZAG;

    private String accessKey;

    private String secretKey;
}
