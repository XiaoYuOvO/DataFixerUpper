// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
package com.mojang.datafixers.types.constant;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.types.templates.TypeTemplate;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;

import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class EmptyPartSaving extends com.mojang.datafixers.types.Type<Dynamic<?>> {
    @Override
    public String toString() {
        return "EmptyPartSaving";
    }

    @Override
    public Optional<Dynamic<?>> point(final DynamicOps<?> ops) {
        return Optional.of(capEmpty(ops));
    }

    private <T> Dynamic<T> capEmpty(final DynamicOps<T> ops) {
        return new Dynamic<>(ops, ops.emptyMap());
    }

    @Override
    public boolean equals(final Object o, final boolean ignoreRecursionPoints, final boolean checkIndex) {
        return this == o;
    }

    @Override
    public TypeTemplate buildTemplate() {
        return DSL.constType(this);
    }

    @Override
    public <T> Pair<T, Optional<Dynamic<?>>> read(final DynamicOps<T> ops, final T input) {
        return Pair.of(ops.empty(), Optional.of(new Dynamic<>(ops, input)));
    }

    @Override
    public final <T> DataResult<T> write(final DynamicOps<T> ops, final T rest, final Dynamic<?> value) {
        if (value.getValue() == value.getOps().empty()) {
            // nothing to merge, return rest
            return DataResult.success(rest);
        }

        final T casted = value.cast(ops);
        if (rest == ops.empty()) {
            // no need to merge anything, return the old value
            return DataResult.success(casted);
        }

        final Optional<Stream<Pair<T, T>>> map = ops.getMapValues(casted);
        if (map.isPresent()) {
            return ops.mergeInto(rest, map.get().collect(Collectors.toMap(Pair::getFirst, Pair::getSecond)));
        }

        final Optional<Stream<T>> stream = ops.getStream(casted);
        if (stream.isPresent()) {
            return ops.mergeInto(rest, stream.get().collect(Collectors.toList()));
        }

        return DataResult.error("Don't know how to merge " + rest + " and " + casted, rest);
    }
}