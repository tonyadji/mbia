package com.lehnade.archfixtures.rule1.family.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DomainUsingJackson {

    @JsonProperty("name")
    private String name;
}
