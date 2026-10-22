package com.riddleslabs.trails;

import com.riddleslabs.paper.api.command.argument.TypedArguments;

public final class TrailArguments {
    private TrailArguments() {}
    public static String routeId(String value) {
        String id=TypedArguments.identifier(48).parse(value);
        if(!id.matches("[a-z0-9][a-z0-9_-]{0,47}")) throw new IllegalArgumentException("Invalid route ID");
        return id;
    }
    public static int npcId(String value) {
        return TypedArguments.integer(Integer.MIN_VALUE,Integer.MAX_VALUE).parse(value);
    }
    public static double coordinate(String value) {
        return TypedArguments.finiteDouble().parse(value);
    }
}
