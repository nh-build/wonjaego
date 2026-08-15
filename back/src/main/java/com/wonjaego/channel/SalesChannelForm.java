package com.wonjaego.channel;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SalesChannelForm {

    @NotBlank
    private String name;

    public static SalesChannelForm from(SalesChannel channel) {
        SalesChannelForm form = new SalesChannelForm();
        form.setName(channel.getName());
        return form;
    }
}
